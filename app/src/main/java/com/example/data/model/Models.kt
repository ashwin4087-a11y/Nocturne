package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class JourneyStatus {
    IDLE,
    ACTIVE,
    COMPLETED,
    CANCELLED
}

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
    UNRELIABLE
}

enum class AnomalyType {
    NONE,
    UNUSUALLY_LONG_STOP,
    PERSISTENT_ROUTE_DEVIATION,
    STALE_GPS_DATA,
    SUDDEN_ETA_CHANGE
}

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

@Entity(tableName = "journeys")
data class JourneyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val destinationName: String,
    val destinationLat: Double,
    val destinationLon: Double,
    val wakeUpBufferMinutes: Int = 30,
    val status: JourneyStatus = JourneyStatus.IDLE,
    val startTimestamp: Long = System.currentTimeMillis(),
    val completedTimestamp: Long? = null,
    val lastKnownLat: Double? = null,
    val lastKnownLon: Double? = null,
    val lastKnownSpeedKmh: Double = 0.0,
    val lastKnownAccuracyMeters: Float = 0f,
    val lastFixTimestamp: Long = 0L,
    val remainingDistanceKm: Double = 0.0, // Route-aware distance
    val straightLineDistanceKm: Double = 0.0, // Great-circle distance
    val routeCircuityFactor: Double = 1.25, // Intercity road network circuity factor
    val isRouteAware: Boolean = true,
    val etaMinutesPoint: Int = 0,
    val etaMinutesLower: Int = 0,
    val etaMinutesUpper: Int = 0,
    val confidence: ConfidenceLevel = ConfidenceLevel.MEDIUM,
    val anomalyType: AnomalyType = AnomalyType.NONE,
    val anomalyDetail: String? = null,
    val alarmTriggered: Boolean = false,
    val alarmTriggeredTimestamp: Long? = null,
    val targetScheduledAlarmMs: Long = 0L, // Durable scheduled alarm trigger timestamp
    val isVehicleStationary: Boolean = false,
    val stationaryDurationMs: Long = 0L,
    val gpsStale: Boolean = false,
    val routeDistanceSource: String = "ESTIMATED_ROUTE",
    val referenceRouteSummary: String? = null,
    val deviationState: String = "NORMAL",
    val crossTrackDistanceKm: Double = 0.0,
    val transportMode: String = "BUS", // "BUS", "TRAIN", "CAR"
    val ticketPnr: String? = null,
    val scheduledArrivalTimestamp: Long? = null,
    val originName: String? = null
)

enum class RouteDeviationState(val label: String) {
    NORMAL("ON SCHEDULED ROUTE"),
    POSSIBLE_DEVIATION("POSSIBLE ROUTE DEVIATION"),
    CONFIRMED_DEVIATION("CONFIRMED ROUTE DIVERGENCE"),
    STALE_GPS("STALE GPS SIGNAL"),
    UNAVAILABLE_GEOMETRY("UNMAPPED GEOMETRY")
}

data class RouteDeviationEvaluation(
    val state: RouteDeviationState,
    val crossTrackDistanceKm: Double,
    val effectiveDeviationKm: Double,
    val thresholdKm: Double,
    val consecutiveFixCount: Int,
    val explanation: String,
    val isRecovered: Boolean = false,
    val recoveryStatus: String? = null
)

data class ExtractedTicket(
    val origin: String? = null,
    val destination: String? = null,
    val boardingPoint: String? = null,
    val dropOffPoint: String? = null,
    val transportMode: String = "BUS", // "BUS", "TRAIN", "CAR"
    val operator: String? = null,
    val serviceNumber: String? = null,
    val pnr: String? = null,
    val departureTimeStr: String? = null,
    val scheduledArrivalTimeStr: String? = null,
    val scheduledArrivalEpochMs: Long? = null,
    val intermediateStops: List<String> = emptyList(),
    val confidenceScore: Float = 0.0f, // 0.0 to 1.0
    val rawTextSnippet: String = "",
    val ambiguousFields: List<String> = emptyList()
)

@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val journeyId: Long,
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val speedKmh: Double,
    val accuracyMeters: Float,
    val distanceToDestKm: Double
)

data class EtaResult(
    val pointMinutes: Int,
    val lowerMinutes: Int,
    val upperMinutes: Int,
    val confidence: ConfidenceLevel,
    val distanceRemainingKm: Double, // Route-aware distance
    val straightLineKm: Double, // Great-circle distance
    val effectiveSpeedKmh: Double, // Moving cruise speed
    val isStationary: Boolean,
    val stationaryDurationMs: Long,
    val isRouteAware: Boolean,
    val explanation: String
)

data class AnomalyDetectionResult(
    val type: AnomalyType,
    val isAnomaly: Boolean,
    val explanation: String,
    val confidenceImpact: ConfidenceLevel
)

data class TransitPreset(
    val name: String,
    val city: String,
    val lat: Double,
    val lon: Double,
    val category: String, // "Central Rail", "Intercity Bus Terminal", "Airport Express"
    val defaultCircuityFactor: Double = 1.25
)

data class ReadinessItem(
    val key: String,
    val title: String,
    val explanation: String,
    val isReady: Boolean,
    val isOptional: Boolean = false
)

enum class SimulationScenario {
    REAL_GPS,
    STEADY_CRUISE,
    EXTENDED_REST_STOP,
    EARLY_FAST_RUN,
    OFF_ROUTE_DIVERSION,
    GPS_TUNNEL_BLACKOUT
}
