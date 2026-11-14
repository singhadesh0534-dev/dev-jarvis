package com.adesh.devjarvis.ui.hud

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
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
import com.adesh.devjarvis.ui.theme.HudLineIcon
import com.adesh.devjarvis.ui.theme.HudShapes
import com.adesh.devjarvis.ui.theme.LineIcon
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
    onOpenSetup: () -> Unit = onOpenSettings,
    onOpenSkills: () -> Unit = {},
    onOpenConnectors: () -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val attachment by viewModel.attachment.collectAsStateWithLifecycle()
    val research by viewModel.research.collectAsStateWithLifecycle()
    val autoMemory by viewModel.autoMemory.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
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

    // The + sheet: camera, photos, files.
    var shot by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        shot?.let { if (saved) viewModel.onPhotoTaken(android.net.Uri.parse(it)) }
        shot = null
    }
    val launchCamera = {
        runCatching {
            val target = viewModel.cameraTarget()
            shot = target.toString()
            takePicture.launch(target)
        }.onFailure { viewModel.onMicPermissionDenied() }
        Unit
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) viewModel.attach(uri) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.attach(uri) }

    var protectedBg by remember { mutableStateOf(true) }
    var missing by remember { mutableStateOf(emptyList<String>()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDevice()
        protectedBg = viewModel.backgroundProtected()
        missing = runCatching { viewModel.missingAccess() }.getOrDefault(emptyList())
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
            StatusStrip(
                ui, role, accent,
                onPower = { openSystem(context, android.content.Intent.ACTION_POWER_USAGE_SUMMARY) },
                onNet = {
                    openSystem(context, if (android.os.Build.VERSION.SDK_INT >= 29) android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY else android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                },
                onBrain = onOpenSettings,
                onRole = { viewModel.roleHint() },
                onToggle = onAlwaysOnToggle,
            )

            // One line for whatever needs attention, most important first (not a stack of big boxes).
            val alerts = buildList {
                if (!ui.hasKey) add(Alert("KEY", "Gemini API key nahi hai — tap karke daaliye", Hud.Amber, onOpenSettings))
                if (missing.isNotEmpty()) add(
                    Alert(
                        "ACCESS",
                        "${missing.first()}" + (if (missing.size > 1) " +${missing.size - 1}" else "") + " off — iske bina kaam adhoore. Tap karke do",
                        Hud.Amber, onOpenSetup,
                    ),
                )
                if (ui.alwaysOn && !protectedBg) add(
                    Alert("PROTECT", "Background protection OFF — tap karke \"Unrestricted\" chuno", Hud.Amber) {
                        runCatching { context.startActivity(com.adesh.devjarvis.ui.setup.SetupIntents.ignoreBatteryOptimization(context)) }
                            .onFailure { runCatching { context.startActivity(com.adesh.devjarvis.ui.setup.SetupIntents.batteryOptimizationList()) } }
                    },
                )
                (update as? AppUpdater.State.Available)?.let { u -> add(Alert("UPDATE", "Naya version ${u.versionName} — tap karke install", Hud.Cyan, onOpenSettings)) }
            }
            Column(Modifier.fillMaxWidth().animateContentSize()) {
                alerts.firstOrNull()?.let { AlertLine(it, more = alerts.size - 1) }
            }

            // The reactor is the hero: as big as the space allows, smaller once a conversation needs the room.
            val hasChat = ui.transcript.any { !it.isSystem } || ui.partial.isNotEmpty()
            val reactorWeight by animateFloatAsState(if (hasChat) 0.9f else 1.7f, tween(500), label = "reactorWeight")
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(reactorWeight),
                contentAlignment = Alignment.Center,
            ) {
                // Reactor and its state line belong together: centred as one group, no gap between them.
                val side = minOf(maxWidth * 0.86f, maxHeight - 72.dp, 360.dp).coerceAtLeast(120.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ArcReactor(
                    state = ui.state,
                    micLevel = ui.micLevel,
                    speechLevel = speechLevel,
                    batteryPercent = ui.batteryPercent,
                    charging = ui.charging,
                    fuelPercent = ui.fuelPercent,
                    onTap = onTap,
                    accent = accent,
                    modifier = Modifier.size(side),
                )
                StateReadout(ui, accent, onTap)
                }
            }

            Column(Modifier.fillMaxWidth().animateContentSize()) {
                ui.pendingConfirmation?.let { q -> ConfirmCard(q, viewModel::answerConfirmation) }
                ui.card?.let { c -> ResultCard(c, viewModel::dismissCard) }
            }
            Transcript(ui, role, accent, Modifier.weight(if (hasChat) 1.1f else 0.35f))
            attachment?.let { a -> AttachmentChip(a.name, a.isImage, accent, onClear = viewModel::clearAttachment) }
            InputBar(
                accent,
                hasAttachment = attachment != null,
                research = research,
                onSend = viewModel::send,
                onPlus = { sheetOpen = true },
            )
        }

        val (skillCount, learnedCount) = remember(sheetOpen) { viewModel.skillCounts() }
        AddSheet(
            visible = sheetOpen,
            accent = accent,
            state = AddSheetState(research, autoMemory, skillCount, learnedCount),
            onDismiss = { sheetOpen = false },
            onCamera = {
                sheetOpen = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            onPhotos = {
                sheetOpen = false
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onFiles = {
                sheetOpen = false
                pickFile.launch(FILE_TYPES)
            },
            onSkills = { sheetOpen = false; onOpenSkills() },
            onConnectors = { sheetOpen = false; onOpenConnectors() },
            onResearch = viewModel::setResearch,
            onMemory = viewModel::setAutoMemory,
        )
        if (sheetOpen) BackHandler { sheetOpen = false }
    }
}

/** What Files offers: photos, PDFs, Word, text, CSV / JSON. */
private val FILE_TYPES = arrayOf(
    "image/*", "application/pdf", "text/*", "application/json",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

/** The file waiting to be sent: type a question, or just press send for "samjhao". */
@Composable
private fun AttachmentChip(name: String, isImage: Boolean, accent: Color, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .glass(accent, HudShapes.Chip, tint = 1f, edge = 0.8f)
            .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudLineIcon(if (isImage) LineIcon.PHOTO else LineIcon.FILE, accent, size = 20.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge, color = Hud.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Sawaal likhiye, ya seedha bhejiye — samjha dunga", style = MaterialTheme.typography.bodySmall, color = Hud.Dim, maxLines = 1)
        }
        IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Hatao", tint = Hud.Dim) }
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
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
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
        // Quick actions: light icons, no box around them.
        Row(verticalAlignment = Alignment.CenterVertically) {
            TopIcon(onReport) { Icon(Icons.Filled.Share, contentDescription = "Report", tint = accent.copy(alpha = 0.85f), modifier = Modifier.size(20.dp)) }
            TopIcon(onOpenMemory) { Icon(Icons.Filled.Person, contentDescription = "Memory", tint = accent.copy(alpha = 0.85f), modifier = Modifier.size(21.dp)) }
            TopIcon(onOpenActivity) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Activity log", tint = accent.copy(alpha = 0.85f), modifier = Modifier.size(21.dp)) }
            TopIcon(onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = accent, modifier = Modifier.size(22.dp)) }
        }
    }
}

