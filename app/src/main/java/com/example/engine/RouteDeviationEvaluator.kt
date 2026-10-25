package com.example.engine

import com.example.data.model.ObservationEntity
import com.example.data.model.RouteDeviationEvaluation
import com.example.data.model.RouteDeviationState

object RouteDeviationEvaluator {
    // Configurable thresholds for intercity transit
    const val DEVIATION_TRIGGER_THRESHOLD_KM = 2.0
    const val RECOVERY_HYSTERESIS_THRESHOLD_KM = 0.8
    const val SUSPECT_DEVIATION_THRESHOLD_KM = 1.2
    const val REQUIRED_CONSECUTIVE_FIXES = 2
    const val STALE_GPS_THRESHOLD_MS = 60_000L // 60 seconds

    /**
     * Evaluates current vehicle position against the active reference corridor.
     * Guaranteed to never classify stale GPS or missing geometry as a confirmed route deviation.
     */
    fun evaluate(
        currentLat: Double?,
        currentLon: Double?,
        currentAccuracyMeters: Float,
        lastFixTimestamp: Long,
        currentTimeMs: Long,
        referencePolyline: List<Pair<Double, Double>>,
        recentObservations: List<ObservationEntity>,
        previousState: RouteDeviationState = RouteDeviationState.NORMAL,
        consecutiveOffTrackCount: Int = 0
    ): RouteDeviationEvaluation {
        // 1. Missing or unmapped geometry
        if (referencePolyline.size < 2) {
            return RouteDeviationEvaluation(
                state = RouteDeviationState.UNAVAILABLE_GEOMETRY,
                crossTrackDistanceKm = 0.0,
                effectiveDeviationKm = 0.0,
                thresholdKm = DEVIATION_TRIGGER_THRESHOLD_KM,
                consecutiveFixCount = 0,
                explanation = "Reference route geometry not mapped for this service. Using mathematical corridor circuity model."
            )
        }

        // 2. Missing or Stale GPS fix
        val fixAgeMs = currentTimeMs - lastFixTimestamp
        if (currentLat == null || currentLon == null || lastFixTimestamp <= 0L || fixAgeMs > STALE_GPS_THRESHOLD_MS) {
            val ageSec = if (lastFixTimestamp > 0L) (fixAgeMs / 1000).toInt() else 0
            return RouteDeviationEvaluation(
                state = RouteDeviationState.STALE_GPS,
                crossTrackDistanceKm = 0.0,
                effectiveDeviationKm = 0.0,
                thresholdKm = DEVIATION_TRIGGER_THRESHOLD_KM,
                consecutiveFixCount = 0,
                explanation = "GPS fix stale ($ageSec s without fix). Route cross-track comparison suspended defensively."
            )
        }

        // 3. Compute cross-track distance using Haversine segment projection
        val rawCrossTrackKm = GeoMath.distanceToPolylineKm(currentLat, currentLon, referencePolyline)
        // Subtract horizontal GPS accuracy error bound to prevent noisy fixes from false alarming
        val accuracyKm = (currentAccuracyMeters / 1000.0).coerceAtLeast(0.0)
        val effectiveDeviationKm = (rawCrossTrackKm - accuracyKm).coerceAtLeast(0.0)

        // 4. Hysteresis Recovery: if vehicle was previously deviating and now returns within 0.8 km
        if (effectiveDeviationKm < RECOVERY_HYSTERESIS_THRESHOLD_KM) {
            val wasDeviating = previousState == RouteDeviationState.CONFIRMED_DEVIATION ||
                    previousState == RouteDeviationState.POSSIBLE_DEVIATION
            return RouteDeviationEvaluation(
                state = RouteDeviationState.NORMAL,
                crossTrackDistanceKm = rawCrossTrackKm,
                effectiveDeviationKm = effectiveDeviationKm,
                thresholdKm = DEVIATION_TRIGGER_THRESHOLD_KM,
                consecutiveFixCount = 0,
                explanation = if (wasDeviating) "Trajectory returned to scheduled corridor (±${formatKm(rawCrossTrackKm)} km). Alignment verified."
                else "On scheduled corridor. Cross-track offset: ±${formatKm(rawCrossTrackKm)} km.",
                isRecovered = wasDeviating,
                recoveryStatus = if (wasDeviating) "RECOVERED TO ROUTE" else null
            )
        }

        // 5. Check if effective deviation meets or exceeds trigger threshold
        if (effectiveDeviationKm >= DEVIATION_TRIGGER_THRESHOLD_KM) {
            // Count recent observations confirming deviation to protect against single-point glitches
            val sustainedObservationsCount = recentObservations.take(4).count { obs ->
                val d = GeoMath.distanceToPolylineKm(obs.lat, obs.lon, referencePolyline)
                val eff = (d - (obs.accuracyMeters / 1000.0)).coerceAtLeast(0.0)
                eff >= (DEVIATION_TRIGGER_THRESHOLD_KM * 0.75) // within 75% of threshold
            }

            val newCount = consecutiveOffTrackCount + 1

            if (newCount >= REQUIRED_CONSECUTIVE_FIXES || sustainedObservationsCount >= REQUIRED_CONSECUTIVE_FIXES) {
                return RouteDeviationEvaluation(
                    state = RouteDeviationState.CONFIRMED_DEVIATION,
                    crossTrackDistanceKm = rawCrossTrackKm,
                    effectiveDeviationKm = effectiveDeviationKm,
                    thresholdKm = DEVIATION_TRIGGER_THRESHOLD_KM,
                    consecutiveFixCount = newCount,
                    explanation = "Trajectory diverged ~${formatKm(rawCrossTrackKm)} km off reference corridor across $newCount consecutive fixes. Transit reroute or detour confirmed."
                )
            } else {
                return RouteDeviationEvaluation(
                    state = RouteDeviationState.POSSIBLE_DEVIATION,
                    crossTrackDistanceKm = rawCrossTrackKm,
                    effectiveDeviationKm = effectiveDeviationKm,
                    thresholdKm = DEVIATION_TRIGGER_THRESHOLD_KM,
                    consecutiveFixCount = newCount,
                    explanation = "Position offset ~${formatKm(rawCrossTrackKm)} km from corridor (fix 1 of $REQUIRED_CONSECUTIVE_FIXES). Awaiting confirmation to filter urban drift."
                )
            }
        }

        // 6. Between recovery threshold and trigger threshold
        if (previousState == RouteDeviationState.CONFIRMED_DEVIATION) {
            // Maintain confirmed state until recovered below RECOVERY_HYSTERESIS_THRESHOLD_KM (Hysteresis)
            return RouteDeviationEvaluation(
                state = RouteDeviationState.CONFIRMED_DEVIATION,
                crossTrackDistanceKm = rawCrossTrackKm,
                effectiveDeviationKm = effectiveDeviationKm,
                thresholdKm = DEVIATION_TRIGGER_THRESHOLD_KM,
                consecutiveFixCount = consecutiveOffTrackCount,
                explanation = "Vehicle remaining off corridor (~${formatKm(rawCrossTrackKm)} km). Recalibrating route trajectory."
            )
        }

        if (effectiveDeviationKm >= SUSPECT_DEVIATION_THRESHOLD_KM) {
            return RouteDeviationEvaluation(
                state = RouteDeviationState.POSSIBLE_DEVIATION,
                crossTrackDistanceKm = rawCrossTrackKm,
                effectiveDeviationKm = effectiveDeviationKm,
                thresholdKm = DEVIATION_TRIGGER_THRESHOLD_KM,
                consecutiveFixCount = 1,
                explanation = "Elevated corridor divergence (~${formatKm(rawCrossTrackKm)} km). Monitoring for sustained reroute."
            )
        }

        return RouteDeviationEvaluation(
            state = RouteDeviationState.NORMAL,
            crossTrackDistanceKm = rawCrossTrackKm,
            effectiveDeviationKm = effectiveDeviationKm,
            thresholdKm = DEVIATION_TRIGGER_THRESHOLD_KM,
            consecutiveFixCount = 0,
            explanation = "On scheduled corridor. Offset: ±${formatKm(rawCrossTrackKm)} km."
        )
    }

    private fun formatKm(km: Double): String {
        return ((km * 10).toInt() / 10.0).toString()
    }
}
