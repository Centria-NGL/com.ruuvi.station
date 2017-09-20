package com.ruuvi.tag.feature.main;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.menu.ExpandedMenuView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


import com.ruuvi.tag.adapters.RuuviTagAdapter;
import com.ruuvi.tag.feature.list.ListActivity;
import com.ruuvi.tag.R;
import com.ruuvi.tag.model.RuuviTag;
import com.ruuvi.tag.service.ScannerService;
import com.ruuvi.tag.feature.settings.SettingsActivity;
import com.ruuvi.tag.util.DeviceIdentifier;

// TODO: 20/09/17 make this, settings and about into fragments 
public class MainActivity extends AppCompatActivity {
    ScannerService service;
    private RuuviTagAdapter adapter;
    private ListView beaconListView;
    private Gson gson;
    private Timer timer;
    private View text;
    private boolean bound;
    private SharedPreferences settings;
    private List<RuuviTag> tags = new ArrayList<>();

    private void setTimerForAdvertise() {
        timer = new Timer();
        TimerTask updateProfile = new MainActivity.CustomTimerTask();
        timer.scheduleAtFixedRate(updateProfile, 0, 1000);
    }

    private class CustomTimerTask extends TimerTask {
        private Handler mHandler = new Handler();

        @Override
        public void run() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            adapter.clear();
                            adapter.addAll(RuuviTag.getAll());
                            adapter.notifyDataSetChanged();
                        }
                    });
                }
            }).start();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DrawerLayout drawerLayout = findViewById(R.id.main_drawerLayout);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close
        );

        drawerLayout.addDrawerListener(drawerToggle);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
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

        gson = new Gson();
        text = findViewById(R.id.noTags_textView);

        DeviceIdentifier.id(this);

        settings = PreferenceManager.getDefaultSharedPreferences(this);

        tags = RuuviTag.getAll();
        if (tags.size() > 0) findViewById(R.id.noTags_textView).setVisibility(View.GONE);

        beaconListView = findViewById(R.id.Tags_listView);
        adapter = new RuuviTagAdapter(getApplicationContext(), tags);
        beaconListView.setAdapter(adapter);

        setTitle(R.string.title_activity_main);

        FloatingActionButton fab = findViewById(R.id.fab_add);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            }
        });

        setTimerForAdvertise();
        adapter.notifyDataSetChanged();
    }

    AdapterView.OnItemClickListener drawerItemClicked = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            // TODO: 20/09/17 make this open differnt fragments 
            Intent intent = new Intent(MainActivity.this, ListActivity.class);
            startActivity(intent);
        }
    };

    @Override
    protected void onStart() {
        Intent intent = new Intent(MainActivity.this, ScannerService.class);
        startService(intent);
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
        }

        adapter.clear();
        adapter.addAll(RuuviTag.getAll());
        adapter.notifyDataSetChanged();
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }
}

