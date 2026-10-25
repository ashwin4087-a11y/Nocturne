package com.example.engine

import com.example.data.model.ConfidenceLevel
import com.example.data.model.EtaResult
import com.example.data.model.ObservationEntity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object EtaEngine {
    private const val DEFAULT_TRANSIT_SPEED_KMH = 55.0
    private const val MIN_MOVING_SPEED_KMH = 22.0
    private const val MAX_PLAUSIBLE_SPEED_KMH = 140.0
    private const val STOPPED_SPEED_THRESHOLD_KMH = 3.5
    private const val STALE_GPS_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes

    /**
     * Calculates uncertainty-aware and stop-aware ETA.
     *
     * @param remainingRouteKm Route-aware travel distance (accounting for circuity / road bends)
     * @param straightLineKm Direct great-circle distance
     * @param recentObservations Recent GPS fixes
     * @param lastFixTimestamp Timestamp of last accepted GPS fix
     * @param isRouteAware Whether route circuity or waypoints were used
     * @param currentTimeMs Current evaluation epoch millis
     */
    fun calculateEta(
        remainingRouteKm: Double,
        straightLineKm: Double,
        recentObservations: List<ObservationEntity>,
        lastFixTimestamp: Long,
        isRouteAware: Boolean = true,
        currentTimeMs: Long = System.currentTimeMillis()
    ): EtaResult {
        if (remainingRouteKm <= 0.25 || straightLineKm <= 0.20) {
            return EtaResult(
                pointMinutes = 0,
                lowerMinutes = 0,
                upperMinutes = 1,
                confidence = ConfidenceLevel.HIGH,
                distanceRemainingKm = remainingRouteKm,
                straightLineKm = straightLineKm,
                effectiveSpeedKmh = 0.0,
                isStationary = true,
                stationaryDurationMs = 0L,
                isRouteAware = isRouteAware,
                explanation = "Destination reached or within arrival perimeter."
            )
        }

        val fixAgeMs = if (lastFixTimestamp > 0) currentTimeMs - lastFixTimestamp else 0L
        val isGpsStale = lastFixTimestamp > 0 && fixAgeMs > STALE_GPS_THRESHOLD_MS

        // Filter valid observations (exclude extreme sensor inaccuracies > 120m)
        val validObs = recentObservations.filter { it.accuracyMeters <= 120f }

        // --- 1. STOP DETECTION & STATIONARY DURATION ---
        val recentFive = validObs.take(5)
        val isStationary = recentFive.size >= 2 && recentFive.take(2).all { it.speedKmh < STOPPED_SPEED_THRESHOLD_KMH }

        var stationaryDurationMs = 0L
        if (isStationary) {
            // Find how long vehicle has had speed < threshold continuously
            val stoppedRun = validObs.takeWhile { it.speedKmh < STOPPED_SPEED_THRESHOLD_KMH }
            if (stoppedRun.isNotEmpty()) {
                val oldestStopped = stoppedRun.last().timestamp
                stationaryDurationMs = max(0L, currentTimeMs - oldestStopped)
            }
        }
        val stoppedMinutes = (stationaryDurationMs / 60000).toInt()

        // --- 2. MOVING CRUISING SPEED MODELING ---
        // Partition out moving observations to compute pure transit velocity
        val movingObs = validObs.filter { it.speedKmh >= STOPPED_SPEED_THRESHOLD_KMH }

        val movingCruisingSpeed: Double
        val speedVariance: Double

        if (movingObs.size >= 3) {
            var weightedSpeedSum = 0.0
            var weightSum = 0.0
            var alpha = 1.0

            for (obs in movingObs.take(10)) {
                val clamped = obs.speedKmh.coerceIn(MIN_MOVING_SPEED_KMH, MAX_PLAUSIBLE_SPEED_KMH)
                weightedSpeedSum += clamped * alpha
                weightSum += alpha
                alpha *= 0.8
            }
            movingCruisingSpeed = weightedSpeedSum / weightSum

            val mean = movingObs.take(10).map { it.speedKmh }.average()
            val variance = movingObs.take(10).map { (it.speedKmh - mean) * (it.speedKmh - mean) }.average()
            speedVariance = sqrt(variance)
        } else if (movingObs.isNotEmpty()) {
            movingCruisingSpeed = movingObs.first().speedKmh.coerceIn(MIN_MOVING_SPEED_KMH, MAX_PLAUSIBLE_SPEED_KMH)
            speedVariance = 16.0
        } else {
            // No moving observations yet (e.g. departing station)
            movingCruisingSpeed = DEFAULT_TRANSIT_SPEED_KMH
            speedVariance = 20.0
        }

        // --- 3. TRAVEL TIME & UNCERTAINTY INTERVAL ---
        // Pure moving time across remaining route distance
        val movingTravelHours = remainingRouteKm / max(movingCruisingSpeed, MIN_MOVING_SPEED_KMH)
        val movingTravelMinutes = (movingTravelHours * 60.0).toInt()

        val baseTravelMinutes: Int
        val lowerMinutes: Int
        val upperMinutes: Int

        if (isStationary) {
            // When stopped, expected remaining stop duration is modeled explicitly
            val expectedRemainingStopMinutes = if (stoppedMinutes < 5) 5 else min(25, stoppedMinutes / 2 + 5)
            baseTravelMinutes = movingTravelMinutes + expectedRemainingStopMinutes

            // Lower bound: vehicle departs immediately at top cruise speed
            val optimisticMovingMinutes = (remainingRouteKm / min(MAX_PLAUSIBLE_SPEED_KMH, movingCruisingSpeed * 1.15) * 60.0).toInt()
            lowerMinutes = max(1, optimisticMovingMinutes)

            // Upper bound: extended delay + slower resumption
            val delayBuffer = max(10, (baseTravelMinutes * 0.35).toInt()) + stoppedMinutes
            upperMinutes = baseTravelMinutes + delayBuffer + (if (isGpsStale) (fixAgeMs / 60000).toInt() else 0)
        } else {
            baseTravelMinutes = movingTravelMinutes

            val uncertaintyPct = when {
                isGpsStale -> 0.45
                validObs.size < 3 -> 0.30
                speedVariance > 25.0 -> 0.25
                speedVariance > 15.0 -> 0.18
                else -> 0.10
            }

            val spreadMinutes = max(3, (baseTravelMinutes * uncertaintyPct).toInt())
            lowerMinutes = max(1, baseTravelMinutes - spreadMinutes)
            upperMinutes = baseTravelMinutes + spreadMinutes + (if (isGpsStale) (fixAgeMs / 60000).toInt() else 0)
        }

        // --- 4. CONFIDENCE CLASSIFICATION ---
        val confidence = when {
            isGpsStale -> ConfidenceLevel.UNRELIABLE
            isStationary && stoppedMinutes > 15 -> ConfidenceLevel.LOW
            isStationary -> ConfidenceLevel.MEDIUM
            validObs.isEmpty() -> ConfidenceLevel.LOW
            validObs.first().accuracyMeters > 75f -> ConfidenceLevel.LOW
            speedVariance > 25.0 -> ConfidenceLevel.MEDIUM
            movingObs.size >= 5 && validObs.first().accuracyMeters <= 35f -> ConfidenceLevel.HIGH
            else -> ConfidenceLevel.MEDIUM
        }

        val explanation = when {
            isGpsStale -> "GPS fix is stale (${(fixAgeMs / 60000).toInt()} min without fix); prediction interval widened."
            isStationary -> "Vehicle stationary for $stoppedMinutes min; ETA accounts for stop and ${movingCruisingSpeed.toInt()} km/h cruise speed."
            confidence == ConfidenceLevel.HIGH -> "High-confidence forecast based on steady velocity of ${movingCruisingSpeed.toInt()} km/h along route."
            confidence == ConfidenceLevel.MEDIUM -> "Estimate derived from ${movingCruisingSpeed.toInt()} km/h cruising velocity with standard transit variance."
            else -> "Wide interval due to sparse recent GPS fixes or variable speeds."
        }

        return EtaResult(
            pointMinutes = max(1, baseTravelMinutes),
            lowerMinutes = lowerMinutes,
            upperMinutes = max(lowerMinutes + 1, upperMinutes),
            confidence = confidence,
            distanceRemainingKm = remainingRouteKm,
            straightLineKm = straightLineKm,
            effectiveSpeedKmh = movingCruisingSpeed,
            isStationary = isStationary,
            stationaryDurationMs = stationaryDurationMs,
            isRouteAware = isRouteAware,
            explanation = explanation
        )
    }
}
