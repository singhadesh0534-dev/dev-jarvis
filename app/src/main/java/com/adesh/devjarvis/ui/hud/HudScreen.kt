package com.adesh.devjarvis.ui.hud

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adesh.devjarvis.brain.Persona
import com.adesh.devjarvis.core.HudCard
import com.adesh.devjarvis.core.HudUiState
import com.adesh.devjarvis.core.JarvisState
import com.adesh.devjarvis.core.NetworkKind
import com.adesh.devjarvis.ui.theme.BuiltByDev
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.HudShapes
import com.adesh.devjarvis.ui.theme.HudTag
import com.adesh.devjarvis.ui.theme.StatusDot
import com.adesh.devjarvis.ui.theme.Wordmark
import com.adesh.devjarvis.ui.theme.cornerTicks
import com.adesh.devjarvis.ui.theme.glass
import com.adesh.devjarvis.ui.theme.hudBackdrop
import com.adesh.devjarvis.update.AppUpdater
import kotlin.math.sin

@Composable
fun HudScreen(
    viewModel: HudViewModel,
    onOpenSettings: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMemory: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val speechLevel by viewModel.speechLevel.collectAsStateWithLifecycle()
    val role by viewModel.role.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onReactorTap() else viewModel.onMicPermissionDenied()
    }
    val alwaysOnPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setAlwaysOn(true) else viewModel.onMicPermissionDenied()
    }
    val onAlwaysOnToggle: (Boolean) -> Unit = { on ->
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!on || granted) viewModel.setAlwaysOn(on) else alwaysOnPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    var protectedBg by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDevice()
        protectedBg = viewModel.backgroundProtected()
    }

    val onTap = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onReactorTap() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // The whole HUD takes the role's colour: cyan for JARVIS / DOST, pink for GF.
    val accent by animateColorAsState(if (role == Persona.PARTNER) Hud.Pink else Hud.Cyan, tween(700), label = "accent")

    Box(
        Modifier
            .fillMaxSize()
            .hudBackdrop(animated = true, glowY = 0.36f)
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            TopBar(
                accent = accent,
                onOpenSettings = onOpenSettings,
                onOpenActivity = onOpenActivity,
                onOpenMemory = onOpenMemory,
                onReport = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, viewModel.report())
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, "JARVIS report bhejo")) }
                },
            )
            TelemetryRow(
                ui, role, accent,
                onPower = { openSystem(context, android.content.Intent.ACTION_POWER_USAGE_SUMMARY) },
                onNet = {
                    openSystem(context, if (android.os.Build.VERSION.SDK_INT >= 29) android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY else android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                },
                onBrain = onOpenSettings,
                onRole = { viewModel.roleHint() },
            )
            WakeStrip(ui, accent, onToggle = onAlwaysOnToggle)

            Column(Modifier.fillMaxWidth().animateContentSize()) {
                if (!ui.hasKey) {
                    HudBanner(
                        tag = "KEY",
                        text = "Gemini API key nahi hai — tap karke Settings mein daaliye.",
                        color = Hud.Amber,
                        onClick = onOpenSettings,
                    )
                }
                if (ui.alwaysOn && !protectedBg) {
                    // XOS stops apps that are not exempt: Sir spoke and nobody heard.
                    HudBanner(
                        tag = "PROTECT",
                        text = "Background protection OFF — phone JARVIS ko band kar sakta hai. Tap karke \"Unrestricted / Don't optimize\" chuno.",
                        color = Hud.Amber,
                        onClick = {
                            runCatching { context.startActivity(com.adesh.devjarvis.ui.setup.SetupIntents.ignoreBatteryOptimization(context)) }
                                .onFailure { runCatching { context.startActivity(com.adesh.devjarvis.ui.setup.SetupIntents.batteryOptimizationList()) } }
                        },
                    )
                }
                (update as? AppUpdater.State.Available)?.let { u ->
                    HudBanner(
                        tag = "UPDATE",
                        text = "Naya JARVIS ${u.versionName} available — Settings > Updates",
                        color = Hud.Cyan,
                        onClick = onOpenSettings,
                    )
                }
            }

            Box(
                Modifier.fillMaxWidth().weight(1.2f, fill = false).padding(top = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                ArcReactor(
                    state = ui.state,
                    micLevel = ui.micLevel,
                    speechLevel = speechLevel,
                    batteryPercent = ui.batteryPercent,
                    charging = ui.charging,
                    fuelPercent = ui.fuelPercent,
                    onTap = onTap,
                    accent = accent,
                    modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth(0.84f).aspectRatio(1f),
                )
            }
            StateReadout(ui, speechLevel, accent, onTap)

            Column(Modifier.fillMaxWidth().animateContentSize()) {
                ui.pendingConfirmation?.let { q -> ConfirmCard(q, viewModel::answerConfirmation) }
                ui.card?.let { c -> ResultCard(c, viewModel::dismissCard) }
            }
            Transcript(ui, role, accent, Modifier.weight(1f))
            InputBar(accent, onSend = viewModel::onTextSubmitted, onFile = viewModel::onFilePicked)
        }
    }
}

