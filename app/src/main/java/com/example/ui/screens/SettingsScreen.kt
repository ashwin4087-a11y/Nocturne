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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ThemePreference
import com.example.ui.components.DraftingGridBackground
import com.example.ui.components.HardShadowBox
import com.example.ui.components.NocturneBrandLogo
import com.example.ui.components.PerforatedTicketSlip
import com.example.ui.components.TactileButton
import com.example.ui.components.TricolorStripe
import com.example.ui.theme.NocturneTheme

@Composable
fun SettingsScreen(
    currentTheme: ThemePreference,
    defaultBufferMinutes: Int,
    onSelectTheme: (ThemePreference) -> Unit,
    onSelectDefaultBuffer: (Int) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = NocturneTheme.colors
    val bufferOptions = listOf(15, 20, 30, 45, 60)

    DraftingGridBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button")
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
                        text = "SETTINGS & PRIVACY",
                        color = theme.ink,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "SYSTEM PREFERENCES & AUDIT CONTROLS",
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Appearance Theme Preference
                item {
                    HardShadowBox(backgroundColor = theme.surface) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DarkMode,
                                    contentDescription = null,
                                    tint = theme.navy,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "APPEARANCE MODE",
                                    color = theme.ink,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Select high-contrast drafting canvas (Light), night transit instrument panel (Dark), or match Android device setting.",
                                color = theme.muted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val themes = listOf(
                                    ThemePreference.SYSTEM to "SYSTEM",
                                    ThemePreference.LIGHT to "LIGHT",
                                    ThemePreference.DARK to "DARK"
                                )

                                themes.forEach { (pref, label) ->
                                    val isSelected = currentTheme == pref
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .background(
                                                if (isSelected) theme.navy else theme.bg,
                                                RoundedCornerShape(2.dp)
                                            )
                                            .border(
                                                1.5.dp,
                                                if (isSelected) theme.navyDeep else theme.border,
                                                RoundedCornerShape(2.dp)
                                            )
                                            .clickable { onSelectTheme(pref) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.White else theme.ink,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Default Wake-Up Buffer
                item {
                    HardShadowBox(backgroundColor = theme.surface) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = theme.warnText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "DEFAULT WAKE-UP BUFFER",
                                    color = theme.ink,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Pre-selected buffer for new journeys (default: 30 minutes).",
                                color = theme.muted,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                bufferOptions.forEach { buf ->
                                    val isSelected = buf == defaultBufferMinutes
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .background(
                                                if (isSelected) theme.navy else theme.bg,
                                                RoundedCornerShape(2.dp)
                                            )
                                            .border(
                                                1.5.dp,
                                                if (isSelected) theme.navyDeep else theme.border,
                                                RoundedCornerShape(2.dp)
                                            )
                                            .clickable { onSelectDefaultBuffer(buf) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${buf}m",
                                            color = if (isSelected) Color.White else theme.ink,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Data Privacy & Deletion
                item {
                    HardShadowBox(backgroundColor = theme.surface) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = theme.okText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "DATA LOCALITY & PRIVACY",
                                    color = theme.ink,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "All GPS observations, journeys, and ETA calculations remain strictly on your device in a local Room database. No server upload occurs.",
                                color = theme.muted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            TactileButton(
                                text = "DELETE ALL JOURNEY HISTORY",
                                onClick = onClearHistory,
                                isDanger = true,
                                icon = Icons.Default.Delete,
                                testTag = "clear_history_button",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Honest Transparency Card
                item {
                    PerforatedTicketSlip {
                        Column {
                            Text(
                                text = "PRODUCT PRINCIPLES & LIMITATIONS",
                                color = theme.muted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• NOCTURNE is an assistive transit tool, not an absolute guarantee of arrival or personal safety.\n" +
                                        "• The core local alarm never requires a backend connection or network connectivity to trigger.\n" +
                                        "• When GPS is degraded, the app transparently communicates uncertainty rather than showing misleading precision.\n" +
                                        "• If exact alarm permissions are restricted by Android OS, alarms fallback to inexact delivery and will alert via high-priority heads-up notification upon location threshold entry.",
                                color = theme.ink,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Official NOCTURNE Brand Identification Card
                item {
                    HardShadowBox(backgroundColor = theme.surface) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NocturneBrandLogo(
                                size = 48.dp,
                                onDarkSurface = theme.surface.luminance() < 0.5f,
                                contentDescription = "NOCTURNE Official Symbol"
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "NOCTURNE v1.0",
                                    color = theme.ink,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Trust-First Analog Transit Intelligence\nOffline-ready local positioning engine",
                                    color = theme.muted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
