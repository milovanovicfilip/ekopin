package com.example.mobile.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

class LocationHelper(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    private val tag = "LocationHelper"
    
    suspend fun getCurrentLocation(): Location? {
        Log.d(tag, "getCurrentLocation: Starting location request")
        return try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(tag, "getCurrentLocation: Location permissions not granted")
                Log.w(tag, context.getString(com.example.mobile.R.string.location_not_available))
                return null
            }
            
            Log.d(tag, "getCurrentLocation: Permissions OK, creating cancellation token")
            val cancellationTokenSource = CancellationTokenSource()
            
            // Add timeout of 10 seconds to prevent hanging
            Log.d(tag, "getCurrentLocation: Requesting location with 10s timeout...")
            val location = withTimeout(10000L) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).await()
            }
            
            if (location != null) {
                Log.d(tag, "getCurrentLocation: Success - lat=${location.latitude}, lon=${location.longitude}, accuracy=${location.accuracy}m")
            } else {
                Log.w(tag, "getCurrentLocation: Location is null (no fix available)")
            }
            
            location
        } catch (e: TimeoutCancellationException) {
            Log.e(tag, "getCurrentLocation: Location request timed out after 10 seconds", e)
            null
        } catch (e: Exception) {
            Log.e(tag, "getCurrentLocation: Error getting location: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }
    
    fun formatLocation(location: Location?): String {
        return if (location != null) {
            String.format(
                java.util.Locale.getDefault(),
                "Lat: %.6f, Lng: %.6f, Accuracy: %.1fm",
                location.latitude,
                location.longitude,
                location.accuracy
            )
        } else {
            context.getString(com.example.mobile.R.string.location_not_available)
        }
    }
    
    fun formatLocationShort(location: Location?): String {
        return if (location != null) {
            String.format(
                java.util.Locale.getDefault(),
                "%.6f, %.6f",
                location.latitude,
                location.longitude
            )
        } else {
            context.getString(com.example.mobile.R.string.location_unknown_short)
        }
    }

    fun getCurrentLocationAsync(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback(null)
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                callback(location)
            }
            .addOnFailureListener {
                callback(null)
            }
    }

}
