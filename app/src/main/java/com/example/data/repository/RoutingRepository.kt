package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.api.ComputeRoutesRequest
import com.example.data.api.GoogleRoutesApiClient
import com.example.data.api.GoogleRoutesApiService
import com.example.data.api.LatLngLiteral
import com.example.data.api.RouteLocation
import com.example.data.api.RouteWaypoint
import com.example.data.model.DestinationSearchState
import com.example.data.model.DestinationSuggestion
import com.example.data.model.DistanceSource
import com.example.data.model.RouteOption
import com.example.data.model.RouteSearchState
import com.example.data.model.TransitMode
import com.example.engine.GeoMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object ActiveRouteHolder {
    @Volatile
    var activePolylinePoints: List<Pair<Double, Double>> = emptyList()

    @Volatile
    var activeRouteOption: RouteOption? = null

    @Volatile
    var currentTransportMode: String = "BUS" // "BUS", "TRAIN", "CAR"

    @Volatile
    var consecutiveDeviations: Int = 0

    @Volatile
    var previousDeviationState: com.example.data.model.RouteDeviationState = com.example.data.model.RouteDeviationState.NORMAL

    @Volatile
    var lastDeviationEvaluation: com.example.data.model.RouteDeviationEvaluation? = null

    fun clear() {
        activePolylinePoints = emptyList()
        activeRouteOption = null
        currentTransportMode = "BUS"
        consecutiveDeviations = 0
        previousDeviationState = com.example.data.model.RouteDeviationState.NORMAL
        lastDeviationEvaluation = null
    }
}

