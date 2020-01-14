package com.ruuvi.station.bluetooth

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.ruuvi.station.gateway.Http
import com.ruuvi.station.model.LeScanResult
import com.ruuvi.station.model.RuuviTag
import com.ruuvi.station.model.TagSensorReading
import com.ruuvi.station.service.ScannerService
import com.ruuvi.station.util.AlarmChecker
import com.ruuvi.station.util.Foreground
import com.ruuvi.station.util.Preferences
import com.ruuvi.station.util.Utils
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.HashMap

class BluetoothScannerInteractor(private val application: Application) {

    private val TAG: String = BluetoothScannerInteractor::class.java.simpleName

    private val prefs: Preferences = Preferences(application)

    private var foreground: Boolean = true

    private val backgroundTags = ArrayList<RuuviTag>()

    //    private val bluetoothAdapter: BluetoothAdapter? = null
    private val scanFilters: List<ScanFilter> = ArrayList()

    private var scanning = false
    private val scanSettings = ScanSettings.Builder()
        .setReportDelay(0)
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    val scanner by lazy {
        val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.getAdapter()
        bluetoothAdapter.bluetoothLeScanner
    }

    init {

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

    fun logTag(ruuviTag: RuuviTag, context: Context?, foreground: Boolean) {
        var ruuviTag = ruuviTag
        val dbTag = RuuviTag.get(ruuviTag.id)
        if (dbTag != null) {
            ruuviTag = dbTag.preserveData(ruuviTag)
            ruuviTag.update()
            if (!dbTag.favorite) return
        } else {
            ruuviTag.updateAt = Date()
            ruuviTag.save()
            return
        }
        if (!foreground) {
            if (ruuviTag.favorite && checkForSameTag(backgroundTags, ruuviTag) == -1) {
                backgroundTags.add(ruuviTag)
            }
            return
        }
        if (ScannerService.lastLogged == null) ScannerService.lastLogged = HashMap()
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, -ScannerService.LOG_INTERVAL)
        val loggingThreshold = calendar.time.time
        for ((key, value) in ScannerService.lastLogged) {
            if (key == ruuviTag.id && value > loggingThreshold) {
                return
            }
        }
        val tags: MutableList<RuuviTag> = ArrayList()
        tags.add(ruuviTag)
        Http.post(tags, null, context)
        ScannerService.lastLogged[ruuviTag.id] = Date().time
        val reading = TagSensorReading(ruuviTag)
        reading.save()
        AlarmChecker.check(ruuviTag, context)
    }

    fun getBackgroundTags(): List<RuuviTag> = backgroundTags

    fun clearBackgroundTags() {
        backgroundTags.clear()
    }

    fun startScan() {
        if (scanning || !canScan()) return
        scanning = true
        try {
            scanner.startScan(Utils.getScanFilters(), scanSettings, nsCallback)
        } catch (e: Exception) {
            Log.e(TAG, e.message)
            scanning = false
            Toast.makeText(application, "Couldn't start scanning, is bluetooth disabled?", Toast.LENGTH_LONG).show()
        }
    }

    fun stopScan() {
        if (!canScan()) return
        scanning = false
        scanner.stopScan(nsCallback)
    }

    private val nsCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            foundDevice(result.device, result.rssi, result.scanRecord.bytes)
        }
    }

    private fun foundDevice(device: BluetoothDevice, rssi: Int, data: ByteArray) {
        val dev = LeScanResult()
        dev.device = device
        dev.rssi = rssi
        dev.scanData = data
        //Log.d(TAG, "found: " + device.getAddress());
        val tag = dev.parse(application)
        if (tag != null) logTag(tag, application, foreground)
    }

    private fun canScan(): Boolean {
        return scanner != null
    }

    private fun checkForSameTag(arr: List<RuuviTag>, ruuvi: RuuviTag): Int {
        for (i in arr.indices) {
            if (ruuvi.id == arr[i].id) {
                return i
            }
        }
        return -1
    }

//    fun Exists(id: String?): Boolean {
//        val count = SQLite.selectCountOf()
//            .from(RuuviTag::class.java)
//            .where(RuuviTag_Table.id.eq(id))
//            .count()
//        return count > 0
//    }
}