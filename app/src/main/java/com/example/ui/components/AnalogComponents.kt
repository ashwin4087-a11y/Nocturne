package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NocturneTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Signature 4dp Tricolor Stripe (Navy 45% / Amber 35% / Green 20%)
 */
@Composable
fun TricolorStripe(modifier: Modifier = Modifier) {
    val theme = NocturneTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .semantics { contentDescription = "Nocturne brand accent stripe" }
    ) {
        Box(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight()
                .background(theme.navy)
        )
        Box(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
                .background(theme.warn)
        )
        Box(
            modifier = Modifier
                .weight(0.20f)
                .fillMaxHeight()
                .background(theme.ok)
        )
    }
}

/**
 * Two-layer Hard Shadow container with zero-blur offset layer.
 */
@Composable
fun HardShadowBox(
    modifier: Modifier = Modifier,
    offsetDp: Dp = 4.dp,
    borderColor: Color = NocturneTheme.colors.border,
    backgroundColor: Color = NocturneTheme.colors.surface,
    isDanger: Boolean = false,
    content: @Composable () -> Unit
) {
    val theme = NocturneTheme.colors
    val shadowColor = if (isDanger) theme.danger else theme.shadowInk
    val actualBorder = if (isDanger) theme.danger else borderColor

    Box(modifier = modifier) {
        // Solid ink shadow layer behind
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = offsetDp, y = offsetDp)
                .background(shadowColor, RoundedCornerShape(3.dp))
        )
        // Card face on top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor, RoundedCornerShape(3.dp))
                .border(1.5.dp, actualBorder, RoundedCornerShape(3.dp))
        ) {
            content()
        }
    }
}

/**
 * Tactile Push Button with mechanical hard shadow and click displacement.
 */
