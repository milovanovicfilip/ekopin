package com.example.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mobile.databinding.ActivityMainBinding
import com.example.mobile.ui.sensors.SensorsActivity
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.example.mobile.ui.map.MapActivity
import com.example.mobile.ui.map.MapFragment

class MainActivity : AppCompatActivity(), AndroidFragmentApplication.Callbacks {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tileCamera.setOnClickListener {
            Toast.makeText(this, "Kamera", Toast.LENGTH_SHORT).show()
        }

        binding.tileMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        binding.tileSettings.setOnClickListener {
            Toast.makeText(this, "Nastavitve", Toast.LENGTH_SHORT).show()
        }

        binding.tileSensors.setOnClickListener {
            startActivity(Intent(this, SensorsActivity::class.java))
        }
    }

    override fun exit() {
        finish()
    }
}
