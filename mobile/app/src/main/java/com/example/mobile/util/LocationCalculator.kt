package com.example.mobile.util

import android.location.Location
import com.example.mobile.data.model.PointOfInterest
import kotlin.math.*

object LocationCalculator {
    
    /**
     * Calculate distance between device location and a POI using Haversine formula
     * Returns distance in meters
     */
    fun calculateDistance(deviceLocation: Location, poi: PointOfInterest): Float {
        val lat1 = Math.toRadians(deviceLocation.latitude)
        val lon1 = Math.toRadians(deviceLocation.longitude)
        val lat2 = Math.toRadians(poi.location.coordinates[1])  // latitude
        val lon2 = Math.toRadians(poi.location.coordinates[0])  // longitude
        
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * asin(sqrt(a))
        
        return (EARTH_RADIUS_METERS * c).toFloat()
    }
    
    /**
     * Find the closest POI from a list of POIs to the device location
     */
    fun findClosestPoi(deviceLocation: Location, pois: List<PointOfInterest>): PointOfInterest? {
        if (pois.isEmpty()) return null
        
        return pois.minByOrNull { calculateDistance(deviceLocation, it) }
    }
    
    private const val EARTH_RADIUS_METERS = 6371000.0  // Earth's radius in meters
}
