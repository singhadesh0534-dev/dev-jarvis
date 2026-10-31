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

object Hud {
    val Background = Color(0xFF03070F)
    val BackgroundGlow = Color(0xFF0A1A2E)
    val Panel = Color(0xFF0A1626)
    val Cyan = Color(0xFF00E5FF)
    val Ice = Color(0xFF9BEFFF)
    val Dim = Color(0xFF4F7189)
    val Faint = Color(0xFF1B3346)
    val Amber = Color(0xFFFFB300)
    val Red = Color(0xFFFF5252)
    val Green = Color(0xFF00E676)
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

private val JarvisTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 4.sp),
    headlineSmall = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp),
    titleMedium = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 2.sp),
    labelSmall = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.5.sp),
    labelMedium = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.5.sp),
    bodyLarge = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp),
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
    outline = Hud.Dim,
    error = Hud.Red,
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JarvisColors, typography = JarvisTypography, content = content)
}
