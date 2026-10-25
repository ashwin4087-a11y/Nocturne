package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.NocturneDatabase
import com.example.data.model.AnomalyType
import com.example.data.model.ConfidenceLevel
import com.example.data.model.JourneyEntity
import com.example.data.model.JourneyStatus
import com.example.service.ElapsedRealtimeClock
import com.example.service.NocturneTrackingService
import com.example.service.RawLocationFix
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

class FakeElapsedRealtimeClock(var currentElapsedRealtime: Long = 100_000L) : ElapsedRealtimeClock {
    override fun elapsedRealtime(): Long = currentElapsedRealtime
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GpsSilenceWatchdogTest {

    private lateinit var context: Context
    private lateinit var database: NocturneDatabase
    private lateinit var fakeClock: FakeElapsedRealtimeClock
    private var testJourneyId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = NocturneDatabase.getInstance(context)
        database.journeyDao().clearAllJourneys()
        database.observationDao().clearAllObservations()

        fakeClock = FakeElapsedRealtimeClock(currentElapsedRealtime = 100_000L)
        NocturneTrackingService.clock = fakeClock

        // Create an active test journey
        val journey = JourneyEntity(
            destinationName = "Central Terminal",
            destinationLat = 40.7527,
            destinationLon = -73.9772,
            wakeUpBufferMinutes = 30,
            status = JourneyStatus.ACTIVE,
            startTimestamp = System.currentTimeMillis()
        )
        testJourneyId = database.journeyDao().insertJourney(journey)
    }

    @After
    fun tearDown() = runBlocking {
        database.journeyDao().clearAllJourneys()
        database.observationDao().clearAllObservations()
    }

    @Test
    fun `no stale state is triggered before 60 seconds`() = runBlocking {
        val serviceController = Robolectric.buildService(NocturneTrackingService::class.java)
        val service = serviceController.create().get()

        // Missing initial fix handled: initialized at t = 100_000
        service.setLastAcceptedFixElapsedRealtimeForTest(100_000L)

        // Advance 30 seconds (130_000L - 100_000L = 30s < 60s threshold)
        fakeClock.currentElapsedRealtime = 130_000L
        service.processWatchdogCheckSequentially(
            checkElapsedRealtime = 130_000L,
            generationSnapshot = service.getFixGenerationForTest()
        )

        val journey = database.journeyDao().getActiveJourneyDirect()
        assertNotNull(journey)
        assertFalse("GPS must not be marked stale before 60 seconds", journey!!.gpsStale)
        assertFalse("isMarkedStale flag must remain false", service.isMarkedStaleForTest())
    }

    @Test
    fun `stale state is triggered after 60-second threshold is exceeded`() = runBlocking {
        val serviceController = Robolectric.buildService(NocturneTrackingService::class.java)
        val service = serviceController.create().get()

        // Set fix at t = 100_000
        service.setLastAcceptedFixElapsedRealtimeForTest(100_000L)

        // Advance 65 seconds (165_000L - 100_000L = 65s > 60s threshold)
        fakeClock.currentElapsedRealtime = 165_000L
        service.processWatchdogCheckSequentially(
            checkElapsedRealtime = 165_000L,
            generationSnapshot = service.getFixGenerationForTest()
        )

        val journey = database.journeyDao().getActiveJourneyDirect()
        assertNotNull(journey)
        assertTrue("GPS must be marked stale after 60 seconds silence", journey!!.gpsStale)
        assertEquals(ConfidenceLevel.UNRELIABLE, journey.confidence)
        assertEquals(AnomalyType.STALE_GPS_DATA, journey.anomalyType)
        assertTrue("Anomaly detail must note silence duration", journey.anomalyDetail?.contains("silent") == true)
        assertTrue("isMarkedStale flag must be set to true", service.isMarkedStaleForTest())
    }

    @Test
    fun `repeated watchdog checks while stale do not cause redundant database writes`() = runBlocking {
        val serviceController = Robolectric.buildService(NocturneTrackingService::class.java)
        val service = serviceController.create().get()

        service.setLastAcceptedFixElapsedRealtimeForTest(100_000L)

        // First check at t = 165_000 (triggers stale)
        fakeClock.currentElapsedRealtime = 165_000L
        service.processWatchdogCheckSequentially(165_000L, service.getFixGenerationForTest())
        assertTrue(service.isMarkedStaleForTest())

        // Mutate a custom field directly in DB to verify if second watchdog check overwrites it
        val dbJourney = database.journeyDao().getActiveJourneyDirect()!!
        val modifiedJourney = dbJourney.copy(anomalyDetail = "CUSTOM_CHECK_NO_OVERWRITE")
        database.journeyDao().updateJourney(modifiedJourney)

        // Second check at t = 170_000 (5s later, still stale)
        fakeClock.currentElapsedRealtime = 170_000L
        service.processWatchdogCheckSequentially(170_000L, service.getFixGenerationForTest())

        // Detail should NOT be overwritten because redundant write was skipped!
        val afterCheck = database.journeyDao().getActiveJourneyDirect()!!
        assertEquals("CUSTOM_CHECK_NO_OVERWRITE", afterCheck.anomalyDetail)
    }

