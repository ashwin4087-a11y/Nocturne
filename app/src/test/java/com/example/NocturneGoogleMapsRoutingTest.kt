package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.ComputeRouteItem
import com.example.data.api.ComputeRoutesRequest
import com.example.data.api.ComputeRoutesResponse
import com.example.data.api.GoogleRoutesApiService
import com.example.data.api.RoutePolyline
import com.example.data.db.NocturneDatabase
import com.example.data.model.AnomalyType
import com.example.data.model.DestinationSearchState
import com.example.data.model.DistanceSource
import com.example.data.model.ObservationEntity
import com.example.data.model.RouteSearchState
import com.example.data.model.TransitMode
import com.example.data.repository.RoutingRepository
import com.example.engine.AnomalyDetector
import com.example.engine.EtaEngine
import com.example.engine.GeoMath
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NocturneGoogleMapsRoutingTest {

    // Fake Google Routes Api Service for testing
    private class FakeGoogleRoutesApiService(
        private val shouldSucceed: Boolean = true,
        private val distanceMeters: Long = 620_000L,
        private val durationSeconds: String = "32400s",
        private val polyline: String? = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
    ) : GoogleRoutesApiService {
        override suspend fun computeRoutes(
            apiKey: String,
            fieldMask: String,
            request: ComputeRoutesRequest
        ): ComputeRoutesResponse {
            if (!shouldSucceed) {
                throw RuntimeException("Simulated network timeout / offline")
            }
            return ComputeRoutesResponse(
                routes = listOf(
                    ComputeRouteItem(
                        distanceMeters = distanceMeters,
                        duration = durationSeconds,
                        polyline = polyline?.let { RoutePolyline(it) },
                        description = "Fastest route via Highway Express"
                    )
                )
            )
        }
    }

    @Test
    fun `search behavior - blank and whitespace queries return Idle without making requests`() = runTest {
        val repo = RoutingRepository()
        val resultBlank = repo.searchDestinations("   ").first()
        val resultEmpty = repo.searchDestinations("").first()

        assertEquals(DestinationSearchState.Idle, resultBlank)
        assertEquals(DestinationSearchState.Idle, resultEmpty)
    }

    @Test
    fun `search behavior - matches curated transit hubs and station names offline`() = runTest {
        val repo = RoutingRepository()
        val states = repo.searchDestinations("Chennai").toList()

        val lastState = states.last()
        assertTrue("Expected Success state for Chennai", lastState is DestinationSearchState.Success)
        val suggestions = (lastState as DestinationSearchState.Success).suggestions
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.name.contains("Chennai", ignoreCase = true) })
    }

    @Test
    fun `search behavior - handles Unicode and multilingual station names safely`() = runTest {
        val repo = RoutingRepository()
        // Tamil query for Tirunelveli
        val states = repo.searchDestinations("திருநெல்வேலி").toList()
        assertTrue(states.isNotEmpty())
    }

    @Test
    fun `routing - successful Google Routes response parsing and distance source classification`() = runTest {
        val fakeService = FakeGoogleRoutesApiService(
            shouldSucceed = true,
            distanceMeters = 580_000L,
            durationSeconds = "28800s"
        )
        val repo = RoutingRepository(fakeService, apiKeyProvider = { "test_api_key" })

        val states = repo.getRouteOptions(
            originLat = 13.0827,
            originLon = 80.2707,
            destLat = 8.7290,
            destLon = 77.7126,
            destName = "Tirunelveli Junction"
        ).toList()

        val successState = states.filterIsInstance<RouteSearchState.Success>().lastOrNull()
        assertNotNull("Expected RouteSearchState.Success", successState)

        val routes = successState!!.routes
        assertEquals(3, routes.size)

        // 1. Bus / Highway route uses mapped road distance
        val busRoute = routes.first { it.mode == TransitMode.HIGHWAY_BUS }
        assertEquals(DistanceSource.MAPPED_ROUTE, busRoute.distanceSource)
        assertEquals(580.0, busRoute.distanceKm, 0.5)
        assertEquals(480, busRoute.durationMinutes) // 28800s / 60

        // 2. Rail route uses physical rail corridor alignment (~1.15x)
        val railRoute = routes.first { it.mode == TransitMode.RAIL_CORRIDOR }
        assertEquals(DistanceSource.ESTIMATED_ROUTE, railRoute.distanceSource)
        assertTrue("Rail duration must be computed", railRoute.durationMinutes > 0)

        // 3. Direct baseline uses straight-line great-circle distance
        val directRoute = routes.first { it.mode == TransitMode.DIRECT_TERRAIN }
        assertEquals(DistanceSource.STRAIGHT_LINE_FALLBACK, directRoute.distanceSource)
    }

    @Test
    fun `routing - offline fallback computes corridor circuity without network crash`() = runTest {
        val offlineService = FakeGoogleRoutesApiService(shouldSucceed = false)
        val repo = RoutingRepository(offlineService)

        val states = repo.getRouteOptions(
            originLat = 13.0827,
            originLon = 80.2707,
            destLat = 8.7290,
            destLon = 77.7126,
            destName = "Tirunelveli Junction"
        ).toList()

        val successState = states.filterIsInstance<RouteSearchState.Success>().lastOrNull()
        assertNotNull("Must succeed with corridor circuity fallback even offline", successState)

        val busRoute = successState!!.routes.first { it.mode == TransitMode.HIGHWAY_BUS }
        assertEquals(DistanceSource.ESTIMATED_ROUTE, busRoute.distanceSource)
        assertTrue("Estimated distance must be positive", busRoute.distanceKm > 400.0)
    }

    @Test
    fun `routing - invalid coordinates return Error state gracefully`() = runTest {
        val repo = RoutingRepository()
        val states = repo.getRouteOptions(
            originLat = 95.0, // Invalid latitude
            originLon = 80.0,
            destLat = 8.0,
            destLon = 77.0,
            destName = "Invalid Target"
        ).toList()

        assertTrue(states.any { it is RouteSearchState.Error })
    }

    @Test
    fun `geomath - polyline decoding correctly decodes Google Encoded Polyline format`() {
        // Standard sample polyline
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val points = GeoMath.decodePolyline(encoded)

        assertTrue(points.isNotEmpty())
        for (pt in points) {
            assertTrue(GeoMath.isValidCoordinate(pt.first, pt.second))
        }
    }

    @Test
    fun `geomath - distance to polyline accurately detects on-route and off-route coordinates`() {
        val corridor = listOf(
            Pair(10.0, 70.0),
            Pair(10.0, 71.0),
            Pair(10.0, 72.0)
        )

        // Point exactly on the corridor segment
        val dOn = GeoMath.distanceToPolylineKm(10.0, 70.5, corridor)
        assertEquals(0.0, dOn, 0.1)

        // Point 1 degree north (~111 km off corridor)
        val dOff = GeoMath.distanceToPolylineKm(11.0, 70.5, corridor)
        assertTrue("Distance off corridor should be > 100 km", dOff > 100.0)
    }

    @Test
    fun `anomaly detector - detects sustained cross-track route deviation against corridor polyline`() {
        val corridor = listOf(
            Pair(10.0, 70.0),
            Pair(10.0, 71.0),
            Pair(10.0, 72.0)
        )

        // 3 consecutive fixes observed 5 km north of corridor
        val recentObs = listOf(
            ObservationEntity(1, 100, 1000L, 10.05, 70.8, 60.0, 15f, 120.0),
            ObservationEntity(2, 100, 2000L, 10.05, 70.7, 60.0, 15f, 130.0),
            ObservationEntity(3, 100, 3000L, 10.05, 70.6, 60.0, 15f, 140.0)
        )

        val result = AnomalyDetector.detectAnomaly(
            currentRemainingKm = 110.0,
            currentEtaMinutes = 90,
            previousEtaMinutes = 90,
            lastFixTimestamp = System.currentTimeMillis(),
            recentObservations = recentObs,
            currentLat = 10.05,
            currentLon = 70.9,
            currentAccuracyMeters = 15f,
            referencePolyline = corridor
        )

        assertTrue(result.isAnomaly)
        assertEquals(AnomalyType.PERSISTENT_ROUTE_DEVIATION, result.type)
        assertTrue(result.explanation.contains("off reference corridor"))
    }

    @Test
    fun `room migration MIGRATION_2_3 - preserves version 2 data and adds routeDistanceSource`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Create SQLite database representing Schema Version 2
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE journeys (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            destinationName TEXT NOT NULL,
                            destinationLat REAL NOT NULL,
                            destinationLon REAL NOT NULL,
                            wakeUpBufferMinutes INTEGER NOT NULL,
                            status TEXT NOT NULL,
                            startTimestamp INTEGER NOT NULL,
                            completedTimestamp INTEGER,
                            lastKnownLat REAL,
                            lastKnownLon REAL,
                            lastKnownSpeedKmh REAL NOT NULL,
                            lastKnownAccuracyMeters REAL NOT NULL,
                            lastFixTimestamp INTEGER NOT NULL,
                            remainingDistanceKm REAL NOT NULL,
                            straightLineDistanceKm REAL NOT NULL,
                            routeCircuityFactor REAL NOT NULL,
                            isRouteAware INTEGER NOT NULL,
                            etaMinutesPoint INTEGER NOT NULL,
                            etaMinutesLower INTEGER NOT NULL,
                            etaMinutesUpper INTEGER NOT NULL,
                            confidence TEXT NOT NULL,
                            anomalyType TEXT NOT NULL,
                            anomalyDetail TEXT,
                            alarmTriggered INTEGER NOT NULL,
                            alarmTriggeredTimestamp INTEGER,
                            targetScheduledAlarmMs INTEGER NOT NULL,
                            isVehicleStationary INTEGER NOT NULL,
                            stationaryDurationMs INTEGER NOT NULL,
                            gpsStale INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val supportDb = helper.writableDatabase

        // 2. Insert active journey in Version 2 format
        supportDb.execSQL(
            """
            INSERT INTO journeys (
                id, destinationName, destinationLat, destinationLon, wakeUpBufferMinutes,
                status, startTimestamp, lastKnownSpeedKmh, lastKnownAccuracyMeters,
                lastFixTimestamp, remainingDistanceKm, straightLineDistanceKm,
                routeCircuityFactor, isRouteAware, etaMinutesPoint, etaMinutesLower,
                etaMinutesUpper, confidence, anomalyType, alarmTriggered,
                targetScheduledAlarmMs, isVehicleStationary, stationaryDurationMs, gpsStale
            ) VALUES (
                1, 'Tirunelveli Junction', 8.7290, 77.7126, 30, 'ACTIVE', 1700000000,
                55.0, 10.0, 1700000500, 620.0, 500.0, 1.25, 1, 600, 550, 650,
                'HIGH', 'NONE', 0, 0, 0, 0, 0
            )
            """.trimIndent()
        )

        // 3. Execute MIGRATION_2_3
        NocturneDatabase.MIGRATION_2_3.migrate(supportDb)

        // 4. Query to verify data preservation and new column defaults
        val cursor = supportDb.query("SELECT * FROM journeys WHERE id = 1")
        assertTrue(cursor.moveToFirst())

        val destNameIdx = cursor.getColumnIndex("destinationName")
        assertEquals("Tirunelveli Junction", cursor.getString(destNameIdx))

        val sourceIdx = cursor.getColumnIndex("routeDistanceSource")
        assertTrue("routeDistanceSource column must exist", sourceIdx >= 0)
        assertEquals("ESTIMATED_ROUTE", cursor.getString(sourceIdx))

        val summaryIdx = cursor.getColumnIndex("referenceRouteSummary")
        assertTrue("referenceRouteSummary column must exist", summaryIdx >= 0)

        cursor.close()
        supportDb.close()
    }
}
