package com.example.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.mobile.databinding.ActivityMainBinding
import com.example.mobile.ui.camera.CameraActivity
import com.example.mobile.ui.sensors.SensorsActivity
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.example.mobile.ui.map.MapActivity
import com.example.mobile.ui.map.MapFragment
import com.example.mobile.util.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope, AndroidFragmentApplication.Callbacks {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationHelper: LocationHelper

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + Job()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestLocationPermission()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationHelper = LocationHelper(this)

        binding.tileCamera.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        binding.tileMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        binding.tileSettings.setOnClickListener {
            Toast.makeText(this, getString(R.string.settings), Toast.LENGTH_SHORT).show()
        }

        binding.tileSensors.setOnClickListener {
            startActivity(Intent(this, SensorsActivity::class.java))
        }

        updateTimeAndLocation()
        startTimeUpdate()
    }

    private fun startTimeUpdate() {
        updateRunnable = object : Runnable {
            override fun run() {
                updateTimeAndLocation()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun updateTimeAndLocation() {
        val currentTime = dateFormat.format(Date())
        binding.tvTime.text = getString(R.string.time_format, currentTime)

        launch(Dispatchers.IO) {
            val location = locationHelper.getCurrentLocation()
            launch(Dispatchers.Main) {
                val locationStr = locationHelper.formatLocationShort(location)
                binding.tvLocation.text = getString(R.string.location_format_display, locationStr)
            }
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
                    updateTimeAndLocation()
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
        binding.tvLocation.text = getString(R.string.location_unknown)
    }

    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun exit() {
        finish()
    }
}