@Composable
private fun TopBar(
    accent: Color,
    onOpenSettings: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMemory: () -> Unit,
    onReport: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "online")
    val dot by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "dot")
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Wordmark()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                StatusDot(Hud.Green, Modifier.graphicsLayer { alpha = dot }, size = 5)
                Text(
                    "ONLINE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 2.sp),
                    color = Hud.Dim,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 2.dp, end = 10.dp),
                )
                BuiltByDev()
            }
        }
        // Quick actions in one glass pill.
        Row(
            Modifier.glass(accent, HudShapes.Pill, tint = 0.6f, edge = 0.7f).padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopIcon(onReport) { Icon(Icons.Filled.Share, contentDescription = "Report", tint = accent, modifier = Modifier.size(19.dp)) }
            TopIcon(onOpenMemory) { Icon(Icons.Filled.Person, contentDescription = "Memory", tint = accent, modifier = Modifier.size(20.dp)) }
            TopIcon(onOpenActivity) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Activity log", tint = accent, modifier = Modifier.size(20.dp)) }
            TopIcon(onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = accent, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun TopIcon(onClick: () -> Unit, icon: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) { icon() }
}

@Composable
private fun TelemetryRow(
    ui: HudUiState, role: String, accent: Color,
    onPower: () -> Unit, onNet: () -> Unit, onBrain: () -> Unit, onRole: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val battWarn = ui.batteryPercent in 0..19 && !ui.charging
        val online = ui.network != NetworkKind.NONE
        Chip(
            label = if (ui.charging) "PWR +" else "PWR",
            value = if (ui.batteryPercent < 0) "--" else "${ui.batteryPercent}%",
            color = if (battWarn) Hud.Amber else accent,
            meter = if (ui.batteryPercent < 0) 0f else ui.batteryPercent / 100f,
            modifier = Modifier.weight(1f),
            onClick = onPower,
        )
        Chip(
            label = "NET",
            value = ui.network.label,
            color = if (online) accent else Hud.Amber,
            meter = if (online) 1f else 0f,
            modifier = Modifier.weight(1.1f),
            onClick = onNet,
        )
        Chip(
            label = "BRAIN",
            value = ui.brainSource.ifBlank { "--" },
            color = if (ui.fuelPercent < 15) Hud.Amber else accent,
            meter = ui.fuelPercent / 100f,
            modifier = Modifier.weight(1.35f),
            onClick = onBrain,
        )
        // Changed by voice only: "dost ban jao", "gf ban jao", "Jarvis ban jao".
        Chip(
            label = "ROOP",
            value = Persona.label(role).uppercase(),
            color = when (role) {
                Persona.PARTNER -> Hud.Pink
                Persona.DOST -> Hud.Ice
                else -> accent
            },
            meter = null,
            segments = Persona.ALL.indexOf(role).coerceAtLeast(0),
            modifier = Modifier.weight(1f),
            onClick = onRole,
        )
    }
}

