package com.ruuvi.tag.service;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.neovisionaries.bluetooth.ble.advertising.ADManufacturerSpecific;
import com.neovisionaries.bluetooth.ble.advertising.ADPayloadParser;
import com.neovisionaries.bluetooth.ble.advertising.ADStructure;
import com.neovisionaries.bluetooth.ble.advertising.EddystoneURL;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.ruuvi.tag.R;
import com.ruuvi.tag.feature.main.MainActivity;
import com.ruuvi.tag.model.Alarm;
import com.ruuvi.tag.model.LeScanResult;
import com.ruuvi.tag.model.RuuviTag;
import com.ruuvi.tag.model.RuuviTag_Table;
import com.ruuvi.tag.model.ScanEvent;
import com.ruuvi.tag.model.ScanEventSingle;
import com.ruuvi.tag.model.TagSensorReading;
import com.ruuvi.tag.util.ComplexPreferences;
import com.ruuvi.tag.util.DeviceIdentifier;
import com.ruuvi.tag.util.Foreground;
import com.ruuvi.tag.model.RuuviTagComplexList;

import static com.ruuvi.tag.RuuviScannerApplication.useNewApi;


public class ScannerService extends Service /*implements BeaconConsumer*/ {
    private static final String TAG = "ScannerService";
    private ArrayList<LeScanResult> scanResults;
    private ScheduledExecutorService scheduler;
    private ScheduledExecutorService alertScheduler;

    List<RuuviTag> ruuviTagArrayList;
    SharedPreferences settings;
    private String backendUrl;
    private Integer[] alertValues;
    private int notificationId;
    private int MAX_NUM_NOTIFICATIONS = 5;
    private Timer timer;
    private NotificationCompat.Builder notification;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private List<ScanFilter> scanFilters = new ArrayList<ScanFilter>();
    private ScanSettings scanSettings;

    private Handler scanTimerHandler;
    private static int MAX_SCAN_TIME_MS = 1300;
    private boolean scanning;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.registerOnSharedPreferenceChangeListener(mListener);
        notificationId = 1512;

        if (settings.getBoolean("pref_bgscan", false))
            startFG();

        Foreground.init(getApplication());
        Foreground.get().addListener(listener);

        ruuviTagArrayList = new ArrayList<>();

