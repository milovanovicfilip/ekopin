package com.example.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.example.mobile.databinding.ActivityMainBinding
import com.example.mobile.ui.camera.CameraActivity
import com.example.mobile.ui.map.MapActivity
import com.example.mobile.ui.sensors.SensorsActivity
import com.example.mobile.util.LocationHelper
import com.example.mobile.util.SimPrefs
import com.example.mobile.util.mqtt.MqttManager
import com.example.mobile.util.mqtt.MqttProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope, AndroidFragmentApplication.Callbacks {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationHelper: LocationHelper
    private var mqtt: MqttManager? = null

    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val NOTIF_PERMISSION_REQUEST_CODE = 2002

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + Job()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestLocationPermission()
        requestNotificationPermissionIfNeeded()

        mqtt = MqttProvider.mqtt

        binding.switchSimulation.setOnCheckedChangeListener(null)
        binding.switchSimulation.isChecked = SimPrefs.isEnabled(this)
        binding.switchSimulation.setOnCheckedChangeListener { _, isChecked ->
            SimPrefs.setEnabled(this, isChecked)
            publishStatusPing()
        }

        locationHelper = LocationHelper(this)

        binding.tileCamera.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        binding.tileMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        binding.tileSensors.setOnClickListener {
            startActivity(Intent(this, SensorsActivity::class.java))
        }

        updateTimeAndLocation()
        startTimeUpdate()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) return

        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIF_PERMISSION_REQUEST_CODE
        )
    }

    private fun currentModePrefix(): String =
        if (SimPrefs.isEnabled(this)) "sim" else "real"

    private fun topicAdd(): String =
        "ekopin/${currentModePrefix()}/pollution_tags/add"

    private fun publishStatusPing() {
        val m = mqtt ?: return
        if (!m.isConnected()) return

        val payload = JSONObject().apply {
            put("deviceId", Build.MODEL ?: "android")
            put("type", "NOTE")
            put("note", "ping")
            put("ts", System.currentTimeMillis())
            put("lat", 0)
            put("lng", 0)
        }.toString()

        m.publish(topicAdd(), payload, qos = 1, retained = false) {}
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
        binding.tvTime.text = dateFormat.format(Date())

        launch(Dispatchers.IO) {
            val location = locationHelper.getCurrentLocation()
            launch(Dispatchers.Main) {
                binding.tvLocation.text = locationHelper.formatLocationShort(location)
            }
        }
    }

    private fun requestLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> Unit

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )

            else -> ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun exit() {
        finish()
    }
}
