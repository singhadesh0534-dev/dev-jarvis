package com.adesh.devjarvis.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adesh.devjarvis.core.JarvisConfig
import com.adesh.devjarvis.settings.JarvisSettings
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.voice.VoiceInfo

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenMemory: () -> Unit,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    var showVoices by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Hud.Background)
            .safeDrawingPadding()
            .imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Hud.Cyan)
            }
            Text("SETTINGS", style = MaterialTheme.typography.headlineSmall, color = Hud.Cyan)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("API KEYS")
            SecretField(
                label = "Gemini API key",
                value = draft.geminiApiKey,
                onChange = { v -> viewModel.edit { it.copy(geminiApiKey = v) } },
            )
            LinkText("Free key yahan se lo: aistudio.google.com/apikey", JarvisConfig.GEMINI_KEY_URL)
            SecretField(
                label = "Groq API key (backup brain + Whisper)",
                value = draft.groqApiKey,
                onChange = { v -> viewModel.edit { it.copy(groqApiKey = v) } },
            )
            LinkText("Free key: console.groq.com/keys", JarvisConfig.GROQ_KEY_URL)

            Section("VOICE ENGINE")
            ChoiceRow(
                options = listOf(JarvisSettings.VOICE_LIVE to "LIVE", JarvisSettings.VOICE_CLASSIC to "CLASSIC"),
                selected = draft.voiceEngine,
            ) { v -> viewModel.edit { it.copy(voiceEngine = v) } }
            Hint(
                "LIVE = Gemini Live: asli awaaz, sabse tez, beech mein bol ke tok sakte ho. Quota/error pe khud CLASSIC pe aa jaata hai. " +
                    "CLASSIC = sunna -> text -> Android TTS (offline voice, kam data)."
            )
            HudField("Live model", draft.liveModel, { v -> viewModel.edit { it.copy(liveModel = v) } })
            HudField("Live voice", draft.liveVoice, { v -> viewModel.edit { it.copy(liveVoice = v) } })
            Hint("Voices: Charon (deep, calm — default), Orus (firm), Fenrir, Puck, Kore, Aoede.")
            Text("Classic mode ears", style = MaterialTheme.typography.bodyMedium, color = Hud.Ice)
            ChoiceRow(
                options = listOf(JarvisSettings.STT_ANDROID to "ANDROID", JarvisSettings.STT_GROQ to "GROQ WHISPER"),
                selected = draft.sttEngine,
            ) { v -> viewModel.edit { it.copy(sttEngine = v) } }
            Hint("Groq Whisper Hinglish behtar samajhta hai (Groq key chahiye).")

            Section("BRAIN")
            HudField("Gemini model", draft.geminiModel, { v -> viewModel.edit { it.copy(geminiModel = v) } })
            Hint("Default: ${JarvisConfig.GEMINI_MODEL}. Naya model aaye to naam yahan badlo.")
            TextButton(onClick = viewModel::resetModel) { Text("Reset model", color = Hud.Cyan) }
            HudField("Groq backup model", draft.groqModel, { v -> viewModel.edit { it.copy(groqModel = v) } })
            Hint("Gemini ka free quota khatam (429) ho to JARVIS chupchaap Groq pe chala jaata hai. HUD pe MODE: FALLBACK dikhega.")

            HudField("Home city (weather)", draft.homeCity, { v -> viewModel.edit { it.copy(homeCity = v) } })
            Hint("Jaise: Mumbai. \"Mausam kaisa hai?\" bina city ke pucho to yahi use hoga.")

            Section("VOICE")
            Text("Speech language (sunne ki bhasha)", style = MaterialTheme.typography.bodyMedium, color = Hud.Ice)
            ChoiceRow(JarvisConfig.STT_LANGUAGES.map { it to it }, draft.sttLanguage) { lang ->
                viewModel.edit { it.copy(sttLanguage = lang) }
            }
            Hint("Hinglish ke liye en-IN best hai. Zyada Hindi bolte ho to hi-IN try karo.")

            Text(
                "Voice: " + draft.ttsVoiceName.ifBlank { "Auto (deep male UK/India)" },
                style = MaterialTheme.typography.bodyMedium,
                color = Hud.Ice,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showVoices = true }) { Text("Choose voice", color = Hud.Cyan) }
                OutlinedButton(onClick = viewModel::testVoice) { Text("Test voice", color = Hud.Cyan) }
                TextButton(onClick = viewModel::resetVoice) { Text("Reset", color = Hud.Cyan) }
            }
            LabeledSlider("Pitch", draft.ttsPitch, 0.5f..1.5f) { v -> viewModel.edit { it.copy(ttsPitch = v) } }
            LabeledSlider("Speed", draft.ttsRate, 0.6f..1.5f) { v -> viewModel.edit { it.copy(ttsRate = v) } }

            Section("WAKE WORD & CONVERSATION")
            Hint("Wake phrases: \"Hello Dev\", \"Jarvis\". Always-on switch HUD pe hai.")
            LabeledSlider("Sensitivity", draft.wakeSensitivity, 0f..1f) { v -> viewModel.edit { it.copy(wakeSensitivity = v) } }
            Hint("Kam = sirf saaf bola hua wake word. Zyada = jaldi jaagta hai, par galti se bhi jaag sakta hai. 0.8+ pe sabse tez.")
            ToggleRow(
                "Continuous conversation",
                "Jawab ke baad ~8 sec bina wake word ke follow-up sunta hai.",
                draft.continuousConversation,
            ) { v -> viewModel.edit { it.copy(continuousConversation = v) } }
            ToggleRow(
                "Voice barge-in",
                "JARVIS bolte waqt \"Jarvis\" / \"stop\" / \"ruko\" bolke rok do (always-on ke saath).",
                draft.voiceBargeIn,
            ) { v -> viewModel.edit { it.copy(voiceBargeIn = v) } }
            OutlinedButton(onClick = onOpenSetup) { Text("Setup & permissions checklist", color = Hud.Cyan) }

            Section("PROACTIVE JARVIS")
            ToggleRow("Morning briefing", "Weather, aaj ke events/reminders, battery + ek witty line.", draft.briefingEnabled) { v ->
                viewModel.edit { it.copy(briefingEnabled = v) }
            }
            TimeField("Briefing time", draft.briefingMinute) { m -> viewModel.edit { it.copy(briefingMinute = m) } }
            OutlinedButton(onClick = viewModel::playBriefingNow) { Text("Play briefing now", color = Hud.Cyan) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { TimeField("Quiet from", draft.quietStartMinute) { m -> viewModel.edit { it.copy(quietStartMinute = m) } } }
                Box(Modifier.weight(1f)) { TimeField("Quiet until", draft.quietEndMinute) { m -> viewModel.edit { it.copy(quietEndMinute = m) } } }
            }
            Hint("Quiet hours, DND, silent mode (bina headphones) ya call ke dauraan JARVIS khud se nahi bolta — sirf notification. Aapke apne reminders raat mein bhi bolte hain.")
            ToggleRow("Important notifications bolna", "Chune hue apps/logon ke message ka chhota summary. OTP/bank kabhi nahi.", draft.notificationAlerts) { v ->
                viewModel.edit { it.copy(notificationAlerts = v) }
            }
            HudField("Important apps (comma)", draft.importantApps, { v -> viewModel.edit { it.copy(importantApps = v) } })
            HudField("Important people (comma)", draft.importantContacts, { v -> viewModel.edit { it.copy(importantContacts = v) } })
            Hint("Jaise: Mummy, Papa, Boss. In logon ka message kisi bhi app se aaye to JARVIS batayega.")
            ToggleRow("Low battery warning", "20% se neeche (charging nahi) — aur 10% pe dobara.", draft.lowBatteryAlert) { v ->
                viewModel.edit { it.copy(lowBatteryAlert = v) }
            }

            Section("MEMORY")
            Hint("JARVIS baat-cheet se khud zaruri baatein yaad rakhta hai (sirf phone pe). Yahan dekho, badlo, mitao.")
            OutlinedButton(onClick = onOpenMemory) { Text("Open memory", color = Hud.Cyan) }

            Section("EXTRAS")
            ToggleRow("Floating mini-reactor", "Doosre apps ke upar chhota reactor jab JARVIS sun/bol raha ho (\"Display over other apps\" chahiye). Tap = kholo, long-press = rok do.", draft.bubbleEnabled) { v ->
                viewModel.edit { it.copy(bubbleEnabled = v) }
            }
            ToggleRow("Sound effects", "Chime, success blip, error tone. (Haptic tick hamesha.)", draft.soundEffects) { v ->
                viewModel.edit { it.copy(soundEffects = v) }
            }
            ToggleRow("Battery saver", "15% se neeche (charging nahi) wake word pause. Tap/home long-press chalte rahenge.", draft.pauseWakeOnLowBattery) { v ->
                viewModel.edit { it.copy(pauseWakeOnLowBattery = v) }
            }
            ToggleRow("Boot animation", "App kholte hi \"JARVIS ONLINE\" system check.", draft.bootAnimation) { v ->
                viewModel.edit { it.copy(bootAnimation = v) }
            }

            Section("DIAGNOSTICS")
            DiagnosticsBlock(viewModel)

            Section("PERSONALITY")
            OutlinedTextField(
                value = draft.systemPrompt,
                onValueChange = { v -> viewModel.edit { it.copy(systemPrompt = v) } },
                textStyle = MaterialTheme.typography.bodySmall,
                colors = hudFieldColors(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::resetPrompt) { Text("Reset personality", color = Hud.Cyan) }
                TextButton(onClick = viewModel::clearConversation) { Text("Clear chat memory", color = Hud.Cyan) }
            }
            Spacer(Modifier.padding(8.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (saved) "Saved ✓" else "",
                style = MaterialTheme.typography.labelMedium,
                color = Hud.Cyan,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = viewModel::save,
                colors = ButtonDefaults.buttonColors(containerColor = Hud.Cyan, contentColor = Hud.Background),
            ) { Text("SAVE", style = MaterialTheme.typography.labelMedium) }
        }
    }

    if (showVoices) {
        VoicePicker(
            voices = remember { viewModel.voices() },
            current = draft.ttsVoiceName.ifBlank { viewModel.activeVoice().orEmpty() },
            onPick = { name ->
                viewModel.edit { it.copy(ttsVoiceName = name) }
                showVoices = false
            },
            onDismiss = { showVoices = false },
        )
    }
}

