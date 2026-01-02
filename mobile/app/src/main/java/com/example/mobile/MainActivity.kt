package com.example.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mobile.databinding.ActivityMainBinding
import com.example.mobile.ui.sensors.SensorsActivity
import com.badlogic.gdx.backends.android.AndroidFragmentApplication

class MainActivity : AppCompatActivity(), AndroidFragmentApplication.Callbacks {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use ViewBinding as your main content view
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Attach the libGDX map fragment into the container (only once)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                // IMPORTANT: this must exist in your activity_main.xml
                // Make sure you have a FrameLayout with this id.
                .replace(R.id.gdx_container, MapFragment())
                .commit()
        }

        // UI actions (placeholders)
        binding.tileCamera.setOnClickListener {
            Toast.makeText(this, "Kamera", Toast.LENGTH_SHORT).show()
        }

        binding.tileMap.setOnClickListener {
            Toast.makeText(this, "Zemljevid", Toast.LENGTH_SHORT).show()
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
