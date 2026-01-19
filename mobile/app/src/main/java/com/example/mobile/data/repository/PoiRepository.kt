package com.example.mobile.data.repository

import android.location.Location
import android.util.Log
import com.example.mobile.data.model.AnalysisResponse
import com.example.mobile.data.model.PointOfInterest
import com.example.mobile.network.RetrofitClient
import com.example.mobile.util.LocationCalculator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class PoiRepository {
    private val tag = "PoiRepository"
    private var cachedPois: List<PointOfInterest> = emptyList()
    private var lastFetchTime: Long = 0
    private val cacheDurationMs = 5 * 60 * 1000  // 5 minutes cache
    
    /**
     * Fetch POIs from the server, filtering only those with type="bin"
     */
    suspend fun fetchBinPois(): Result<List<PointOfInterest>> {
        return try {
            // Check if cache is still valid
            if (cachedPois.isNotEmpty() && (System.currentTimeMillis() - lastFetchTime) < cacheDurationMs) {
                Log.d(tag, "Using cached POIs (${cachedPois.size} items)")
                return Result.success(cachedPois)
            }
            
            val apiService = RetrofitClient.getPoiApiService()
            val allPois = apiService.getPois()
            
            // Filter only POIs with type="bin"
            val binPois = allPois.filter { it.type == "bin" }
            
            cachedPois = binPois
            lastFetchTime = System.currentTimeMillis()
            
            Log.d(tag, "Successfully fetched ${binPois.size} bin POIs")
            Result.success(binPois)
        } catch (e: Exception) {
            Log.e(tag, "Error fetching POIs: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Find the closest POI to the device location
     */
    fun findClosestPoi(deviceLocation: Location): PointOfInterest? {
        return LocationCalculator.findClosestPoi(deviceLocation, cachedPois)
    }
    
    /**
     * Upload image with POI ID for analysis
     */
    suspend fun uploadImageForAnalysis(poiId: String, imageFile: File): Result<AnalysisResponse> {
        return try {
            val requestBody = imageFile.asRequestBody("image/jpeg".toMediaType())
            val imagePart = MultipartBody.Part.createFormData("slika", imageFile.name, requestBody)
            val poiIdBody = okhttp3.RequestBody.create("text/plain".toMediaType(), poiId)
            
            val apiService = RetrofitClient.getAnalysisApiService()
            val response = apiService.uploadImageAndAnalyze(poiIdBody, imagePart)
            
            Log.d(tag, "Analysis response: ${response.message}, probability: ${response.probability}")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(tag, "Error uploading image: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get cached POIs without fetching from server
     */
    fun getCachedPois(): List<PointOfInterest> = cachedPois
    
    /**
     * Clear cached POIs
     */
    fun clearCache() {
        cachedPois = emptyList()
        lastFetchTime = 0
    }
}
