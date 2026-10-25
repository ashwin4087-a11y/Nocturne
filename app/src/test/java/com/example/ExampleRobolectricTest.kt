package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.NocturneDatabase
import com.example.data.model.JourneyEntity
import com.example.data.model.JourneyStatus
import com.example.data.model.ThemePreference
import com.example.data.repository.JourneyRepository
import com.example.data.repository.UserSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context verifies NOCTURNE app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NOCTURNE", appName)
    }

    @Test
    fun `Room database persists and cancels active journeys correctly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = NocturneDatabase.getInstance(context)
        val repo = JourneyRepository(db.journeyDao(), db.observationDao())

        // Start journey
        val journeyId = repo.startNewJourney(
            destinationName = "Grand Central Terminal",
            destinationLat = 40.7527,
            destinationLon = -73.9772,
            wakeUpBufferMinutes = 30
        )
        assertTrue(journeyId > 0)

        val active = repo.getActiveJourneyDirect()
        assertNotNull(active)
        assertEquals("Grand Central Terminal", active?.destinationName)
        assertEquals(JourneyStatus.ACTIVE, active?.status)
        assertEquals(30, active?.wakeUpBufferMinutes)

        // Cancel / End journey
        repo.endJourney(journeyId, completed = true)
        val afterEnd = repo.getActiveJourneyDirect()
        assertNull("Active journey should be null after completion", afterEnd)

        // Clear history
        repo.clearHistory()
    }

    @Test
    fun `UserSettings repository persists theme preference and default buffer`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = UserSettingsRepository(context)

        settings.setThemePreference(ThemePreference.DARK)
        assertEquals(ThemePreference.DARK, settings.getThemePreference())

        settings.setDefaultBufferMinutes(45)
        assertEquals(45, settings.getDefaultBufferMinutes())
    }

    @Test
    fun `launch MainActivity with Robolectric`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
        controller.create().start().resume().visible()
        val activity = controller.get()
        assertNotNull(activity)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
