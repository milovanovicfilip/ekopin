package com.example.mobile.util.mqtt

import android.content.Context
import android.util.Log
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MqttManager(
    context: Context,
    serverUri: String,
    clientId: String = MqttClient.generateClientId()
) {
    private val client = MqttAndroidClient(context.applicationContext, serverUri, clientId)
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(String, String) -> Unit>()

    @Volatile private var connecting = false
    @Volatile private var connected = false

    fun isConnected(): Boolean = connected && client.isConnected

    fun connect(
        onConnected: () -> Unit,
        onError: (Throwable) -> Unit,
        onMessage: (topic: String, payload: String) -> Unit
    ) {
        if (isConnected()) {
            Log.d(TAG, "connect() skipped: already connected (${client.serverURI})")
            onConnected()
            return
        }
        if (connecting) {
            Log.d(TAG, "connect() skipped: already connecting (${client.serverURI})")
            return
        }
        connecting = true

        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                connected = true
                connecting = false
                Log.d(TAG, "connectComplete reconnect=$reconnect uri=$serverURI isConnected=${client.isConnected}")
                onConnected()
            }

            override fun connectionLost(cause: Throwable?) {
                connected = false
                connecting = false
                Log.w(TAG, "connectionLost isConnected=${client.isConnected}", cause)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic == null || message == null) return
                val payload = message.payload?.toString(Charsets.UTF_8) ?: ""
                onMessage(topic, payload)
                listeners.forEach { it(topic, payload) }
            }


            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 20
        }

        Log.d(TAG, "Connecting to ${client.serverURI} ...")

        client.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d(TAG, "connect() onSuccess token=$asyncActionToken isConnected=${client.isConnected} (waiting for connectComplete)")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                connected = false
                connecting = false
                val err = exception ?: RuntimeException("MQTT connect failed")
                Log.e(TAG, "connect() onFailure token=$asyncActionToken", err)
                onError(err)
            }
        })
    }

    fun addMessageListener(l: (topic: String, payload: String) -> Unit) {
        listeners.add(l)
    }

    fun removeMessageListener(l: (String, String) -> Unit) {
        listeners.remove(l)
    }

    fun subscribe(topic: String, qos: Int = 1, onError: (Throwable) -> Unit) {
        if (!isConnected()) {
            onError(IllegalStateException("Subscribe failed: MQTT not connected"))
            return
        }
        client.subscribe(topic, qos, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d(TAG, "Subscribed to $topic (qos=$qos)")
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                val err = exception ?: RuntimeException("Subscribe failed")
                Log.e(TAG, "Subscribe failed for $topic", err)
                onError(err)
            }
        })
    }

    fun unsubscribe(topic: String, onError: (Throwable) -> Unit) {
        if (!isConnected()) {
            onError(IllegalStateException("Unsubscribe failed: MQTT not connected"))
            return
        }
        client.unsubscribe(topic, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d(TAG, "Unsubscribed from $topic")
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                val err = exception ?: RuntimeException("Unsubscribe failed")
                Log.e(TAG, "Unsubscribe failed for $topic", err)
                onError(err)
            }
        })
    }

    fun publish(
        topic: String,
        payload: String,
        qos: Int = 1,
        retained: Boolean = false,
        onError: (Throwable) -> Unit
    ) {
        if (!isConnected()) {
            onError(IllegalStateException("Publish failed: MQTT not connected"))
            return
        }

        val msg = MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply {
            this.qos = qos
            isRetained = retained
        }

        client.publish(topic, msg, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d(TAG, "Published to $topic (qos=$qos retained=$retained)")
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                val err = exception ?: RuntimeException("Publish failed")
                Log.e(TAG, "Publish failed for $topic", err)
                onError(err)
            }
        })
    }

    fun disconnect() {
        connecting = false
        connected = false
        if (client.isConnected) {
            try {
                client.disconnect()
                Log.d(TAG, "Disconnected")
            } catch (e: Exception) {
                Log.w(TAG, "Disconnect threw", e)
            }
        }
    }

    companion object {
        private const val TAG = "MQTT"
    }
}
