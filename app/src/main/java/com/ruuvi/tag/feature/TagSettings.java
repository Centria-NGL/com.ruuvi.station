package com.ruuvi.tag.feature;

import android.content.DialogInterface;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.crystal.crystalrangeseekbar.interfaces.OnRangeSeekbarChangeListener;
import com.crystal.crystalrangeseekbar.widgets.CrystalRangeSeekbar;
import com.ruuvi.tag.R;
import com.ruuvi.tag.model.Alarm;
import com.ruuvi.tag.model.RuuviTag;

import java.util.ArrayList;
import java.util.List;

public class TagSettings extends AppCompatActivity {
    public static final String TAG_ID = "TAG_ID";

    private RuuviTag tag;
    List<Alarm> tagAlarms = new ArrayList<>();
    List<AlarmItem> alarmItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        String tagId = getIntent().getStringExtra(TAG_ID);

        tag = RuuviTag.get(tagId);
        if (tag == null) {
            finish();
            return;
        }
        tagAlarms = Alarm.getForTag(tagId);

        final TextView nameTextView = findViewById(R.id.input_name);
        nameTextView.setText((tag.name != null && !tag.name.isEmpty()) ? tag.name : tag.id);

        final TextView gatewayTextView = findViewById(R.id.input_gatewayUrl);
        if (tag.gatewayUrl != null && !tag.gatewayUrl.isEmpty()) gatewayTextView.setText(tag.gatewayUrl);

        // TODO: 25/10/17 make this less ugly
        nameTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(TagSettings.this, R.style.Theme_AppCompat_Light_Dialog);
                builder.setTitle(getString(R.string.tag_name));
                final EditText input = new EditText(TagSettings.this);
                input.setText(tag.name);
                builder.setView(input);
                builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        tag.name = input.getText().toString();
                        nameTextView.setText(tag.name);
                    }
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            }
        });

        gatewayTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(TagSettings.this, R.style.Theme_AppCompat_Light_Dialog);
                builder.setTitle(getString(R.string.gateway_url));
                final EditText input = new EditText(TagSettings.this);
                input.setText(tag.gatewayUrl);
                builder.setView(input);
                builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        tag.gatewayUrl = input.getText().toString();
                        gatewayTextView.setText(!tag.gatewayUrl.isEmpty() ? tag.gatewayUrl : getString(R.string.no_gateway_url));
                    }
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            }
        });

        alarmItems.add(new AlarmItem(getString(R.string.temperature), Alarm.TEMPERATURE, false, -40, 85));
        alarmItems.add(new AlarmItem(getString(R.string.humidity), Alarm.HUMIDITY, false, 0, 100));
        alarmItems.add(new AlarmItem(getString(R.string.pressure), Alarm.PERSSURE, false, 300, 1100));
        alarmItems.add(new AlarmItem(getString(R.string.rssi), Alarm.RSSI, false, -100 ,0));

        for (Alarm alarm: tagAlarms) {
            AlarmItem item = alarmItems.get(alarm.type);
            item.high = alarm.high;
            item.low = alarm.low;
            item.checked = true;
            item.alarm = alarm;
        }

        LayoutInflater inflater = getLayoutInflater();
        ConstraintLayout parentLayout = findViewById(R.id.tag_settings_item_layout);
        TextView alertsHeader = findViewById(R.id.alerts_header);

        for (int i = 0; i < alarmItems.size(); i++) {
            AlarmItem item = alarmItems.get(i);
            item.view = inflater.inflate(R.layout.view_alarm, parentLayout, false);
            item.view.setId(item.view.getId() + i);
            item.view.findViewById(R.id.alert_checkbox).setTag(i);
            ((CheckBox)item.view.findViewById(R.id.alert_checkbox)).setOnCheckedChangeListener(alarmCheckboxListener);
            item.createView();
            parentLayout.addView(item.view);
            ConstraintSet set = new ConstraintSet();
            set.clone(parentLayout);
            set.connect(item.view.getId(), ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT);
            set.connect(item.view.getId(), ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT);
            set.connect(item.view.getId(), ConstraintSet.TOP, (i == 0 ? alertsHeader.getId() : alarmItems.get(i - 1).view.getId()), ConstraintSet.BOTTOM);
            set.applyTo(parentLayout);
        }
    }

    CompoundButton.OnCheckedChangeListener alarmCheckboxListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            AlarmItem ai = alarmItems.get((int)buttonView.getTag());
            ai.checked = isChecked;
            ai.updateView();
        }
    };

    private class AlarmItem {
        public String name;
        public String subtitle;
        public boolean checked;
        public int low;
        public int high;
        public int max;
        public int min;
        public int type;
        public View view;
        public Alarm alarm;

        public AlarmItem(String name, int type, boolean checked, int min, int max) {
            this.name = name;
            this.type = type;
            this.checked = checked;
            this.min = min;
            this.max = max;
            this.low = min;
            this.high = max;
        }

        public void createView() {
            CrystalRangeSeekbar seekBar = this.view.findViewById(R.id.alert_seekBar);
            seekBar.setMinValue(this.min);
            seekBar.setMaxValue(this.max);
            seekBar.setMinStartValue(this.low);
            seekBar.setMaxStartValue(this.high);
            seekBar.apply();

            seekBar.setOnRangeSeekbarChangeListener(new OnRangeSeekbarChangeListener() {
                @Override
                public void valueChanged(Number minValue, Number maxValue) {
                    low = minValue.intValue();
                    high = maxValue.intValue();
                    updateView();
                }
            });

            updateView();
        }

        public void updateView() {
            CrystalRangeSeekbar seekBar = this.view.findViewById(R.id.alert_seekBar);
            if (this.checked) {
                seekBar.setBarHighlightColor(getResources().getColor(R.color.main));
                seekBar.setLeftThumbColor(getResources().getColor(R.color.main));
                seekBar.setRightThumbColor(getResources().getColor(R.color.main));
                this.subtitle = String.format(getString(R.string.alert_subtitle_on), this.low, this.high);
            } else {
                seekBar.setBarHighlightColor(getResources().getColor(R.color.ap_gray));
                seekBar.setLeftThumbColor(getResources().getColor(R.color.ap_gray));
                seekBar.setRightThumbColor(getResources().getColor(R.color.ap_gray));
                this.subtitle = getString(R.string.alert_subtitle_off);
            }
            seekBar.setEnabled(this.checked);
            ((CheckBox)this.view.findViewById(R.id.alert_checkbox)).setChecked(this.checked);
            ((TextView)this.view.findViewById(R.id.alert_title)).setText(this.name);
            ((TextView)this.view.findViewById(R.id.alert_subtitle)).setText(this.subtitle);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return super.onSupportNavigateUp();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            tag.update();
            for (AlarmItem alarmItem: alarmItems) {
                if (alarmItem.checked) {
                    if (alarmItem.alarm == null) {
                        alarmItem.alarm = new Alarm(alarmItem.low, alarmItem.high, alarmItem.type, tag.id);
                        alarmItem.alarm.save();
                    } else {
                        alarmItem.alarm.low = alarmItem.low;
                        alarmItem.alarm.high = alarmItem.high;
                        alarmItem.alarm.update();
                    }
                } else if (alarmItem.alarm != null) {
                    alarmItem.alarm.delete();
                }
            }
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_edit, menu);
        return true;
    }
}
