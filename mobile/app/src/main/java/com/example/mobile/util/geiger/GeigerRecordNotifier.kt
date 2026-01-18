package com.example.mobile.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mobile.R
import com.example.mobile.util.mqtt.MqttManager
import org.json.JSONObject
import java.util.Locale
import kotlin.random.Random

object GeigerRecordNotifier {

    private const val CHANNEL_ID = "geiger_records"
    @Volatile private var installed = false

    fun installOnce(appContext: Context, mqtt: MqttManager) {
        if (installed) return
        installed = true

        mqtt.addMessageListener { topic, payload ->
            if (!topic.endsWith("/geiger/record")) return@addMessageListener
            maybeNotify(appContext, payload)
        }
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Geiger counter records",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun maybeNotify(ctx: Context, payload: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
        }

        val obj = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val type = obj.optString("type")
        val value = obj.optDouble("value", Double.NaN)
        val prev = obj.optDouble("prev", Double.NaN)
        val deviceId = obj.optString("deviceId")

        if (value.isNaN()) return
        ensureChannel(ctx)

        val title = when (type) {
            "NEW_MAX" -> "New max radiation"
            "NEW_MIN" -> "New min radiation"
            else -> "Geiger counter record"
        }

        val body = buildString {
            append(String.format(Locale.getDefault(), "%.2f CPM", value))
            if (!prev.isNaN()) append(" (prev ${String.format(Locale.getDefault(), "%.2f", prev)} CPM)")
            if (deviceId.isNotBlank()) append(" • $deviceId")
        }

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(ctx)
                .notify(Random.nextInt(1, Int.MAX_VALUE), n)
        } catch (e: SecurityException) {
            Log.w("GEIGER_NOTIFY", "blocked", e)
        }
    }
}
