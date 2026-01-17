package com.example.mobile.ui.map

import android.app.Activity
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.mobile.databinding.ActivityPollutionReportBinding
import com.example.mobile.util.SimPrefs
import com.example.mobile.util.mqtt.MqttProvider
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONObject

class PollutionReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPollutionReportBinding
    private var pickedImage: Uri? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                pickedImage = uri
                binding.ivPreview.setImageURI(uri)
                binding.btnPickImage.text = "Zamenjaj sliko"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPollutionReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val startLat = readDoubleExtra(intent, "lat")
        val startLng = readDoubleExtra(intent, "lon")

        val ts = intent.getLongExtra("ts", System.currentTimeMillis())

        binding.etLat.setText(String.format(Locale.US, "%.6f", startLat))
        binding.etLon.setText(String.format(Locale.US, "%.6f", startLng))

        val severities = listOf("Nizka", "Srednja", "Visoka")
        binding.spSeverity.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, severities).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

        val tsStr =
            SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(ts))
        binding.tvMeta.text = "Čas: $tsStr"

        binding.btnPickImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.btnSave.setOnClickListener {
            submitReport(ts)
        }

        binding.btnCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun submitReport(ts: Long) {
        val title = binding.etReportTitle.text?.toString()?.trim().orEmpty()
        val desc = binding.etDesc.text?.toString()?.trim().orEmpty()

        val lat = binding.etLat.text?.toString()?.trim()?.toDoubleOrNull()
        val lng = binding.etLon.text?.toString()?.trim()?.toDoubleOrNull()

        if (title.isBlank()) {
            binding.etReportTitle.error = "Naslov je obvezen"
            return
        }

        if (lat == null || lng == null || !isValidLatLng(lat, lng)) {
            binding.etLat.error = "Neveljavna lokacija"
            binding.etLon.error = "Neveljavna lokacija"
            Log.e("POLLUTION_REPORT", "Invalid lat/lng: $lat , $lng")
            return
        }

        val mqtt = MqttProvider.mqtt
        if (mqtt == null) {
            Toast.makeText(this, "MQTT še ni inicializiran (App?)", Toast.LENGTH_LONG).show()
            return
        }
        if (!mqtt.isConnected()) {
            Toast.makeText(this, "MQTT ni povezan", Toast.LENGTH_LONG).show()
            return
        }


        val mode = if (SimPrefs.isEnabled(this)) "sim" else "real"
        val topic = "ekopin/$mode/pollution_tags/add"
        val severityRaw = binding.spSeverity.selectedItem?.toString()?.trim()
        val severity = if (severityRaw.isNullOrBlank() || severityRaw.equals("null", true)) "Nizka" else severityRaw


        val (imageBase64, imageMime) = encodeImageBase64(contentResolver, pickedImage)

        val payload = JSONObject().apply {
            put("deviceId", Build.MODEL ?: "android")
            put("label", title)
            put("description", desc)
            put("severity", severity)
            put("lat", lat)
            put("lng", lng)
            put("ts", ts)
            put("mode", mode)

            if (imageBase64 != null && imageMime != null) {
                put("imageBase64", imageBase64)
                put("imageMime", imageMime)
            }
        }.toString()

        Log.d("POLLUTION_REPORT", "Publishing to $topic")
        Log.d("POLLUTION_REPORT", payload)

        mqtt.publish(topic, payload, qos = 1, retained = false) { err ->
            Log.e("POLLUTION_REPORT", "Publish failed", err)
        }

        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun readDoubleExtra(intent: android.content.Intent, key: String): Double {
        val any = intent.extras?.get(key) ?: return 0.0
        return when (any) {
            is Double -> any
            is Float -> any.toDouble()
            is Int -> any.toDouble()
            is Long -> any.toDouble()
            is String -> any.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun isValidLatLng(lat: Double, lng: Double): Boolean {
        if (!lat.isFinite() || !lng.isFinite()) return false
        if (lat !in -90.0..90.0) return false
        if (lng !in -180.0..180.0) return false
        if (lat == 0.0 && lng == 0.0) return false
        return true
    }

    private fun encodeImageBase64(
        resolver: ContentResolver,
        uri: Uri?
    ): Pair<String?, String?> {
        if (uri == null) return null to null

        return try {
            val bmp = resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return null to null

            val scaled = scaleDown(bmp, 1280)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            val bytes = out.toByteArray()

            Base64.encodeToString(bytes, Base64.NO_WRAP) to "image/jpeg"
        } catch (e: Exception) {
            Log.e("POLLUTION_REPORT", "Failed to encode image", e)
            null to null
        }
    }

    private fun scaleDown(bm: Bitmap, maxSide: Int): Bitmap {
        val w = bm.width
        val h = bm.height
        if (w <= maxSide && h <= maxSide) return bm

        val scale =
            if (w >= h) maxSide.toFloat() / w.toFloat()
            else maxSide.toFloat() / h.toFloat()

        val nw = (w * scale).roundToInt().coerceAtLeast(1)
        val nh = (h * scale).roundToInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bm, nw, nh, true)
    }
}
