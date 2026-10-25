package com.example

import com.example.data.model.AnomalyType
import com.example.data.model.ConfidenceLevel
import com.example.data.model.ObservationEntity
import com.example.engine.AlarmEvaluator
import com.example.engine.AnomalyDetector
import com.example.engine.EtaEngine
import com.example.engine.GeoMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NocturneTransitEngineTest {

    @Test
    fun `GeoMath calculates straight-line and route-aware circuity distances`() {
        // Direct coordinates between Chennai Central and Tirunelveli Junction
        val straightLine = GeoMath.calculateDistanceKm(13.0827, 80.2707, 8.7290, 77.7126)
        assertTrue("Straight-line distance should be ~556 km", straightLine in 550.0..565.0)

        // Route-aware distance with 1.25x road circuity factor
        val routeDistance = GeoMath.calculateRouteDistanceKm(13.0827, 80.2707, 8.7290, 77.7126, circuityFactor = 1.25)
        assertEquals(straightLine * 1.25, routeDistance, 0.01)
        assertTrue("Route distance must exceed straight-line distance", routeDistance > straightLine)

        // Polyline distance along waypoints
        val waypoints = listOf(
            Pair(10.0, 78.0),
            Pair(10.5, 78.2),
            Pair(11.0, 78.5)
        )
        val polyDist = GeoMath.calculatePolylineDistanceKm(waypoints)
        assertTrue("Polyline distance must be positive", polyDist > 0.0)
    }

    @Test
    fun `EtaEngine uses route-aware distance instead of naive straight line`() {
        val now = System.currentTimeMillis()
        val observations = listOf(
            ObservationEntity(1, 1, now - 10000, 10.0, 78.0, 60.0, 15f, 75.0),
            ObservationEntity(2, 1, now - 20000, 10.05, 78.05, 60.0, 12f, 77.0),
            ObservationEntity(3, 1, now - 30000, 10.10, 78.10, 60.0, 14f, 79.0)
        )

        val straightLineKm = 60.0
        val routeDistanceKm = 75.0 // 1.25x road network

        val result = EtaEngine.calculateEta(
            remainingRouteKm = routeDistanceKm,
            straightLineKm = straightLineKm,
            recentObservations = observations,
            lastFixTimestamp = now - 5000,
            isRouteAware = true,
            currentTimeMs = now
        )

        // At 60 km/h, 75 km route takes 75 minutes, NOT 60 minutes
        assertTrue("Point ETA must reflect 75km route travel time (~75 min), got ${result.pointMinutes}", result.pointMinutes in 70..80)
        assertTrue(result.isRouteAware)
    }

    @Test
    fun `EtaEngine separates stationary stop from moving cruising speed`() {
        val now = System.currentTimeMillis()
        // Bus has been stopped at a highway dhaba/station for 15 minutes (speed < 3.5 km/h)
        val observations = listOf(
            ObservationEntity(1, 1, now - 1000, 10.0, 78.0, 0.0, 10f, 50.0),
            ObservationEntity(2, 1, now - 3 * 60 * 1000L, 10.0, 78.0, 0.5, 10f, 50.0),
            ObservationEntity(3, 1, now - 7 * 60 * 1000L, 10.0, 78.0, 0.0, 10f, 50.0),
            ObservationEntity(4, 1, now - 11 * 60 * 1000L, 10.0, 78.0, 0.2, 10f, 50.0),
            ObservationEntity(5, 1, now - 15 * 60 * 1000L, 10.0, 78.0, 0.0, 10f, 50.0),
            // Prior moving observation
            ObservationEntity(6, 1, now - 20 * 60 * 1000L, 10.1, 78.1, 70.0, 12f, 55.0)
        )

        val result = EtaEngine.calculateEta(
            remainingRouteKm = 50.0,
            straightLineKm = 40.0,
            recentObservations = observations,
            lastFixTimestamp = now - 1000,
            isRouteAware = true,
            currentTimeMs = now
        )

        assertTrue("Vehicle must be flagged as stationary", result.isStationary)
        assertTrue("Stationary duration should be ~15 mins, got ${result.stationaryDurationMs / 60000}", result.stationaryDurationMs >= 14 * 60 * 1000L)
        // Cruising speed when moving should reflect prior moving history (70 km/h), NOT clamped to 18 km/h
        assertTrue("Effective moving speed should be around 70 km/h, got ${result.effectiveSpeedKmh}", result.effectiveSpeedKmh >= 60.0)
        // Interval must be widened significantly to account for unknown stop duration
        val intervalSpread = result.upperMinutes - result.lowerMinutes
        assertTrue("Uncertainty interval must widen under persistent stop (got spread $intervalSpread)", intervalSpread >= 20)
    }

    @Test
    fun `EtaEngine widens uncertainty and flags stale GPS fixes`() {
        val now = System.currentTimeMillis()
        val staleFixTime = now - (9 * 60 * 1000L) // 9 minutes without fix
        val observations = listOf(
            ObservationEntity(1, 1, staleFixTime, 10.0, 78.0, 55.0, 15f, 50.0)
        )

        val result = EtaEngine.calculateEta(
            remainingRouteKm = 50.0,
            straightLineKm = 40.0,
            recentObservations = observations,
            lastFixTimestamp = staleFixTime,
            isRouteAware = true,
            currentTimeMs = now
        )

        assertEquals(ConfidenceLevel.UNRELIABLE, result.confidence)
        assertTrue("Upper bound must absorb the 9-minute stale delay", result.upperMinutes >= result.pointMinutes + 9)
    }

    @Test
    fun `AlarmEvaluator reconciles persisted alarm on process restart`() {
        val now = System.currentTimeMillis()

        // 1. Scheduled alarm in future: should re-arm with persisted timestamp
        val futureAlarmTime = now + (20 * 60 * 1000L)
        val decisionFuture = AlarmEvaluator.reconcileOnRestart(
            persistedScheduledTimestamp = futureAlarmTime,
            alreadyTriggered = false,
            currentTimeMs = now
        )
        assertFalse(decisionFuture.shouldTriggerNow)
        assertTrue(decisionFuture.shouldReschedule)
        assertEquals(futureAlarmTime, decisionFuture.targetTriggerTimeMs)

        // 2. Scheduled alarm elapsed while process was killed: must trigger immediately!
        val pastAlarmTime = now - (5 * 60 * 1000L)
        val decisionElapsed = AlarmEvaluator.reconcileOnRestart(
            persistedScheduledTimestamp = pastAlarmTime,
            alreadyTriggered = false,
            currentTimeMs = now
        )
        assertTrue(decisionElapsed.shouldTriggerNow)
        assertFalse(decisionElapsed.shouldReschedule)

        // 3. Already triggered prior to restart: must not trigger duplicate
        val decisionTriggered = AlarmEvaluator.reconcileOnRestart(
            persistedScheduledTimestamp = pastAlarmTime,
            alreadyTriggered = true,
            currentTimeMs = now
        )
        assertFalse(decisionTriggered.shouldTriggerNow)
        assertFalse(decisionTriggered.shouldReschedule)
    }

    @Test
    fun `AlarmEvaluator incorporates lower bound under high uncertainty`() {
        val now = System.currentTimeMillis()
        // Point ETA is 34 min (slightly above 30m buffer), but lower bound is 26 min due to uncertainty
        val uncertainEta = com.example.data.model.EtaResult(
            pointMinutes = 34,
            lowerMinutes = 26,
            upperMinutes = 52,
            confidence = ConfidenceLevel.UNRELIABLE,
            distanceRemainingKm = 25.0,
            straightLineKm = 20.0,
            effectiveSpeedKmh = 50.0,
            isStationary = false,
            stationaryDurationMs = 0L,
            isRouteAware = true,
            explanation = "GPS degraded"
        )

        val decision = AlarmEvaluator.evaluate(
            etaResult = uncertainEta,
            wakeUpBufferMinutes = 30,
            alreadyTriggered = false,
            persistedTargetTriggerMs = 0L,
            currentTimeMs = now
        )

        // Lower bound has penetrated 30m buffer under degraded GPS: triggers defensive alarm!
        assertTrue("Defensive alarm should trigger under uncertainty when lower bound <= buffer", decision.shouldTriggerNow)
    }

    @Test
    fun `AnomalyDetector identifies persistent route deviation and stops`() {
        val now = System.currentTimeMillis()
        val observations = listOf(
            ObservationEntity(1, 1, now - 1000, 10.0, 78.0, 60.0, 15f, 45.0),
            ObservationEntity(2, 1, now - 30000, 10.05, 78.05, 60.0, 15f, 40.0),
            ObservationEntity(3, 1, now - 60000, 10.10, 78.10, 60.0, 15f, 37.0),
            ObservationEntity(4, 1, now - 90000, 10.15, 78.15, 60.0, 15f, 35.0)
        )

        val result = AnomalyDetector.detectAnomaly(
            currentRemainingKm = 45.0,
            currentEtaMinutes = 50,
            previousEtaMinutes = 35,
            lastFixTimestamp = now,
            recentObservations = observations,
            currentTimeMs = now
        )

        assertTrue(result.isAnomaly)
        assertEquals(AnomalyType.PERSISTENT_ROUTE_DEVIATION, result.type)
    }
}
