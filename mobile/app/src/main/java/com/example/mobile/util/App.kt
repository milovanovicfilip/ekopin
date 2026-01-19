package com.example.mobile.util

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.example.mobile.BuildConfig
import com.example.mobile.util.mqtt.MqttManager
import com.example.mobile.util.mqtt.MqttProvider

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        applyThemeFromPreferences()

        val brokerUri = "tcp://${BuildConfig.MQTT_HOST}:${BuildConfig.MQTT_PORT}"
        val mqtt = MqttManager(applicationContext, brokerUri)
        MqttProvider.mqtt = mqtt

        mqtt.connect(
            onConnected = {
                Log.d("APP_MQTT", "connected")
                ensureGlobalSubscriptions()
                ensureTempNotifierInstalled()
                ensureGeigerNotifierInstalled()
            },
            onError = { e -> Log.e("APP_MQTT", "connect error", e) },
            onMessage = { _, _ -> }
        )
    }

    private fun applyThemeFromPreferences() {
        val savedTheme = SettingsPrefs.getThemeMode(this)
        val mode = when (savedTheme) {
            SettingsPrefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsPrefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            SettingsPrefs.THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun ensureGlobalSubscriptions() {
        val mqtt = MqttProvider.mqtt ?: return

        fun sub(topic: String) {
            mqtt.subscribe(topic, qos = 1) { err ->
                Log.e("APP_MQTT", "subscribe failed for $topic", err)
            }
        }

        sub("ekopin/sim/pollution_tags/added")
        sub("ekopin/sim/pollution_tags/deleted")
        sub("ekopin/real/pollution_tags/added")
        sub("ekopin/real/pollution_tags/deleted")

        sub("ekopin/sim/temperature/record")
        sub("ekopin/real/temperature/record")

        sub("ekopin/sim/geiger/record")
        sub("ekopin/real/geiger/record")

        Log.d("APP_MQTT", "Global subscriptions ensured")
    }

    private fun ensureTempNotifierInstalled() {
        val mqtt = MqttProvider.mqtt ?: return
        if (MqttProvider.tempNotifierInstalled) return
        MqttProvider.tempNotifierInstalled = true

        TemperatureRecordNotifier.installOnce(applicationContext, mqtt)
        Log.d("APP_MQTT", "Temp record notifier installed")
    }

    private fun ensureGeigerNotifierInstalled() {
        val mqtt = MqttProvider.mqtt ?: return
        if (MqttProvider.geigerNotifierInstalled) return
        MqttProvider.geigerNotifierInstalled = true

        GeigerRecordNotifier.installOnce(applicationContext, mqtt)
        Log.d("APP_MQTT", "Geiger record notifier installed")
    }
}
