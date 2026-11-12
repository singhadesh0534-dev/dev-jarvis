package com.adesh.devjarvis.ui.hud

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.adesh.devjarvis.brain.Persona
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adesh.devjarvis.core.HudCard
import com.adesh.devjarvis.core.HudUiState
import com.adesh.devjarvis.core.JarvisState
import com.adesh.devjarvis.core.NetworkKind
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.update.AppUpdater

@Composable
fun HudScreen(
    viewModel: HudViewModel,
    onOpenSettings: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMemory: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val speechLevel by viewModel.speechLevel.collectAsStateWithLifecycle()
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

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDevice() }

    val onTap = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onReactorTap() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(listOf(Hud.BackgroundGlow, Hud.Background), radius = 1400f)
            )
            .hudGrid()
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            val role by viewModel.role.collectAsStateWithLifecycle()
            TopBar(onOpenSettings, onOpenActivity, onOpenMemory)
            StatusRow(ui, role)
            AlwaysOnRow(ui, onToggle = onAlwaysOnToggle)
            if (!ui.hasKey) KeyBanner(onOpenSettings)
            val update by viewModel.update.collectAsStateWithLifecycle()
            (update as? AppUpdater.State.Available)?.let { u ->
                Text(
                    "⬆  Naya JARVIS ${u.versionName} available — Settings > Updates",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Hud.Cyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .border(1.dp, Hud.Cyan.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .clickable(onClick = onOpenSettings)
                        .padding(10.dp),
                )
            }

            Box(
                Modifier.fillMaxWidth().weight(1f, fill = false),
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
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(0.86f).aspectRatio(1f),
                )
            }
            Text(
                if (ui.state == JarvisState.LISTENING && ui.inConversation && ui.transcript.lastOrNull()?.fromUser == false) "FOLLOW-UP" else ui.state.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (ui.state == JarvisState.IDLE) Hud.Dim else Hud.Cyan,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 8.dp),
            )

            ui.pendingConfirmation?.let { q -> ConfirmCard(q, viewModel::answerConfirmation) }
            ui.card?.let { c -> ResultCard(c, viewModel::dismissCard) }
            Transcript(ui, role, Modifier.weight(1f))
            InputRow(onSend = viewModel::onTextSubmitted, onFile = viewModel::onFilePicked)
        }
    }
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit, onOpenActivity: () -> Unit, onOpenMemory: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "online")
    val dot by pulse.animateFloat(0.25f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot")
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "DEV JARVIS",
                style = MaterialTheme.typography.headlineSmall.copy(
                    brush = Brush.linearGradient(listOf(Hud.Ice, Hud.Cyan, Hud.Cyan.copy(alpha = 0.7f))),
                    shadow = Shadow(color = Hud.Cyan.copy(alpha = 0.8f), blurRadius = 18f),
                    letterSpacing = 5.sp,
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(Modifier.size(6.dp).alpha(dot).background(Hud.Green, CircleShape))
                Text(
                    "  NEURAL CORE ONLINE  ·  ",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Hud.Dim,
                )
                Text(
                    "BUILT BY DEV",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp, letterSpacing = 2.5.sp,
                        shadow = Shadow(color = Hud.Amber.copy(alpha = 0.9f), blurRadius = 10f),
                    ),
                    color = Hud.Amber,
                )
            }
        }
        IconButton(onClick = onOpenMemory) {
            Icon(Icons.Filled.Person, contentDescription = "Memory", tint = Hud.Cyan)
        }
        IconButton(onClick = onOpenActivity) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Activity log", tint = Hud.Cyan)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Hud.Cyan)
        }
    }
}

@Composable
private fun StatusRow(ui: HudUiState, role: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val battWarn = ui.batteryPercent in 0..19 && !ui.charging
        Chip("PWR", if (ui.batteryPercent < 0) "--" else "${ui.batteryPercent}%", battWarn, Modifier.weight(1f))
        Chip("NET", ui.network.label, ui.network == NetworkKind.NONE, Modifier.weight(1f))
        Chip("FUEL", "${ui.fuelPercent}%", ui.fuelPercent < 15, Modifier.weight(1f))
        // Changed by voice only: "dost ban jao", "gf ban jao", "Jarvis ban jao".
        Chip("ROOP", Persona.label(role).uppercase(), false, Modifier.weight(1f), accent = role != Persona.JARVIS)
    }
}

