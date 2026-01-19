package com.example.mobile.util.mqtt

object MqttProvider {
    var mqtt: MqttManager? = null

    @Volatile var globalSubsInstalled: Boolean = false
    @Volatile var tempNotifierInstalled: Boolean = false
    @Volatile var geigerNotifierInstalled: Boolean = false
}
