package com.ruuvi.tag.feature.main;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Fragment;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.ruuvi.tag.R;
import com.ruuvi.tag.model.RuuviTag;
import com.ruuvi.tag.scanning.BackgroundScanner;
import com.ruuvi.tag.util.DataUpdateListener;
import com.ruuvi.tag.scanning.RuuviTagListener;
import com.ruuvi.tag.scanning.RuuviTagScanner;
import com.ruuvi.tag.util.Utils;

public class MainActivity extends AppCompatActivity implements RuuviTagListener {
    private static final String BATTERY_ASKED_PREF = "BATTERY_ASKED_PREF";
    private static final int REQUEST_ENABLE_BT = 1337;
    private static final int TAG_UI_UPDATE_FREQ = 1000;

    private DrawerLayout drawerLayout;
    private RuuviTagScanner scanner;
    public List<RuuviTag> myRuuviTags = new ArrayList<>();
    public List<RuuviTag> otherRuuviTags = new ArrayList<>();
    private DataUpdateListener fragmentWithCallback;
    private Handler handler;
    SharedPreferences settings;

    private Runnable updater = new Runnable() {
        @Override
        public void run() {
            if (fragmentWithCallback != null) fragmentWithCallback.dataUpdated();
            handler.postDelayed(updater, TAG_UI_UPDATE_FREQ);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.main_drawerLayout);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setLogo(R.drawable.logo);

        handler = new Handler();

        settings = PreferenceManager.getDefaultSharedPreferences(this);

        myRuuviTags = RuuviTag.getAll();


        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close
        );

        drawerLayout.addDrawerListener(drawerToggle);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
        drawerToggle.syncState();

        ListView drawerListView = findViewById(R.id.navigationDrawer_listView);

