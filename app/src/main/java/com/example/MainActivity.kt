package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.db.NocturneDatabase
import com.example.data.model.ThemePreference
import com.example.data.repository.JourneyRepository
import com.example.data.repository.UserSettingsRepository
import com.example.ui.screens.ActiveJourneyScreen
import com.example.ui.screens.DestinationPickerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ReadinessCheckScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NocturneViewModel

enum class ScreenState {
    ONBOARDING,
    HOME,
    PLAN_DESTINATION,
    ACTIVE_JOURNEY,
    READINESS_CHECK,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: NocturneViewModel by viewModels {
        val app = application as? NocturneApplication
        val journeyRepo = app?.journeyRepository ?: run {
            val db = NocturneDatabase.getInstance(applicationContext)
            JourneyRepository(db.journeyDao(), db.observationDao())
        }
        val settingsRepo = app?.userSettingsRepository ?: UserSettingsRepository(applicationContext)
        NocturneViewModel.Factory(
            applicationContext,
            journeyRepo,
            settingsRepo
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedText = if (intent?.action == android.content.Intent.ACTION_SEND) {
            intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
        } else null

        setContent {
            val themePref by viewModel.themePreference.collectAsState()
            val isDark = when (themePref) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }

            MyApplicationTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NocturneApp(viewModel = viewModel, initialSharedTicketText = sharedText)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshReadiness()
    }
}

@Composable
fun NocturneApp(
    viewModel: NocturneViewModel,
    initialSharedTicketText: String? = null
) {
    val activeJourney by viewModel.activeJourney.collectAsState()
    val journeyHistory by viewModel.journeyHistory.collectAsState()
    val readinessItems by viewModel.readinessState.collectAsState()
    val themePref by viewModel.themePreference.collectAsState()
    val currentScenario by viewModel.currentScenario.collectAsState()
    val isExactAlarmPermitted by viewModel.isExactAlarmPermitted.collectAsState()

    val initialScreen = if (viewModel.isOnboardingCompleted()) {
        if (activeJourney != null) ScreenState.ACTIVE_JOURNEY else ScreenState.HOME
    } else {
        ScreenState.ONBOARDING
    }

    var currentScreen by remember { mutableStateOf(initialScreen) }

    val handleConfirmImportedTicket: (String, Double, Double, Int, String, String?, Long?, String?) -> Unit = { name, lat, lon, buffer, mode, pnr, schedMs, origin ->
        val circuity = when (mode.uppercase()) {
            "TRAIN" -> 1.15
            "CAR" -> 1.20
            else -> 1.25
        }
        viewModel.startJourney(
            destinationName = name,
            destinationLat = lat,
            destinationLon = lon,
            wakeUpBufferMinutes = buffer,
            circuityFactor = circuity,
            routeDistanceSource = "ESTIMATED_ROUTE",
            referenceRouteSummary = "Imported $mode: $name",
            transportMode = mode,
            ticketPnr = pnr,
            scheduledArrivalTimestamp = schedMs,
            originName = origin
        )
        currentScreen = ScreenState.ACTIVE_JOURNEY
    }

    when (currentScreen) {
        ScreenState.ONBOARDING -> {
            OnboardingScreen(
                onContinue = {
                    viewModel.setOnboardingCompleted(true)
                    currentScreen = ScreenState.HOME
                }
            )
        }

        ScreenState.HOME -> {
            HomeScreen(
                activeJourney = activeJourney,
                journeyHistory = journeyHistory,
                onNavigateToPlan = { currentScreen = ScreenState.PLAN_DESTINATION },
                onNavigateToActive = { currentScreen = ScreenState.ACTIVE_JOURNEY },
                onNavigateToReadiness = { currentScreen = ScreenState.READINESS_CHECK },
                onNavigateToSettings = { currentScreen = ScreenState.SETTINGS },
                onEndJourney = { viewModel.endJourney(completed = false) },
                onConfirmImportedTicket = handleConfirmImportedTicket
            )
        }

        ScreenState.PLAN_DESTINATION -> {
            BackHandler { currentScreen = ScreenState.HOME }
            DestinationPickerScreen(
                defaultBufferMinutes = viewModel.getDefaultBufferMinutes(),
                viewModel = viewModel,
                onBack = { currentScreen = ScreenState.HOME },
                onStartJourney = { name, lat, lon, buffer, circuity, distanceSource, routeSummary ->
                    viewModel.startJourney(
                        destinationName = name,
                        destinationLat = lat,
                        destinationLon = lon,
                        wakeUpBufferMinutes = buffer,
                        circuityFactor = circuity,
                        routeDistanceSource = distanceSource,
                        referenceRouteSummary = routeSummary
                    )
                    currentScreen = ScreenState.ACTIVE_JOURNEY
                }
            )
        }

        ScreenState.ACTIVE_JOURNEY -> {
            BackHandler { currentScreen = ScreenState.HOME }
            if (activeJourney != null) {
                ActiveJourneyScreen(
                    journey = activeJourney!!,
                    currentScenario = currentScenario,
                    onBack = { currentScreen = ScreenState.HOME },
                    onEndJourney = {
                        viewModel.endJourney(completed = false)
                        currentScreen = ScreenState.HOME
                    },
                    onSelectScenario = { scenario ->
                        viewModel.setSimulationScenario(scenario)
                    },
                    onTriggerTestAlarm = {
                        viewModel.triggerTestAlarm()
                    },
                    isExactAlarmPermitted = isExactAlarmPermitted,
                    onConfirmImportedTicket = handleConfirmImportedTicket
                )
            } else {
                HomeScreen(
                    activeJourney = null,
                    journeyHistory = journeyHistory,
                    onNavigateToPlan = { currentScreen = ScreenState.PLAN_DESTINATION },
                    onNavigateToActive = { currentScreen = ScreenState.ACTIVE_JOURNEY },
                    onNavigateToReadiness = { currentScreen = ScreenState.READINESS_CHECK },
                    onNavigateToSettings = { currentScreen = ScreenState.SETTINGS },
                    onEndJourney = {},
                    onConfirmImportedTicket = handleConfirmImportedTicket
                )
            }
        }

        ScreenState.READINESS_CHECK -> {
            BackHandler { currentScreen = ScreenState.HOME }
            ReadinessCheckScreen(
                readinessItems = readinessItems,
                onRefresh = { viewModel.refreshReadiness() },
                onBack = { currentScreen = ScreenState.HOME }
            )
        }

        ScreenState.SETTINGS -> {
            BackHandler { currentScreen = ScreenState.HOME }
            SettingsScreen(
                currentTheme = themePref,
                defaultBufferMinutes = viewModel.getDefaultBufferMinutes(),
                onSelectTheme = { viewModel.setTheme(it) },
                onSelectDefaultBuffer = { viewModel.setDefaultBufferMinutes(it) },
                onClearHistory = { viewModel.clearHistory() },
                onBack = { currentScreen = ScreenState.HOME }
            )
        }
    }
}
