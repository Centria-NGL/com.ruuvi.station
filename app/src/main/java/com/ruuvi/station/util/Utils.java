package com.ruuvi.station.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;

import com.ruuvi.station.R;
import com.ruuvi.station.database.tables.RuuviTagEntity;
import com.ruuvi.station.tag.domain.RuuviTag;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import androidx.core.content.ContextCompat;
import timber.log.Timber;


public class Utils {

    public static Bitmap createBall(int radius, int ballColor, int letterColor, String letter) {
        letter = letter.toUpperCase();
        Bitmap bitmap = Bitmap.createBitmap(radius * 2, radius * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint2 = new Paint();
        paint2.setColor(ballColor);
        canvas.drawCircle(radius, radius, (float) radius, paint2);
        Paint paint = new Paint();
        paint.setColor(letterColor);
        paint.setTextSize(100);
        paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        Rect textBounds = new Rect();
        paint.getTextBounds(letter, 0, letter.length(), textBounds);
        canvas.drawText(letter, radius - textBounds.exactCenterX(), radius - textBounds.exactCenterY(), paint);
        return bitmap;
    }

    public static String strDescribingTimeSince(Date date) {
        String output = "";
        Date dateNow = new Date();
        long diffInMS = dateNow.getTime() - date.getTime();
        // show date if the tag has not been seen for 24h
        if (diffInMS > 24 * 60 * 60 * 1000) {
            output += date.toString();
        } else {
            int seconds = (int) (diffInMS / 1000) % 60;
            int minutes = (int) ((diffInMS / (1000 * 60)) % 60);
            int hours = (int) ((diffInMS / (1000 * 60 * 60)) % 24);
            if (hours > 0) output += hours + " h ";
            if (minutes > 0) output += minutes + " min ";
            output += seconds + " s ago";
        }
        return output;
    }

    public static Bitmap getBackground(Context context, RuuviTagEntity tag) {
        try {
            Uri uri = Uri.parse(tag.getUserBackground());
            return MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
        } catch (Exception e) {
            Timber.e("Could not set user background");
        }

        return BitmapFactory.decodeResource(context.getResources(), getDefaultBackground(tag.getDefaultBackground()));
    }

    public static Bitmap getBackground(Context context, RuuviTag tag) {
        try {
            Uri uri = Uri.parse(tag.getUserBackground());
            return MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
        } catch (Exception e) {
            Timber.e("Could not set user background");
        }

        return BitmapFactory.decodeResource(context.getResources(), getDefaultBackground(tag.getDefaultBackground()));
    }


    public static Drawable getDefaultBackground(int number, Context context) {
        return ContextCompat.getDrawable(context, getDefaultBackground(number));
    }

    private static int getDefaultBackground(int number) {
        switch (number) {
            case 1:
                return R.drawable.bg2;
            case 2:
                return R.drawable.bg3;
            case 3:
                return R.drawable.bg4;
            case 4:
                return R.drawable.bg5;
            case 5:
                return R.drawable.bg6;
            case 6:
                return R.drawable.bg7;
            case 7:
                return R.drawable.bg8;
            case 8:
                return R.drawable.bg9;
            default:
                return R.drawable.bg1;
        }
    }

    public static double celciusToFahrenheit(double celcius) {
        return round(celcius * 1.8 + 32.0, 2);
    }

    public static double celsiusToKelvin(double celsius) {
        return celsius + 273.15;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
