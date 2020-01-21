package com.ruuvi.station.bluetooth.domain

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.ruuvi.station.bluetooth.HumidityCalibration
import com.ruuvi.station.bluetooth.interfaces.RuuviTagFactory
import com.ruuvi.station.bluetooth.interfaces.RuuviTagListener
import com.ruuvi.station.bluetooth.RuuviTagScanner
import com.ruuvi.station.bluetooth.interfaces.IRuuviTag
import com.ruuvi.station.database.RuuviTagRepository
import com.ruuvi.station.gateway.Http
import com.ruuvi.station.model.TagSensorReading
import com.ruuvi.station.util.AlarmChecker
import com.ruuvi.station.util.Foreground
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.HashMap

class BluetoothScannerInteractor(
    private val application: Application,
    private val ruuviTagFactory: RuuviTagFactory
) {

    private val TAG: String = BluetoothScannerInteractor::class.java.simpleName

    private val backgroundTags = ArrayList<IRuuviTag>()

    private val lastLogged: MutableMap<String, Long> = HashMap()
    private val LOG_INTERVAL = 5 // seconds

    private var scanning = false

    private var foreground: Boolean = true.also {
        val listener: Foreground.Listener = object : Foreground.Listener {
            override fun onBecameForeground() {
                foreground = true
            }

            override fun onBecameBackground() {
                foreground = false
            }
        }

        Foreground.init(application)
        Foreground.get().addListener(listener)
    }

    private val ruuviTagScanner by lazy {
        RuuviTagScanner(
            RuuviTagListener { logTag(it, application, foreground) },
            ruuviTagFactory,
            application
        )
    }

    fun logTag(ruuviTag: IRuuviTag, context: Context?, foreground: Boolean) {
        var ruuviTag = HumidityCalibration.apply(ruuviTag)

        val dbTag = RuuviTagRepository.get(ruuviTag.id)
        if (dbTag != null) {
            ruuviTag = dbTag.preserveData(ruuviTag)
            RuuviTagRepository.update(ruuviTag)
            if (!dbTag.favorite) return
        } else {
            ruuviTag.updateAt = Date()
            RuuviTagRepository.save(ruuviTag)
            return
        }
        if (!foreground) {
            if (ruuviTag.favorite && checkForSameTag(backgroundTags, ruuviTag) == -1) {
                backgroundTags.add(ruuviTag)
            }
            return
        }
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, -LOG_INTERVAL)
        val loggingThreshold = calendar.time.time
        for ((key, value) in lastLogged) {
            if (key == ruuviTag.id && value > loggingThreshold) {
                return
            }
        }
        val tags: MutableList<IRuuviTag> = ArrayList()
        tags.add(ruuviTag)
        Http.post(tags, null, context)
        ruuviTag.id?.let { id ->
            lastLogged[id] = Date().time
        }
        val reading = TagSensorReading(ruuviTag)
        reading.save()
        AlarmChecker.check(ruuviTag, context)
    }

    fun getBackgroundTags(): List<IRuuviTag> = backgroundTags

    fun clearBackgroundTags() {
        backgroundTags.clear()
    }

    fun startScan() {
        if (scanning || !ruuviTagScanner.canScan()) return
        scanning = true
        try {
            ruuviTagScanner.start()
        } catch (e: Exception) {
            Log.e(TAG, e.message)
            scanning = false
            Toast.makeText(application, "Couldn't start scanning, is bluetooth disabled?", Toast.LENGTH_LONG).show()
        }
    }

    fun stopScan() {
        if (!ruuviTagScanner.canScan()) return
        scanning = false
        ruuviTagScanner.stop()
    }

    private fun checkForSameTag(arr: List<IRuuviTag>, ruuvi: IRuuviTag): Int {
        for (i in arr.indices) {
            if (ruuvi.id == arr[i].id) {
                return i
            }
        }
        return -1
    }
}