package com.example.mobile.ui.sensors

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobile.R
import com.example.mobile.data.model.Sensor
import com.example.mobile.databinding.ActivitySensorsBinding
import com.example.mobile.ui.camera.CameraActivity
import com.example.mobile.ui.geiger.GeigerActivity
import com.example.mobile.ui.temperature.TemperatureActivity
import com.example.mobile.util.SimPrefs
import com.example.mobile.util.geiger.GeigerCaptureManager
import com.example.mobile.util.mqtt.MqttProvider
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
        GeigerCaptureManager.initialize(this)

        // Setup MQTT publishers
        val mqtt = MqttProvider.mqtt
        if (mqtt != null) {
            TemperatureCaptureManager.setPublisher { topic, payload ->
                mqtt.publish(topic = topic, payload = payload, qos = 1, retained = false, onError = { })
            }
            GeigerCaptureManager.setPublisher { topic, payload ->
                mqtt.publish(topic = topic, payload = payload, qos = 1, retained = false, onError = { })
            }
        }

        binding.rvSensors.layoutManager = LinearLayoutManager(this)

        adapter = SensorsAdapter(
            sensors = buildSensorsList(),
            onClick = { sensor ->
                when (sensor.name) {
                    getString(R.string.sensor_temperature) -> startActivity(Intent(this, TemperatureActivity::class.java))
                    getString(R.string.sensor_geiger) -> startActivity(Intent(this, GeigerActivity::class.java))
                    getString(R.string.sensor_camera) -> startActivity(Intent(this, CameraActivity::class.java))
                }
            },
            onToggle = { sensor, isChecked ->
                when (sensor.name) {
                    getString(R.string.sensor_temperature) -> {
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
                    getString(R.string.sensor_geiger) -> {
                        if (isChecked) {
                            val s = GeigerCaptureManager.getCurrentSettings()
                            GeigerCaptureManager.startCapturing(
                                frequencyValue = if (s.frequencyValue > 0) s.frequencyValue else 10,
                                frequencyUnit = if (s.frequencyUnit.isNotBlank()) s.frequencyUnit else getString(R.string.minute),
                                minCpm = s.minCpm,
                                maxCpm = s.maxCpm
                            )
                        } else {
                            GeigerCaptureManager.stopCapturing()
                        }
                        refreshList()
                    }
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
                    getString(R.string.sensor_geiger) -> startActivity(Intent(this, GeigerActivity::class.java))
                    getString(R.string.sensor_camera) -> startActivity(Intent(this, CameraActivity::class.java))
                }
            },
            onToggle = { sensor, isChecked ->
                when (sensor.name) {
                    getString(R.string.sensor_temperature) -> {
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
                    getString(R.string.sensor_geiger) -> {
                        if (isChecked) {
                            val s = GeigerCaptureManager.getCurrentSettings()
                            GeigerCaptureManager.startCapturing(
                                frequencyValue = if (s.frequencyValue > 0) s.frequencyValue else 10,
                                frequencyUnit = if (s.frequencyUnit.isNotBlank()) s.frequencyUnit else getString(R.string.minute),
                                minCpm = s.minCpm,
                                maxCpm = s.maxCpm
                            )
                        } else {
                            GeigerCaptureManager.stopCapturing()
                        }
                        refreshList()
                    }
                }
            }
        )
        binding.rvSensors.adapter = adapter
    }

    private fun buildSensorsList(): List<Sensor> {
        val isSimMode = SimPrefs.isEnabled(this)
        
        val tempSettings = TemperatureCaptureManager.getCurrentSettings()
        val isTempCapturing = TemperatureCaptureManager.isCapturing()

        // V real mode ne prikazujemo razpona (hard-coded)
        val tempRangeText = if (isSimMode) {
            String.format(
                Locale.getDefault(),
                "↝ Od %.2f do %.2f",
                tempSettings.minTemp,
                tempSettings.maxTemp
            )
        } else {
            "" // Prazen string za real mode
        }

        val tempFrequencyText = "⏱ Vsakih ${tempSettings.frequencyValue} ${prettyUnit(tempSettings.frequencyValue, tempSettings.frequencyUnit)}"

        val geigerSettings = GeigerCaptureManager.getCurrentSettings()
        val isGeigerCapturing = GeigerCaptureManager.isCapturing()

        // V real mode ne prikazujemo razpona (hard-coded)
        val geigerRangeText = if (isSimMode) {
            String.format(
                Locale.getDefault(),
                "↝ Od %.2f do %.2f CPM",
                geigerSettings.minCpm,
                geigerSettings.maxCpm
            )
        } else {
            "" // Prazen string za real mode
        }

        val geigerFrequencyText = "⏱ Vsakih ${geigerSettings.frequencyValue} ${prettyUnit(geigerSettings.frequencyValue, geigerSettings.frequencyUnit)}"

        return listOf(
            Sensor(
                name = getString(R.string.sensor_temperature),
                range = tempRangeText,
                frequency = tempFrequencyText,
                location = "Koroška cesta 46, Maribor",
                enabled = isTempCapturing
            ),
            Sensor(
                name = getString(R.string.sensor_geiger),
                range = geigerRangeText,
                frequency = geigerFrequencyText,
                location = "Koroška cesta 46, Maribor",
                enabled = isGeigerCapturing
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
