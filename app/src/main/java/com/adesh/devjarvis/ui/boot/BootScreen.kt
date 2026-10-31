package com.adesh.devjarvis.ui.boot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.adesh.devjarvis.core.CrashReporter
import com.adesh.devjarvis.memory.MemoryRepository
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.Orbitron
import com.adesh.devjarvis.wakeword.VoskModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject

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

/** "JARVIS ONLINE" boot sequence with real system checks. Tap anywhere to skip. */
@Composable
fun BootScreen(viewModel: BootViewModel, onDone: () -> Unit) {
    var checks by remember { mutableStateOf<List<BootCheck>>(emptyList()) }
    var shown by remember { mutableIntStateOf(0) }
    val title = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    val finish = { if (!finished) { finished = true; onDone() } }

    LaunchedEffect(Unit) {
        if (!viewModel.enabled) { finish(); return@LaunchedEffect }
        checks = viewModel.checks()
        for (i in checks.indices) {
            delay(150)
            shown = i + 1
        }
        title.animateTo(1f, tween(500))
        delay(700)
        finish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Hud.Background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { finish() })
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            checks.take(shown).forEach { c ->
                Row(Modifier.fillMaxWidth()) {
                    Text("> ${c.label}", style = MaterialTheme.typography.labelMedium, color = Hud.Dim, modifier = Modifier.weight(1f))
                    Text(c.status, style = MaterialTheme.typography.labelMedium, color = if (c.ok) Hud.Cyan else Hud.Amber)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "JARVIS ONLINE",
                style = TextStyle(
                    fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 6.sp,
                    shadow = Shadow(color = Hud.Cyan, blurRadius = 24f),
                ),
                color = Hud.Cyan,
                modifier = Modifier.alpha(title.value).align(Alignment.CenterHorizontally),
            )
            Text(
                "At your service, Sir.",
                style = MaterialTheme.typography.bodyMedium,
                color = Hud.Ice,
                modifier = Modifier.alpha(title.value).align(Alignment.CenterHorizontally),
            )
        }
    }
}