@Composable
private fun VoicePicker(voices: List<VoiceInfo>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Hud.Cyan) } },
        title = { Text("VOICE", style = MaterialTheme.typography.titleMedium, color = Hud.Cyan) },
        containerColor = Hud.Panel,
        text = {
            if (voices.isEmpty()) {
                Text(
                    "Koi English voice nahi mili. Play Store se 'Speech Services by Google' install karo, " +
                        "phir Settings > Text-to-speech mein English (India/UK) voice data download karo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(voices, key = { it.name }) { v ->
                        val selected = v.name == current
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(v.name) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                v.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) Hud.Cyan else Hud.Ice,
                            )
                            Text(
                                "${v.locale} · ${if (v.offline) "offline" else "needs internet"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Hud.Dim,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = Hud.Cyan, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Hud.Dim)
}

@Composable
private fun LinkText(text: String, url: String) {
    val context = LocalContext.current
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Hud.Ice,
        modifier = Modifier.clickable {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        },
    )
}

@Composable
private fun hudFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Hud.Cyan,
    unfocusedBorderColor = Hud.Faint,
    cursorColor = Hud.Cyan,
    focusedLabelColor = Hud.Cyan,
    unfocusedLabelColor = Hud.Dim,
)

@Composable
private fun HudField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = hudFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "HIDE" else "SHOW", style = MaterialTheme.typography.labelSmall, color = Hud.Cyan)
            }
        },
        colors = hudFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DiagnosticsBlock(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var log by remember { mutableStateOf(viewModel.diagnostics()) }
    if (log == null) {
        Hint("Koi crash nahi. Sab systems nominal, Sir.")
        return
    }
    Text(
        log!!.take(1500),
        style = MaterialTheme.typography.bodySmall,
        color = Hud.Dim,
        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())
            .border(1.dp, Hud.Faint, RoundedCornerShape(4.dp)).padding(8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, log)
            runCatching { context.startActivity(Intent.createChooser(send, "Share JARVIS log")) }
        }) { Text("Share log", color = Hud.Cyan) }
        TextButton(onClick = { viewModel.clearDiagnostics(); log = null }) { Text("Clear", color = Hud.Dim) }
    }
    Hint("Log mein sirf error ka stack trace + phone/app version hota hai — API keys ya baat-cheet nahi.")
}

