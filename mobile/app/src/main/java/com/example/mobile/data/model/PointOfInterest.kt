package com.example.mobile.data.model

data class PointOfInterest(
    val _id: String,
    val type: String,
    val location: Location,
    val lastChecked: String,
    val __v: Int,
    val createdAt: String,
    val updatedAt: String
) {
    data class Location(
        val type: String,
        val coordinates: List<Double>  // [longitude, latitude] in GeoJSON format
    )
}

data class AnalysisResponse(
    val message: String,
    val poi_id: String,
    val probability: Double,
    val status: String
)
