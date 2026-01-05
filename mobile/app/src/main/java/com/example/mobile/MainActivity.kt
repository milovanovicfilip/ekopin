package com.example.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.mobile.databinding.ActivityMainBinding
import com.example.mobile.ui.camera.CameraActivity
import com.example.mobile.ui.sensors.SensorsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestLocationPermission()

        // Ob ustvarjanju je tudi treba naredit da izpiše čas in lokacijo tam čisto spodaj
        // Pa še nekaj da posodablja čas, tega se mi ni dalo delat :/

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tileCamera.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        binding.tileMap.setOnClickListener {
            Toast.makeText(this, getString(R.string.map), Toast.LENGTH_SHORT).show()
        }

        binding.tileSettings.setOnClickListener {
            Toast.makeText(this, getString(R.string.settings), Toast.LENGTH_SHORT).show()
        }

        binding.tileSensors.setOnClickListener {
            val intent = Intent(this, SensorsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED -> {
                }
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) -> {
                    showPermissionRationale()
                }
                else -> {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    handlePermissionDenied()
                }
            }
        }
    }

    private fun showPermissionRationale() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun handlePermissionDenied() {
    }
}
