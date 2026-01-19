package com.example.mobile.util

import android.location.Location
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SensorDataLogger {
    
    private const val TAG = "SensorData"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    fun logSensorData(
        sensorType: String,
        value: String,
        location: Location?
    ) {
        val timestamp = dateFormat.format(Date())
        val locationStr = if (location != null) {
            String.format(
                Locale.getDefault(),
                "Lat: %.6f, Lng: %.6f, Accuracy: %.1fm",
                location.latitude,
                location.longitude,
                location.accuracy
            )
        } else {
            "Lokacija ni na voljo"
        }
        
        val logMessage = String.format(
            Locale.getDefault(),
            "========================================\nSENZOR: %s\nČAS: %s\nLOKACIJA: %s\nVREDNOST: %s\n========================================",
            sensorType,
            timestamp,
            locationStr,
            value
        )
        
        Log.i(TAG, logMessage)
    }
    
    fun logCameraCapture(
        imagePath: String?,
        location: Location?
    ) {
        val timestamp = dateFormat.format(Date())
        val locationStr = if (location != null) {
            String.format(
                Locale.getDefault(),
                "Lat: %.6f, Lng: %.6f, Accuracy: %.1fm",
                location.latitude,
                location.longitude,
                location.accuracy
            )
        } else {
            "Lokacija ni na voljo"
        }
        
        val imagePathStr = imagePath ?: "Zajeta (simulacija obdelave)"
        
        val logMessage = String.format(
            Locale.getDefault(),
            "========================================\nSENZOR: Camera\nČAS: %s\nLOKACIJA: %s\nSLIKA: %s\nSTATUS: Poslano na obdelavo\n========================================",
            timestamp,
            locationStr,
            imagePathStr
        )
        
        Log.i(TAG, logMessage)
    }

    fun logMessage(message: String, location: Location? = null) {
        val timestamp = dateFormat.format(Date())
        val locationStr = if (location != null) {
            String.format(Locale.getDefault(), "Lat: %.6f, Lng: %.6f", location.latitude, location.longitude)
        } else {
            "N/A"
        }
        
        val logMessage = String.format(
            Locale.getDefault(),
            "========================================\nSPOROČILO: %s\nČAS: %s\nLOKACIJA: %s\n========================================",
            message,
            timestamp,
            locationStr
        )
        
        Log.i(TAG, logMessage)
    }
}
