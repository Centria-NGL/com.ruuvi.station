package com.ruuvi.station.bluetooth.decoder;

import com.ruuvi.station.bluetooth.RuuviTagFactory;
import com.ruuvi.station.bluetooth.domain.IRuuviTag;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DecodeFormat5 implements RuuviTagDecoder {
    // offset = 7
    @Override
    public IRuuviTag decode(RuuviTagFactory factory, byte[] data, int offset) {
        IRuuviTag tag = factory.createTag();
        tag.setDataFormat(5);
        tag.setTemperature((data[1 + offset] << 8 | data[2 + offset] & 0xFF) / 200d);
        tag.setHumidity(((data[3 + offset] & 0xFF) << 8 | data[4 + offset] & 0xFF) / 400d);
        tag.setPressure((double) ((data[5 + offset] & 0xFF) << 8 | data[6 + offset] & 0xFF) + 50000);
        tag.setPressure(tag.getPressure() / 100.0);

        tag.setAccelX((data[7 + offset] << 8 | data[8 + offset] & 0xFF) / 1000d);
        tag.setAccelY((data[9 + offset] << 8 | data[10 + offset] & 0xFF) / 1000d);
        tag.setAccelZ((data[11 + offset] << 8 | data[12 + offset] & 0xFF) / 1000d);

        int powerInfo = (data[13 + offset] & 0xFF) << 8 | data[14 + offset] & 0xFF;
        if ((powerInfo >>> 5) != 0b11111111111) {
            tag.setVoltage((powerInfo >>> 5) / 1000d + 1.6d);
        }
        if ((powerInfo & 0b11111) != 0b11111) {
            tag.setTxPower((powerInfo & 0b11111) * 2 - 40);
        }
        tag.setMovementCounter(data[15 + offset] & 0xFF);
        tag.setMeasurementSequenceNumber((data[17 + offset] & 0xFF) << 8 | data[16 + offset] & 0xFF);

        // make it pretty
        tag.setTemperature(round(tag.getTemperature(), 2));
        tag.setHumidity(round(tag.getHumidity(), 2));
        tag.setPressure(round(tag.getPressure(), 2));
        tag.setVoltage(round(tag.getVoltage(), 4));
        tag.setAccelX(round(tag.getAccelX(), 4));
        tag.setAccelY(round(tag.getAccelY(), 4));
        tag.setAccelZ(round(tag.getAccelZ(), 4));
        return tag;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