class RoutingRepository(
    private val routesApiService: GoogleRoutesApiService = GoogleRoutesApiClient.service,
    private val apiKeyProvider: () -> String = {
        try {
            BuildConfig.GOOGLE_MAPS_API_KEY
        } catch (e: Throwable) {
            ""
        }
    }
) {
    companion object {
        private const val TAG = "NocturneRouting"
        private const val CACHE_EXPIRATION_MS = 6 * 60 * 60 * 1000L // 6 hours

        // Built-in curated transit hub catalog for dependable offline discovery
        val PRESET_TRANSIT_STATIONS = listOf(
            DestinationSuggestion("hub_1", "Tirunelveli Junction", "Tamil Nadu, India", 8.7290, 77.7126, "Southern Terminus", true),
            DestinationSuggestion("hub_2", "Chennai Central (MAS)", "Tamil Nadu, India", 13.0827, 80.2707, "Major Junction", true),
            DestinationSuggestion("hub_3", "Madurai Junction", "Tamil Nadu, India", 9.9195, 78.1118, "Southern Mainline", true),
            DestinationSuggestion("hub_4", "Kanyakumari Terminal", "Tamil Nadu, India", 8.0883, 77.5385, "Coastal Terminus", true),
            DestinationSuggestion("hub_5", "Coimbatore Junction", "Tamil Nadu, India", 11.0016, 76.9628, "Western Corridor", true),
            DestinationSuggestion("hub_6", "Bangalore City (SBC)", "Karnataka, India", 12.9781, 77.5696, "Intercity Terminal", true),
            DestinationSuggestion("hub_7", "Mumbai CSMT", "Maharashtra, India", 18.9400, 72.8353, "Central Hub", true),
            DestinationSuggestion("hub_8", "New Delhi Railway Station", "Delhi, India", 28.6429, 77.2195, "Northern Gateway", true),
            DestinationSuggestion("hub_9", "Grand Central Terminal", "New York, USA", 40.7527, -73.9772, "Rail Terminal", true),
            DestinationSuggestion("hub_10", "Union Station", "Chicago, USA", 41.8787, -87.6402, "Intercity Rail & Bus", true),
            DestinationSuggestion("hub_11", "King's Cross Station", "London, UK", 51.5308, -0.1238, "Intercity Terminal", true),
            DestinationSuggestion("hub_12", "Shinjuku Station", "Tokyo, Japan", 35.6896, 139.7006, "Transit Mega-Hub", true),
            DestinationSuggestion("hub_13", "Berlin Hauptbahnhof", "Berlin, Germany", 52.5251, 13.3694, "Central Station", true),
            DestinationSuggestion("hub_14", "Gare de Lyon", "Paris, France", 48.8443, 2.3744, "TGV Terminal", true)
        )
    }

    private data class CachedRoute(
        val timestamp: Long,
        val routes: List<RouteOption>
    )

    private val routeCache = ConcurrentHashMap<String, CachedRoute>()

    /**
     * Searches destination suggestions with debouncing and input validation.
     * Guaranteed offline fallback matches against curated intercity hubs.
     */
    fun searchDestinations(query: String): Flow<DestinationSearchState> = flow {
        val sanitized = query.trim()
        if (sanitized.isEmpty()) {
            emit(DestinationSearchState.Idle)
            return@flow
        }

        emit(DestinationSearchState.Searching)

        // Local catalog fuzzy search
        val lower = sanitized.lowercase()
        val matches = PRESET_TRANSIT_STATIONS.filter {
            it.name.lowercase().contains(lower) ||
                    it.locality.lowercase().contains(lower) ||
                    it.category.lowercase().contains(lower)
        }

        if (matches.isNotEmpty()) {
            emit(DestinationSearchState.Success(matches))
        } else {
            // Check if coordinates format was typed (e.g. "8.7290, 77.7126")
            val parts = sanitized.split(",", " ")
            if (parts.size >= 2) {
                val lat = parts[0].trim().toDoubleOrNull()
                val lon = parts[1].trim().toDoubleOrNull()
                if (lat != null && lon != null && GeoMath.isValidCoordinate(lat, lon)) {
                    val custom = DestinationSuggestion(
                        id = "custom_coord",
                        name = "Custom Target Coordinates",
                        locality = String.format("%.4f, %.4f", lat, lon),
                        lat = lat,
                        lon = lon,
                        category = "GPS Coordinate Target",
                        isLocalPreset = true
                    )
                    emit(DestinationSearchState.Success(listOf(custom)))
                    return@flow
                }
            }
            emit(DestinationSearchState.NoResults(sanitized))
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Computes route options between origin and destination coordinates.
     * Uses Google Routes API when available, falling back cleanly to corridor circuity modeling.
     */
    fun getRouteOptions(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        destName: String
    ): Flow<RouteSearchState> = flow {
        if (!GeoMath.isValidCoordinate(originLat, originLon) || !GeoMath.isValidCoordinate(destLat, destLon)) {
            emit(RouteSearchState.Error("Invalid origin or destination coordinates.", canRetry = false))
            return@flow
        }

        emit(RouteSearchState.Loading)

        val cacheKey = String.format("%.4f,%.4f->%.4f,%.4f", originLat, originLon, destLat, destLon)
        val now = System.currentTimeMillis()

        // 1. Check local route cache
        routeCache[cacheKey]?.let { cached ->
            if (now - cached.timestamp < CACHE_EXPIRATION_MS) {
                emit(RouteSearchState.Success(cached.routes.map { it.copy(isCached = true) }))
                return@flow
            }
        }

        // 2. Calculate baseline distance
        val straightLineKm = GeoMath.calculateDistanceKm(originLat, originLon, destLat, destLon)
        val fallbackMilestones = GeoMath.generateCorridorMilestones(originLat, originLon, destLat, destLon)

        var mappedRoadDistanceKm: Double? = null
        var mappedDurationMinutes: Int? = null
        var mappedEncodedPolyline: String? = null
        var mappedPolylinePoints: List<Pair<Double, Double>> = emptyList()

        // 3. Attempt Google Routes API if key is available
        val apiKey = apiKeyProvider()
        val hasValidKey = apiKey.isNotBlank() && (apiKey != "MY_GOOGLE_MAPS_API_KEY" || apiKey == "test_api_key")

        if (hasValidKey) {
            try {
                val request = ComputeRoutesRequest(
                    origin = RouteWaypoint(RouteLocation(LatLngLiteral(originLat, originLon))),
                    destination = RouteWaypoint(RouteLocation(LatLngLiteral(destLat, destLon))),
                    travelMode = "DRIVE",
                    routingPreference = "TRAFFIC_AWARE"
                )
                val response = routesApiService.computeRoutes(apiKey = apiKey, request = request)
                val firstRoute = response.routes?.firstOrNull()
                if (firstRoute != null) {
                    val distMeters = firstRoute.distanceMeters ?: 0L
                    if (distMeters > 0) {
                        mappedRoadDistanceKm = distMeters / 1000.0
                        mappedDurationMinutes = parseDurationSeconds(firstRoute.duration) / 60
                        mappedEncodedPolyline = firstRoute.polyline?.encodedPolyline
                        if (!mappedEncodedPolyline.isNullOrEmpty()) {
                            mappedPolylinePoints = GeoMath.decodePolyline(mappedEncodedPolyline)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Routes API network lookup bypassed: falling back to corridor modeling")
            }
        }

        // 4. Construct typed Transit Route options
        val routes = mutableListOf<RouteOption>()

        // Option A: Highway Bus / Road Corridor
        val busDistance = mappedRoadDistanceKm ?: (straightLineKm * 1.25)
        val busDuration = mappedDurationMinutes ?: ((busDistance / 55.0) * 60).toInt().coerceAtLeast(1)
        val busPolyline = if (mappedPolylinePoints.isNotEmpty()) mappedPolylinePoints else fallbackMilestones
        routes.add(
            RouteOption(
                id = "route_bus",
                mode = TransitMode.HIGHWAY_BUS,
                summary = if (mappedRoadDistanceKm != null) "Express Highway Network" else "Intercity Bus Corridor (1.25x circuity)",
                distanceKm = busDistance,
                durationMinutes = busDuration,
                distanceSource = if (mappedRoadDistanceKm != null) DistanceSource.MAPPED_ROUTE else DistanceSource.ESTIMATED_ROUTE,
                isApproximate = mappedRoadDistanceKm == null,
                isCached = false,
                encodedPolyline = mappedEncodedPolyline,
                polylinePoints = busPolyline,
                corridorMilestones = generateMilestoneLabels(originLat, originLon, destLat, destLon, destName),
                warningNotice = "Reference road route. Long-distance buses follow arterial highway geometry."
            )
        )

        // Option B: Express Rail Corridor
        // Rail travels on physical track alignment (~1.15x circuity), independent of car congestion
        val railDistance = straightLineKm * 1.15
        val railDuration = ((railDistance / 65.0) * 60).toInt().coerceAtLeast(1)
        routes.add(
            RouteOption(
                id = "route_rail",
                mode = TransitMode.RAIL_CORRIDOR,
                summary = "Dedicated Mainline Rail Corridor (1.15x)",
                distanceKm = railDistance,
                durationMinutes = railDuration,
                distanceSource = DistanceSource.ESTIMATED_ROUTE,
                isApproximate = true,
                isCached = false,
                encodedPolyline = null,
                polylinePoints = fallbackMilestones,
                corridorMilestones = generateMilestoneLabels(originLat, originLon, destLat, destLon, destName),
                warningNotice = "Train tracking operates on railway track alignment; road traffic does not apply."
            )
        )

        // Option C: Direct Baseline
        routes.add(
            RouteOption(
                id = "route_direct",
                mode = TransitMode.DIRECT_TERRAIN,
                summary = "Direct Great-Circle Baseline",
                distanceKm = straightLineKm,
                durationMinutes = ((straightLineKm / 60.0) * 60).toInt().coerceAtLeast(1),
                distanceSource = DistanceSource.STRAIGHT_LINE_FALLBACK,
                isApproximate = true,
                isCached = false,
                encodedPolyline = null,
                polylinePoints = listOf(Pair(originLat, originLon), Pair(destLat, destLon)),
                corridorMilestones = listOf("Departure Point", destName),
                warningNotice = "Straight-line flight baseline; does not account for terrain curvature."
            )
        )

        // Cache routes
        routeCache[cacheKey] = CachedRoute(timestamp = now, routes = routes)
        emit(RouteSearchState.Success(routes = routes, selectedIndex = 0))
    }.flowOn(Dispatchers.IO)

    private fun parseDurationSeconds(durationStr: String?): Int {
        if (durationStr.isNullOrEmpty()) return 0
        return durationStr.removeSuffix("s").toIntOrNull() ?: 0
    }

    private fun generateMilestoneLabels(
        originLat: Double, originLon: Double,
        destLat: Double, destLon: Double,
        destName: String
    ): List<String> {
        return listOf(
            "Departure Point",
            "Mid-Corridor Sector",
            "Terminal Approach",
            destName
        )
    }
}
