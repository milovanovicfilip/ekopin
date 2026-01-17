package com.example.mobile.util

import android.content.Context
import java.util.UUID

object DeviceId {
    private const val PREF = "device_prefs"
    private const val KEY = "device_id"

    fun get(context: Context): String {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val existing = p.getString(KEY, null)
        if (existing != null) return existing

        val created = UUID.randomUUID().toString()
        p.edit().putString(KEY, created).apply()
        return created
    }
}
