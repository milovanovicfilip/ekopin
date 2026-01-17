package com.example.mobile.util

import android.app.Application
import android.util.Log
import com.example.mobile.BuildConfig
import com.example.mobile.util.mqtt.MqttManager
import com.example.mobile.util.mqtt.MqttProvider

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        val brokerUri = "tcp://${BuildConfig.MQTT_HOST}:${BuildConfig.MQTT_PORT}"
        val mqtt = MqttManager(applicationContext, brokerUri)
        MqttProvider.mqtt = mqtt

        mqtt.connect(
            onConnected = {
                Log.d("APP_MQTT", "connected")
                ensureGlobalSubscriptions()
                ensureTempNotifierInstalled()
            },
            onError = { e -> Log.e("APP_MQTT", "connect error", e) },
            onMessage = { _, _ -> }
        )
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

        sub("ekopin/+/temperature/record")

        Log.d("APP_MQTT", "Global subscriptions ensured")
    }

    private fun ensureTempNotifierInstalled() {
        val mqtt = MqttProvider.mqtt ?: return
        if (MqttProvider.tempNotifierInstalled) return
        MqttProvider.tempNotifierInstalled = true

        TemperatureRecordNotifier.installOnce(applicationContext, mqtt)
        Log.d("APP_MQTT", "Temp record notifier installed")
    }
}
