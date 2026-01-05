package com.example.mobile.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.mobile.R
import com.example.mobile.databinding.ActivityCameraBinding
import com.example.mobile.util.LocationHelper
import com.example.mobile.util.SensorDataLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity(), CoroutineScope {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var locationHelper: LocationHelper
    
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    override val coroutineContext = Dispatchers.Main + Job()
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_needed), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        locationHelper = LocationHelper(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        setupUI()
        checkCameraPermission()
        
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
    
    private fun setupUI() {
        binding.btnCapture.setOnClickListener {
            captureImage()
        }
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
                
                imageCapture = ImageCapture.Builder().build()
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                try {
                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.camera_error_start, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.camera_error_init, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun captureImage() {
        val imageCapture = imageCapture ?: return
        
        binding.btnCapture.isEnabled = false
        binding.tvStatus.text = getString(R.string.capturing_image)
        
        launch(Dispatchers.IO) {
            val location = locationHelper.getCurrentLocation()
            
            val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
                .format(System.currentTimeMillis())
            
            SensorDataLogger.logCameraCapture(
                imagePath = "camera_$name.jpg",
                location = location
            )
            
            launch(Dispatchers.Main) {
                binding.tvStatus.text = getString(R.string.image_captured)
                val timestamp = dateFormat.format(Date())
                binding.tvLastCapture.text = getString(R.string.last_capture, timestamp)
                
                val locationStr = locationHelper.formatLocationShort(location)
                addLogEntry(getString(R.string.image_captured_log, locationStr))
                
                binding.btnCapture.isEnabled = true
                
                binding.root.postDelayed({
                    binding.tvStatus.text = getString(R.string.press_to_capture)
                }, 2000)
            }
        }
    }
    
    private fun addLogEntry(entry: String) {
        val timestamp = dateFormat.format(Date())
        val currentLog = binding.tvLog.text.toString()
        val newLog = "[$timestamp] $entry\n" + currentLog.replace(getString(R.string.camera_log_title), "")
        binding.tvLog.text = getString(R.string.camera_log_title) + newLog
        
        binding.svLog.post {
            binding.svLog.fullScroll(View.FOCUS_DOWN)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}