/**
 * Telemetry chip: tiny label, the value, and a 2dp meter underneath.
 * [segments] (0..2) lights one of three segments instead — used for the role.
 */
@Composable
private fun Chip(
    label: String,
    value: String,
    color: Color,
    meter: Float?,
    modifier: Modifier,
    segments: Int = -1,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(color, tween(500), label = "chip")
    val fill by animateFloatAsState((meter ?: 0f).coerceIn(0f, 1f), tween(700), label = "meter")
    Column(
        modifier
            .glass(tint, HudShapes.Chip, tint = 1f, edge = 0.8f)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 6.dp, top = 7.dp, bottom = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.5.sp),
            color = Hud.Dim,
            maxLines = 1,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp, bottom = 6.dp),
        )
        if (segments >= 0) {
            Row(Modifier.fillMaxWidth().height(2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0 until 3) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (i == segments) tint else Hud.Faint, RoundedCornerShape(1.dp)),
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Hud.Faint, RoundedCornerShape(1.dp))) {
                Box(
                    Modifier
                        .fillMaxWidth(fill)
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.5f), tint)), RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

/** "Hello Dev" always-on control. The Switch stays the only checkable thing on the HUD. */
@Composable
private fun WakeStrip(ui: HudUiState, accent: Color, onToggle: (Boolean) -> Unit) {
    val tone by animateColorAsState(if (ui.alwaysOn) accent else Hud.Dim, tween(500), label = "wake")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .glass(tone, HudShapes.Chip, tint = if (ui.alwaysOn) 1f else 0.4f, edge = if (ui.alwaysOn) 0.9f else 0.6f)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WakeRadar(on = ui.alwaysOn, color = tone)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                if (ui.alwaysOn) "ALWAYS LISTENING" else "WAKE WORD OFF",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 1.8.sp),
                color = tone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = ui.wakeNotice
                ?: if (ui.alwaysOn && ui.wakeHeard.isNotBlank()) "heard: ${ui.wakeHeard}" else null
                ?: if (ui.alwaysOn) "\"Hello Dev\" boliye — screen off bhi chalega" else "Off — sirf tap se baat"
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = if (ui.wakeNotice != null) Hud.Amber else Hud.Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = ui.alwaysOn,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Hud.Background,
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                uncheckedThumbColor = Hud.Dim,
                uncheckedTrackColor = Hud.Panel,
                uncheckedBorderColor = Hud.Line,
            ),
        )
    }
}

/** Small radar: a steady dot, with a ring sweeping outward while always-on is listening. */
@Composable
private fun WakeRadar(on: Boolean, color: Color) {
    // The clock runs only while listening.
    val ping: State<Float>? = if (on) {
        rememberInfiniteTransition(label = "radar")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "ping")
    } else {
        null
    }
    Canvas(Modifier.size(22.dp)) {
        val r = size.minDimension / 2f
        drawCircle(color.copy(alpha = 0.18f), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        if (ping != null) {
            val p = ping.value
            drawCircle(
                color.copy(alpha = (1f - p) * 0.6f),
                radius = r * (0.3f + 0.7f * p),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
            )
        }
        drawCircle(color, radius = r * 0.26f)
    }
}

