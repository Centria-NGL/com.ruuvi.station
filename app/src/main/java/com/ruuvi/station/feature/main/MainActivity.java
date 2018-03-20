package com.ruuvi.station.feature.main;

import android.Manifest;
import android.app.AlarmManager;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.ruuvi.station.R;
import com.ruuvi.station.feature.WelcomeActivity;
import com.ruuvi.station.model.RuuviTag;
import com.ruuvi.station.scanning.BackgroundScanner;
import com.ruuvi.station.service.ScannerService;
import com.ruuvi.station.util.DataUpdateListener;
import com.ruuvi.station.scanning.RuuviTagListener;
import com.ruuvi.station.scanning.RuuviTagScanner;
import com.ruuvi.station.util.Utils;

public class MainActivity extends AppCompatActivity implements RuuviTagListener {
    private static final String TAG = "MainActivity";
    private static final String BATTERY_ASKED_PREF = "BATTERY_ASKED_PREF";
    private static final String FIRST_START_PREF = "BATTERY_ASKED_PREF";
    private static final int REQUEST_ENABLE_BT = 1337;
    private static final int TAG_UI_UPDATE_FREQ = 1000;
    private static final int FROM_WELCOME = 1447;

    private DrawerLayout drawerLayout;
    private RuuviTagScanner scanner;
    public List<RuuviTag> myRuuviTags = new ArrayList<>();
    public List<RuuviTag> otherRuuviTags = new ArrayList<>();
    private DataUpdateListener fragmentWithCallback;
    private Handler handler;
    SharedPreferences settings;
    boolean dashboardVisible = true;

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
        myRuuviTags = RuuviTag.getAll(true);

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
        if (!getPrefDone(FIRST_START_PREF)) {
            openFragment(0);
            Intent intent = new Intent(this, WelcomeActivity.class);
            startActivityForResult(intent, FROM_WELCOME);
        } else {
            if (isBluetoothEnabled()) {
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
            setBackgroundScanning(false, this, settings);

            openFragment(1);
        }
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

    public static void setBackgroundScanning(boolean restartFlag, Context context, SharedPreferences settings) {
        PendingIntent pendingIntent = getPendingIntent(context);
        boolean shouldRun = settings.getBoolean("pref_bgscan", false);
        boolean isRunning = pendingIntent != null;
        if (isRunning && (!shouldRun || restartFlag)) {
            AlarmManager am = (AlarmManager) context
                    .getSystemService(ALARM_SERVICE);
            try {
                am.cancel(pendingIntent);
            } catch (Exception e) {
                Log.d(TAG, "Could not cancel background intent");
            }
            pendingIntent.cancel();
            isRunning = false;
        }
        if (shouldRun && !isRunning) {
            int scanInterval = Integer.parseInt(settings.getString("pref_scaninterval", "30")) * 1000;
            if (scanInterval < 15 * 1000) scanInterval = 15 * 1000;

            boolean batterySaving = settings.getBoolean("pref_bgscan_battery_saving", false);
            Intent intent = new Intent(context, BackgroundScanner.class);
            PendingIntent sender = PendingIntent.getBroadcast(context, BackgroundScanner.REQUEST_CODE, intent, 0);
            AlarmManager am = (AlarmManager) context
                    .getSystemService(ALARM_SERVICE);
            try {
                if (batterySaving) {
                    am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(),
                            scanInterval, sender);
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        checkAndAskForBatteryOptimization(context);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean(BATTERY_ASKED_PREF, true).apply();
                        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + scanInterval, sender);
                    }
                    else {
                        am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + scanInterval, sender);
                    }
                }
            } catch (Exception e) {
                Toast.makeText(context, "Could not start background scanning", Toast.LENGTH_LONG).show();
            }
        }
    }

    public static void checkAndAskForBatteryOptimization(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
            String packageName = context.getPackageName();
            // this below does not seems to work on my device
            try {
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                    context.startActivity(intent);
                }

            } catch (Exception e) {
                Log.d(TAG, "Could not set ignoring battery optimization");
            }
        }
    }

    private static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, BackgroundScanner.class);
        return PendingIntent.getBroadcast(context, BackgroundScanner.REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE);
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

        final List<String> listPermissionsNeeded = new ArrayList<>();

        if(permissionCoarseLocation != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if(permissionWriteExternal != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if(!listPermissionsNeeded.isEmpty()) {
            if (!getPrefDone(FIRST_START_PREF)) {
                // welcome activity should be showing so let's not bug the user about permissions yet
                final AppCompatActivity activity = this;
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle(getString(R.string.permission_dialog_title));
                alertDialog.setMessage(getString(R.string.permission_dialog_request_message));
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        ActivityCompat.requestPermissions(activity, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), 1);
                    }
                });
                alertDialog.show();
            }
        } else {
            settings.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
            refrshTagLists();
            handler.post(updater);

            if (isBluetoothEnabled()) {
                Intent scannerService = new Intent(this, ScannerService.class);
                startService(scannerService);
            }
        }
    }

    private void refrshTagLists() {
        myRuuviTags.clear();
        myRuuviTags.addAll(RuuviTag.getAll(true));
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
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    public void openFragment(int type) {
        Fragment fragment = null;
        dashboardVisible = false;
        switch (type) {
            case 1:
                refrshTagLists();
                fragment = new DashboardFragment();
                fragmentWithCallback = (DataUpdateListener)fragment;
                dashboardVisible = true;
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
                myTag.update();
                if (fragmentWithCallback != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            fragmentWithCallback.dataUpdated();
                        }
                    });
                }
                return;
            }
        }

        for (RuuviTag myTag: otherRuuviTags) {
            if (myTag.id.equals(tag.id)) {
                myTag.updateDataFrom(tag);
                Utils.sortTagsByRssi(otherRuuviTags);
                if (fragmentWithCallback != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            fragmentWithCallback.dataUpdated();
                        }
                    });
                }
                return;
            }
        }
        otherRuuviTags.add(tag);
    }

    public void setPrefDone(String pref) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(pref, true);
        editor.apply();
    }

    public boolean getPrefDone(String pref) {
        return settings.getBoolean(pref, false);
    }

    public SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            setBackgroundScanning(true, getApplicationContext(), settings);
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK) {
            if (requestCode == REQUEST_ENABLE_BT) {
                scanner = new RuuviTagScanner(MainActivity.this, getApplicationContext());
            }
        } else {
            if (requestCode == FROM_WELCOME) {
                if (isBluetoothEnabled()) {
                    scanner = new RuuviTagScanner(this, getApplicationContext());
                } else {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
                setPrefDone(FIRST_START_PREF);
                setBackgroundScanning(false, this, settings);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (dashboardVisible) {
            super.onBackPressed();
        } else {
            openFragment(1);
        }
    }
}

