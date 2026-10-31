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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            TopBar(onOpenSettings, onOpenActivity, onOpenMemory)
            StatusRow(ui)
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
            Transcript(ui, Modifier.weight(1f))
            InputRow(onSend = viewModel::onTextSubmitted)
        }
    }
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit, onOpenActivity: () -> Unit, onOpenMemory: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("J.A.R.V.I.S.", style = MaterialTheme.typography.headlineSmall, color = Hud.Cyan)
            Text("JUST A RATHER VERY INTELLIGENT SYSTEM", style = MaterialTheme.typography.labelSmall, color = Hud.Dim)
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
private fun StatusRow(ui: HudUiState) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val battWarn = ui.batteryPercent in 0..19 && !ui.charging
        Chip("PWR", if (ui.batteryPercent < 0) "--" else "${ui.batteryPercent}%", battWarn, Modifier.weight(1f))
        Chip("NET", ui.network.label, ui.network == NetworkKind.NONE, Modifier.weight(1f))
        Chip("MODE", if (ui.alwaysOn) "ALWAYS-ON" else ui.mode, false, Modifier.weight(1f))
        Chip("FUEL", "${ui.fuelPercent}%", ui.fuelPercent < 15, Modifier.weight(1f))
    }
}

@Composable
private fun Chip(label: String, value: String, warn: Boolean, modifier: Modifier) {
    val color = if (warn) Hud.Amber else Hud.Cyan
    Column(
        modifier
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .background(Hud.Panel.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Hud.Dim)
        Text(value, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Composable
private fun AlwaysOnRow(ui: HudUiState, onToggle: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "\u201CHELLO DEV\u201D ALWAYS-ON",
                    style = MaterialTheme.typography.labelMedium,
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
private fun Transcript(ui: HudUiState, modifier: Modifier) {
    val listState = rememberLazyListState()
    val lastLength = ui.transcript.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(ui.transcript.size, lastLength, ui.partial) {
        val count = ui.transcript.size + if (ui.partial.isNotEmpty()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Hud.Faint, RoundedCornerShape(6.dp))
            .background(Hud.Panel.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(ui.transcript, key = { it.id }) { line ->
            when {
                line.isSystem -> Text(line.text, style = MaterialTheme.typography.bodySmall, color = Hud.Dim)
                line.fromUser -> Row {
                    Text("YOU › ", style = MaterialTheme.typography.labelSmall, color = Hud.Dim, modifier = Modifier.padding(top = 3.dp))
                    Text(line.text, style = MaterialTheme.typography.bodyMedium, color = Hud.Dim)
                }
                else -> Row {
                    Text("JARVIS › ", style = MaterialTheme.typography.labelSmall, color = Hud.Cyan, modifier = Modifier.padding(top = 4.dp))
                    Text(line.text, style = MaterialTheme.typography.bodyLarge, color = Hud.White)
                }
            }
        }
        if (ui.partial.isNotEmpty()) {
            item(key = "partial") {
                Text(
                    ui.partial + " …",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = Hud.Dim.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun InputRow(onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
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
            placeholder = { Text("Type to JARVIS…", style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Hud.Cyan,
                unfocusedBorderColor = Hud.Faint,
                cursorColor = Hud.Cyan,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = send) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Hud.Cyan)
        }
    }
}