/** Live state under the reactor: name, a tiny level meter on both sides, and what a tap does now. */
@Composable
private fun StateReadout(ui: HudUiState, speechLevel: Float, accent: Color, onTap: () -> Unit) {
    val followUp = ui.state == JarvisState.LISTENING && ui.inConversation && ui.transcript.lastOrNull()?.fromUser == false
    val label = if (followUp) "FOLLOW-UP" else ui.state.label
    val color by animateColorAsState(
        when (ui.state) {
            JarvisState.IDLE -> Hud.Dim
            JarvisState.THINKING -> Hud.Ice
            else -> accent
        },
        tween(450),
        label = "stateColor",
    )
    val level = when (ui.state) {
        JarvisState.LISTENING -> ui.micLevel
        JarvisState.SPEAKING -> speechLevel
        JarvisState.THINKING -> 0.45f
        JarvisState.IDLE -> 0.06f
    }
    val hint = when (ui.state) {
        JarvisState.IDLE -> if (ui.alwaysOn) "\"Hello Dev\" boliye ya reactor tap kijiye" else "Reactor tap kijiye aur boliye"
        JarvisState.LISTENING -> if (followUp) "Boliye, Sir — sun raha hoon · tap = band" else "Sun raha hoon · tap = band"
        JarvisState.THINKING -> "Soch raha hoon · tap = roko"
        JarvisState.SPEAKING -> "Bol raha hoon · tap = beech mein roko"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelBars(level, ui.state != JarvisState.IDLE, color, mirrored = true)
            Crossfade(targetState = label, animationSpec = tween(250), label = "stateLabel") { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
                    color = color,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            LevelBars(level, ui.state != JarvisState.IDLE, color, mirrored = false)
        }
        Row(
            Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (ui.mode.isNotBlank()) HudTag(ui.mode.uppercase(), if (ui.mode.equals("FALLBACK", ignoreCase = true)) Hud.Amber else Hud.Dim)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = Hud.Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** Five little bars that move with the voice level; flat and quiet in standby. */
@Composable
private fun LevelBars(level: Float, live: Boolean, color: Color, mirrored: Boolean) {
    val smooth by animateFloatAsState(level.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessMedium), label = "bars")
    val t = rememberInfiniteTransition(label = "bars")
    val phase by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase")
    Canvas(Modifier.size(width = 34.dp, height = 16.dp)) {
        val n = 5
        val gap = size.width / n
        for (i in 0 until n) {
            val idx = if (mirrored) n - 1 - i else i
            val wobble = if (live) 0.55f + 0.45f * sin(phase + idx * 1.1f) else 0.5f
            val falloff = 1f - idx * 0.14f
            val h = size.height * (0.12f + 0.88f * smooth * wobble * falloff).coerceIn(0.12f, 1f)
            val x = gap * i + gap / 2f
            drawLine(
                color.copy(alpha = 0.35f + 0.6f * falloff * if (live) 1f else 0.4f),
                Offset(x, (size.height - h) / 2f),
                Offset(x, (size.height + h) / 2f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Warning / info strip: coloured edge, a tag, the message, and a chevron that says "tap me". */
@Composable
private fun HudBanner(tag: String, text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .glass(color, HudShapes.Chip, tint = 1.4f)
            .clickable(onClick = onClick)
            .drawBehind { drawRect(color, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudTag(tag, color)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Hud.White.copy(alpha = 0.92f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text("›", style = MaterialTheme.typography.titleLarge, color = color)
    }
}

/** Latest tool result (weather, reminder, expense...). Tap to dismiss; it also clears itself. */
@Composable
private fun ResultCard(card: HudCard, onDismiss: () -> Unit) {
    val accent = if (card.ok) Hud.Cyan else Hud.Amber
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .glass(accent, HudShapes.Card, tint = 1.3f)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(accent, size = 5)
            Text(
                if (card.ok) card.title else "${card.title} · FAILED",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            Text("✕", style = MaterialTheme.typography.labelSmall, color = Hud.Dim)
        }
        Text(
            card.body,
            style = MaterialTheme.typography.bodyMedium,
            color = Hud.White,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ConfirmCard(question: String, onAnswer: (Boolean) -> Unit) {
    val t = rememberInfiniteTransition(label = "confirm")
    val glow by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "edge")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .glass(Hud.Amber, HudShapes.Card, tint = 1.6f, edge = glow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HudTag("CONFIRM", Hud.Amber)
            Text(
                "Boliye \"haan\" / \"nahi\" — ya tap kijiye",
                style = MaterialTheme.typography.bodySmall,
                color = Hud.Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(question, style = MaterialTheme.typography.bodyLarge, color = Hud.White, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onAnswer(true) },
                shape = HudShapes.Chip,
                colors = ButtonDefaults.buttonColors(containerColor = Hud.Cyan, contentColor = Hud.Background),
                modifier = Modifier.weight(1f),
            ) { Text("YES", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
            OutlinedButton(
                onClick = { onAnswer(false) },
                shape = HudShapes.Chip,
                border = androidx.compose.foundation.BorderStroke(1.dp, Hud.Amber.copy(alpha = 0.7f)),
                modifier = Modifier.weight(1f),
            ) {
                Text("NO", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Hud.Amber)
            }
        }
    }
}

@Composable
private fun Transcript(ui: HudUiState, role: String, accent: Color, modifier: Modifier) {
    val listState = rememberLazyListState()
    val lastLength = ui.transcript.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(ui.transcript.size, lastLength, ui.partial) {
        val count = ui.transcript.size + if (ui.partial.isNotEmpty()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    val speaker = Persona.label(role).uppercase()
    Box(
        modifier
            .fillMaxWidth()
            .cornerTicks(accent.copy(alpha = 0.55f), length = 10f)
            .glass(accent, HudShapes.Panel, tint = 0.5f, edge = 0.6f),
    ) {
        if (ui.transcript.isEmpty() && ui.partial.isEmpty()) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("COMMS READY", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp), color = accent.copy(alpha = 0.7f))
                Text(
                    "Boliye ya neeche likhiye — baat-cheet yahan dikhegi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Hud.Dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ui.transcript, key = { it.id }) { line ->
                when {
                    line.isSystem -> Text(
                        "▸ " + line.text,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = Hud.Dim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                    line.fromUser -> UserBubble(line.text, accent)
                    else -> AssistantBubble(speaker, line.text, accent)
                }
            }
            if (ui.partial.isNotEmpty()) {
                item(key = "partial") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            ui.partial + " …",
                            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                            color = Hud.Ice.copy(alpha = 0.6f),
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String, accent: Color) {
    val shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    Box(Modifier.fillMaxWidth().padding(start = 40.dp), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = Hud.Ice,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.16f), Hud.Faint.copy(alpha = 0.55f))), shape)
                .border(1.dp, accent.copy(alpha = 0.22f), shape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AssistantBubble(speaker: String, text: String, voiceColor: Color) {
    Column(
        Modifier
            .fillMaxWidth(0.94f)
            .background(
                Brush.horizontalGradient(listOf(voiceColor.copy(alpha = 0.13f), Color.Transparent)),
                RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            )
            .drawBehind {
                drawRect(voiceColor, size = Size(2.dp.toPx(), size.height))
            }
            .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
    ) {
        Text(
            speaker,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.Bold),
            color = voiceColor,
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Hud.White, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun InputBar(accent: Color, onSend: (String) -> Unit, onFile: (android.net.Uri) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onFile(uri) }
    val send = {
        if (text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ready = text.isNotBlank()
    val edge by animateFloatAsState(if (focused) 1f else 0.55f, tween(250), label = "edge")
    val sendFill by animateColorAsState(if (ready) accent else Hud.Faint, tween(250), label = "send")

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
            .glass(accent, HudShapes.Pill, tint = 0.8f, edge = edge)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Photo / PDF: JARVIS looks at it and explains.
        Text(
            "FILE",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
            color = accent,
            modifier = Modifier
                .clip(HudShapes.Pill)
                .background(accent.copy(alpha = 0.10f), HudShapes.Pill)
                .clickable { picker.launch(arrayOf("image/*", "application/pdf")) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Hud.White),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            interactionSource = interaction,
            modifier = Modifier.weight(1f).heightIn(min = 40.dp).padding(horizontal = 12.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = 40.dp), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text("Type to JARVIS…", style = MaterialTheme.typography.bodyLarge, color = Hud.Dim, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        IconButton(
            onClick = send,
            modifier = Modifier
                .size(44.dp)
                .background(
                    Brush.linearGradient(listOf(sendFill, sendFill.copy(alpha = 0.7f))),
                    CircleShape,
                ),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (ready) Hud.Background else accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun openSystem(context: android.content.Context, action: String) {
    runCatching { context.startActivity(android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
