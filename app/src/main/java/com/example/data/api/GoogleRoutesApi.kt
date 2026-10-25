package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ComputeRoutesRequest(
    val origin: RouteWaypoint,
    val destination: RouteWaypoint,
    val travelMode: String = "DRIVE",
    val routingPreference: String? = "TRAFFIC_AWARE",
    val computeAlternativeRoutes: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RouteWaypoint(
    val location: RouteLocation
)

@JsonClass(generateAdapter = true)
data class RouteLocation(
    val latLng: LatLngLiteral
)

@JsonClass(generateAdapter = true)
data class LatLngLiteral(
    val latitude: Double,
    val longitude: Double
)

@JsonClass(generateAdapter = true)
data class ComputeRoutesResponse(
    val routes: List<ComputeRouteItem>? = null
)

@JsonClass(generateAdapter = true)
data class ComputeRouteItem(
    val distanceMeters: Long? = null,
    val duration: String? = null,
    val polyline: RoutePolyline? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class RoutePolyline(
    val encodedPolyline: String? = null
)

interface GoogleRoutesApiService {
    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String = "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.description",
        @Body request: ComputeRoutesRequest
    ): ComputeRoutesResponse
}

object GoogleRoutesApiClient {
    private const val BASE_URL = "https://routes.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val service: GoogleRoutesApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GoogleRoutesApiService::class.java)
    }
}