@Composable
private fun TopIcon(onClick: () -> Unit, icon: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) { icon() }
}

/** Something that needs Sir's attention, shown as one slim line. */
private class Alert(val tag: String, val text: String, val color: Color, val action: () -> Unit)

/**
 * Everything about the system in ONE glass strip: four readouts (each taps to its page) and the wake-word switch.
 * Replaces five separate boxes that ate a third of the screen.
 */
@Composable
private fun StatusStrip(
    ui: HudUiState, role: String, accent: Color,
    onPower: () -> Unit, onNet: () -> Unit, onBrain: () -> Unit, onRole: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val battWarn = ui.batteryPercent in 0..19 && !ui.charging
    val online = ui.network != NetworkKind.NONE
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .glass(accent, HudShapes.Card, tint = 0.7f, edge = 0.55f),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Readout("PWR", if (ui.batteryPercent < 0) "--" else "${ui.batteryPercent}%" + if (ui.charging) "⚡" else "", if (battWarn) Hud.Amber else accent, Modifier.weight(1f), onPower)
            Hairline()
            Readout("NET", ui.network.label, if (online) accent else Hud.Amber, Modifier.weight(1f), onNet)
            Hairline()
            // The brain in use and how much of its free quota ("fuel") is left today.
            Readout(
                "BRAIN", ui.brainSource.ifBlank { "--" }, if (ui.fuelPercent < 15) Hud.Amber else accent, Modifier.weight(1.45f), onBrain,
                extra = "FUEL ${ui.fuelPercent}%",
                extraColor = when {
                    ui.fuelPercent < 15 -> Hud.Amber
                    ui.fuelPercent < 40 -> Hud.Ice
                    else -> Hud.Green
                },
            )
            Hairline()
            Readout(
                "ROOP", Persona.label(role).uppercase(),
                when (role) { Persona.PARTNER -> Hud.Pink; Persona.DOST -> Hud.Ice; else -> accent },
                Modifier.weight(1f), onRole,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hud.Line.copy(alpha = 0.6f)))
        val tone by animateColorAsState(if (ui.alwaysOn) accent else Hud.Dim, tween(500), label = "wake")
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WakeRadar(on = ui.alwaysOn, color = tone)
            val sub = ui.wakeNotice
                ?: if (ui.alwaysOn && ui.wakeHeard.isNotBlank()) "heard: ${ui.wakeHeard}" else null
                ?: if (ui.alwaysOn) "\"Hello Dev\" boliye — screen off bhi" else "Wake word off — reactor tap karke baat"
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = if (ui.wakeNotice != null) Hud.Amber else if (ui.alwaysOn) Hud.Ice.copy(alpha = 0.85f) else Hud.Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 10.dp, end = 8.dp),
            )
            Switch(
                checked = ui.alwaysOn,
                onCheckedChange = onToggle,
                modifier = Modifier.graphicsLayer { scaleX = 0.82f; scaleY = 0.82f },
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
}