    @Test
    fun `GPS recovery clears stale state and restores data quality`() = runBlocking {
        val serviceController = Robolectric.buildService(NocturneTrackingService::class.java)
        val service = serviceController.create().get()

        service.setLastAcceptedFixElapsedRealtimeForTest(100_000L)

        // Trigger stale at t = 170_000
        fakeClock.currentElapsedRealtime = 170_000L
        service.processWatchdogCheckSequentially(170_000L, service.getFixGenerationForTest())
        assertTrue(database.journeyDao().getActiveJourneyDirect()!!.gpsStale)

        // New valid GPS fix arrives at t = 175_000
        fakeClock.currentElapsedRealtime = 175_000L
        val freshFix = RawLocationFix(
            lat = 40.7000,
            lon = -73.9500,
            speedKmh = 60.0,
            accuracyMeters = 15f,
            timestamp = System.currentTimeMillis()
        )

        service.processLocationFixSequentially(freshFix, 175_000L)

        val recoveredJourney = database.journeyDao().getActiveJourneyDirect()!!
        assertFalse("GPS stale flag must be cleared on recovery", recoveredJourney.gpsStale)
        assertFalse("isMarkedStale flag must be cleared", service.isMarkedStaleForTest())
        assertEquals("Monotonic fix timestamp must be updated", 175_000L, service.getLastAcceptedFixElapsedRealtime())
        assertTrue("Confidence must be restored", recoveredJourney.confidence != ConfidenceLevel.UNRELIABLE)
    }

    @Test
    fun `GPS recovery cannot be overwritten by an older watchdog operation (race protection)`() = runBlocking {
        val serviceController = Robolectric.buildService(NocturneTrackingService::class.java)
        val service = serviceController.create().get()

        service.setLastAcceptedFixElapsedRealtimeForTest(100_000L)

        // Watchdog snapshots generation 0 at t = 170_000
        val staleSnapshotGen = service.getFixGenerationForTest()

        // Fresh GPS fix arrives before watchdog check executes, incrementing generation
        fakeClock.currentElapsedRealtime = 172_000L
        val freshFix = RawLocationFix(
            lat = 40.7200,
            lon = -73.9600,
            speedKmh = 65.0,
            accuracyMeters = 12f,
            timestamp = System.currentTimeMillis()
        )
        service.processLocationFixSequentially(freshFix, 172_000L)

        // Stale watchdog check now attempts to execute with older snapshot generation
        service.processWatchdogCheckSequentially(175_000L, generationSnapshot = staleSnapshotGen)

        // Assert that fresh GPS state was NOT overwritten by older stale check!
        val currentJourney = database.journeyDao().getActiveJourneyDirect()!!
        assertFalse("Stale watchdog must not overwrite fresh recovery", currentJourney.gpsStale)
        assertEquals(40.7200, currentJourney.lastKnownLat!!, 0.0001)
    }

    @Test
    fun `missing initial location fixes safely use start time reference`() = runBlocking {
        val serviceController = Robolectric.buildService(NocturneTrackingService::class.java)
        val service = serviceController.create().get()

        // Start tracking without any prior fixes at t = 50_000
        fakeClock.currentElapsedRealtime = 50_000L
        service.setLastAcceptedFixElapsedRealtimeForTest(50_000L)

        // Check at 40s after start (t = 90_000): not stale
        fakeClock.currentElapsedRealtime = 90_000L
        service.processWatchdogCheckSequentially(90_000L, service.getFixGenerationForTest())
        assertFalse(database.journeyDao().getActiveJourneyDirect()!!.gpsStale)

        // Check at 65s after start (t = 115_000): triggers stale
        fakeClock.currentElapsedRealtime = 115_000L
        service.processWatchdogCheckSequentially(115_000L, service.getFixGenerationForTest())
        assertTrue("Initial GPS silence must trigger stale state after 60s from start", database.journeyDao().getActiveJourneyDirect()!!.gpsStale)
    }
}
