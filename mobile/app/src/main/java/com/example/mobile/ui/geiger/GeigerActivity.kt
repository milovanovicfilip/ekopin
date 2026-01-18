package com.example.mobile.ui.geiger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.mobile.BuildConfig
import com.example.mobile.R
import com.example.mobile.databinding.ActivityGeigerBinding
import com.example.mobile.util.geiger.GeigerCaptureManager
import com.example.mobile.util.mqtt.MqttManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeigerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGeigerBinding

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    private lateinit var mqttManager: MqttManager
    private val serverUri = "tcp://${BuildConfig.MQTT_HOST}:${BuildConfig.MQTT_PORT}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityGeigerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        GeigerCaptureManager.initialize(this)

        mqttManager = MqttManager(this, serverUri)
        mqttManager.connect(
            onConnected = {
                GeigerCaptureManager.setPublisher { topic, payload ->
                    mqttManager.publish(
                        topic = topic,
                        payload = payload,
                        qos = 1,
                        retained = false,
                        onError = { }
                    )
                }
            },
            onError = { },
            onMessage = { _, _ -> }
        )

        setupUI()
        checkPermissions()
        updateUIState()
        startUIUpdate()
    }

    private fun setupUI() {
        val units = arrayOf(
            getString(R.string.second),
            getString(R.string.minute),
            getString(R.string.hour)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, units)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spUnit.adapter = adapter

        val settings = GeigerCaptureManager.getCurrentSettings()
        binding.etFrequency.setText(settings.frequencyValue.toString())
        binding.etMinCpm.setText(settings.minCpm.toString())
        binding.etMaxCpm.setText(settings.maxCpm.toString())

        val unitIndex = when (settings.frequencyUnit.trim().lowercase(Locale.getDefault())) {
            getString(R.string.second).trim().lowercase(Locale.getDefault()), "sekund" -> 0
            getString(R.string.minute).trim().lowercase(Locale.getDefault()), "minut" -> 1
            getString(R.string.hour).trim().lowercase(Locale.getDefault()), "ur" -> 2
            else -> 1
        }
        binding.spUnit.setSelection(unitIndex)

        binding.btnStart.setOnClickListener { startCapturing() }
        binding.btnStop.setOnClickListener { stopCapturing() }
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
        }
    }

    private fun startCapturing() {
        val frequencyValue = binding.etFrequency.text.toString().toIntOrNull() ?: 10
        val minCpm = binding.etMinCpm.text.toString().toDoubleOrNull() ?: 0.0
        val maxCpm = binding.etMaxCpm.text.toString().toDoubleOrNull() ?: 100.0
        val frequencyUnit = binding.spUnit.selectedItem.toString()

        if (minCpm >= maxCpm) {
            Toast.makeText(this, getString(R.string.error_cpm_range), Toast.LENGTH_SHORT).show()
            return
        }

        if (!mqttManager.isConnected()) {
            Toast.makeText(this, "MQTT ni povezan", Toast.LENGTH_SHORT).show()
            return
        }

        GeigerCaptureManager.startCapturing(
            frequencyValue = frequencyValue,
            frequencyUnit = frequencyUnit,
            minCpm = minCpm,
            maxCpm = maxCpm
        )

        updateUIState()
        addLogEntry(getString(R.string.geiger_capture_started, frequencyValue, frequencyUnit))
    }

    private fun stopCapturing() {
        GeigerCaptureManager.stopCapturing()
        updateUIState()
        addLogEntry(getString(R.string.geiger_capture_stopped))
    }

    private fun updateUIState() {
        val isCapturing = GeigerCaptureManager.isCapturing()
        binding.btnStart.isEnabled = !isCapturing
        binding.btnStop.isEnabled = isCapturing
        binding.etFrequency.isEnabled = !isCapturing
        binding.etMinCpm.isEnabled = !isCapturing
        binding.etMaxCpm.isEnabled = !isCapturing
        binding.spUnit.isEnabled = !isCapturing
    }

    private fun startUIUpdate() {
        updateRunnable = object : Runnable {
            override fun run() {
                updateGeigerDisplay()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun updateGeigerDisplay() {
        val cpm = GeigerCaptureManager.getLastCpm()
        if (cpm != null) {
            val cpmFormatted = String.format(Locale.getDefault(), "%.2f", cpm)
            binding.tvCurrentCpm.text = "$cpmFormatted ${getString(R.string.geiger_unit)}"
            binding.tvLastUpdate.text = getString(R.string.last_update, dateFormat.format(Date()))
        }
    }

    private fun addLogEntry(entry: String) {
        val timestamp = dateFormat.format(Date())
        val currentLog = binding.tvLog.text.toString()
        val newLog = getString(R.string.log_entry_format, timestamp, entry) + currentLog.replace(getString(R.string.log_data), "")
        binding.tvLog.text = getString(R.string.log_data) + newLog

        binding.svLog.post {
            binding.svLog.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { handler.removeCallbacks(it) }
        mqttManager.disconnect()
    }
}
