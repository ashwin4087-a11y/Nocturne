package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DraftingGridBackground
import com.example.ui.components.HardShadowBox
import com.example.ui.components.NocturneBrandLogo
import com.example.ui.components.PerforatedTicketSlip
import com.example.ui.components.StatusStamp
import com.example.ui.components.TactileButton
import com.example.ui.components.TricolorStripe
import com.example.ui.theme.NocturneTheme

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = NocturneTheme.colors

    DraftingGridBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surface)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        text = "TRANSIT CALIBRATION ONBOARDING",
                        color = theme.muted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            TricolorStripe()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    PerforatedTicketSlip {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatusStamp(
                                    text = "HONEST TRANSIT INTELLIGENCE",
                                    icon = Icons.Default.CheckCircle,
                                    color = theme.okText,
                                    bgColor = theme.okBg
                                )
                                NocturneBrandLogo(
                                    size = 40.dp,
                                    onDarkSurface = theme.surface.luminance() < 0.5f,
                                    contentDescription = "NOCTURNE Symbol"
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Arrive Rested and Informed",
                                color = theme.ink,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Conventional alarms wake you at fixed times regardless of delays. NOCTURNE continuously adapts to real velocity, distance, and stops, alerting you ~30 minutes prior to arrival.",
                                color = theme.muted,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                item {
                    OnboardingPointCard(
                        icon = Icons.Default.GpsFixed,
                        title = "Why Location Access Is Needed",
                        description = "Calculates remaining route distance and velocity toward your destination. Tracking runs completely on-device."
                    )
                }

                item {
                    OnboardingPointCard(
                        icon = Icons.Default.Alarm,
                        title = "Background Location While Sleeping",
                        description = "When you lock the screen or sleep on board, an ongoing foreground service continues monitoring the arrival threshold."
                    )
                }

                item {
                    OnboardingPointCard(
                        icon = Icons.Default.WifiOff,
                        title = "Offline-First Design",
                        description = "Once configured, the alarm does not depend on internet or cell towers. Satellite GPS and local physics continue offline."
                    )
                }

                item {
                    OnboardingPointCard(
                        icon = Icons.Default.BatteryAlert,
                        title = "Battery & OEM Power Optimizations",
                        description = "Some manufacturers aggressively terminate background tasks. Keep your phone charging if possible, and whitelist NOCTURNE from battery savers."
                    )
                }

                item {
                    TactileButton(
                        text = "I UNDERSTAND — CONTINUE TO SETUP",
                        onClick = onContinue,
                        testTag = "onboarding_continue_button",
                        backgroundColor = theme.navy,
                        minHeight = 52.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPointCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    val theme = NocturneTheme.colors
    HardShadowBox(backgroundColor = theme.surface) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(theme.bg, RoundedCornerShape(2.dp))
                    .border(1.dp, theme.border, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = theme.navy,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = theme.ink,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = theme.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
