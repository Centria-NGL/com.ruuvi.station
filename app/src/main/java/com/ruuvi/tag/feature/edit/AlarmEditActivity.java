package com.ruuvi.tag.feature.edit;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.crystal.crystalrangeseekbar.interfaces.OnRangeSeekbarChangeListener;
import com.crystal.crystalrangeseekbar.interfaces.OnRangeSeekbarFinalValueListener;
import com.crystal.crystalrangeseekbar.widgets.CrystalRangeSeekbar;
import com.ruuvi.tag.R;

public class AlarmEditActivity extends AppCompatActivity {
    private int index;
    private int[] maxValues;
    private CrystalRangeSeekbar rangeSeekbar;
    private TextView temp;
    private int tag;
    private Integer[] valuesArray;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm_edit);
        maxValues = new int[]{-40,85,0,100,300,1100,-100,0};
        valuesArray = new Integer[8];

        if(getIntent().getExtras() != null) {
            index = getIntent().getExtras().getInt("index");
        }

        // TODO: 12/09/17 remove the cursor and replace with dbflow 
        /*
        cursor = db.query(DBContract.RuuviTagDB.TABLE_NAME, null, "_ID= ?", new String[] { "" + index }, null, null, null);
        if(cursor.moveToFirst()) {
            String stringValues = cursor.getString(cursor.getColumnIndex(DBContract.RuuviTagDB.COLUMN_VALUES));
            Integer[] temp = readSeparated(stringValues);
            int index = 0;
            for(Integer i : temp) {
                if(i != -500) {
                    valuesArray[index] = i;
                    if(index == 0 | index == 1) {
                        ((CheckBox) findViewById(R.id.check_temp)).setChecked(true);
                    }
                    if(index == 2 | index == 3) {
                        ((CheckBox) findViewById(R.id.check_humi)).setChecked(true);
                    }
                    if(index == 4 | index == 5) {
                        ((CheckBox) findViewById(R.id.check_pres)).setChecked(true);
                    }
                    if(index == 6 | index == 7) {
                        ((CheckBox) findViewById(R.id.check_rssi)).setChecked(true);
                    }
                } else {
                    valuesArray[index] = maxValues[index];
                }
                index++;
            }
        }
        */

        // get seekbar from view
        rangeSeekbar = (CrystalRangeSeekbar) findViewById(R.id.rangeSeekbar5);
        // get min and max text view
        final TextView tvMin = (TextView) findViewById(R.id.TextMin1);
        final TextView tvMax = (TextView) findViewById(R.id.TextMax1);
        // set listener
        rangeSeekbar.setOnRangeSeekbarChangeListener(new OnRangeSeekbarChangeListener() {
            @Override
            public void valueChanged(Number minValue, Number maxValue) {
                tvMin.setText(String.valueOf(minValue));
                tvMax.setText(String.valueOf(maxValue));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadValues(findViewById(R.id.text_temp));
    }

    public void loadValues(View view) {
        if(temp != null) {
            temp.setPaintFlags(temp.getPaintFlags() & (~ Paint.UNDERLINE_TEXT_FLAG));
        }

        temp = (TextView) findViewById(view.getId());
        temp.setPaintFlags(temp.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        temp.setText(temp.getText());

        tag = Integer.parseInt(view.getTag().toString());

        switch(tag) {
            case 1: {
                rangeSeekbar.setMinValue(maxValues[0]);
                rangeSeekbar.setMaxValue(maxValues[1]);
                rangeSeekbar.setMinStartValue(valuesArray[0]);
                rangeSeekbar.setMaxStartValue(valuesArray[1]);
                rangeSeekbar.apply();
                break;
                }
            case 2: {
                rangeSeekbar.setMinValue(maxValues[2]);
                rangeSeekbar.setMaxValue(maxValues[3]);
                rangeSeekbar.setMinStartValue(valuesArray[2]);
                rangeSeekbar.setMaxStartValue(valuesArray[3]);
                rangeSeekbar.apply();
                break;
            }
            case 3: {
                rangeSeekbar.setMinValue(maxValues[4]);
                rangeSeekbar.setMaxValue(maxValues[5]);
                rangeSeekbar.setMinStartValue(valuesArray[4]);
                rangeSeekbar.setMaxStartValue(valuesArray[5]);
                rangeSeekbar.apply();
                break;
            }
            case 4: {
                rangeSeekbar.setMinValue(maxValues[6]);
                rangeSeekbar.setMaxValue(maxValues[7]);
                rangeSeekbar.setMinStartValue(valuesArray[6]);
                rangeSeekbar.setMaxStartValue(valuesArray[7]);
                rangeSeekbar.apply();
                break;
            }
        }

        rangeSeekbar.setOnRangeSeekbarFinalValueListener(new OnRangeSeekbarFinalValueListener() {
            @Override
            public void finalValue(Number minValue, Number maxValue) {
                switch(tag) {
                    case 1: {
                        valuesArray[0] = minValue.intValue();
                        valuesArray[1] = maxValue.intValue();
                        break;
                    }
                    case 2: {
                        valuesArray[2] = minValue.intValue();
                        valuesArray[3] = maxValue.intValue();
                        break;
                    }
                    case 3: {
                        valuesArray[4] = minValue.intValue();
                        valuesArray[5] = maxValue.intValue();
                        break;
                    }
                    case 4: {
                        valuesArray[6] = minValue.intValue();
                        valuesArray[7] = maxValue.intValue();
                        break;
                    }
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_save) {
            this.save(null);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_edit, menu);
        return true;
    }

    public void save(View view) {
        ContentValues values = new ContentValues();

        if(!((CheckBox) findViewById(R.id.check_temp)).isChecked()) {
            valuesArray[0] = -500;
            valuesArray[1] = -500;
        }
        if(!((CheckBox) findViewById(R.id.check_humi)).isChecked()) {
            valuesArray[2] = -500;
            valuesArray[3] = -500;
        }
        if(!((CheckBox) findViewById(R.id.check_pres)).isChecked()) {
            valuesArray[4] = -500;
            valuesArray[5] = -500;
        }
        if(!((CheckBox) findViewById(R.id.check_rssi)).isChecked()) {
            valuesArray[6] = -500;
            valuesArray[7] = -500;
        }
        // TODO: 12/09/17 update dbflow..
        //values.put(DBContract.RuuviTagDB.COLUMN_VALUES, commaSeparate(valuesArray));
        //db.update(DBContract.RuuviTagDB.TABLE_NAME, values, "_ID= ?", new String[] { "" + index });
        finish();
    }

    public String commaSeparate(Integer[] data) {
        if(data != null) {

            String separated = "";

            for (Integer i : data) {
                separated += String.valueOf(i) + ",";
            }

            return separated.substring(0, separated.length() - 1);
        }
        return null;
    }

    public Integer[] readSeparated(String data) {
        String[] linevector;
        int index = 0;

        linevector = data.split(",");

        Integer[] values = new Integer[linevector.length];

        for(String l : linevector) {
            try {
                values[index] = Integer.parseInt(l);
            } catch (NumberFormatException e) {
                values[index] = null;
            }
            index++;
        }

        return values;
    }
}