@Composable
private fun Chip(label: String, value: String, warn: Boolean, modifier: Modifier, accent: Boolean = false) {
    val color = when {
        warn -> Hud.Amber
        accent -> Hud.Pink
        else -> Hud.Cyan
    }
    Column(
        modifier
            .hudFrame(color.copy(alpha = 0.8f), corner = 7f)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.10f), Hud.Panel.copy(alpha = 0.35f))))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = Hud.Dim)
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, shadow = Shadow(color = color.copy(alpha = 0.7f), blurRadius = 8f)),
            color = color, maxLines = 1,
        )
    }
}

@Composable
private fun AlwaysOnRow(ui: HudUiState, onToggle: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (ui.alwaysOn) "◉  ALWAYS LISTENING" else "○  WAKE WORD OFF",
                    style = MaterialTheme.typography.labelMedium.copy(
                        shadow = if (ui.alwaysOn) Shadow(color = Hud.Cyan.copy(alpha = 0.7f), blurRadius = 10f) else null,
                    ),
                    color = if (ui.alwaysOn) Hud.Cyan else Hud.Dim,
                )
                val sub = ui.wakeNotice
                    ?: if (ui.alwaysOn && ui.wakeHeard.isNotBlank()) "heard: ${ui.wakeHeard}" else null
                    ?: if (ui.alwaysOn) "Listening for wake word — screen off bhi chalega" else "Off — sirf tap se baat"
                Text(sub, style = MaterialTheme.typography.bodySmall, color = if (ui.wakeNotice != null) Hud.Amber else Hud.Dim, maxLines = 1)
            }
            Switch(
                checked = ui.alwaysOn,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Hud.Background,
                    checkedTrackColor = Hud.Cyan,
                    uncheckedThumbColor = Hud.Dim,
                    uncheckedTrackColor = Hud.Faint,
                ),
            )
        }
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
            .border(1.dp, accent, RoundedCornerShape(6.dp))
            .background(Hud.Panel, RoundedCornerShape(6.dp))
            .clickable(onClick = onDismiss)
            .padding(12.dp),
    ) {
        Text(if (card.ok) card.title else "${card.title} · FAILED", style = MaterialTheme.typography.labelSmall, color = accent)
        Text(card.body, style = MaterialTheme.typography.bodyMedium, color = Hud.White)
    }
}

@Composable
private fun ConfirmCard(question: String, onAnswer: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .border(1.dp, Hud.Amber, RoundedCornerShape(6.dp))
            .background(Hud.Panel, RoundedCornerShape(6.dp))
            .padding(12.dp),
    ) {
        Text("CONFIRM", style = MaterialTheme.typography.labelSmall, color = Hud.Amber)
        Text(question, style = MaterialTheme.typography.bodyLarge, color = Hud.White)
        Text("Boliye \"haan\" / \"nahi\" — ya tap kijiye", style = MaterialTheme.typography.bodySmall, color = Hud.Dim)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onAnswer(true) },
                colors = ButtonDefaults.buttonColors(containerColor = Hud.Cyan, contentColor = Hud.Background),
            ) { Text("YES", style = MaterialTheme.typography.labelMedium) }
            OutlinedButton(onClick = { onAnswer(false) }) {
                Text("NO", style = MaterialTheme.typography.labelMedium, color = Hud.Amber)
            }
        }
    }
}

@Composable
private fun KeyBanner(onOpenSettings: () -> Unit) {
    Text(
        "⚠  Gemini API key missing — tap to open Settings",
        style = MaterialTheme.typography.bodyMedium,
        color = Hud.Amber,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hud.Amber.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .clickable(onClick = onOpenSettings)
            .padding(10.dp),
    )
}

