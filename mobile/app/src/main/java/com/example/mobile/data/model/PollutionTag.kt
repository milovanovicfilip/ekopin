package com.example.mobile.data.model

data class PollutionTag(
    val id: String,
    val deviceId: String,
    val label: String,
    val description: String,
    val severity: String,
    val lat: Double,
    val lng: Double,
    val ts: Long,
    val mode: String,
    val imageMime: String? = null,
    val imageBase64: String? = null
)
