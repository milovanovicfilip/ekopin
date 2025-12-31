package com.example.mobile.ui.sensors

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobile.data.model.Sensor
import com.example.mobile.databinding.ActivitySensorsBinding
import com.example.mobile.ui.camera.CameraActivity
import com.example.mobile.ui.temperature.TemperatureActivity

class SensorsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensorsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensorsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sensors = listOf(
            Sensor(
                "Temperature",
                "From -20.00 to 36.00",
                "Every 10 minutes",
                "Koroška cesta 46, Maribor",
                true
            ),
            Sensor(
                "Camera",
                "Capture on event",
                "Manual",
                "Koroška cesta 46, Maribor",
                false
            )
        )

        binding.rvSensors.layoutManager = LinearLayoutManager(this)
        binding.rvSensors.adapter = SensorsAdapter(sensors) { sensor ->
            when (sensor.name) {
                "Temperature" -> {
                    startActivity(Intent(this, TemperatureActivity::class.java))
                }
                "Camera" -> {
                    startActivity(Intent(this, CameraActivity::class.java))
                }
            }
        }

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}