@Composable
fun TactileButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "tactile_button",
    backgroundColor: Color = NocturneTheme.colors.navy,
    textColor: Color = NocturneTheme.colors.surface,
    isDanger: Boolean = false,
    isSecondary: Boolean = false,
    icon: ImageVector? = null,
    minHeight: Dp = 48.dp
) {
    val theme = NocturneTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val actualBg = when {
        isDanger -> theme.danger
        isSecondary -> theme.surface
        else -> backgroundColor
    }

    val actualText = when {
        isDanger -> theme.surface
        isSecondary -> theme.ink
        else -> textColor
    }

    val shadowOffset = if (isPressed) 1.5.dp else 3.5.dp
    val faceOffset = if (isPressed) 2.dp else 0.dp

    Box(
        modifier = modifier
            .testTag(testTag)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = text }
    ) {
        // Shadow layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(
                    if (isDanger) theme.hatch else theme.shadowInk,
                    RoundedCornerShape(3.dp)
                )
        )
        // Face button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(minHeight)
                .offset(x = faceOffset, y = faceOffset)
                .background(actualBg, RoundedCornerShape(3.dp))
                .border(
                    1.5.dp,
                    if (isSecondary) theme.border else theme.navyDeep,
                    RoundedCornerShape(3.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = actualText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text.uppercase(),
                    color = actualText,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/**
 * Rubber-ink status stamps with 2dp border, icon, and subtle tilt.
 */
@Composable
fun StatusStamp(
    text: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
    tiltAngle: Float = -1.2f
) {
    Box(
        modifier = modifier
            .rotate(tiltAngle)
            .background(bgColor, RoundedCornerShape(2.dp))
            .border(1.5.dp, color, RoundedCornerShape(2.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .semantics { contentDescription = "Status: $text" }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text.uppercase(),
                color = color,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
}

/**
 * Recessed mechanical Odometer Drums displaying remaining transit time (HH:MM).
 */
@Composable
fun OdometerDrums(
    totalMinutes: Int,
    isApprox: Boolean,
    modifier: Modifier = Modifier
) {
    val theme = NocturneTheme.colors
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val timeFormatted = String.format("%02d:%02d", hours, minutes)

    val plainLabel = if (isApprox) {
        "Approximately $hours hours and $minutes minutes remaining, estimate uncertain"
    } else {
        "$hours hours and $minutes minutes remaining"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.semantics { contentDescription = plainLabel }
    ) {
        Text(
            text = "ESTIMATED TRAVEL TIME REMAINING",
            color = theme.muted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Recessed Chamber Well
        Box(
            modifier = Modifier
                .background(theme.chamber, RoundedCornerShape(4.dp))
                .border(2.dp, theme.chamberEdge, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isApprox) {
                    Text(
                        text = "~",
                        color = theme.warn,
                        fontSize = 32.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                // Mechanical Drum Digits
                timeFormatted.forEach { char ->
                    if (char == ':') {
                        Text(
                            text = ":",
                            color = theme.warn,
                            fontSize = 34.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 3.dp)
                        )
                    } else {
                        OdometerDigitBox(digit = char)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "HRS",
                    color = theme.muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun OdometerDigitBox(digit: Char) {
    val theme = NocturneTheme.colors
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(width = 30.dp, height = 44.dp)
            .background(Color(0xFF030D17), RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF162B40), RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            color = Color(0xFFE8EEF4),
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 180-degree Radial Dual-Needle Gauge matching the NOCTURNE logo!
 * Solid needle = current ETA estimate
 * Dashed amber needle = projected upper bound ETA
 */
@Composable
fun DualNeedleGauge(
    currentMinutes: Int,
    upperMinutes: Int,
    bufferMinutes: Int,
    modifier: Modifier = Modifier
) {
    val theme = NocturneTheme.colors
    val maxGaugeMinutes = 180f // 3 hours max scale

    // Map minutes to angle: 180 degrees (left, 0 min) to 0 degrees (right, 180 min)
    // When remaining time is low (approaching destination), needle moves toward right (0 deg)
    val currentRatio = (currentMinutes.coerceIn(0, 180) / maxGaugeMinutes)
    val upperRatio = (upperMinutes.coerceIn(0, 180) / maxGaugeMinutes)
    val bufferRatio = (bufferMinutes.coerceIn(0, 180) / maxGaugeMinutes)

    // Sweep: 180 deg to 0 deg
    val currentAngle = 180f - (currentRatio * 180f)
    val upperAngle = 180f - (upperRatio * 180f)

    val animatedCurrentAngle by animateFloatAsState(
        targetValue = currentAngle,
        animationSpec = tween(durationMillis = 1000),
        label = "currentNeedle"
    )

    val animatedUpperAngle by animateFloatAsState(
        targetValue = upperAngle,
        animationSpec = tween(durationMillis = 1000),
        label = "upperNeedle"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "Transit ETA gauge. Current estimate: $currentMinutes minutes. Projected upper bound: $upperMinutes minutes. Alarm threshold at $bufferMinutes minutes."
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height - 12f
                val radius = size.height * 0.82f

                // Draw 180-degree Arch Arc
                val arcRect = Size(radius * 2, radius * 2)
                val arcTopLeft = Offset(cx - radius, cy - radius)

                // Outer thick arc
                drawArc(
                    color = Color(0xFF163C62),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcRect,
                    style = Stroke(width = 16f, cap = StrokeCap.Round)
                )

                // Safe threshold zone on the right (approaching buffer minutes)
                val bufferSweep = bufferRatio * 180f
                drawArc(
                    color = Color(0xFFF4B942),
                    startAngle = 0f,
                    sweepAngle = -bufferSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcRect,
                    style = Stroke(width = 16f, cap = StrokeCap.Butt)
                )

                // Gauge Ticks along the arc
                for (i in 0..12) {
                    val angleDeg = 180f - (i * 15f)
                    val rad = Math.toRadians(angleDeg.toDouble())
                    val tickLen = if (i % 3 == 0) 14f else 8f
                    val outerX = cx + (radius + 2f) * cos(rad).toFloat()
                    val outerY = cy - (radius + 2f) * sin(rad).toFloat()
                    val innerX = cx + (radius - tickLen) * cos(rad).toFloat()
                    val innerY = cy - (radius - tickLen) * sin(rad).toFloat()

                    drawLine(
                        color = Color(0xFFFFFFFF),
                        start = Offset(innerX, innerY),
                        end = Offset(outerX, outerY),
                        strokeWidth = if (i % 3 == 0) 4f else 2f
                    )
                }

                // Dashed Projected Needle (Amber #F4B942)
                val upperRad = Math.toRadians(animatedUpperAngle.toDouble())
                val upperNeedleLen = radius * 0.76f
                val upperX = cx + upperNeedleLen * cos(upperRad).toFloat()
                val upperY = cy - upperNeedleLen * sin(upperRad).toFloat()

                drawLine(
                    color = Color(0xFFF4B942),
                    start = Offset(cx, cy),
                    end = Offset(upperX, upperY),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )

                // Solid Current Needle (White #FFFFFF)
                val currentRad = Math.toRadians(animatedCurrentAngle.toDouble())
                val currentNeedleLen = radius * 0.88f
                val currentX = cx + currentNeedleLen * cos(currentRad).toFloat()
                val currentY = cy - currentNeedleLen * sin(currentRad).toFloat()

                drawLine(
                    color = Color(0xFFFFFFFF),
                    start = Offset(cx, cy),
                    end = Offset(currentX, currentY),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )

                // Center Pivot Bolt
                drawCircle(
                    color = Color(0xFFFFFFFF),
                    radius = 12f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color(0xFF0C2A47),
                    radius = 6f,
                    center = Offset(cx, cy)
                )
            }
        }

        // Legend line beneath gauge
        Row(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp, 3.dp)
                    .background(Color.White)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "CURRENT ($currentMinutes M)",
                color = theme.ink,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .size(10.dp, 3.dp)
                    .background(theme.warn)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "UPPER WINDOW ($upperMinutes M)",
                color = theme.warnText,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Perforated Paper Slip card with circular serrated edges.
 */
@Composable
fun PerforatedTicketSlip(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val theme = NocturneTheme.colors
    HardShadowBox(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = theme.surface,
        borderColor = theme.border
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Perforated Top Edge
            PerforatedEdge()

            // Content inside
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                content()
            }

            // Perforated Bottom Edge
            PerforatedEdge()
        }
    }
}

@Composable
private fun PerforatedEdge() {
    val theme = NocturneTheme.colors
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        val holeRadius = 4.dp.toPx()
        val spacing = 16.dp.toPx()
        val count = (size.width / spacing).toInt()
        val startX = (size.width - (count * spacing)) / 2f

        for (i in 0..count) {
            drawCircle(
                color = theme.bg,
                radius = holeRadius,
                center = Offset(startX + (i * spacing), size.height / 2f)
            )
        }
    }
}

/**
 * 45-degree Repeating Diagonal Hazard Stripe Banner for alerts and disruptions.
 */
@Composable
fun HazardStripeBanner(
    text: String,
    modifier: Modifier = Modifier,
    isCritical: Boolean = false
) {
    val theme = NocturneTheme.colors
    val stripeColor = if (isCritical) theme.danger else theme.warn
    val hatchColor = if (isCritical) theme.hatch else theme.navyDeep

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val stripeWidth = 14f
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(
                    color = stripeColor,
                    start = Offset(x, size.height),
                    end = Offset(x + size.height, 0f),
                    strokeWidth = stripeWidth
                )
                x += stripeWidth * 2
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isCritical) theme.dangerBg else theme.warnBg)
                .border(1.dp, stripeColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isCritical) theme.dangerText else theme.warnText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    color = if (isCritical) theme.dangerText else theme.warnText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 24dp Drafting Grid Background for technical precision instrument aesthetic.
 */
@Composable
fun DraftingGridBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val theme = NocturneTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.bg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 24.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = theme.gridLine,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = theme.gridLine,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += step
            }
        }
        content()
    }
}

/**
 * Official NOCTURNE Brand Symbol Logo Component.
 * Automatically chooses between light-background and dark-background assets based on
 * the target surface contrast, ensuring compliance with brand guidelines.
 */
@Composable
fun NocturneBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    onDarkSurface: Boolean = true,
    hasBackgroundPadding: Boolean = true,
    contentDescription: String = "NOCTURNE Logo Symbol"
) {
    val resId = if (hasBackgroundPadding) {
        if (onDarkSurface) {
            com.example.R.drawable.nocturne_symbol_dark_background
        } else {
            com.example.R.drawable.nocturne_symbol_light_background
        }
    } else {
        if (onDarkSurface) {
            com.example.R.drawable.nocturne_symbol_dark
        } else {
            com.example.R.drawable.nocturne_symbol_light
        }
    }

    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(id = resId),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .testTag("nocturne_brand_logo")
    )
}
