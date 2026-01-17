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
import com.example.mobile.util.temperature.TemperatureCaptureManager
import java.util.Locale

class SensorsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensorsBinding
    private lateinit var adapter: SensorsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensorsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TemperatureCaptureManager.initialize(this)

        binding.rvSensors.layoutManager = LinearLayoutManager(this)

        adapter = SensorsAdapter(
            sensors = buildSensorsList(),
            onClick = { sensor ->
                when (sensor.name) {
                    getString(R.string.sensor_temperature) -> startActivity(Intent(this, TemperatureActivity::class.java))
                    getString(R.string.sensor_camera) -> startActivity(Intent(this, CameraActivity::class.java))
                }
            },
            onToggle = { sensor, isChecked ->
                if (sensor.name == getString(R.string.sensor_temperature)) {
                    if (isChecked) {
                        val s = TemperatureCaptureManager.getCurrentSettings()
                        TemperatureCaptureManager.startCapturing(
                            frequencyValue = if (s.frequencyValue > 0) s.frequencyValue else 10,
                            frequencyUnit = if (s.frequencyUnit.isNotBlank()) s.frequencyUnit else getString(R.string.minute),
                            minTemp = s.minTemp,
                            maxTemp = s.maxTemp
                        )
                    } else {
                        TemperatureCaptureManager.stopCapturing()
                    }
                    refreshList()
                }
            }
        )

        binding.rvSensors.adapter = adapter

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        adapter = SensorsAdapter(
            sensors = buildSensorsList(),
            onClick = { sensor ->
                when (sensor.name) {
                    getString(R.string.sensor_temperature) -> startActivity(Intent(this, TemperatureActivity::class.java))
                    getString(R.string.sensor_camera) -> startActivity(Intent(this, CameraActivity::class.java))
                }
            },
            onToggle = { sensor, isChecked ->
                if (sensor.name == getString(R.string.sensor_temperature)) {
                    if (isChecked) {
                        val s = TemperatureCaptureManager.getCurrentSettings()
                        TemperatureCaptureManager.startCapturing(
                            frequencyValue = if (s.frequencyValue > 0) s.frequencyValue else 10,
                            frequencyUnit = if (s.frequencyUnit.isNotBlank()) s.frequencyUnit else getString(R.string.minute),
                            minTemp = s.minTemp,
                            maxTemp = s.maxTemp
                        )
                    } else {
                        TemperatureCaptureManager.stopCapturing()
                    }
                    refreshList()
                }
            }
        )
        binding.rvSensors.adapter = adapter
    }

    private fun buildSensorsList(): List<Sensor> {
        val settings = TemperatureCaptureManager.getCurrentSettings()
        val isCapturing = TemperatureCaptureManager.isCapturing()

        val rangeText = String.format(
            Locale.getDefault(),
            "↝ Od %.2f do %.2f",
            settings.minTemp,
            settings.maxTemp
        )

        val frequencyText = "⏱ Vsakih ${settings.frequencyValue} ${prettyUnit(settings.frequencyValue, settings.frequencyUnit)}"

        return listOf(
            Sensor(
                name = getString(R.string.sensor_temperature),
                range = rangeText,
                frequency = frequencyText,
                location = "Koroška cesta 46, Maribor",
                enabled = isCapturing
            ),
            Sensor(
                name = getString(R.string.sensor_camera),
                range = getString(R.string.capture_on_event),
                frequency = getString(R.string.manual),
                location = "Koroška cesta 46, Maribor",
                enabled = false
            )
        )
    }

    private fun prettyUnit(value: Int, unit: String): String {
        val u = unit.trim().lowercase(Locale.getDefault())

        val secondUi = getString(R.string.second).trim().lowercase(Locale.getDefault())
        val minuteUi = getString(R.string.minute).trim().lowercase(Locale.getDefault())
        val hourUi = getString(R.string.hour).trim().lowercase(Locale.getDefault())

        return when (u) {
            "sekund", secondUi -> if (value == 1) "sekundo" else "sekund"
            "minut", minuteUi -> if (value == 1) "minuto" else "minut"
            "ur", hourUi -> if (value == 1) "uro" else "ur"
            else -> "minut"
        }
    }
}
