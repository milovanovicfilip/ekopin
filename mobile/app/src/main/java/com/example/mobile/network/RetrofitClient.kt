package com.example.mobile.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var poiApiService: ApiService? = null
    private var analysisApiService: ApiService? = null
    
    // Use 10.0.2.2 for Android emulator (refers to host machine)
    // Change to your actual machine IP if using a physical device
    private const val POI_BASE_URL = "http://74.242.216.220:3000/"
    private const val ANALYSIS_BASE_URL = "http://74.242.216.220:8000/"
    
    fun getPoiApiService(): ApiService {
        if (poiApiService == null) {
            poiApiService = buildRetrofit(POI_BASE_URL).create(ApiService::class.java)
        }
        return poiApiService!!
    }
    
    fun getAnalysisApiService(): ApiService {
        if (analysisApiService == null) {
            analysisApiService = buildRetrofit(ANALYSIS_BASE_URL).create(ApiService::class.java)
        }
        return analysisApiService!!
    }
    
    private fun buildRetrofit(baseUrl: String): Retrofit {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }
}