        backendUrl = settings.getString("pref_backend", null);
        scanTimerHandler = new Handler();
        scanResults = new ArrayList<LeScanResult>();

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (useNewApi()) {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
            scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
            scanSettings = scanSettingsBuilder.build();
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        int scanInterval = Integer.parseInt(settings.getString("pref_scaninterval", "5")) * 1000;
        if (scanInterval <= MAX_SCAN_TIME_MS) scanInterval = MAX_SCAN_TIME_MS + 100;

        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (!scheduler.isShutdown())
                    startScan();
            }
        }, 0, scanInterval, TimeUnit.MILLISECONDS);

        timer = new Timer();
        TimerTask alertManager = new ScannerService.alertManager();
        timer.scheduleAtFixedRate(alertManager, 2500, 2500);
    }

    private void startScan() {
        if (scanning)
            return;

        scanTimerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scanning = false;
                if (useNewApi()) {
                    bleScanner.stopScan(bleScannerCallback);
                } else {
                    bluetoothAdapter.stopLeScan(mLeScanCallback);
                }
                processFoundDevices();
                scanResults = new ArrayList<LeScanResult>();
            }
        }, MAX_SCAN_TIME_MS);

        scanning = true;

        if (useNewApi()) {
            bleScanner.startScan(scanFilters, scanSettings, bleScannerCallback);
        } else {
            bluetoothAdapter.startLeScan(mLeScanCallback);
        }
    }

    @SuppressLint("NewApi")
    private ScanCallback bleScannerCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            foundDevice(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());
        }
    };

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
        new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                foundDevice(device, rssi, scanRecord);
            }
        };

    private void foundDevice(BluetoothDevice device, int rssi, byte[] data) {
            Iterator<LeScanResult> itr = scanResults.iterator();
            LeScanResult dev = new LeScanResult();
            dev.device = device;
            dev.rssi = rssi;
            dev.scanData = data;

            boolean devFound = false;
            while (itr.hasNext()) {
                LeScanResult element = itr.next();
                if (device.getAddress().equalsIgnoreCase(element.device.getAddress()))
                    devFound = true;
            }

            if (!devFound) {
                Log.d(TAG, "found: " + device.getAddress());
                scanResults.add(dev);
            }
    }

    void processFoundDevices() {
        //ruuviTagArrayList.clear();
        ScanEvent scanEvent = new ScanEvent(getApplicationContext(), DeviceIdentifier.id(getApplicationContext()));

        Iterator<LeScanResult> itr = scanResults.iterator();
        while (itr.hasNext()) {
            LeScanResult element = itr.next();

            // Parse the payload of the advertisement packet
            // as a list of AD structures.
            List<ADStructure> structures =
                    ADPayloadParser.getInstance().parse(element.scanData);

            // For each AD structure contained in the advertisement packet.
            for (ADStructure structure : structures) {
                if (structure instanceof EddystoneURL) {
                    // Eddystone URL
                    EddystoneURL es = (EddystoneURL) structure;
                    if (es.getURL().toString().startsWith("https://ruu.vi/#") || es.getURL().toString().startsWith("https://r/")) {
                        RuuviTag tag = new RuuviTag(element.device.getAddress(), es.getURL().toString(), null, element.rssi, false);
                        addFoundTagToLists(tag, scanEvent);
                    }
                }
                // If the AD structure represents Eddystone TLM.
                else if (structure instanceof ADManufacturerSpecific) {
                    ADManufacturerSpecific es = (ADManufacturerSpecific) structure;
                    if (es.getCompanyId() == 0x0499) {
                        byte[] data = es.getData();
                        if (data != null) {
                            RuuviTag tag = new RuuviTag(element.device.getAddress(), null, data, element.rssi, false);
                            addFoundTagToLists(tag, scanEvent);
                        }
                    }
                }
            }
        }

        Log.d(TAG, "Found " + scanEvent.tags.size() + " tags");
        exportRuuviTags();

        ScanEvent eventBatch = new ScanEvent(scanEvent.deviceId, scanEvent.time);
        for (int i = 0; i < scanEvent.tags.size(); i++) {
            RuuviTag tagFromDb = RuuviTag.get(scanEvent.tags.get(i).id);
            // don't send data about tags not in "My RuuviTags" list
            if (tagFromDb == null) continue;

            eventBatch.tags.add(tagFromDb);

            if (tagFromDb.gatewayUrl != null && !tagFromDb.gatewayUrl.isEmpty()) {
                // send the single tag to its gateway
                ScanEventSingle single = new ScanEventSingle(scanEvent.deviceId, scanEvent.time);
                single.tag = tagFromDb;

                Ion.with(getApplicationContext())
                        .load(tagFromDb.gatewayUrl)
                        .setJsonPojoBody(single)
                        .asJsonObject()
                        .setCallback(new FutureCallback<JsonObject>() {
                            @Override
                            public void onCompleted(Exception e, JsonObject result) {
                                if (e != null) {
                                    Log.e(TAG, "Sending failed.");
                                }
                            }
                        });
            }
        }
        if (backendUrl != null && eventBatch.tags.size() > 0)
        {
            Ion.with(getApplicationContext())
                    .load(backendUrl)
                    .setJsonPojoBody(eventBatch)
                    .asJsonObject()
                    .setCallback(new FutureCallback<JsonObject>() {
                        @Override
                        public void onCompleted(Exception e, JsonObject result) {
                            if (e != null) {
                                Log.e(TAG, "Sending failed.");
                            }
                        }
                    });
        }
        exportRuuviTags();
    }

    public SharedPreferences.OnSharedPreferenceChangeListener mListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (settings.getBoolean("pref_bgscan", false))
                startFG();

            if (!settings.getBoolean("pref_bgscan", false))
                stopForeground(true);

            backendUrl = settings.getString("pref_backend", null);
            scheduler.shutdown();
            scheduler = Executors.newSingleThreadScheduledExecutor();
            int scanInterval = Integer.parseInt(settings.getString("pref_scaninterval", "5")) * 1000;
            if (scanInterval <= MAX_SCAN_TIME_MS) scanInterval = MAX_SCAN_TIME_MS + 1;
            scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    if (!scheduler.isShutdown())
                        startScan();
                }
            }, 0, scanInterval, TimeUnit.MILLISECONDS);
        }
    };

    public void startFG() {
        Intent notificationIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        NotificationCompat.Builder notification;
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        notification
                = new NotificationCompat.Builder(getApplicationContext())
                .setContentTitle(this.getString(R.string.scanner_notification_title))
                .setSmallIcon(R.mipmap.ic_launcher_small)
                .setTicker(this.getString(R.string.scanner_notification_ticker))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(this.getString(R.string.scanner_notification_message)))
                .setContentText(this.getString(R.string.scanner_notification_message))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setLargeIcon(bitmap)
                .setContentIntent(pendingIntent);

        startForeground(notificationId, notification.build());
    }

    private void exportRuuviTags() {
        ArrayList<RuuviTag> templist = new ArrayList<>();
        RuuviTagComplexList ruuvilist = new RuuviTagComplexList();
        for (RuuviTag ruuviTag : ruuviTagArrayList) {
            if (!ruuviTag.favorite)
                templist.add(ruuviTag);
        }
        ruuvilist.setRuuviTags(templist);
        ComplexPreferences complexPreferences = ComplexPreferences
                .getComplexPreferences(this, "saved_tags", MODE_PRIVATE);
        complexPreferences.putObject("ruuvi", ruuvilist);
        complexPreferences.commit();
    }

    private int checkForSameTag(List<RuuviTag> arr, RuuviTag ruuvi) {
        for (int i = 0; i < arr.size(); i++) {
            if (ruuvi.id.equals(arr.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onDestroy() {
        scheduler.shutdown();
        settings.unregisterOnSharedPreferenceChangeListener(mListener);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    Foreground.Listener listener = new Foreground.Listener() {
        public void onBecameForeground() {
            if (!isRunning(ScannerService.class))
                startService(new Intent(ScannerService.this, ScannerService.class));
        }

        public void onBecameBackground() {
            if (!settings.getBoolean("pref_bgscan", false)) {
                timer.cancel();
                scheduler.shutdown();
                stopSelf();
            }
        }
    };

    public static void logTag(RuuviTag ruuviTag) {
        if (!Exists(ruuviTag.id)) return;

        // TODO: 13/09/17 remember the name some better way
        RuuviTag oldTag = RuuviTag.get(ruuviTag.id);
        ruuviTag.name = oldTag.name;
        ruuviTag.gatewayUrl = oldTag.gatewayUrl;
        ruuviTag.updateAt = new Date();
        ruuviTag.update();
        TagSensorReading reading = new TagSensorReading(ruuviTag);
        reading.save();
    }

    public static boolean Exists(String id) {
        long count = SQLite.selectCountOf()
                .from(RuuviTag.class)
                .where(RuuviTag_Table.id.eq(id))
                .count();
        return count > 0;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopForeground(true);
        stopSelf();
        Foreground.get().removeListener(listener);
    }

    private boolean isRunning(Class<?> serviceClass) {
        ActivityManager mgr = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : mgr.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private class alertManager extends TimerTask {
        private Handler handler = new Handler();

        @Override
        public void run() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            List<RuuviTag> tags = RuuviTag.getAll();
                            for (int i = 0; i < tags.size(); i++) {
                                RuuviTag tag = tags.get(i);
                                List<Alarm> alarms = Alarm.getForTag(tag.id);

                                // TODO: 12/09/17 check if this really works
                                // used as notification id so there may be one notification per tag

                                int notificationTextResourceId = -9001;
                                for (Alarm alarm: alarms) {
                                    switch (alarm.type) {
                                        case Alarm.TEMPERATURE:
                                            if (alarm.low != -500 && tag.temperature < alarm.low)
                                                notificationTextResourceId = R.string.alert_notification_temperature_low;
                                            if (alarm.high != -500 && tag.temperature > alarm.high)
                                                notificationTextResourceId = R.string.alert_notification_temperature_high;
                                            break;
                                        case Alarm.HUMIDITY:
                                            if (alarm.low != -500 && tag.humidity < alarm.low)
                                                notificationTextResourceId = R.string.alert_notification_humidity_low;
                                            if (alarm.high != -500 && tag.humidity > alarm.high)
                                                notificationTextResourceId = R.string.alert_notification_humidity_high;
                                            break;
                                        case Alarm.PERSSURE:
                                            if (alarm.low != -500 && tag.pressure < alarm.low)
                                                notificationTextResourceId = R.string.alert_notification_pressure_low;
                                            if (alarm.high != -500 && tag.pressure > alarm.high)
                                                notificationTextResourceId = R.string.alert_notification_pressure_high;
                                            break;
                                        case Alarm.RSSI:
                                            if (alarm.low != -500 && tag.rssi < alarm.low)
                                                notificationTextResourceId = R.string.alert_notification_rssi_low;
                                            if (alarm.high != -500 && tag.rssi > alarm.high)
                                                notificationTextResourceId = R.string.alert_notification_rssi_high;
                                            break;
                                    }
                                }
                                if (notificationTextResourceId != -9001)
                                    sendAlert(notificationTextResourceId, i, tag.name);
                            }
                        }
                    });
                }
            }).start();
        }
    }

    private void sendAlert(int stringResId, int _id, String name) {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        int notificationid = _id + stringResId;

        if (notification == null) {
            notification
                    = new NotificationCompat.Builder(getApplicationContext())
                    .setContentTitle(name)
                    .setSmallIcon(R.mipmap.ic_launcher_small)
                    .setTicker(name + " " + getString(stringResId))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(stringResId)))
                    .setContentText(getString(stringResId))
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setLargeIcon(bitmap);
        } else {
            notification.setContentTitle(name)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(stringResId)))
                    .setContentText(getString(stringResId));
        }

        NotificationManager NotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotifyMgr.notify(notificationid, notification.build());
    }

    public void addFoundTagToLists(RuuviTag tag, ScanEvent scanEvent) {
        int index = checkForSameTag(ruuviTagArrayList, tag);
        if (index == -1) {
            ruuviTagArrayList.add(tag);
            scanEvent.tags.add(tag);
            logTag(tag);
        } else {
            ruuviTagArrayList.set(index, tag);
        }

        index = checkForSameTag(scanEvent.tags, tag);
        if (index == -1) {
            scanEvent.tags.add(tag);
            logTag(tag);
        }
    }
}