@Composable
private fun Transcript(ui: HudUiState, role: String, modifier: Modifier) {
    val listState = rememberLazyListState()
    val lastLength = ui.transcript.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(ui.transcript.size, lastLength, ui.partial) {
        val count = ui.transcript.size + if (ui.partial.isNotEmpty()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    val speaker = Persona.label(role).uppercase()
    val voiceColor = if (role == Persona.PARTNER) Hud.Pink else Hud.Cyan
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .hudFrame(Hud.Cyan.copy(alpha = 0.55f), corner = 14f)
            .background(Hud.Panel.copy(alpha = 0.30f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ui.transcript, key = { it.id }) { line ->
            when {
                line.isSystem -> Text(
                    "▸ " + line.text,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Hud.Dim.copy(alpha = 0.85f),
                    maxLines = 2,
                    modifier = Modifier.padding(start = 4.dp),
                )
                line.fromUser -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        line.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Hud.Ice,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .background(Hud.Faint.copy(alpha = 0.55f), RoundedCornerShape(14.dp, 14.dp, 3.dp, 14.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
                else -> Column(
                    Modifier
                        .fillMaxWidth(0.92f)
                        .background(
                            Brush.horizontalGradient(listOf(voiceColor.copy(alpha = 0.14f), Color.Transparent)),
                            RoundedCornerShape(3.dp, 14.dp, 14.dp, 14.dp),
                        )
                        .drawBehind {
                            drawRect(voiceColor, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
                        }
                        .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
                ) {
                    Text(speaker, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = voiceColor)
                    Text(line.text, style = MaterialTheme.typography.bodyLarge, color = Hud.White)
                }
            }
        }
        if (ui.partial.isNotEmpty()) {
            item(key = "partial") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        ui.partial + " …",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = Hud.Dim,
                    )
                }
            }
        }
    }
}

@Composable
private fun InputRow(onSend: (String) -> Unit, onFile: (android.net.Uri) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onFile(uri) }
    val send = {
        if (text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Type to JARVIS…", style = MaterialTheme.typography.bodyMedium, color = Hud.Dim) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Hud.Cyan,
                unfocusedBorderColor = Hud.Cyan.copy(alpha = 0.35f),
                cursorColor = Hud.Cyan,
                focusedContainerColor = Hud.Panel.copy(alpha = 0.6f),
                unfocusedContainerColor = Hud.Panel.copy(alpha = 0.4f),
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1f),
        )
        // Photo / PDF: JARVIS looks at it and explains.
        TextButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }) {
            Text("FILE", style = MaterialTheme.typography.labelMedium, color = Hud.Cyan)
        }
        IconButton(onClick = send) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Hud.Cyan)
        }
    }
}

/** Faint moving grid with a scan line, behind the whole HUD. */
@Composable
private fun Modifier.hudGrid(): Modifier {
    val t = rememberInfiniteTransition(label = "grid")
    val scan by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "scan")
    return drawBehind {
        val step = 28.dp.toPx()
        val line = Hud.Cyan.copy(alpha = 0.035f)
        var x = 0f
        while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height)); x += step }
        var y = 0f
        while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y)); y += step }
        val sy = size.height * scan
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Hud.Cyan.copy(alpha = 0.07f), Color.Transparent), startY = sy - 60f, endY = sy + 60f),
            topLeft = Offset(0f, sy - 60f), size = androidx.compose.ui.geometry.Size(size.width, 120f),
        )
    }
}

/** Thin outline with bright cut corners — the HUD panel look. */
private fun Modifier.hudFrame(color: Color, corner: Float = 10f): Modifier = drawBehind {
    val c = corner.dp.toPx()
    val w = 1.5.dp.toPx()
    drawRect(color.copy(alpha = color.alpha * 0.35f), style = Stroke(1.dp.toPx()))
    listOf(
        Offset(0f, 0f) to Offset(1f, 1f), Offset(size.width, 0f) to Offset(-1f, 1f),
        Offset(0f, size.height) to Offset(1f, -1f), Offset(size.width, size.height) to Offset(-1f, -1f),
    ).forEach { (p, d) ->
        drawLine(color, p, Offset(p.x + d.x * c, p.y), strokeWidth = w)
        drawLine(color, p, Offset(p.x, p.y + d.y * c), strokeWidth = w)
    }
}
