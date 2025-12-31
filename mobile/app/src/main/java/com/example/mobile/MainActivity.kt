package com.example.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.mobile.databinding.ActivityMainBinding
import com.example.mobile.ui.sensors.SensorsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tileCamera.setOnClickListener {
            Toast.makeText(this, "Kamera", Toast.LENGTH_SHORT).show()
        }

        binding.tileMap.setOnClickListener {
            Toast.makeText(this, "Zemljevid", Toast.LENGTH_SHORT).show()
        }

        binding.tileSettings.setOnClickListener {
            Toast.makeText(this, "Nastavitvr", Toast.LENGTH_SHORT).show()
        }

        binding.tileSensors.setOnClickListener {
            val intent = Intent(this, SensorsActivity::class.java)
            startActivity(intent)
        }
    }
}
