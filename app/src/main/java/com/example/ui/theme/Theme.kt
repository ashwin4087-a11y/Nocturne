package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val DarkColorScheme = darkColorScheme(
    primary = NocturneNavyDark,
    onPrimary = NocturneBgDark,
    primaryContainer = NocturneNavyDeepDark,
    onPrimaryContainer = NocturneInkDark,
    secondary = NocturneWarnDark,
    onSecondary = NocturneBgDark,
    background = NocturneBgDark,
    onBackground = NocturneInkDark,
    surface = NocturneSurfaceDark,
    onSurface = NocturneInkDark,
    surfaceVariant = NocturneBorderDark,
    onSurfaceVariant = NocturneMutedDark,
    outline = NocturneBorderDark,
    error = NocturneDangerDark,
    onError = NocturneBgDark
)

private val LightColorScheme = lightColorScheme(
    primary = NocturneNavyLight,
    onPrimary = NocturneSurfaceLight,
    primaryContainer = NocturneNavyDeepLight,
    onPrimaryContainer = NocturneSurfaceLight,
    secondary = NocturneWarnLight,
    onSecondary = NocturneInkLight,
    background = NocturneBgLight,
    onBackground = NocturneInkLight,
    surface = NocturneSurfaceLight,
    onSurface = NocturneInkLight,
    surfaceVariant = NocturneBorderLight,
    onSurfaceVariant = NocturneMutedLight,
    outline = NocturneBorderLight,
    error = NocturneDangerLight,
    onError = NocturneSurfaceLight
)

object NocturneTheme {
    val colors: NocturnePalette
        @Composable
        @ReadOnlyComposable
        get() = LocalNocturnePalette.current
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkNocturnePalette else LightNocturnePalette
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalNocturnePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
