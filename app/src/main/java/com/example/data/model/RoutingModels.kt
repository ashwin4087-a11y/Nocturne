package com.example.data.model

enum class TransitMode(val label: String, val circuityFactor: Double) {
    HIGHWAY_BUS("Intercity Bus / Highway", 1.25),
    RAIL_CORRIDOR("Express Rail / Train", 1.15),
    DIRECT_TERRAIN("Direct Baseline / Estimated", 1.0)
}

enum class DistanceSource(val label: String) {
    MAPPED_ROUTE("MAPPED ROUTE"),
    ESTIMATED_ROUTE("CORRIDOR ESTIMATE"),
    STRAIGHT_LINE_FALLBACK("DIRECT FALLBACK")
}

data class DestinationSuggestion(
    val id: String,
    val name: String,
    val locality: String,
    val lat: Double,
    val lon: Double,
    val category: String,
    val isLocalPreset: Boolean = false
)

data class RouteOption(
    val id: String,
    val mode: TransitMode,
    val summary: String,
    val distanceKm: Double,
    val durationMinutes: Int,
    val distanceSource: DistanceSource,
    val isApproximate: Boolean,
    val isCached: Boolean,
    val encodedPolyline: String? = null,
    val polylinePoints: List<Pair<Double, Double>> = emptyList(),
    val corridorMilestones: List<String> = emptyList(),
    val warningNotice: String? = null
)

sealed interface RouteSearchState {
    data object Idle : RouteSearchState
    data object Loading : RouteSearchState
    data class Success(
        val routes: List<RouteOption>,
        val selectedIndex: Int = 0
    ) : RouteSearchState
    data class Empty(val reason: String) : RouteSearchState
    data class Error(val message: String, val canRetry: Boolean = true) : RouteSearchState
}

sealed interface DestinationSearchState {
    data object Idle : DestinationSearchState
    data object Searching : DestinationSearchState
    data class Success(val suggestions: List<DestinationSuggestion>) : DestinationSearchState
    data class NoResults(val query: String) : DestinationSearchState
    data class Error(val message: String) : DestinationSearchState
}
