package fi.centria.ruuvitag;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.CatmullRomInterpolator;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.google.gson.Gson;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import fi.centria.ruuvitag.database.DBContract;
import fi.centria.ruuvitag.database.DBHandler;
import fi.centria.ruuvitag.model.ScanEvent;
import fi.centria.ruuvitag.util.ComplexPreferences;
import fi.centria.ruuvitag.util.PlotSource;
import fi.centria.ruuvitag.util.Ruuvitag;
import fi.centria.ruuvitag.util.RuuvitagComplexList;
import fi.centria.ruuvitag.util.listAdapter;

public class PlotActivity extends AppCompatActivity
{
    private XYPlot temp_plot;
    private XYPlot hum_plot;
    private XYPlot pres_plot;

    private PlotSource plotSource;
    private Date[] domains;
    private String id;

    Number temp[];
    Number humidity[];
    Number pressure[];

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plot);

        id = getIntent().getExtras().getString("id");

        plotSource = PlotSource.getInstance();
        domains = plotSource.getDomains();
        temp_plot = (XYPlot) findViewById(R.id.plotTemperature);
        hum_plot = (XYPlot) findViewById(R.id.plotHumidity);
        pres_plot = (XYPlot) findViewById(R.id.plotPressure);


        Ruuvitag[] series = plotSource.getSeriesForTag(id);

       temp = new Number[series.length];
        humidity = new Number[series.length];
        pressure = new Number[series.length];
        for(int i= 0; i < series.length; i++)
        {
            if(series[i] != null)
            {
                temp[i] = Double.parseDouble(series[i].getTemperature());
                humidity[i] = Double.parseDouble(series[i].getHumidity());
                pressure[i] =  Double.parseDouble(series[i].getPressure());
            }
        }

        makeTemperaturePlot();
        makeHumidityPlot();
        makePressurePlot();
    }

    private void makeTemperaturePlot()
    {
        temp_plot.setRangeBoundaries(-40,40, BoundaryMode.FIXED);
        LineAndPointFormatter series1Format =
                new LineAndPointFormatter(this, R.xml.line_point_formatter_with_labels);
        XYSeries series1 = new SimpleXYSeries(Arrays.asList(temp), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Temperature");
        temp_plot.addSeries(series1, series1Format);
        temp_plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new Format() {
            @Override
            public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {

                int i = Math.round(((Number) obj).floatValue());
                SimpleDateFormat dt1 = new SimpleDateFormat("HH:mm");

                return toAppendTo.append(dt1.format(domains[i]));
            }
            @Override
            public Object parseObject(String source, ParsePosition pos) {
                return null;
            }
        });
    }

    private void makeHumidityPlot()
    {
        hum_plot.setRangeBoundaries(0,100, BoundaryMode.FIXED);
        LineAndPointFormatter series1Format =
                new LineAndPointFormatter(this, R.xml.line_point_formatter_with_labels);
        XYSeries series1 = new SimpleXYSeries(Arrays.asList(humidity), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Humidity");
        hum_plot.addSeries(series1, series1Format);
        hum_plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new Format() {
            @Override
            public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {

                int i = Math.round(((Number) obj).floatValue());
                SimpleDateFormat dt1 = new SimpleDateFormat("HH:mm");

                return toAppendTo.append(dt1.format(domains[i]));
            }
            @Override
            public Object parseObject(String source, ParsePosition pos) {
                return null;
            }
        });
    }

    private void makePressurePlot()
    {
        pres_plot.setRangeBoundaries(800,1200, BoundaryMode.FIXED);
        LineAndPointFormatter series1Format =
                new LineAndPointFormatter(this, R.xml.line_point_formatter_with_labels);
        XYSeries series1 = new SimpleXYSeries(Arrays.asList(pressure), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Humidity");
        pres_plot.addSeries(series1, series1Format);
        pres_plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new Format() {
            @Override
            public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {

                int i = Math.round(((Number) obj).floatValue());
                SimpleDateFormat dt1 = new SimpleDateFormat("HH:mm");

                return toAppendTo.append(dt1.format(domains[i]));
            }
            @Override
            public Object parseObject(String source, ParsePosition pos) {
                return null;
            }
        });
    }


}
