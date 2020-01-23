package com.ruuvi.station.model;

import android.support.annotation.Nullable;

import com.ruuvi.station.bluetooth.interfaces.IRuuviTag;

import java.util.Date;
import java.util.HashMap;

public class HumidityCalibration {

    private static HashMap<String, HumidityCalibration> cache = new HashMap<>();

    public String mac;
    public float humidityOffset;
    public Date timestamp = new Date();

    public static HumidityCalibration calibrate(IRuuviTag tag) {
        HumidityCalibration prevCalibration = HumidityCalibration.get(tag);
        float prevCalibrationValue = 0f;
        if (prevCalibration != null) {
            prevCalibrationValue = prevCalibration.humidityOffset;
        }
        float calibration = 75f-((float) tag.getHumidity() -prevCalibrationValue);
        HumidityCalibration newCalibration = new HumidityCalibration();
        newCalibration.humidityOffset = calibration;
        newCalibration.mac = tag.getId();
        cache.put(tag.getId(), newCalibration);
        return newCalibration;
    }

    public static void clear(IRuuviTag tag) {
        cache.remove(tag.getId());
    }

    public static IRuuviTag apply(IRuuviTag tag) {
        HumidityCalibration calibration = get(tag);
        if (calibration != null) {
            tag.setHumidity(tag.getHumidity() + calibration.humidityOffset);
        }
        return tag;
    }

    @Nullable
    public static HumidityCalibration get(IRuuviTag tag) {
        if (cache.containsKey(tag.getId())) {
            return cache.get(tag.getId());
        }
        return null;
    }
}
