package com.example.mobile.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.mobile.R
import com.example.mobile.data.model.AnalysisResponse
import com.example.mobile.data.model.PointOfInterest
import com.example.mobile.data.repository.PoiRepository
import com.example.mobile.databinding.ActivityCameraBinding
import com.example.mobile.util.LocationCalculator
import com.example.mobile.util.LocationHelper
import com.example.mobile.util.SensorDataLogger
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity(), CoroutineScope {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var locationHelper: LocationHelper
    private lateinit var poiRepository: PoiRepository

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private var cachedPois: List<PointOfInterest> = emptyList()
    private var poisLoaded = false

    private val tag = "CameraActivity"
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override val coroutineContext = Dispatchers.Main + Job()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        locationHelper = LocationHelper(this)
        poiRepository = PoiRepository()
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupUI()
        checkCameraPermission()
        checkLocationPermission()
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) startCamera()
            else toast("Camera permission required")
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) fetchPois()
            else toast("Location permission required")
        }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) fetchPois()
        else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun fetchPois() {
        binding.tvStatus.text = "Nalagam POI..."
        binding.btnCapture.isEnabled = false

        launch {
            poiRepository.fetchBinPois()
                .onSuccess {
                    cachedPois = it
                    poisLoaded = true
                    binding.tvStatus.text = getString(R.string.press_to_capture)
                    binding.btnCapture.isEnabled = true
                    addLog("POI naloženi: ${it.size}")
                }
                .onFailure {
                    poisLoaded = false
                    binding.tvStatus.text = "Napaka pri nalaganju POI"
                    addLog("Napaka POI: ${it.message}")
                }
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupUI() {
        binding.btnCapture.setOnClickListener {
            if (!poisLoaded) {
                toast("POI še niso naloženi")
                return@setOnClickListener
            }
            captureImage()
        }
    }

    private fun captureImage() {
        val capture = imageCapture ?: return

        binding.btnCapture.isEnabled = false
        binding.tvStatus.text = "Zajemanje slike..."
        addLog("Začenjam zajem slike")

        val file = File(
            cacheDir,
            "img_${System.currentTimeMillis()}.jpg"
        )

        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    addLog("Slika zajeta")

                    locationHelper.getCurrentLocationAsync { location ->
                        if (location == null) {
                            resetUI("Lokacija ni na voljo")
                            file.delete()
                            return@getCurrentLocationAsync
                        }

                        handleLocationAndUpload(file, location)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    resetUI("Napaka kamere")
                    addLog("Napaka: ${exception.message}")
                }
            }
        )
    }

    private fun handleLocationAndUpload(file: File, location: Location) {
        launch(Dispatchers.IO) {

            var closestPoi: PointOfInterest? = null
            var minDistance = Float.MAX_VALUE

            for (poi in cachedPois) {
                val distance = LocationCalculator.calculateDistance(location, poi)
                if (distance < minDistance) {
                    minDistance = distance
                    closestPoi = poi
                }
            }

            if (closestPoi == null) {
                withContext(Dispatchers.Main) {
                    resetUI("POI ni najden")
                }
                file.delete()
                return@launch
            }

            SensorDataLogger.logCameraCapture(file.absolutePath, location)

            withContext(Dispatchers.Main) {
                binding.tvStatus.text = "Pošiljanje v analizo..."
                addLog("Najbližji POI: ${closestPoi._id}")
            }

            poiRepository.uploadImageForAnalysis(closestPoi._id, file)
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        showAnalysis(it)
                        resetUI(getString(R.string.press_to_capture))
                    }
                }
                .onFailure {
                    withContext(Dispatchers.Main) {
                        resetUI("Analiza ni uspela")
                        addLog("Napaka: ${it.message}")
                    }
                }

            file.delete()
        }
    }

    private fun showAnalysis(r: AnalysisResponse) {
        val text = """
            Analiza:
            POI: ${r.poi_id}
            Status: ${r.status}
            Verjetnost: ${(r.probability * 100).toInt()}%
        """.trimIndent()

        addLog(text)
    }

    private fun resetUI(status: String) {
        binding.tvStatus.text = status
        binding.btnCapture.isEnabled = true
    }

    private fun addLog(msg: String) {
        val time = dateFormat.format(Date())
        binding.tvLog.text = "[${time}] $msg\n" + binding.tvLog.text
        binding.svLog.post { binding.svLog.fullScroll(View.FOCUS_DOWN) }
        Log.d(tag, msg)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}