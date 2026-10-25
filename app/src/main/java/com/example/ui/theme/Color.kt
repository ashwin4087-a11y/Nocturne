package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light Theme Tokens
val NocturneBgLight = Color(0xFFF7FAFC)
val NocturneGridLineLight = Color(0x80DCE5ED)
val NocturneSurfaceLight = Color(0xFFFFFFFF)
val NocturneInkLight = Color(0xFF17212B)
val NocturneMutedLight = Color(0xFF5B6B80)
val NocturneBorderLight = Color(0xFFDCE5ED)
val NocturneNavyLight = Color(0xFF123B63)
val NocturneNavyDeepLight = Color(0xFF0C2A47)
val NocturneChamberLight = Color(0xFF081D33)
val NocturneChamberEdgeLight = Color(0xFF061526)
val NocturneOkLight = Color(0xFF56A67A)
val NocturneOkTextLight = Color(0xFF2E7D57)
val NocturneOkBgLight = Color(0xFFEBF7F0)
val NocturneWarnLight = Color(0xFFF4B942)
val NocturneWarnTextLight = Color(0xFF8A5A00) // WCAG AA 6:1 compliant
val NocturneWarnBgLight = Color(0xFFFEF7EA)
val NocturneDangerLight = Color(0xFF1E3A5F) // Navy-based structural alert surface
val NocturneDangerTextLight = Color(0xFF0C2A47)
val NocturneDangerBgLight = Color(0xFFE8EEF5)
val NocturneHatchLight = Color(0xFF0C2A47)
val NocturneShadowInkLight = Color(0xFF123B63)

// Dark Theme Tokens
val NocturneBgDark = Color(0xFF0A1522)
val NocturneGridLineDark = Color(0x99162A40)
val NocturneSurfaceDark = Color(0xFF0F2236)
val NocturneInkDark = Color(0xFFE8EEF4)
val NocturneMutedDark = Color(0xFF9DB0C4)
val NocturneBorderDark = Color(0xFF24405C)
val NocturneNavyDark = Color(0xFF5E8DBD)
val NocturneNavyDeepDark = Color(0xFF0C2A47)
val NocturneChamberDark = Color(0xFF050E19)
val NocturneChamberEdgeDark = Color(0xFF030911)
val NocturneOkDark = Color(0xFF56A67A)
val NocturneOkTextDark = Color(0xFF7FD1A3)
val NocturneOkBgDark = Color(0xFF10281E)
val NocturneWarnDark = Color(0xFFF4B942)
val NocturneWarnTextDark = Color(0xFFF4B942)
val NocturneWarnBgDark = Color(0xFF2B2110)
val NocturneDangerDark = Color(0xFF4A729A) // Deep navy-blue alert surface
val NocturneDangerTextDark = Color(0xFFE8EEF4)
val NocturneDangerBgDark = Color(0xFF13283E)
val NocturneHatchDark = Color(0xFF081D33)
val NocturneShadowInkDark = Color(0xFF000000)

@Immutable
data class NocturnePalette(
    val bg: Color,
    val gridLine: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val border: Color,
    val navy: Color,
    val navyDeep: Color,
    val chamber: Color,
    val chamberEdge: Color,
    val ok: Color,
    val okText: Color,
    val okBg: Color,
    val warn: Color,
    val warnText: Color,
    val warnBg: Color,
    val danger: Color,
    val dangerText: Color,
    val dangerBg: Color,
    val hatch: Color,
    val shadowInk: Color,
    val isDark: Boolean
)

val LightNocturnePalette = NocturnePalette(
    bg = NocturneBgLight,
    gridLine = NocturneGridLineLight,
    surface = NocturneSurfaceLight,
    ink = NocturneInkLight,
    muted = NocturneMutedLight,
    border = NocturneBorderLight,
    navy = NocturneNavyLight,
    navyDeep = NocturneNavyDeepLight,
    chamber = NocturneChamberLight,
    chamberEdge = NocturneChamberEdgeLight,
    ok = NocturneOkLight,
    okText = NocturneOkTextLight,
    okBg = NocturneOkBgLight,
    warn = NocturneWarnLight,
    warnText = NocturneWarnTextLight,
    warnBg = NocturneWarnBgLight,
    danger = NocturneDangerLight,
    dangerText = NocturneDangerTextLight,
    dangerBg = NocturneDangerBgLight,
    hatch = NocturneHatchLight,
    shadowInk = NocturneShadowInkLight,
    isDark = false
)

val DarkNocturnePalette = NocturnePalette(
    bg = NocturneBgDark,
    gridLine = NocturneGridLineDark,
    surface = NocturneSurfaceDark,
    ink = NocturneInkDark,
    muted = NocturneMutedDark,
    border = NocturneBorderDark,
    navy = NocturneNavyDark,
    navyDeep = NocturneNavyDeepDark,
    chamber = NocturneChamberDark,
    chamberEdge = NocturneChamberEdgeDark,
    ok = NocturneOkDark,
    okText = NocturneOkTextDark,
    okBg = NocturneOkBgDark,
    warn = NocturneWarnDark,
    warnText = NocturneWarnTextDark,
    warnBg = NocturneWarnBgDark,
    danger = NocturneDangerDark,
    dangerText = NocturneDangerTextDark,
    dangerBg = NocturneDangerBgDark,
    hatch = NocturneHatchDark,
    shadowInk = NocturneShadowInkDark,
    isDark = true
)

val LocalNocturnePalette = staticCompositionLocalOf { LightNocturnePalette }
