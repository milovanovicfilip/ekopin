package com.example.mobile.util

import android.content.Context
import android.content.SharedPreferences

object SettingsPrefs {
    private const val PREFS_NAME = "settings_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_SIM_LOCATION = "sim_location"

    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM = "system"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getThemeMode(context: Context): String {
        return getPrefs(context).getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    fun setThemeMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun getSimLocation(context: Context): String {
        return getPrefs(context).getString(KEY_SIM_LOCATION, "") ?: ""
    }

    fun setSimLocation(context: Context, location: String) {
        getPrefs(context).edit().putString(KEY_SIM_LOCATION, location).apply()
    }
}
