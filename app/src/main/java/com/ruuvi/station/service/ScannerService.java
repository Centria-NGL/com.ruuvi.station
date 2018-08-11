package com.ruuvi.station.service;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
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
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.ruuvi.station.R;
import com.ruuvi.station.feature.main.MainActivity;
import com.ruuvi.station.gateway.Http;
import com.ruuvi.station.model.Alarm;
import com.ruuvi.station.model.LeScanResult;
import com.ruuvi.station.model.RuuviTag;
import com.ruuvi.station.model.RuuviTag_Table;
import com.ruuvi.station.model.ScanEvent;
import com.ruuvi.station.model.ScanEventSingle;
import com.ruuvi.station.model.TagSensorReading;
import com.ruuvi.station.util.AlarmChecker;
import com.ruuvi.station.util.ComplexPreferences;
import com.ruuvi.station.util.Constants;
import com.ruuvi.station.util.DeviceIdentifier;
import com.ruuvi.station.util.Foreground;
import com.ruuvi.station.model.RuuviTagComplexList;
import com.ruuvi.station.util.Utils;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;

import static com.ruuvi.station.RuuviScannerApplication.useNewApi;


public class ScannerService extends Service {
    private static final String TAG = "ScannerService";

    private boolean scanning;

    private no.nordicsemi.android.support.v18.scanner.ScanSettings scanSettings;
    private BluetoothLeScannerCompat scanner;
    private Handler handler;
    private boolean isForegroundMode = false;
    private SharedPreferences settings;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Foreground.init(getApplication());
        Foreground.get().addListener(listener);

        scanSettings = new no.nordicsemi.android.support.v18.scanner.ScanSettings.Builder()
                .setReportDelay(0)
                .setScanMode(no.nordicsemi.android.support.v18.scanner.ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setUseHardwareBatchingIfSupported(false).build();

        scanner = BluetoothLeScannerCompat.getScanner();

        if (getForegroundMode()) startFG();
        handler = new Handler();
        handler.post(reStarter);
    }

    private boolean getForegroundMode() {
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        int getInterval = settings.getInt("pref_background_scan_interval", Constants.DEFAULT_SCAN_INTERVAL);
        return settings.getBoolean("pref_bgscan", false) && getInterval < 15 * 60;
    }

    private Runnable reStarter = new Runnable() {
        @Override
        public void run() {
            stopScan();
            startScan();
            handler.postDelayed(reStarter, 5 * 60 * 1000);
        }
    };

    public void startFG() {
        isForegroundMode = true;
        Intent notificationIntent = new Intent(this, MainActivity.class);

        Bitmap bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.mipmap.ic_launcher);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        NotificationCompat.Builder notification;
        notification
                = new NotificationCompat.Builder(getApplicationContext(), "notify_001")
                .setContentTitle(this.getString(R.string.scanner_notification_title))
                .setSmallIcon(R.mipmap.ic_launcher_small)
                .setTicker(this.getString(R.string.scanner_notification_ticker))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(this.getString(R.string.scanner_notification_message)))
                .setContentText(this.getString(R.string.scanner_notification_message))
                .setDefaults(Notification.DEFAULT_ALL)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setLargeIcon(bitmap)
                .setContentIntent(pendingIntent);

        if (Build.VERSION.SDK_INT < 21) {
            notification.setSmallIcon(R.mipmap.ic_launcher_small);
        } else {
            notification.setSmallIcon(R.drawable.ic_ruuvi_notification_icon_v1);
        }

        startForeground(1337, notification.build());
    }

    private boolean canScan() {
        return scanner != null;
    }

    public void startScan() {
        if (scanning || !canScan()) return;
        scanning = true;
        try {
            scanner.startScan(null, scanSettings, nsCallback);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            scanning = false;
            Toast.makeText(getApplicationContext(), "Couldn't start scanning, is bluetooth disabled?", Toast.LENGTH_LONG).show();
        }
    }

    public void stopScan() {
        if (!canScan()) return;
        scanning = false;
        scanner.stopScan(nsCallback);
    }

    private no.nordicsemi.android.support.v18.scanner.ScanCallback nsCallback = new no.nordicsemi.android.support.v18.scanner.ScanCallback() {
        @Override
        public void onScanResult(int callbackType, no.nordicsemi.android.support.v18.scanner.ScanResult result) {
            super.onScanResult(callbackType, result);
            foundDevice(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());
        }
    };

    private void foundDevice(BluetoothDevice device, int rssi, byte[] data) {
        LeScanResult dev = new LeScanResult();
        dev.device = device;
        dev.rssi = rssi;
        dev.scanData = data;

        //Log.d(TAG, "found: " + device.getAddress());
        RuuviTag tag = dev.parse();
        if (tag != null) logTag(tag, getApplicationContext());
    }

    @Override
    public void onDestroy() {
        stopScan();
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
            if (!getForegroundMode()) {
                stopSelf();
                isForegroundMode = false;
            } else {
                if (!isForegroundMode) startFG();
            }
        }
    };

    public static Map<String, Long> lastLogged = null;
    public static int LOG_INTERVAL = 5; // seconds

    public static void logTag(RuuviTag ruuviTag, Context context) {
        RuuviTag dbTag = RuuviTag.get(ruuviTag.id);
        if (dbTag != null) {
            ruuviTag = dbTag.preserveData(ruuviTag);
            ruuviTag.update();
            if (!dbTag.favorite) return;
        } else {
            ruuviTag.updateAt = new Date();
            ruuviTag.save();
            return;
        }

        if (lastLogged == null) lastLogged = new HashMap<>();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, -LOG_INTERVAL);
        long loggingTreshold = calendar.getTime().getTime();
        for (Map.Entry<String, Long> entry : lastLogged.entrySet())
        {
            if (entry.getKey().equals(ruuviTag.id) && entry.getValue() > loggingTreshold) {
                return;
            }
        }

        List<RuuviTag> tags = new ArrayList<>();
        tags.add(ruuviTag);
        Http.post(tags, null, context);

        lastLogged.put(ruuviTag.id, new Date().getTime());
        TagSensorReading reading = new TagSensorReading(ruuviTag);
        reading.save();
        AlarmChecker.check(ruuviTag, context);
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
}