package com.example.mobile.util

import android.content.Context

data class SimSettings(
    val enabled: Boolean,
    val topic: String,
    val min: Double,
    val max: Double,
    val freqSeconds: Int,
    val useManualLocation: Boolean,
    val lat: Double,
    val lon: Double
)

object SimPrefs {
    private const val PREFS_NAME = "sim_prefs"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_TOPIC = "topic"
    private const val KEY_MIN = "min"
    private const val KEY_MAX = "max"
    private const val KEY_FREQ = "freqSeconds"
    private const val KEY_USE_MANUAL_LOC = "useManualLocation"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"

    fun get(context: Context): SimSettings {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return SimSettings(
            enabled = p.getBoolean(KEY_ENABLED, false),
            topic = p.getString(KEY_TOPIC, "dt/device/sensor/temperature") ?: "dt/device/sensor/temperature",
            min = java.lang.Double.longBitsToDouble(p.getLong(KEY_MIN, java.lang.Double.doubleToRawLongBits(-20.0))),
            max = java.lang.Double.longBitsToDouble(p.getLong(KEY_MAX, java.lang.Double.doubleToRawLongBits(36.0))),
            freqSeconds = p.getInt(KEY_FREQ, 600),
            useManualLocation = p.getBoolean(KEY_USE_MANUAL_LOC, true),
            lat = java.lang.Double.longBitsToDouble(p.getLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(46.56))),
            lon = java.lang.Double.longBitsToDouble(p.getLong(KEY_LON, java.lang.Double.doubleToRawLongBits(15.64)))
        )
    }

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun setManualLocation(context: Context, useManual: Boolean, lat: Double, lon: Double) {
        edit(context) {
            putBoolean(KEY_USE_MANUAL_LOC, useManual)
            putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(lat))
            putLong(KEY_LON, java.lang.Double.doubleToRawLongBits(lon))
        }
    }

    private inline fun edit(context: Context, block: android.content.SharedPreferences.Editor.() -> Unit) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        p.edit().apply {
            block()
            apply()
        }
    }
}
