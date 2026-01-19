package com.example.mobile.network

import com.example.mobile.data.model.AnalysisResponse
import com.example.mobile.data.model.PointOfInterest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    
    @GET("/api/poi")
    suspend fun getPois(): List<PointOfInterest>
    
    @Multipart
    @POST("/analiziraj_in_shrani")
    suspend fun uploadImageAndAnalyze(
        @Part("poi_id") poiId: RequestBody,
        @Part image: MultipartBody.Part
    ): AnalysisResponse
}
