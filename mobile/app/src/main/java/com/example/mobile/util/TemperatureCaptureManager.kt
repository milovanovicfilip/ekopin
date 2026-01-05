package com.example.mobile.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
object TemperatureCaptureManager {
    
    private var handler: Handler? = null
    private var captureRunnable: Runnable? = null
    private var isCapturing = false
    private var context: Context? = null
    private var locationHelper: LocationHelper? = null
    
    private var minTemp = -20.0
    private var maxTemp = 36.0
    private var frequencyValue = 10
    private var frequencyUnit = "minut"
    
    private val random = Random()
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    fun initialize(context: Context) {
        if (this.context == null) {
            this.context = context.applicationContext
            this.locationHelper = LocationHelper(context.applicationContext)
            this.handler = Handler(Looper.getMainLooper())
        }
    }
    
    fun isCapturing(): Boolean = isCapturing
    
    fun startCapturing(
        frequencyValue: Int,
        frequencyUnit: String,
        minTemp: Double,
        maxTemp: Double
    ) {
        if (handler == null || context == null) {
            return
        }
        
        if (minTemp >= maxTemp) {
            return
        }
        
        stopCapturing()
        
        this.frequencyValue = frequencyValue
        this.frequencyUnit = frequencyUnit
        this.minTemp = minTemp
        this.maxTemp = maxTemp
        this.isCapturing = true
        
        captureTemperature()
        
        val intervalMillis = when (frequencyUnit) {
            "sekund" -> frequencyValue * 1000L
            "minut" -> frequencyValue * 60 * 1000L
            "ur" -> frequencyValue * 60 * 60 * 1000L
            else -> 10 * 60 * 1000L
        }
        
        captureRunnable = object : Runnable {
            override fun run() {
                if (isCapturing) {
                    captureTemperature()
                    handler?.postDelayed(this, intervalMillis)
                }
            }
        }
        
        handler?.postDelayed(captureRunnable!!, intervalMillis)
    }
    
    fun stopCapturing() {
        isCapturing = false
        captureRunnable?.let { handler?.removeCallbacks(it) }
        captureRunnable = null
    }
    
    private fun captureTemperature() {
        val context = this.context ?: return
        val locationHelper = this.locationHelper ?: return
        
        scope.launch(Dispatchers.IO) {
            val location = locationHelper.getCurrentLocation()
            
            val temperature = minTemp + (maxTemp - minTemp) * random.nextDouble()
            val tempFormatted = String.format(Locale.getDefault(), "%.2f", temperature)
            
            SensorDataLogger.logSensorData(
                sensorType = context.getString(com.example.mobile.R.string.sensor_temperature),
                value = "$tempFormatted ${context.getString(com.example.mobile.R.string.temperature_unit)}",
                location = location
            )
        }
    }
    
    fun getCurrentSettings(): TemperatureSettings {
        return TemperatureSettings(frequencyValue, frequencyUnit, minTemp, maxTemp)
    }
    
    data class TemperatureSettings(
        val frequencyValue: Int,
        val frequencyUnit: String,
        val minTemp: Double,
        val maxTemp: Double
    )
}
