package com.example.mobile.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

class LocationHelper(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    private val tag = "LocationHelper"
    
    suspend fun getCurrentLocation(): Location? {
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
                Log.w(tag, context.getString(com.example.mobile.R.string.location_not_available))
                return null
            }
            
            val cancellationTokenSource = CancellationTokenSource()
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await()
            
            location
        } catch (e: Exception) {
            Log.e(tag, "Error getting location: ${e.message}", e)
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
}
