package com.example.engine

import com.example.data.model.AnomalyDetectionResult
import com.example.data.model.AnomalyType
import com.example.data.model.ConfidenceLevel
import com.example.data.model.ObservationEntity
import kotlin.math.abs

object AnomalyDetector {
    private const val UNUSUAL_STOP_DURATION_MS = 12 * 60 * 1000L // 12 minutes
    private const val STOPPED_SPEED_THRESHOLD_KMH = 3.5
    private const val STALE_GPS_MS = 6 * 60 * 1000L // 6 minutes
    private const val SUDDEN_ETA_JUMP_MINUTES = 25

    /**
     * Inspects current journey telemetry and recent observations for potential anomalies.
     */
    fun detectAnomaly(
        currentRemainingKm: Double,
        currentEtaMinutes: Int,
        previousEtaMinutes: Int,
        lastFixTimestamp: Long,
        recentObservations: List<ObservationEntity>,
        currentTimeMs: Long = System.currentTimeMillis(),
        currentLat: Double? = null,
        currentLon: Double? = null,
        currentAccuracyMeters: Float = 20f,
        referencePolyline: List<Pair<Double, Double>> = emptyList()
    ): AnomalyDetectionResult {
        // 1. Check for Stale GPS
        val fixAgeMs = currentTimeMs - lastFixTimestamp
        if (lastFixTimestamp > 0 && fixAgeMs > STALE_GPS_MS) {
            return AnomalyDetectionResult(
                type = AnomalyType.STALE_GPS_DATA,
                isAnomaly = true,
                explanation = "No fresh GPS observation in ${(fixAgeMs / 60000).toInt()} minutes. Tracking degraded.",
                confidenceImpact = ConfidenceLevel.UNRELIABLE
            )
        }

        // 2. Check for Sudden ETA Jump (e.g. unexpected reroute or severe delay)
        if (previousEtaMinutes > 0 && abs(currentEtaMinutes - previousEtaMinutes) >= SUDDEN_ETA_JUMP_MINUTES) {
            return AnomalyDetectionResult(
                type = AnomalyType.SUDDEN_ETA_CHANGE,
                isAnomaly = true,
                explanation = "ETA changed abruptly by ${abs(currentEtaMinutes - previousEtaMinutes)} minutes.",
                confidenceImpact = ConfidenceLevel.LOW
            )
        }

        // 3. Check for Persistent Route Deviation
        // Priority A: Geometry-based cross-track comparison against reference corridor polyline
        if (currentLat != null && currentLon != null && referencePolyline.size >= 2 && currentRemainingKm > 3.0) {
            val eval = RouteDeviationEvaluator.evaluate(
                currentLat = currentLat,
                currentLon = currentLon,
                currentAccuracyMeters = currentAccuracyMeters,
                lastFixTimestamp = lastFixTimestamp,
                currentTimeMs = currentTimeMs,
                referencePolyline = referencePolyline,
                recentObservations = recentObservations,
                previousState = com.example.data.repository.ActiveRouteHolder.previousDeviationState,
                consecutiveOffTrackCount = com.example.data.repository.ActiveRouteHolder.consecutiveDeviations
            )
            com.example.data.repository.ActiveRouteHolder.previousDeviationState = eval.state
            com.example.data.repository.ActiveRouteHolder.consecutiveDeviations = eval.consecutiveFixCount
            com.example.data.repository.ActiveRouteHolder.lastDeviationEvaluation = eval

            if (eval.state == com.example.data.model.RouteDeviationState.CONFIRMED_DEVIATION) {
                return AnomalyDetectionResult(
                    type = AnomalyType.PERSISTENT_ROUTE_DEVIATION,
                    isAnomaly = true,
                    explanation = eval.explanation,
                    confidenceImpact = ConfidenceLevel.LOW
                )
            }
        } else if (recentObservations.size >= 4 && currentRemainingKm > 5.0) {
            // Priority B: Trajectory distance heuristic fallback
            val dist0 = recentObservations[0].distanceToDestKm
            val dist1 = recentObservations[1].distanceToDestKm
            val dist2 = recentObservations[2].distanceToDestKm
            val dist3 = recentObservations[3].distanceToDestKm

            if (dist0 > dist1 && dist1 > dist2 && dist2 > dist3 && (dist0 - dist3) > 3.0) {
                return AnomalyDetectionResult(
                    type = AnomalyType.PERSISTENT_ROUTE_DEVIATION,
                    isAnomaly = true,
                    explanation = "Observed trajectory is moving away from destination (+${(dist0 - dist3).toInt()} km). Possible detour.",
                    confidenceImpact = ConfidenceLevel.LOW
                )
            }
        }

        // 4. Check for Unusually Long Stop
        // If consecutive observations over >12 min indicate near-zero speed away from destination
        if (recentObservations.size >= 5 && currentRemainingKm > 4.0) {
            val recentZeroSpeed = recentObservations.filter { it.speedKmh < STOPPED_SPEED_THRESHOLD_KMH }
            if (recentZeroSpeed.size >= 4) {
                val oldestZero = recentZeroSpeed.last().timestamp
                val durationStoppedMs = currentTimeMs - oldestZero
                if (durationStoppedMs >= UNUSUAL_STOP_DURATION_MS) {
                    val stopMinutes = (durationStoppedMs / 60000).toInt()
                    return AnomalyDetectionResult(
                        type = AnomalyType.UNUSUALLY_LONG_STOP,
                        isAnomaly = true,
                        explanation = "Vehicle stationary for $stopMinutes minutes away from destination. Possible unexpected halt.",
                        confidenceImpact = ConfidenceLevel.MEDIUM
                    )
                }
            }
        }

        return AnomalyDetectionResult(
            type = AnomalyType.NONE,
            isAnomaly = false,
            explanation = "Journey trajectory matches expected parameters.",
            confidenceImpact = ConfidenceLevel.HIGH
        )
    }
}