        drawerListView.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        getResources().getStringArray(R.array.navigation_items)
                )
        );

        drawerListView.setOnItemClickListener(drawerItemClicked);

        if (isBluetoothEnabled()) {
            scanner = new RuuviTagScanner(this, getApplicationContext());
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        setBackgroundScanning(false);
        openFragment(1);
    }

    public static boolean isBluetoothEnabled() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return bluetoothAdapter.isEnabled();
    }

    AdapterView.OnItemClickListener drawerItemClicked = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            // TODO: 10/10/17 make this in a sane way
            openFragment(i);
        }
    };

    private void setBackgroundScanning(boolean restartFlag) {
        PendingIntent pendingIntent = getPendingIntent();
        boolean shouldRun = settings.getBoolean("pref_bgscan", false);
        boolean isRunning = pendingIntent != null;
        if (isRunning && (!shouldRun || restartFlag)) {
            AlarmManager am = (AlarmManager) getApplicationContext()
                    .getSystemService(ALARM_SERVICE);
            am.cancel(pendingIntent);
            pendingIntent.cancel();
            isRunning = false;
        }
        if (shouldRun && !isRunning) {
            int scanInterval = Integer.parseInt(settings.getString("pref_scaninterval", "300")) * 1000;
            if (scanInterval < 15 * 1000) scanInterval = 15 * 1000;

            boolean batterySaving = settings.getBoolean("pref_bgscan_battery_saving", false);
            Intent intent = new Intent(getApplicationContext(), BackgroundScanner.class);
            PendingIntent sender = PendingIntent.getBroadcast(getApplicationContext(), BackgroundScanner.REQUEST_CODE, intent, 0);
            AlarmManager am = (AlarmManager) getApplicationContext()
                    .getSystemService(ALARM_SERVICE);
            if (batterySaving) {
                am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(),
                        scanInterval, sender);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    checkAndAskForBatteryOptimization();
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + scanInterval, sender);
                }
                else {
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + scanInterval, sender);
                }
            }
        }
    }

    private void checkAndAskForBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasShownBatteryOptimizationDialog()) {
            PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(POWER_SERVICE);
            String packageName = getPackageName();
            // this below does not seems to work on my device
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(getString(R.string.battery_optimization_request))
                        .setPositiveButton(getString(R.string.yes), batteryDialogClick)
                        .setNegativeButton(getString(R.string.no), batteryDialogClick)
                        .show();

                BatteryOptimizationDialogShown();
            }
        }
    }

    DialogInterface.OnClickListener batteryDialogClick = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(intent);
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        }
    };

    private PendingIntent getPendingIntent() {
        Intent intent = new Intent(getApplicationContext(), BackgroundScanner.class);
        return PendingIntent.getBroadcast(getApplicationContext(), BackgroundScanner.REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE);
    }

    @Override
    protected void onStart() {
        //Intent intent = new Intent(MainActivity.this, ScannerService.class);
        //startService(intent);
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Permission check for Marshmallow and newer
        int permissionCoarseLocation = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION);

        int permissionWriteExternal = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        List<String> listPermissionsNeeded = new ArrayList<>();

        if(permissionCoarseLocation != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if(permissionWriteExternal != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if(!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), 1);
        } else {
            if (scanner != null) scanner.start();
            settings.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
            refrshTagLists();
            handler.post(updater);
        }
    }

    private void refrshTagLists() {
        myRuuviTags.clear();
        myRuuviTags.addAll(RuuviTag.getAll());
        otherRuuviTags.clear();
    }

    @Override
    protected void onPause() {
        super.onPause();
        settings.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        if (scanner != null) scanner.stop();
        handler.removeCallbacks(updater);
        for (RuuviTag tag: myRuuviTags) {
            tag.update();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }

    public void openFragment(int type) {
        Fragment fragment = null;
        switch (type) {
            case 1:
                refrshTagLists();
                fragment = new DashboardFragment();
                fragmentWithCallback = (DataUpdateListener)fragment;
                break;
            case 2:
                fragment = new SettingsFragment();
                fragmentWithCallback = null;
                break;
            case 3:
                fragment = new AboutFragment();
                fragmentWithCallback = null;
                break;
            default:
                refrshTagLists();
                fragment = new AddTagFragment();
                fragmentWithCallback = (DataUpdateListener)fragment;
                type = 0;
                break;
        }
        getFragmentManager().beginTransaction()
                .replace(R.id.main_contentFrame, fragment)
                .commit();
        if ((type == 1 || type == 3) && getSupportActionBar() != null) {
            getSupportActionBar().setIcon(R.drawable.logo);
        } else if (getSupportActionBar() != null) {
            getSupportActionBar().setIcon(null);
        }
        setTitle(getResources().getStringArray(R.array.navigation_items_titles)[type]);
        drawerLayout.closeDrawers();
    }

    @Override
    public void tagFound(RuuviTag tag) {
        for (RuuviTag myTag: myRuuviTags) {
            if (myTag.id.equals(tag.id)) {
                myTag.updateDataFrom(tag);
                if (fragmentWithCallback != null) {
                    fragmentWithCallback.dataUpdated();
                }
                return;
            }
        }

        for (RuuviTag myTag: otherRuuviTags) {
            if (myTag.id.equals(tag.id)) {
                myTag.updateDataFrom(tag);
                Utils.sortTagsByRssi(otherRuuviTags);
                if (fragmentWithCallback != null) {
                    fragmentWithCallback.dataUpdated();
                }
                return;
            }
        }
        otherRuuviTags.add(tag);
    }

    public void BatteryOptimizationDialogShown() {
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(BATTERY_ASKED_PREF, true);
        editor.apply();
    }

    public boolean hasShownBatteryOptimizationDialog() {
        return settings.getBoolean(BATTERY_ASKED_PREF, false);
    }

    public SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            setBackgroundScanning(true);
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK) {
            if (requestCode == REQUEST_ENABLE_BT) {
                scanner = new RuuviTagScanner(MainActivity.this, getApplicationContext());
            }
        }
    }
}

