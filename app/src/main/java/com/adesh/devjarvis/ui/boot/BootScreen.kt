package com.adesh.devjarvis.ui.boot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.adesh.devjarvis.core.CrashReporter
import com.adesh.devjarvis.core.Edition
import com.adesh.devjarvis.memory.MemoryRepository
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.ui.theme.BuiltByDev
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.HudShapes
import com.adesh.devjarvis.ui.theme.Wordmark
import com.adesh.devjarvis.ui.theme.glass
import com.adesh.devjarvis.ui.theme.hudBackdrop
import com.adesh.devjarvis.wakeword.VoskModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One system-check line: label ... status. */
data class BootCheck(val label: String, val status: String, val ok: Boolean)

@HiltViewModel
class BootViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val models: VoskModelManager,
    private val memory: MemoryRepository,
) : ViewModel() {

    private fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    suspend fun checks(): List<BootCheck> {
        val s = settings.current
        val accessibility = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().contains(context.packageName + "/")
        val crashed = CrashReporter.lastCrash(context) != null
        return listOf(
            BootCheck("CORE SYSTEMS", "OK", true),
            BootCheck("EDITION", Edition.label, true),
            BootCheck("NEURAL LINK · GEMINI", if (s.hasGeminiKey) "OK" else "NO KEY", s.hasGeminiKey),
            BootCheck("BACKUP LINK · GROQ", if (s.hasGroqKey) "OK" else "STANDBY", s.hasGroqKey),
            BootCheck("AUDIO SENSORS", if (has(Manifest.permission.RECORD_AUDIO)) "OK" else "OFFLINE", has(Manifest.permission.RECORD_AUDIO)),
            BootCheck("WAKE ENGINE", if (models.isInstalled()) "OK" else "NO MODEL", models.isInstalled()),
            BootCheck("MANIPULATORS · ACCESSIBILITY", if (accessibility) "OK" else "OFFLINE", accessibility),
            BootCheck(
                "COMMS · NOTIFICATIONS",
                if (NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) "OK" else "OFFLINE",
                NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
            ),
            BootCheck("MEMORY BANKS", "${memory.all().size} FACTS", true),
            BootCheck("LAST SESSION", if (crashed) "RECOVERED" else "CLEAN", !crashed),
        )
    }

    val enabled: Boolean get() = settings.current.bootAnimation
}

/**
 * "JARVIS ONLINE" boot sequence with real system checks. Tap anywhere to skip.
 * Rings power up, the checks tick in, the name fades in — about two seconds in all.
 */
@Composable
fun BootScreen(viewModel: BootViewModel, onDone: () -> Unit) {
    var checks by remember { mutableStateOf<List<BootCheck>>(emptyList()) }
    var shown by remember { mutableIntStateOf(0) }
    val rings = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    val finish = { if (!finished) { finished = true; onDone() } }

    LaunchedEffect(Unit) {
        if (!viewModel.enabled) { finish(); return@LaunchedEffect }
        launch { rings.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        checks = viewModel.checks()
        for (i in checks.indices) {
            delay(90)
            shown = i + 1
        }
        title.animateTo(1f, tween(450))
        delay(650)
        finish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .hudBackdrop(animated = true, glowY = 0.30f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { finish() })
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 420.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BootReticle(power = rings.value, online = title.value, modifier = Modifier.size(140.dp))
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.graphicsLayer {
                    alpha = title.value
                    val s = 0.94f + 0.06f * title.value
                    scaleX = s
                    scaleY = s
                },
            ) { Wordmark(fontSize = 26) }
            Text(
                if (title.value > 0f) "At your service, Sir." else "INITIALISING…",
                style = if (title.value > 0f) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                color = if (title.value > 0f) Hud.Ice else Hud.Dim,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
            CheckPanel(checks, shown)
            Spacer(Modifier.height(16.dp))
            BuiltByDev(Modifier.alpha(title.value))
        }
    }
}

/** The system checks in a glass panel with a progress bar; rows keep their place so nothing jumps. */
@Composable
private fun CheckPanel(checks: List<BootCheck>, shown: Int) {
    val total = checks.size.coerceAtLeast(1)
    val progress = shown.toFloat() / total
    Column(
        Modifier
            .fillMaxWidth()
            .glass(Hud.Cyan, HudShapes.Card, tint = 0.6f, edge = 0.6f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SYSTEM CHECK",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 2.sp),
                color = Hud.Ice,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Hud.Cyan,
            )
        }
        Box(
            Modifier
                .padding(top = 8.dp, bottom = 8.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(Hud.Faint, RoundedCornerShape(1.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Hud.Teal, Hud.Cyan)), RoundedCornerShape(1.dp)),
            )
        }
        checks.forEachIndexed { i, c ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).alpha(if (i < shown) 1f else 0f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    c.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                    color = Hud.Dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Text(
                    c.status,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                    color = if (c.ok) Hud.Cyan else Hud.Amber,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Mini reactor that powers up: rings draw in with [power], the core lights with [online]. */
@Composable
private fun BootReticle(power: Float, online: Float, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "boot")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "spin")
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        val c = center
        val hair = 1.dp.toPx()
        drawCircle(
            Brush.radialGradient(listOf(Hud.Cyan.copy(alpha = 0.10f + 0.20f * online), Color.Transparent), center = c, radius = r),
            radius = r, center = c,
        )
        // Outer ring draws itself in.
        drawArc(
            Hud.Cyan.copy(alpha = 0.55f), -90f, 360f * power, false,
            Offset(c.x - r * 0.95f, c.y - r * 0.95f), Size(r * 1.9f, r * 1.9f),
            style = Stroke(1.5f * hair, cap = StrokeCap.Round),
        )
        // Ticks.
        rotate(spin * 0.3f, c) {
            val outer = r * 0.86f
            val count = (60 * power).toInt()
            for (i in 0 until count) {
                val a = i * 6f * PI.toFloat() / 180f
                val len = if (i % 5 == 0) r * 0.08f else r * 0.04f
                drawLine(
                    Hud.Cyan.copy(alpha = if (i % 5 == 0) 0.7f else 0.3f),
                    Offset(c.x + cos(a) * outer, c.y + sin(a) * outer),
                    Offset(c.x + cos(a) * (outer - len), c.y + sin(a) * (outer - len)),
                    strokeWidth = hair,
                )
            }
        }
        // Segments light up one by one.
        rotate(spin, c) {
            val rr = r * 0.66f
            val lit = (12 * power).toInt()
            for (i in 0 until 12) {
                drawArc(
                    Hud.Cyan.copy(alpha = if (i < lit) 0.85f else 0.10f),
                    i * 30f, 22f, false,
                    Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2),
                    style = Stroke(r * 0.07f),
                )
            }
        }
        // Inner counter-ring.
        rotate(-spin * 1.5f, c) {
            val rr = r * 0.48f
            drawArc(
                Hud.Ice.copy(alpha = 0.8f), 0f, 300f * power, false,
                Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2),
                style = Stroke(1.5f * hair, cap = StrokeCap.Round),
            )
        }
        // Core.
        val coreR = r * (0.12f + 0.16f * online + 0.06f * power)
        drawCircle(
            Brush.radialGradient(
                0f to Hud.White,
                0.4f to Hud.Ice,
                0.75f to Hud.Cyan.copy(alpha = 0.7f),
                1f to Color.Transparent,
                center = c, radius = coreR * 1.7f,
            ),
            radius = coreR * 1.7f, center = c,
        )
    }
}