@Composable
private fun Readout(
    label: String, value: String, color: Color, modifier: Modifier, onClick: () -> Unit,
    extra: String? = null, extraColor: Color = color,
) {
    val tint by animateColorAsState(color, tween(500), label = "readout")
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.5.sp), color = Hud.Dim, maxLines = 1)
            if (extra != null) {
                Text(
                    "  $extra",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold),
                    color = extraColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(Hud.Line.copy(alpha = 0.7f)))
}

/** One slim line: tag, message, "+n" when more are waiting, chevron. Tap = fix it. */
@Composable
private fun AlertLine(alert: Alert, more: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(HudShapes.Chip)
            .background(alert.color.copy(alpha = 0.10f), HudShapes.Chip)
            .border(1.dp, alert.color.copy(alpha = 0.35f), HudShapes.Chip)
            .clickable(onClick = alert.action)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudTag(alert.tag, alert.color)
        Text(
            alert.text,
            style = MaterialTheme.typography.bodySmall,
            color = Hud.White.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        if (more > 0) Text("+$more", style = MaterialTheme.typography.labelSmall, color = alert.color, modifier = Modifier.padding(end = 6.dp))
        Text("›", style = MaterialTheme.typography.titleMedium, color = alert.color)
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

/** Live state under the reactor: its name and what a tap does now. */
@Composable
private fun StateReadout(ui: HudUiState, accent: Color, onTap: () -> Unit) {
    val followUp = ui.state == JarvisState.LISTENING && ui.inConversation && ui.transcript.lastOrNull()?.fromUser == false
    val label = if (followUp) "FOLLOW-UP" else ui.state.label
    val color by animateColorAsState(
        when (ui.state) {
            JarvisState.IDLE -> Hud.Ice.copy(alpha = 0.55f)
            JarvisState.THINKING -> Hud.Ice
            else -> accent
        },
        tween(450),
        label = "stateColor",
    )
    val hint = when (ui.state) {
        JarvisState.IDLE -> if (ui.alwaysOn) "\"Hello Dev\" boliye ya reactor tap kijiye" else "Reactor tap kijiye aur boliye"
        JarvisState.LISTENING -> if (followUp) "Boliye — sun raha hoon" else "Sun raha hoon · tap = band"
        JarvisState.THINKING -> "Soch raha hoon · tap = roko"
        JarvisState.SPEAKING -> "Bol raha hoon · tap = roko"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .padding(top = 2.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Crossfade(targetState = label, animationSpec = tween(250), label = "stateLabel") { text ->
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                color = color,
                maxLines = 1,
            )
        }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ui.mode.equals("FALLBACK", ignoreCase = true) || ui.mode.equals("LIVE", ignoreCase = true)) {
                HudTag(ui.mode.uppercase(), if (ui.mode.equals("FALLBACK", ignoreCase = true)) Hud.Amber else accent, Modifier.padding(end = 8.dp))
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Hud.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "FUEL ${ui.fuelPercent}%  ·  PWR ${if (ui.batteryPercent < 0) "--" else "${ui.batteryPercent}%"}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.5.sp),
            color = if (ui.fuelPercent < 15) Hud.Amber else accent.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 3.dp),
        )
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
    // No box: messages float over the HUD and fade out at the top edge.
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.14f to Color.Black, 1f to Color.Black),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Tool steps ("⚙ / ✓ / ✗ set_toggle: …") are for the Activity log, not the conversation.
            items(ui.transcript.filterNot { it.isSystem && it.text.firstOrNull() in TOOL_MARKS }, key = { it.id }) { line ->
                when {
                    line.isSystem -> Text(
                        line.text,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Hud.Dim.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    line.fromUser -> UserBubble(line.text, accent)
                    else -> {
                        // Each line keeps the role it was spoken in.
                        val r = line.role.ifBlank { role }
                        AssistantBubble(Persona.label(r).uppercase(), line.text, if (r == Persona.PARTNER) Hud.Pink else Hud.Cyan)
                    }
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
private fun InputBar(
    accent: Color,
    hasAttachment: Boolean,
    research: Boolean,
    onSend: (String) -> Unit,
    onPlus: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (text.isNotBlank() || hasAttachment) {
            onSend(text)
            text = ""
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ready = text.isNotBlank() || hasAttachment
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
        // + : photo, camera, file, skills, connectors, research, memory.
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f), CircleShape)
                .border(1.dp, accent.copy(alpha = 0.40f), CircleShape)
                .clickable(onClick = onPlus),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "JARVIS ko do: photo, file, skills", tint = accent)
            if (research) {
                Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(7.dp).background(Hud.Green, CircleShape))
            }
        }
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
                        Text(
                            when {
                                hasAttachment -> "Is file ke baare mein poochiye…"
                                research -> "Research: kuch bhi poochiye…"
                                else -> "Type to JARVIS…"
                            },
                            style = MaterialTheme.typography.bodyLarge, color = Hud.Dim, maxLines = 1,
                        )
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

private val TOOL_MARKS = setOf('⚙', '✓', '✗', '?')

private fun openSystem(context: android.content.Context, action: String) {
    runCatching { context.startActivity(android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
