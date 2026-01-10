package com.example.mobile.ui.sensors

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobile.R
import com.example.mobile.data.model.Sensor
import com.example.mobile.databinding.ActivitySensorsBinding
import com.example.mobile.ui.camera.CameraActivity
import com.example.mobile.ui.temperature.TemperatureActivity
import com.example.mobile.util.TemperatureCaptureManager

class SensorsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensorsBinding
    private lateinit var adapter: SensorsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensorsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TemperatureCaptureManager.initialize(this)

        val sensors = listOf(
            Sensor(
                getString(R.string.sensor_temperature),
                getString(R.string.from_to_range),
                getString(R.string.every_minutes),
                "Koroška cesta 46, Maribor",
                TemperatureCaptureManager.isCapturing()
            ),
            Sensor(
                getString(R.string.sensor_camera),
                getString(R.string.capture_on_event),
                getString(R.string.manual),
                "Koroška cesta 46, Maribor",
                false
            )
        )

        binding.rvSensors.layoutManager = LinearLayoutManager(this)
        adapter = SensorsAdapter(sensors, { sensor ->
            when (sensor.name) {
                getString(R.string.sensor_temperature) -> {
                    startActivity(Intent(this, TemperatureActivity::class.java))
                }
                getString(R.string.sensor_camera) -> {
                    startActivity(Intent(this, CameraActivity::class.java))
                }
            }
        }) { sensor, isChecked ->
            if (sensor.name == getString(R.string.sensor_temperature)) {
                if (isChecked) {
                    val settings = TemperatureCaptureManager.getCurrentSettings()
                    if (settings.frequencyValue == 0) {
                        TemperatureCaptureManager.startCapturing(
                            frequencyValue = 10,
                            frequencyUnit = getString(R.string.minute),
                            minTemp = -20.0,
                            maxTemp = 36.0
                        )
                    } else {
                        TemperatureCaptureManager.startCapturing(
                            frequencyValue = settings.frequencyValue,
                            frequencyUnit = settings.frequencyUnit,
                            minTemp = settings.minTemp,
                            maxTemp = settings.maxTemp
                        )
                    }
                } else {
                    TemperatureCaptureManager.stopCapturing()
                }
            }
        }

        binding.rvSensors.adapter = adapter

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        val sensors = listOf(
            Sensor(
                getString(R.string.sensor_temperature),
                getString(R.string.from_to_range),
                getString(R.string.every_minutes),
                "Koroška cesta 46, Maribor",
                TemperatureCaptureManager.isCapturing()
            ),
            Sensor(
                getString(R.string.sensor_camera),
                getString(R.string.capture_on_event),
                getString(R.string.manual),
                "Koroška cesta 46, Maribor",
                false
            )
        )
        adapter = SensorsAdapter(sensors, { sensor ->
            when (sensor.name) {
                getString(R.string.sensor_temperature) -> {
                    startActivity(Intent(this, TemperatureActivity::class.java))
                }
                getString(R.string.sensor_camera) -> {
                    startActivity(Intent(this, CameraActivity::class.java))
                }
            }
        }) { sensor, isChecked ->
            if (sensor.name == getString(R.string.sensor_temperature)) {
                if (isChecked) {
                    val settings = TemperatureCaptureManager.getCurrentSettings()
                    if (settings.frequencyValue == 0) {
                        TemperatureCaptureManager.startCapturing(
                            frequencyValue = 10,
                            frequencyUnit = getString(R.string.minute),
                            minTemp = -20.0,
                            maxTemp = 36.0
                        )
                    } else {
                        TemperatureCaptureManager.startCapturing(
                            frequencyValue = settings.frequencyValue,
                            frequencyUnit = settings.frequencyUnit,
                            minTemp = settings.minTemp,
                            maxTemp = settings.maxTemp
                        )
                    }
                } else {
                    TemperatureCaptureManager.stopCapturing()
                }
            }
        }
        binding.rvSensors.adapter = adapter
    }
}
