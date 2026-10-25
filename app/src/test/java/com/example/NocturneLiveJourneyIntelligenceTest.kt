import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.db.NocturneDatabase
import com.example.data.model.ObservationEntity
import com.example.data.model.RouteDeviationState
import com.example.engine.RouteDeviationEvaluator
import com.example.engine.TicketParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NocturneLiveJourneyIntelligenceTest {

    private val referencePolyline = listOf(
        Pair(13.0827, 80.2707), // Chennai Central
        Pair(12.5000, 79.8000), // Milestone 1
        Pair(11.9400, 79.4800), // Milestone 2 (Villupuram)
        Pair(10.7905, 78.7047), // Milestone 3 (Trichy)
        Pair(8.7290, 77.7126)   // Tirunelveli Junction
    )

    @Test
    fun testRouteDeviationEvaluator_NormalTrajectory() {
        val eval = RouteDeviationEvaluator.evaluate(
            currentLat = 12.5002,
            currentLon = 79.8001,
            currentAccuracyMeters = 15f,
            lastFixTimestamp = 100_000L,
            currentTimeMs = 102_000L,
            referencePolyline = referencePolyline,
            recentObservations = emptyList()
        )

        assertEquals(RouteDeviationState.NORMAL, eval.state)
        assertTrue("Effective deviation should be near zero", eval.effectiveDeviationKm < 0.5)
        assertFalse("Should not be confirmed deviation", eval.state == RouteDeviationState.CONFIRMED_DEVIATION)
    }

    @Test
    fun testRouteDeviationEvaluator_SingleFixSuspectDeviation_DoesNotFalseAlarm() {
        // Offset by ~5.4 km west from corridor vertex (12.5000, 79.8000)
        val eval = RouteDeviationEvaluator.evaluate(
            currentLat = 12.5000,
            currentLon = 79.7500,
            currentAccuracyMeters = 20f,
            lastFixTimestamp = 100_000L,
            currentTimeMs = 105_000L,
            referencePolyline = referencePolyline,
            recentObservations = emptyList(),
            consecutiveOffTrackCount = 0
        )

        assertEquals(RouteDeviationState.POSSIBLE_DEVIATION, eval.state)
        assertEquals(1, eval.consecutiveFixCount)
        assertTrue(eval.explanation.contains("Awaiting confirmation"))
    }

    @Test
    fun testRouteDeviationEvaluator_ConsecutiveFixes_ConfirmsDeviation() {
        val recentObs = listOf(
            ObservationEntity(1, 1, 100_000L, 12.5000, 79.7500, 60.0, 20f, 400.0),
            ObservationEntity(2, 1, 105_000L, 12.5000, 79.7480, 60.0, 20f, 395.0)
        )

        val eval = RouteDeviationEvaluator.evaluate(
            currentLat = 12.5000,
            currentLon = 79.7450,
            currentAccuracyMeters = 20f,
            lastFixTimestamp = 110_000L,
            currentTimeMs = 112_000L,
            referencePolyline = referencePolyline,
            recentObservations = recentObs,
            previousState = RouteDeviationState.POSSIBLE_DEVIATION,
            consecutiveOffTrackCount = 1
        )

        assertEquals(RouteDeviationState.CONFIRMED_DEVIATION, eval.state)
        assertEquals(2, eval.consecutiveFixCount)
        assertTrue(eval.explanation.contains("confirmed"))
    }

    @Test
    fun testRouteDeviationEvaluator_HysteresisRecovery() {
        // Vehicle was in confirmed deviation, now returns within 0.5 km of polyline
        val eval = RouteDeviationEvaluator.evaluate(
            currentLat = 12.5001,
            currentLon = 79.8001,
            currentAccuracyMeters = 10f,
            lastFixTimestamp = 120_000L,
            currentTimeMs = 122_000L,
            referencePolyline = referencePolyline,
            recentObservations = emptyList(),
            previousState = RouteDeviationState.CONFIRMED_DEVIATION,
            consecutiveOffTrackCount = 3
        )

        assertEquals(RouteDeviationState.NORMAL, eval.state)
        assertTrue("Should be marked recovered", eval.isRecovered)
        assertEquals("RECOVERED TO ROUTE", eval.recoveryStatus)
        assertEquals(0, eval.consecutiveFixCount)
    }

    @Test
    fun testRouteDeviationEvaluator_StaleGps_NeverClassifiedAsDeviation() {
        val eval = RouteDeviationEvaluator.evaluate(
            currentLat = 12.5000,
            currentLon = 79.8000,
            currentAccuracyMeters = 20f,
            lastFixTimestamp = 100_000L,
            currentTimeMs = 200_000L, // 100 seconds silence (>60s stale threshold)
            referencePolyline = referencePolyline,
            recentObservations = emptyList()
        )

        assertEquals(RouteDeviationState.STALE_GPS, eval.state)
        assertTrue(eval.explanation.contains("GPS fix stale"))
        assertFalse(eval.state == RouteDeviationState.CONFIRMED_DEVIATION)
    }

    @Test
    fun testRouteDeviationEvaluator_UnavailableGeometry_NeverClassifiedAsDeviation() {
        val eval = RouteDeviationEvaluator.evaluate(
            currentLat = 12.5000,
            currentLon = 79.8000,
            currentAccuracyMeters = 20f,
            lastFixTimestamp = 100_000L,
            currentTimeMs = 102_000L,
            referencePolyline = emptyList(), // No polyline
            recentObservations = emptyList()
        )

        assertEquals(RouteDeviationState.UNAVAILABLE_GEOMETRY, eval.state)
        assertTrue(eval.explanation.contains("circuity model"))
        assertFalse(eval.state == RouteDeviationState.CONFIRMED_DEVIATION)
    }

    @Test
    fun testTicketParser_IrctcTrainTicket() {
        val raw = """
            ELECTRONIC RESERVATION SLIP (ERS)
            PNR: 452-9182341
            Train No: 12631 / Nellai Superfast Express
            From: Chennai Central (MAS) To: Tirunelveli Junction (TEN)
            Departure: 20:10
            Scheduled Arrival: 06:40
            Class: 3A Coach: B2 Berth: 45
        """.trimIndent()

        val ticket = TicketParser.parse(raw)

        assertEquals("TRAIN", ticket.transportMode)
        assertEquals("452-9182341", ticket.pnr)
        assertEquals("12631", ticket.serviceNumber)
        assertNotNull(ticket.origin)
        assertTrue(ticket.origin!!.contains("Chennai Central"))
        assertNotNull(ticket.destination)
        assertTrue(ticket.destination!!.contains("Tirunelveli Junction"))
        assertEquals("20:10", ticket.departureTimeStr)
        assertEquals("06:40", ticket.scheduledArrivalTimeStr)
        assertNotNull(ticket.scheduledArrivalEpochMs)
        assertTrue("Confidence should be high (>0.8)", ticket.confidenceScore >= 0.8f)
    }

    @Test
    fun testTicketParser_IntercityBusTicket() {
        val raw = """
            Booking Confirmation - KPN Travels
            Ticket No: KPN-77201
            Bus No: KA-01-F-4412
            Boarding Point: Madiwala Bangalore
            Destination: Madurai Junction
            Departure: 21:30
            Scheduled Drop: 05:45 AM
            Seat: A4 Sleeper
        """.trimIndent()

        val ticket = TicketParser.parse(raw)

        assertEquals("BUS", ticket.transportMode)
        assertEquals("KPN-77201", ticket.pnr)
        assertNotNull(ticket.destination)
        assertTrue(ticket.destination!!.contains("Madurai Junction"))
        assertTrue("Confidence should be substantial (>0.5)", ticket.confidenceScore >= 0.5f)
    }

    @Test
    fun testTicketParser_EmptyOrCorruptText_ReturnsSafeFallback() {
        val ticket = TicketParser.parse("   ")
        assertEquals(0f, ticket.confidenceScore)
        assertNull(ticket.destination)
        assertTrue(ticket.ambiguousFields.isNotEmpty())
    }

    @Test
    fun testDatabase_Migration_3_to_4_PreservesActiveJourney() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Create SQLite database representing Schema Version 3
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
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
                            gpsStale INTEGER NOT NULL,
                            routeDistanceSource TEXT NOT NULL,
                            referenceRouteSummary TEXT
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val supportDb = helper.writableDatabase

        // 2. Insert active journey in Version 3 format
        supportDb.execSQL(
            """
            INSERT INTO journeys (
                id, destinationName, destinationLat, destinationLon, wakeUpBufferMinutes, status, startTimestamp,
                lastKnownSpeedKmh, lastKnownAccuracyMeters, lastFixTimestamp, remainingDistanceKm, straightLineDistanceKm,
                routeCircuityFactor, isRouteAware, etaMinutesPoint, etaMinutesLower, etaMinutesUpper,
                confidence, anomalyType, alarmTriggered, targetScheduledAlarmMs, isVehicleStationary,
                stationaryDurationMs, gpsStale, routeDistanceSource, referenceRouteSummary
            ) VALUES (
                1, 'Tirunelveli Junction', 8.7290, 77.7126, 30, 'ACTIVE', 1700000000,
                55.0, 10.0, 1700000500, 620.0, 500.0,
                1.25, 1, 600, 550, 650,
                'HIGH', 'NONE', 0, 1700030000, 0,
                0, 0, 'MAPPED_ROUTE', 'NH44 Expressway Corridor'
            )
            """.trimIndent()
        )

        // 3. Execute MIGRATION_3_4
        NocturneDatabase.MIGRATION_3_4.migrate(supportDb)

        // 4. Query to verify data preservation and new column defaults
        val cursor = supportDb.query("SELECT destinationName, status, deviationState, crossTrackDistanceKm, transportMode, ticketPnr FROM journeys WHERE id = 1")
        assertTrue("Active journey must exist after MIGRATION_3_4", cursor.moveToFirst())
        assertEquals("Tirunelveli Junction", cursor.getString(cursor.getColumnIndexOrThrow("destinationName")))
        assertEquals("ACTIVE", cursor.getString(cursor.getColumnIndexOrThrow("status")))
        assertEquals("NORMAL", cursor.getString(cursor.getColumnIndexOrThrow("deviationState")))
        assertEquals(0.0, cursor.getDouble(cursor.getColumnIndexOrThrow("crossTrackDistanceKm")), 0.001)
        assertEquals("BUS", cursor.getString(cursor.getColumnIndexOrThrow("transportMode")))
        assertNull(cursor.getString(cursor.getColumnIndexOrThrow("ticketPnr")))
        cursor.close()
    }
}
