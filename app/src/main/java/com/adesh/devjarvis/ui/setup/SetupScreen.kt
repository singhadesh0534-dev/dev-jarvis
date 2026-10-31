package com.adesh.devjarvis.ui.setup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adesh.devjarvis.core.Edition
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.wakeword.VoskModelManager

/** First-launch setup: ek screen per permission, "Open setting" button, aur green tick jab ho jaaye. */
@Composable
fun SetupScreen(viewModel: SetupViewModel, onDone: () -> Unit) {
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    var index by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    // Permissions the user denied; next tap opens app settings instead (Android stops showing the dialog).
    val denied = remember { mutableStateMapOf<String, Boolean>() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val step = steps.getOrNull(index)
        if (!granted && step?.permission != null) denied[step.permission] = true
        viewModel.refresh()
    }

    val step = steps.getOrNull(index.coerceIn(0, steps.lastIndex)) ?: return
    val isLast = index >= steps.lastIndex

    fun runAction() {
        when (step.action) {
            StepAction.RUNTIME_PERMISSION -> {
                val perm = step.permission ?: return
                val activity = context as? Activity
                val canAsk = denied[perm] != true ||
                    (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, perm))
                if (canAsk) permissionLauncher.launch(perm)
                else SetupIntents.launchFirst(context, listOf(SetupIntents.appDetails(context)))
            }
            StepAction.DOWNLOAD -> viewModel.downloadModel()
            StepAction.OPEN_SETTINGS, StepAction.MANUAL_CONFIRM -> {
                val intents = when (step.id) {
                    StepId.BATTERY ->
                        if (Edition.declares(context, android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) {
                            listOf(SetupIntents.ignoreBatteryOptimization(context), SetupIntents.appDetails(context))
                        } else {
                            listOf(SetupIntents.batteryOptimizationList(), SetupIntents.appDetails(context))
                        }
                    StepId.XOS_AUTOSTART -> SetupIntents.xosAutoStart(context)
                    StepId.OVERLAY -> listOf(SetupIntents.overlay(context))
                    StepId.ACCESSIBILITY -> listOf(SetupIntents.accessibility())
                    StepId.NOTIFICATION_ACCESS -> listOf(SetupIntents.notificationAccess())
                    StepId.WRITE_SETTINGS -> listOf(SetupIntents.writeSettings(context))
                    StepId.ASSISTANT -> SetupIntents.defaultAssistant()
                    else -> listOf(SetupIntents.appDetails(context))
                }
                SetupIntents.launchFirst(context, intents)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Hud.Background)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Text("SYSTEM SETUP", style = MaterialTheme.typography.headlineSmall, color = Hud.Cyan)
        Text("Step ${index + 1} / ${steps.size}", style = MaterialTheme.typography.labelSmall, color = Hud.Dim)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, s ->
                val color = when {
                    s.granted -> Hud.Green
                    i == index -> Hud.Cyan
                    else -> Hud.Faint
                }
                Box(Modifier.size(10.dp).background(color, CircleShape))
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.title.uppercase(), style = MaterialTheme.typography.titleMedium, color = Hud.Ice, modifier = Modifier.weight(1f))
                StatusBadge(step.granted, step.optional)
            }
            Text(step.why, style = MaterialTheme.typography.bodyLarge, color = Hud.White)
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Hud.Faint, RoundedCornerShape(6.dp))
                    .background(Hud.Panel, RoundedCornerShape(6.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                step.instructions.forEachIndexed { i, line ->
                    Row {
                        Text("${i + 1}. ", style = MaterialTheme.typography.bodyMedium, color = Hud.Cyan)
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = Hud.Ice)
                    }
                }
            }

            if (step.id == StepId.WAKE_MODEL) ModelProgress(modelState)

            if (!step.granted) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (step.actionLabel.isNotEmpty()) {
                        Button(
                            onClick = ::runAction,
                            enabled = !(step.id == StepId.WAKE_MODEL && modelState is VoskModelManager.State.Downloading),
                            colors = ButtonDefaults.buttonColors(containerColor = Hud.Cyan, contentColor = Hud.Background),
                        ) { Text(step.actionLabel.uppercase(), style = MaterialTheme.typography.labelMedium) }
                    }
                    if (step.action == StepAction.MANUAL_CONFIRM || step.id == StepId.ASSISTANT) {
                        OutlinedButton(onClick = { viewModel.confirmManual(step.id) }) {
                            Text("HO GAYA ✓", style = MaterialTheme.typography.labelMedium, color = Hud.Green)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (index > 0) TextButton(onClick = { index-- }) { Text("BACK", color = Hud.Dim) }
            Spacer(Modifier.weight(1f))
            if (!isLast) {
                TextButton(onClick = { index++ }) {
                    Text(if (step.granted) "NEXT" else "SKIP", color = Hud.Cyan, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Button(
                    onClick = {
                        viewModel.finish()
                        onDone()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Hud.Cyan, contentColor = Hud.Background),
                ) { Text("FINISH", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun StatusBadge(granted: Boolean, optional: Boolean) {
    val (text, color) = when {
        granted -> "✓ DONE" to Hud.Green
        optional -> "OPTIONAL" to Hud.Dim
        else -> "PENDING" to Hud.Amber
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ModelProgress(state: VoskModelManager.State) {
    when (state) {
        is VoskModelManager.State.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                color = Hud.Cyan,
                trackColor = Hud.Faint,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Downloading… ${state.percent}%", style = MaterialTheme.typography.bodySmall, color = Hud.Dim)
        }
        is VoskModelManager.State.Failed ->
            Text("Download fail: ${state.message}. Internet check karke phir try karo.", style = MaterialTheme.typography.bodySmall, color = Hud.Amber)
        else -> Unit
    }
}