/** "07:30" style field; invalid text is ignored until it parses. */
@Composable
private fun TimeField(label: String, minute: Int, onChange: (Int) -> Unit) {
    var text by remember(minute) { mutableStateOf("%02d:%02d".format(minute / 60, minute % 60)) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v
            Regex("""^(\d{1,2}):(\d{2})$""").find(v.trim())?.let { m ->
                val h = m.groupValues[1].toInt()
                val mm = m.groupValues[2].toInt()
                if (h in 0..23 && mm in 0..59) onChange(h * 60 + mm)
            }
        },
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = hudFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = selected == value
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (on) Hud.Background else Hud.Cyan,
                modifier = Modifier
                    .border(1.dp, Hud.Cyan, RoundedCornerShape(4.dp))
                    .background(if (on) Hud.Cyan else Hud.Background, RoundedCornerShape(4.dp))
                    .clickable { onPick(value) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Hud.Ice)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Hud.Dim)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Hud.Background, checkedTrackColor = Hud.Cyan),
        )
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ${"%.2f".format(value)}", style = MaterialTheme.typography.bodyMedium, color = Hud.Ice, modifier = Modifier.width(110.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Hud.Cyan, activeTrackColor = Hud.Cyan, inactiveTrackColor = Hud.Faint),
            modifier = Modifier.weight(1f),
        )
    }
}
