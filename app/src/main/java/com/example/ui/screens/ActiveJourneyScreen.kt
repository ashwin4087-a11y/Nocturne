package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AnomalyType
import com.example.data.model.ConfidenceLevel
import com.example.data.model.JourneyEntity
import com.example.data.model.SimulationScenario
import com.example.ui.components.DraftingGridBackground
import com.example.ui.components.DualNeedleGauge
import com.example.ui.components.HardShadowBox
import com.example.ui.components.HazardStripeBanner
import com.example.ui.components.NocturneLiveJourneyMap
import com.example.ui.components.OdometerDrums
import com.example.ui.components.RouteDeviationBanner
import com.example.ui.components.SmartTicketImportSheet
import com.example.ui.components.StatusStamp
import com.example.ui.components.TactileButton
import com.example.ui.components.TricolorStripe
import com.example.ui.theme.NocturneTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActiveJourneyScreen(
    journey: JourneyEntity,
    currentScenario: SimulationScenario,
    onBack: () -> Unit,
    onEndJourney: () -> Unit,
    onSelectScenario: (SimulationScenario) -> Unit,
    onTriggerTestAlarm: () -> Unit,
    isExactAlarmPermitted: Boolean = true,
    onConfirmImportedTicket: ((
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        wakeUpBufferMinutes: Int,
        transportMode: String,
        pnr: String?,
        scheduledArrivalMs: Long?,
        originName: String?
    ) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val theme = NocturneTheme.colors

    val isApprox = journey.confidence != ConfidenceLevel.HIGH || journey.isVehicleStationary
    val isAnomaly = journey.anomalyType != AnomalyType.NONE || journey.gpsStale

    val lastFixTimeStr = if (journey.lastFixTimestamp > 0) {
        val diffSec = (System.currentTimeMillis() - journey.lastFixTimestamp) / 1000
        if (diffSec < 60) "${diffSec}s ago" else "${diffSec / 60}m ago"
    } else {
        "Awaiting fix"
    }

    val scheduledAlarmStr = if (journey.targetScheduledAlarmMs > 0) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(journey.targetScheduledAlarmMs))
    } else {
        "Pending calculation"
    }

    var showTicketSheet by remember { mutableStateOf(false) }
    val polylinePoints = remember(journey.destinationLat, journey.destinationLon) {
        com.example.data.repository.ActiveRouteHolder.activePolylinePoints
    }

    DraftingGridBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("active_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = theme.ink
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = journey.destinationName.uppercase(),
                                color = theme.ink,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (journey.isRouteAware) "ROUTE-AWARE SURVEILLANCE (${journey.routeCircuityFactor}x)" else "DIRECT SURVEILLANCE",
                                color = theme.muted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    StatusStamp(
                        text = if (journey.alarmTriggered) "ALARM FIRED" else "ARMED ($scheduledAlarmStr)",
                        icon = Icons.Default.Alarm,
                        color = if (journey.alarmTriggered) theme.warnText else theme.navy,
                        bgColor = if (journey.alarmTriggered) theme.warnBg else theme.bg
                    )
                }

                TricolorStripe()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Exact Alarm Permission Warning Banner
                    if (!isExactAlarmPermitted) {
                        item {
                            HazardStripeBanner(
                                text = "EXACT ALARMS RESTRICTED: Device settings may delay wake-up alerts in battery-saver mode. Grant 'Alarms & Reminders' in App Settings for guaranteed precision.",
                                isCritical = true
                            )
                        }
                    }

                    // 1. Visual Route Deviation Alert Component
                    item {
                        RouteDeviationBanner(journey = journey)
                    }

                    // 2. Interactive Live Journey Map
                    item {
                        NocturneLiveJourneyMap(
                            journey = journey,
                            polylinePoints = polylinePoints
                        )
                    }

                    // 3. Ticket Timetable & Booking Details (if present)
                    if (journey.ticketPnr != null || journey.scheduledArrivalTimestamp != null || journey.originName != null) {
                        item {
                            TicketDetailsCard(journey = journey)
                        }
                    }

                    // GPS Watchdog Stale Banner
                    if (journey.gpsStale) {
                        item {
                            HazardStripeBanner(
                                text = "WATCHDOG: GPS FIX STALE (${((System.currentTimeMillis() - journey.lastFixTimestamp)/60000)}m without fix) — ETA interval widened defensively.",
                                isCritical = true
                            )
                        }
                    } else if (isAnomaly) {
                        item {
                            HazardStripeBanner(
                                text = journey.anomalyDetail ?: "Transit anomaly detected. Travel time recalibrating.",
                                isCritical = journey.anomalyType == AnomalyType.PERSISTENT_ROUTE_DEVIATION
                            )
                        }
                    }

                    // HERO PANEL: The Precision Instrument Core
                    item {
                        HardShadowBox(
                            backgroundColor = theme.navyDeep,
                            borderColor = theme.warn,
                            offsetDp = 5.dp
                        ) {
                            Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Status Stamp Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (journey.isVehicleStationary) {
                                    StatusStamp(
                                        text = "STOPPED (${journey.stationaryDurationMs / 60000}M)",
                                        icon = Icons.Default.PauseCircle,
                                        color = theme.warnText,
                                        bgColor = theme.warnBg
                                    )
                                } else {
                                    StatusStamp(
                                        text = if (journey.lastKnownAccuracyMeters > 50) "GPS WEAK" else "TRACKING",
                                        icon = if (journey.lastKnownAccuracyMeters > 50) Icons.Default.GpsNotFixed else Icons.Default.GpsFixed,
                                        color = if (journey.lastKnownAccuracyMeters > 50) theme.warnText else theme.okText,
                                        bgColor = if (journey.lastKnownAccuracyMeters > 50) theme.warnBg else theme.okBg
                                    )
                                }

                                StatusStamp(
                                    text = "${journey.confidence.name} CONFIDENCE",
                                    icon = if (journey.confidence == ConfidenceLevel.HIGH) Icons.Default.GpsFixed else Icons.Default.Warning,
                                    color = when (journey.confidence) {
                                        ConfidenceLevel.HIGH -> theme.okText
                                        ConfidenceLevel.MEDIUM -> theme.warnText
                                        ConfidenceLevel.LOW, ConfidenceLevel.UNRELIABLE -> theme.warnText
                                    },
                                    bgColor = when (journey.confidence) {
                                        ConfidenceLevel.HIGH -> theme.okBg
                                        ConfidenceLevel.MEDIUM -> theme.warnBg
                                        ConfidenceLevel.LOW, ConfidenceLevel.UNRELIABLE -> theme.warnBg
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // ODOMETER MECHANICAL DRUMS
                            OdometerDrums(
                                totalMinutes = journey.etaMinutesPoint,
                                isApprox = isApprox
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // 180-DEGREE DUAL NEEDLE GAUGE
                            DualNeedleGauge(
                                currentMinutes = journey.etaMinutesPoint,
                                upperMinutes = journey.etaMinutesUpper,
                                bufferMinutes = journey.wakeUpBufferMinutes
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Range Explanation
                            Text(
                                text = "Estimated arrival window: ~${journey.etaMinutesPoint} min (between ${journey.etaMinutesLower}m and ${journey.etaMinutesUpper}m)",
                                color = Color(0xFFDCE5ED),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Telemetry Metrics Grid
                item {
                    HardShadowBox(backgroundColor = theme.surface) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "ROUTE-AWARE TELEMETRY & DURABLE STATE",
                                color = theme.muted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "ROUTE DISTANCE", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = "${(journey.remainingDistanceKm * 10).toInt() / 10.0} km",
                                        color = theme.ink,
                                        fontSize = 17.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${(journey.straightLineDistanceKm * 10).toInt() / 10.0} km direct",
                                        color = theme.muted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = journey.routeDistanceSource.replace("_", " "),
                                        color = if (journey.routeDistanceSource == "MAPPED_ROUTE") Color(0xFF2E7D32) else theme.navy,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "SPEED STATE", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = if (journey.isVehicleStationary) "0 km/h" else "${journey.lastKnownSpeedKmh.toInt()} km/h",
                                        color = if (journey.isVehicleStationary) theme.warnText else theme.ink,
                                        fontSize = 17.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (journey.isVehicleStationary) "Halted ${journey.stationaryDurationMs / 60000}m" else "Cruising",
                                        color = if (journey.isVehicleStationary) theme.warnText else theme.okText,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "LAST FIX", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = lastFixTimeStr,
                                        color = if (journey.gpsStale) theme.warnText else theme.ink,
                                        fontSize = 17.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "±${journey.lastKnownAccuracyMeters.toInt()}m accuracy",
                                        color = theme.muted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "WAKE-UP BUFFER", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = "${journey.wakeUpBufferMinutes} min prior",
                                        color = theme.warnText,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "SCHEDULED ALARM", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = scheduledAlarmStr,
                                        color = theme.ink,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val context = LocalContext.current
                            val isExactRestricted = remember(context) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                                    am?.canScheduleExactAlarms() == false
                                } else {
                                    false
                                }
                            } || com.example.service.NocturneAlarmManager.lastScheduleStatus == com.example.service.AlarmScheduleStatus.INEXACT_FALLBACK

                            if (isExactRestricted) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFFFF3CD), RoundedCornerShape(4.dp))
                                        .border(1.dp, Color(0xFFFFEEBA), RoundedCornerShape(4.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Alarm warning",
                                        tint = Color(0xFF856404),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "EXACT ALARM RESTRICTED: Standard wake-up fallback active. Wake-up may be delayed by system battery saving (Doze). Grant 'Alarms & reminders' in system settings for pinpoint timing.",
                                        color = Color(0xFF856404),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Deterministic Simulation & Test Harness
                item {
                    HardShadowBox(backgroundColor = theme.surface) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = theme.navy,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "TRANSIT SIMULATOR & TEST HARNESS",
                                    color = theme.ink,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Test delayed stops, route diversions, early arrivals, and GPS blackouts deterministically:",
                                color = theme.muted,
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Scenario Selector Grid
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val scenarios = listOf(
                                    SimulationScenario.REAL_GPS to "Real Hardware GPS",
                                    SimulationScenario.STEADY_CRUISE to "Steady Highway Cruise (65 km/h)",
                                    SimulationScenario.EXTENDED_REST_STOP to "Extended Stop / Unscheduled Halt (0 km/h)",
                                    SimulationScenario.EARLY_FAST_RUN to "Early High-Speed Run (105 km/h)",
                                    SimulationScenario.OFF_ROUTE_DIVERSION to "Route Deviation / Detour",
                                    SimulationScenario.GPS_TUNNEL_BLACKOUT to "GPS Tunnel Blackout (Tests Watchdog)"
                                )

                                scenarios.forEach { (scen, label) ->
                                    val isSelected = currentScenario == scen
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isSelected) theme.navyDeep else theme.bg,
                                                RoundedCornerShape(2.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) theme.warn else theme.border,
                                                RoundedCornerShape(2.dp)
                                            )
                                            .clickable { onSelectScenario(scen) }
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = (if (isSelected) "● " else "○ ") + label,
                                            color = if (isSelected) Color.White else theme.ink,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Test Trigger Alarm Button
                            TactileButton(
                                text = "FIRE TEST WAKE-UP ALARM NOW",
                                onClick = onTriggerTestAlarm,
                                icon = Icons.Default.NotificationsActive,
                                testTag = "test_alarm_button",
                                isSecondary = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Primary End Journey Button
                item {
                    TactileButton(
                        text = "TERMINATE & END JOURNEY",
                        onClick = onEndJourney,
                        isDanger = true,
                        icon = Icons.Default.Close,
                        testTag = "terminate_journey_button",
                        minHeight = 54.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Floating '+' Smart Ticket Import Action
        FloatingActionButton(
            onClick = { showTicketSheet = true },
            containerColor = theme.warn,
            contentColor = theme.navyDeep,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp)
                .testTag("smart_ticket_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Smart Ticket Import",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "TICKET",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        if (showTicketSheet && onConfirmImportedTicket != null) {
            SmartTicketImportSheet(
                activeJourney = journey,
                onDismiss = { showTicketSheet = false },
                onConfirmTicket = { destName, lat, lon, buffer, mode, pnr, schedMs, origin ->
                    showTicketSheet = false
                    onConfirmImportedTicket(destName, lat, lon, buffer, mode, pnr, schedMs, origin)
                }
            )
        }
    }
}
}

@Composable
private fun TicketDetailsCard(journey: JourneyEntity) {
    val theme = NocturneTheme.colors
    HardShadowBox(backgroundColor = theme.surface) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ConfirmationNumber,
                        contentDescription = null,
                        tint = theme.warn,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "IMPORTED TICKET DETAILS",
                        color = theme.ink,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                if (journey.ticketPnr != null) {
                    Text(
                        text = "PNR: ${journey.ticketPnr}",
                        color = theme.warnText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (journey.originName != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "ORIGIN / BOARDING", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text(text = journey.originName, color = theme.ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "TRANSPORT MODE", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text(text = journey.transportMode, color = theme.ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                if (journey.scheduledArrivalTimestamp != null && journey.scheduledArrivalTimestamp > 0) {
                    val schedStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(journey.scheduledArrivalTimestamp))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(text = "TIMETABLE ARRIVAL", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text(text = schedStr, color = theme.warnText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
