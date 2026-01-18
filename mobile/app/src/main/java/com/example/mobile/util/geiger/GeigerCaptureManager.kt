package com.example.mobile.util.geiger

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.mobile.R
import com.example.mobile.util.LocationHelper
import com.example.mobile.util.SensorDataLogger
import com.example.mobile.util.SimPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import java.util.Random

@SuppressLint("StaticFieldLeak")
object GeigerCaptureManager {

    private var handler: Handler? = null
    private var captureRunnable: Runnable? = null
    private var isCapturing = false
    private var context: Context? = null
    private var locationHelper: LocationHelper? = null

    private var minCpm = 0.0
    private var maxCpm = 100.0
    private var frequencyValue = 10
    private var frequencyUnit = "minut"

    private val random = Random()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    @Volatile private var lastCpm: Double? = null
    fun getLastCpm(): Double? = lastCpm

    private var publisher: ((topic: String, payload: String) -> Unit)? = null

    fun initialize(context: Context) {
        if (this.context == null) {
            this.context = context.applicationContext
            this.locationHelper = LocationHelper(context.applicationContext)
            this.handler = Handler(Looper.getMainLooper())
        }
    }

    fun setPublisher(p: (topic: String, payload: String) -> Unit) {
        publisher = p
    }

    fun isCapturing(): Boolean = isCapturing

    fun startCapturing(
        frequencyValue: Int,
        frequencyUnit: String,
        minCpm: Double,
        maxCpm: Double
    ) {
        if (handler == null || context == null) return
        if (minCpm >= maxCpm) return

        stopCapturing()

        this.frequencyValue = frequencyValue
        this.frequencyUnit = frequencyUnit
        this.minCpm = minCpm
        this.maxCpm = maxCpm
        this.isCapturing = true

        captureGeiger()

        val intervalMillis = computeIntervalMillis(frequencyValue, frequencyUnit)

        captureRunnable = object : Runnable {
            override fun run() {
                if (isCapturing) {
                    captureGeiger()
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

    private fun computeIntervalMillis(value: Int, unit: String): Long {
        val ctx = context ?: return 10 * 60_000L

        val u = unit.trim().lowercase(Locale.getDefault())

        val secondSl = "sekund"
        val minuteSl = "minut"
        val hourSl = "ur"

        val secondUi = ctx.getString(R.string.second).trim().lowercase(Locale.getDefault())
        val minuteUi = ctx.getString(R.string.minute).trim().lowercase(Locale.getDefault())
        val hourUi = ctx.getString(R.string.hour).trim().lowercase(Locale.getDefault())

        return when (u) {
            secondSl, secondUi -> value * 1000L
            minuteSl, minuteUi -> value * 60_000L
            hourSl, hourUi -> value * 3_600_000L
            else -> value * 60_000L
        }
    }

    private fun captureGeiger() {
        val ctx = context ?: return
        val locHelper = locationHelper ?: return

        scope.launch(Dispatchers.IO) {
            val location = locHelper.getCurrentLocation()

            val cpm = minCpm + (maxCpm - minCpm) * random.nextDouble()
            lastCpm = cpm

            val cpmFormatted = String.Companion.format(Locale.getDefault(), "%.2f", cpm)

            SensorDataLogger.logSensorData(
                sensorType = ctx.getString(R.string.sensor_geiger),
                value = "$cpmFormatted ${ctx.getString(R.string.geiger_unit)}",
                location = location
            )

            val deviceId = Build.MODEL ?: "android"
            
            // Upoštevaj globalno nastavitev sim/real
            val mode = if (SimPrefs.isEnabled(ctx)) "sim" else "real"
            val topic = "ekopin/$mode/geiger/add"

            val payload = JSONObject().apply {
                put("deviceId", deviceId)
                put("ts", System.currentTimeMillis())
                put("value", cpm)
                if (location != null) {
                    put("lat", location.latitude)
                    put("lng", location.longitude)
                    put("accuracy", location.accuracy)
                }
            }.toString()

            publisher?.invoke(topic, payload)
        }
    }

    fun getCurrentSettings(): GeigerSettings {
        return GeigerSettings(frequencyValue, frequencyUnit, minCpm, maxCpm)
    }

    data class GeigerSettings(
        val frequencyValue: Int,
        val frequencyUnit: String,
        val minCpm: Double,
        val maxCpm: Double
    )
}
