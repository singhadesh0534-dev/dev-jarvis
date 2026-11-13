package com.adesh.devjarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.adesh.devjarvis.R

/**
 * HUD palette. Deep navy glass, one cyan accent, amber only for warnings.
 * Every screen (and the bubble / camera overlays) reads colours from here.
 */
object Hud {
    /** Deepest layer: the space behind everything. */
    val Abyss = Color(0xFF010409)
    val Background = Color(0xFF03070F)
    val BackgroundGlow = Color(0xFF0B1D33)
    /** Glass panels: base and the lit top edge. */
    val Panel = Color(0xFF0A1626)
    val PanelHi = Color(0xFF10243A)
    /** Hairline borders and dividers. */
    val Line = Color(0xFF1E3D54)
    val Cyan = Color(0xFF00E5FF)
    /** Deeper cyan for gradients (Cyan -> Teal). */
    val Teal = Color(0xFF00B8D4)
    val Ice = Color(0xFFA8F2FF)
    /** Secondary text: readable on glass, still clearly "second". */
    val Dim = Color(0xFF6A8EA6)
    val Faint = Color(0xFF1B3346)
    val Amber = Color(0xFFFFB300)
    val Red = Color(0xFFFF5252)
    val Green = Color(0xFF00E676)
    /** The girlfriend role's colour on the HUD. */
    val Pink = Color(0xFFFF6FB5)
    val White = Color(0xFFEFFBFF)
}

@OptIn(ExperimentalTextApi::class)
private fun orbitron(weight: Int) = Font(
    R.font.orbitron,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Orbitron = FontFamily(orbitron(400), orbitron(500), orbitron(700), orbitron(900))

val Rajdhani = FontFamily(
    Font(R.font.rajdhani_regular, FontWeight.Normal),
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

/** Orbitron for labels and headings (short, uppercase); Rajdhani for anything people read. */
private val JarvisTypography = Typography(
    displaySmall = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 6.sp),
    headlineLarge = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 4.sp),
    headlineMedium = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 4.sp),
    headlineSmall = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp),
    titleLarge = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 2.5.sp),
    titleMedium = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 2.sp),
    titleSmall = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp),
    labelLarge = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.5.sp),
    labelMedium = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.5.sp),
    labelSmall = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.5.sp),
    bodyLarge = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp),
)

private val JarvisColors = darkColorScheme(
    primary = Hud.Cyan,
    onPrimary = Hud.Background,
    secondary = Hud.Ice,
    tertiary = Hud.Amber,
    background = Hud.Background,
    onBackground = Hud.Ice,
    surface = Hud.Panel,
    onSurface = Hud.Ice,
    surfaceVariant = Hud.Faint,
    onSurfaceVariant = Hud.Dim,
    surfaceContainerLowest = Hud.Abyss,
    surfaceContainerLow = Hud.Background,
    surfaceContainer = Hud.Panel,
    surfaceContainerHigh = Hud.PanelHi,
    surfaceContainerHighest = Hud.PanelHi,
    outline = Hud.Dim,
    outlineVariant = Hud.Line,
    error = Hud.Red,
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JarvisColors, typography = JarvisTypography, content = content)
}
