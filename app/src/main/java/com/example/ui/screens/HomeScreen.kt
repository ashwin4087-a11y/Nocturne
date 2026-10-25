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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JourneyEntity
import com.example.data.model.JourneyStatus
import com.example.ui.components.DraftingGridBackground
import com.example.ui.components.HardShadowBox
import com.example.ui.components.NocturneBrandLogo
import com.example.ui.components.PerforatedTicketSlip
import com.example.ui.components.SmartTicketImportSheet
import com.example.ui.components.StatusStamp
import com.example.ui.components.TactileButton
import com.example.ui.components.TricolorStripe
import com.example.ui.theme.NocturneTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    activeJourney: JourneyEntity?,
    journeyHistory: List<JourneyEntity>,
    onNavigateToPlan: () -> Unit,
    onNavigateToActive: () -> Unit,
    onNavigateToReadiness: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onEndJourney: () -> Unit,
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
    var showTicketSheet by remember { mutableStateOf(false) }

    DraftingGridBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surface)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NocturneBrandLogo(
                        size = 36.dp,
                        onDarkSurface = theme.surface.luminance() < 0.5f,
                        contentDescription = "NOCTURNE Official Logo"
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "NOCTURNE",
                            color = theme.ink,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "TRANSIT CALIBRATION & ALARM",
                            color = theme.muted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onNavigateToReadiness,
                        modifier = Modifier.testTag("readiness_check_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Readiness Checklist",
                            tint = theme.navy
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = theme.navy
                        )
                    }
                }
            }

            // Signature Tricolor Stripe
            TricolorStripe()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Active Journey Banner if tracking is currently active
                if (activeJourney != null && activeJourney.status == JourneyStatus.ACTIVE) {
                    item {
                        ActiveJourneyTicketCard(
                            journey = activeJourney,
                            onOpenDashboard = onNavigateToActive,
                            onEndJourney = onEndJourney
                        )
                    }
                }

                // Primary Action: Plan new journey
                item {
                    HardShadowBox(
                        backgroundColor = theme.surface,
                        borderColor = theme.navyDeep
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "TRANSIT MISSION",
                                    color = theme.muted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                StatusStamp(
                                    text = "OFFLINE READY",
                                    icon = Icons.Default.CheckCircle,
                                    color = theme.okText,
                                    bgColor = theme.okBg
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Adaptive Destination Alarm",
                                color = theme.ink,
                                fontSize = 19.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Track speed & remaining distance on long intercity trips. Wake up based on real estimated travel time, not a fragile static clock.",
                                color = theme.muted,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            TactileButton(
                                text = "Set Destination & Buffer",
                                onClick = onNavigateToPlan,
                                icon = Icons.Default.PlayArrow,
                                testTag = "plan_journey_button",
                                backgroundColor = theme.navy,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Readiness summary slip
                item {
                    PerforatedTicketSlip {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = theme.navy,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "PRE-FLIGHT READINESS",
                                        color = theme.ink,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "CHECK >",
                                    color = theme.navy,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onNavigateToReadiness() }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Inspect GPS, background location, exact-alarm permissions, and battery optimization before sleeping on board.",
                                color = theme.muted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Journey History
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = theme.muted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "JOURNEY LOGBOOK (${journeyHistory.size})",
                            color = theme.muted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                if (journeyHistory.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(theme.surface, RoundedCornerShape(3.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No past trips recorded. Start a journey to log telemetry and arrival alarms locally.",
                                color = theme.muted,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else {
                    items(journeyHistory.take(5)) { trip ->
                        PastJourneyItem(journey = trip)
                    }
                }
            }
        }

        // Floating '+' Smart Ticket Import Action Button
        FloatingActionButton(
            onClick = { showTicketSheet = true },
            containerColor = theme.warn,
            contentColor = theme.navyDeep,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("home_ticket_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Smart Ticket Import",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "IMPORT TICKET",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        if (showTicketSheet && onConfirmImportedTicket != null) {
            SmartTicketImportSheet(
                activeJourney = activeJourney,
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
private fun ActiveJourneyTicketCard(
    journey: JourneyEntity,
    onOpenDashboard: () -> Unit,
    onEndJourney: () -> Unit
) {
    val theme = NocturneTheme.colors
    HardShadowBox(
        backgroundColor = theme.navyDeep,
        borderColor = theme.warn,
        offsetDp = 5.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusStamp(
                    text = "ACTIVE TRACKING",
                    icon = Icons.Default.GpsFixed,
                    color = theme.okText,
                    bgColor = theme.okBg
                )
                StatusStamp(
                    text = "ALARM ARMED",
                    icon = Icons.Default.Alarm,
                    color = theme.warnText,
                    bgColor = theme.warnBg
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = journey.destinationName.uppercase(),
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "ETA ESTIMATE",
                        color = Color(0xFF9DB0C4),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "~${journey.etaMinutesPoint} min (${journey.etaMinutesLower}-${journey.etaMinutesUpper}m)",
                        color = theme.warn,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "WAKE BUFFER",
                        color = Color(0xFF9DB0C4),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${journey.wakeUpBufferMinutes} min prior",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                TactileButton(
                    text = "Live Dashboard",
                    onClick = onOpenDashboard,
                    testTag = "open_dashboard_button",
                    backgroundColor = theme.warn,
                    textColor = theme.navyDeep,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                TactileButton(
                    text = "End",
                    onClick = onEndJourney,
                    testTag = "home_end_journey_button",
                    isDanger = true,
                    modifier = Modifier.width(90.dp)
                )
            }
        }
    }
}

@Composable
private fun PastJourneyItem(journey: JourneyEntity) {
    val theme = NocturneTheme.colors
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        .format(Date(journey.startTimestamp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surface, RoundedCornerShape(2.dp))
            .border(1.dp, theme.border, RoundedCornerShape(2.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = journey.destinationName,
                    color = theme.ink,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateStr,
                    color = theme.muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            StatusStamp(
                text = journey.status.name,
                icon = if (journey.status == JourneyStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.Warning,
                color = if (journey.status == JourneyStatus.COMPLETED) theme.okText else theme.muted,
                bgColor = if (journey.status == JourneyStatus.COMPLETED) theme.okBg else theme.bg
            )
        }
    }
}
