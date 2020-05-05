package com.ruuvi.station.app.preferences

import android.content.Context
import android.content.SharedPreferences
import android.support.v7.preference.PreferenceManager
import com.ruuvi.station.model.HumidityUnit
import com.ruuvi.station.util.BackgroundScanModes
import com.ruuvi.station.util.Constants

class Preferences(val context: Context) {
    val sharedPreferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    var backgroundScanInterval: Int
        get() = sharedPreferences.getInt("pref_background_scan_interval", Constants.DEFAULT_SCAN_INTERVAL)
        set(interval) {
            sharedPreferences.edit().putInt("pref_background_scan_interval", interval).apply()
        }

    var backgroundScanMode: BackgroundScanModes
        get() = BackgroundScanModes.fromInt(sharedPreferences.getInt("pref_background_scan_mode", BackgroundScanModes.DISABLED.value))!!
        set(mode) {
            sharedPreferences.edit().putInt("pref_background_scan_mode", mode.value).apply()
        }

    var isFirstStart: Boolean
        get() = sharedPreferences.getBoolean("FIRST_START_PREF", true)
        set(enabled) {
            sharedPreferences.edit().putBoolean("FIRST_START_PREF", enabled).apply()
        }

    var isFirstGraphVisit: Boolean
        get() = sharedPreferences.getBoolean("first_graph_visit", true)
        set(enabled) {
            sharedPreferences.edit().putBoolean("first_graph_visit", enabled).apply()
        }

    var temperatureUnit: String
        get() = sharedPreferences.getString("pref_temperature_unit", DEFAULT_TEMPERATURE_UNIT) ?: DEFAULT_TEMPERATURE_UNIT
        set(unit) {
            sharedPreferences.edit().putString("pref_temperature_unit", unit).apply()
        }

    var humidityUnit: HumidityUnit
        get() {
            val code = sharedPreferences.getInt("pref_humidity_unit", 0)
            return when (code) {
                0 -> HumidityUnit.PERCENT
                1 -> HumidityUnit.GM3
                2 -> HumidityUnit.DEW
                else -> HumidityUnit.PERCENT
            }
        }
        set(value) {
            sharedPreferences.edit().putInt("pref_humidity_unit", value.code).apply()
        }

    var gatewayUrl: String
        get() = sharedPreferences.getString("pref_backend", DEFAULT_GATEWAY_URL) ?: DEFAULT_GATEWAY_URL
        set(url) {
            sharedPreferences.edit().putString("pref_backend", url).apply()
        }

    var deviceId: String
        get() = sharedPreferences.getString("pref_device_id", DEFAULT_DEVICE_ID) ?: DEFAULT_DEVICE_ID
        set(id) {
            sharedPreferences.edit().putString("pref_device_id", id).apply()
        }

    var serviceWakelock: Boolean
        get() = sharedPreferences.getBoolean("pref_wakelock", false)
        set(enabled) {
            sharedPreferences.edit().putBoolean("pref_wakelock", enabled).apply()
        }

    var dashboardEnabled: Boolean
        get() = sharedPreferences.getBoolean("DASHBOARD_ENABLED_PREF", false)
        set(enabled) {
            sharedPreferences.edit().putBoolean("DASHBOARD_ENABLED_PREF", enabled).apply()
        }

    var batterySaverEnabled: Boolean
        get() = sharedPreferences.getBoolean("pref_bgscan_battery_saving", false)
        set(enabled) {
            sharedPreferences.edit().putBoolean("pref_bgscan_battery_saving", enabled).apply()
        }

    companion object {
        const val DEFAULT_TEMPERATURE_UNIT = "C"
        const val DEFAULT_GATEWAY_URL = ""
        const val DEFAULT_DEVICE_ID = ""
    }
}