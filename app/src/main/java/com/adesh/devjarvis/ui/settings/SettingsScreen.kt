package com.adesh.devjarvis.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adesh.devjarvis.core.Edition
import com.adesh.devjarvis.core.JarvisConfig
import com.adesh.devjarvis.settings.JarvisSettings
import com.adesh.devjarvis.ui.theme.BuiltByDev
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.HudShapes
import com.adesh.devjarvis.ui.theme.ScreenHeader
import com.adesh.devjarvis.ui.theme.StatusDot
import com.adesh.devjarvis.ui.theme.Wordmark
import com.adesh.devjarvis.ui.theme.glass
import com.adesh.devjarvis.ui.theme.hudBackdrop
import com.adesh.devjarvis.update.AppUpdater
import com.adesh.devjarvis.voice.VoiceInfo
import kotlinx.coroutines.launch

/** Section rail entries: key (anchor) to the short label shown in the rail. */
private val SECTIONS = listOf(
    "updates" to "UPDATES",
    "keys" to "KEYS",
    "roop" to "ROOP",
    "engine" to "ENGINE",
    "brain" to "BRAIN",
    "voice" to "VOICE",
    "wake" to "WAKE",
    "proactive" to "PROACTIVE",
    "sos" to "SOS",
    "memory" to "MEMORY",
    "extras" to "EXTRAS",
    "diag" to "DIAGNOSTICS",
    "personality" to "PERSONALITY",
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenVoiceLock: () -> Unit,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val rejectedKeys by viewModel.rejectedKeys.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    var showVoices by remember { mutableStateOf(false) }

    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // Where each section card starts inside the scrolling column (for the rail's jump-to).
    val anchors = remember { HashMap<String, Int>() }
    val anchor: (String) -> Modifier = { key ->
        Modifier.onGloballyPositioned { anchors[key] = it.positionInParent().y.toInt() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .hudBackdrop(glowY = 0.0f)
            .safeDrawingPadding()
            .imePadding()
    ) {
        ScreenHeader(
            title = "SETTINGS",
            caption = "Control centre — JARVIS ka har switch yahan",
            onBack = onBack,
        )
        SectionRail { key -> scope.launch { scroll.animateScrollTo(anchors[key] ?: 0) } }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(0.dp))

            ControlCard("UPDATES", "App ka version aur naye update", anchor("updates")) {
                UpdatesBlock(viewModel.updater)
            }

            ControlCard("API KEYS", "JARVIS ka dimaag aur awaaz in keys se chalte hain", anchor("keys")) {
                Hint("JARVIS ka dimaag chalu karne ke liye Gemini key zaruri hai (bilkul free). Button dabao → key banao → copy karo → yahan paste karo → SAVE.")
                ActionRow {
                    HudButton("Keys check karo", viewModel::checkKeys)
                }
                viewModel.keyReport.collectAsStateWithLifecycle().value?.let { report ->
                    Text(
                        report,
                        style = MaterialTheme.typography.bodySmall,
                        color = Hud.Ice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Hud.Abyss.copy(alpha = 0.45f), HudShapes.Chip)
                            .padding(12.dp),
                    )
                }

                SubLabel("GEMINI")
                KeySlots("Gemini API key", draft.geminiApiKey, rejectedKeys) { v -> viewModel.edit { it.copy(geminiApiKey = v) } }
                ActionRow { OpenLinkButton("Get free Gemini key", JarvisConfig.GEMINI_KEY_URL) }

                SubLabel("GROQ")
                KeySlots("Groq API key", draft.groqApiKey, rejectedKeys) { v -> viewModel.edit { it.copy(groqApiKey = v) } }
                ActionRow { OpenLinkButton("Get free Groq key", JarvisConfig.GROQ_KEY_URL) }

                SubLabel("TEESRA DIMAAG")
                SecretField(
                    label = "Third brain key (optional)",
                    value = draft.extraApiKey,
                    onChange = { v -> viewModel.edit { it.copy(extraApiKey = v) } },
                )
                ActionRow {
                    OpenLinkButton("Get free OpenRouter key", JarvisConfig.OPENROUTER_KEY_URL)
                    OpenLinkButton("See free OpenRouter models", JarvisConfig.OPENROUTER_MODELS_URL)
                    OpenLinkButton("Or: free Cerebras key", JarvisConfig.CEREBRAS_KEY_URL)
                }
                HudField("Third brain model (optional)", draft.extraModel, { v -> viewModel.edit { it.copy(extraModel = v) } })
                HudField("Third brain URL", draft.extraBaseUrl, { v -> viewModel.edit { it.copy(extraBaseUrl = v) } })
                Hint("Teesra dimaag tab chalta hai jab Gemini aur Groq dono ki limit khatam ho. Model khaali chhod sakte ho — JARVIS us service ke sabse achhe free model khud chunta hai. Cerebras ke liye URL: https://api.cerebras.ai/v1.")

                SubLabel("ELEVENLABS")
                KeySlots("ElevenLabs key", draft.elevenApiKey, rejectedKeys, count = 3) { v -> viewModel.edit { it.copy(elevenApiKey = v) } }
                ActionRow { OpenLinkButton("Get free ElevenLabs key", JarvisConfig.ELEVEN_KEY_URL) }
                Hint("Bas key daaliye: awaaz (gehri, mard, British) aur model JARVIS aapke account mein se khud chun leta hai.")
                Hint("ElevenLabs sirf Classic mode, reminders aur confirmations ki awaaz badalta hai — Live mode ki awaaz Gemini ki apni hai. Free account mein mahine ke thode hi characters milte hain; khatam hote hi JARVIS khud phone ki awaaz pe aa jaata hai.")
                Hint("Har key apne alag box mein daalo (alag-alag accounts ki). Box khaali chhod sakte ho. Ek ki free limit khatam hote hi JARVIS turant agli key pe chala jaata hai — awaaz, yaaddasht, personality sab wahi rehta hai.")
            }

            ControlCard("ROOP & AWAAZ", "Teeno roop, unki awaaz aur jawab ka andaaz", anchor("roop"), accent = roopAccent(draft.persona)) {
                Hint("Teeno roop ki apni awaaz aur andaaz. Roop HUD pe awaaz se bhi badalta hai: \"dost ban jao\", \"gf ban jao\", \"Jarvis ban jao\".")
                SubLabel("Abhi ka roop")
                ChoiceRow(
                    listOf("jarvis" to "JARVIS", "dost" to "DOST", "gf" to "GF"),
                    com.adesh.devjarvis.brain.Persona.normalize(draft.persona),
                ) { v -> viewModel.edit { it.copy(persona = v) } }
                SubLabel("JARVIS ki awaaz (Live)")
                ChoiceRow(com.adesh.devjarvis.brain.Persona.HIS_VOICES.map { it to it.uppercase() }, draft.liveVoice) { v -> viewModel.edit { it.copy(liveVoice = v) } }
                SubLabel("DOST ki awaaz (Live)")
                ChoiceRow(com.adesh.devjarvis.brain.Persona.HIS_VOICES.map { it to it.uppercase() }, draft.dostVoice) { v -> viewModel.edit { it.copy(dostVoice = v) } }
                SubLabel("GF ki awaaz (Live)")
                ChoiceRow(com.adesh.devjarvis.brain.Persona.HER_VOICES.map { it to it.uppercase() }, draft.gfVoice, accent = Hud.Pink) { v -> viewModel.edit { it.copy(gfVoice = v) } }
                Hint("Save karke agli baar bulao — nayi awaaz sunai degi. Kaun si achhi lagi, 2-3 try karke dekho.")
                SubLabel("Jawab ki lambai")
                ChoiceRow(listOf("short" to "CHHOTA", "normal" to "NORMAL", "detailed" to "DETAIL"), draft.replyStyle) { v -> viewModel.edit { it.copy(replyStyle = v) } }
                LabeledSlider("Follow-up sec", draft.followUpSeconds.toFloat(), 3f..30f) { v -> viewModel.edit { it.copy(followUpSeconds = v.toInt()) } }
                Hint("Jawab ke baad JARVIS kitni der bina wake word ke sunta rahe.")
                HudField("Wake words (comma)", draft.customWakePhrases, { v -> viewModel.edit { it.copy(customWakePhrases = v) } })
                Hint("Khaali = \"hello dev, jarvis\". Seedhe English shabd likho, jaise: jarvis, hey jarvis, hello dev.")
            }

            ControlCard("VOICE ENGINE", "Live ya Classic, awaaz ki quality aur shor filter", anchor("engine")) {
                ChoiceRow(
                    options = listOf(JarvisSettings.VOICE_LIVE to "LIVE", JarvisSettings.VOICE_CLASSIC to "CLASSIC"),
                    selected = draft.voiceEngine,
                ) { v -> viewModel.edit { it.copy(voiceEngine = v) } }
                Hint(
                    "LIVE = Gemini Live: asli awaaz, sabse tez, beech mein bol ke tok sakte ho. Quota/error pe khud CLASSIC pe aa jaata hai. " +
                        "CLASSIC = sunna -> text -> Android TTS (offline voice, kam data)."
                )
                CardDivider()
                ToggleRow(
                    "HD Live voice",
                    "ON = saaf, poori quality (media volume). Speaker pe JARVIS bolte waqt mic band rehta hai (apni awaaz se khud ko nahi kaat-ta); beech mein rokna ho to reactor tap karo, ya earphones lagao.",
                    draft.liveHdAudio,
                ) { v -> viewModel.edit { it.copy(liveHdAudio = v) } }
                ToggleRow(
                    "JARVIS ki awaaz hamesha full",
                    "Phone ke speaker pe JARVIS 100% volume pe bolta hai; baat khatam hote hi aapka media volume wapas wahi ho jaata hai. Earphones mein nahi badalta.",
                    draft.fullVoiceVolume,
                ) { v -> viewModel.edit { it.copy(fullVoiceVolume = v) } }
                CardDivider()
                SubLabel("Shor filter (Live)")
                ChoiceRow(
                    options = listOf("0" to "OFF", "1" to "NORMAL", "2" to "STRONG", "3" to "BHEED"),
                    selected = draft.noiseFilter.toString(),
                ) { v -> viewModel.edit { it.copy(noiseFilter = v.toIntOrNull() ?: 1) } }
                Hint("Sirf paas ki, shor se saaf tez awaaz JARVIS tak jaati hai — door ki baatein, TV, gaadi, music nahi. Bahut shor (DJ, bike) mein STRONG; bheed / market / function mein BHEED — tab sirf phone ke bilkul paas boli baat jaati hai. Agar JARVIS aapki awaaz hi na sune to NORMAL / OFF.")
                HudField("Live model", draft.liveModel, { v -> viewModel.edit { it.copy(liveModel = v) } })
                HudField("Live voice", draft.liveVoice, { v -> viewModel.edit { it.copy(liveVoice = v) } })
                Hint("Voices: Charon (deep, calm — default), Orus (firm), Fenrir, Puck, Kore, Aoede.")
                SubLabel("Classic mode ears")
                ChoiceRow(
                    options = listOf(JarvisSettings.STT_ANDROID to "ANDROID", JarvisSettings.STT_GROQ to "GROQ WHISPER"),
                    selected = draft.sttEngine,
                ) { v -> viewModel.edit { it.copy(sttEngine = v) } }
                Hint("Groq Whisper Hinglish behtar samajhta hai (Groq key chahiye).")
            }

            ControlCard("BRAIN", "Kaunsa model sochta hai, aur aapka shehar", anchor("brain")) {
                HudField("Gemini model", draft.geminiModel, { v -> viewModel.edit { it.copy(geminiModel = v) } })
                Hint("Default: ${JarvisConfig.GEMINI_MODEL}. Naya model aaye to naam yahan badlo.")
                ActionRow { HudTextButton("Reset model", viewModel::resetModel) }
                HudField("Groq backup model", draft.groqModel, { v -> viewModel.edit { it.copy(groqModel = v) } })
                Hint("Gemini ka free quota khatam (429) ho to JARVIS chupchaap Groq pe chala jaata hai. HUD pe MODE: FALLBACK dikhega.")
                CardDivider()
                HudField("Home city (weather)", draft.homeCity, { v -> viewModel.edit { it.copy(homeCity = v) } })
                Hint("Jaise: Mumbai. \"Mausam kaisa hai?\" bina city ke pucho to yahi use hoga.")
            }

            ControlCard("VOICE", "Sunne ki bhasha aur phone ki awaaz", anchor("voice")) {
                SubLabel("Speech language (sunne ki bhasha)")
                ChoiceRow(JarvisConfig.STT_LANGUAGES.map { it to it }, draft.sttLanguage) { lang ->
                    viewModel.edit { it.copy(sttLanguage = lang) }
                }
                Hint("Hinglish ke liye en-IN best hai. Zyada Hindi bolte ho to hi-IN try karo.")
                CardDivider()
                Text(
                    "Voice: " + draft.ttsVoiceName.ifBlank { "Auto (deep male UK/India)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Hud.Ice,
                )
                ActionRow {
                    HudButton("Choose voice", { showVoices = true })
                    HudButton("Test voice", viewModel::testVoice)
                    HudTextButton("Reset", viewModel::resetVoice)
                }
                LabeledSlider("Pitch", draft.ttsPitch, 0.5f..1.5f) { v -> viewModel.edit { it.copy(ttsPitch = v) } }
                LabeledSlider("Speed", draft.ttsRate, 0.6f..1.5f) { v -> viewModel.edit { it.copy(ttsRate = v) } }
            }

            ControlCard("WAKE WORD & CONVERSATION", "Kitni jaldi jaage, kitni der sune", anchor("wake")) {
                Hint("Wake phrases: \"Hello Dev\", \"Jarvis\". Always-on switch HUD pe hai.")
                LabeledSlider("Sensitivity", draft.wakeSensitivity, 0f..1f) { v -> viewModel.edit { it.copy(wakeSensitivity = v) } }
                Hint("Kam = sirf saaf bola hua wake word. Zyada = jaldi jaagta hai, par galti se bhi jaag sakta hai. 0.8+ pe sabse tez.")
                CardDivider()
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
                CardDivider()
                ActionRow {
                    HudButton("Setup & permissions checklist", onOpenSetup)
                    HudButton("Sirf meri awaaz (speaker lock)", onOpenVoiceLock)
                }
                Hint("Best-effort: JARVIS sirf aapki awaaz pe jaage, doosron ki ignore kare. Isme model download + ek baar enroll karna hota hai.")
            }

            ControlCard("PROACTIVE JARVIS", "Briefing, quiet hours, alerts aur aadatein", anchor("proactive")) {
                ToggleRow("Morning briefing", "Weather, aaj ke events/reminders, battery + ek witty line.", draft.briefingEnabled) { v ->
                    viewModel.edit { it.copy(briefingEnabled = v) }
                }
                TimeField("Briefing time", draft.briefingMinute) { m -> viewModel.edit { it.copy(briefingMinute = m) } }
                ActionRow { HudButton("Play briefing now", viewModel::playBriefingNow) }
                CardDivider()
                ToggleRow("Night summary", "Raat ko: aaj ka kharcha, kal ke events/reminders, battery.", draft.nightSummaryEnabled) { v ->
                    viewModel.edit { it.copy(nightSummaryEnabled = v) }
                }
                TimeField("Night summary time", draft.nightSummaryMinute) { m -> viewModel.edit { it.copy(nightSummaryMinute = m) } }
                ActionRow { HudButton("Play night summary now", viewModel::playNightSummaryNow) }
                CardDivider()
                SubLabel("Quiet hours")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { TimeField("Quiet from", draft.quietStartMinute) { m -> viewModel.edit { it.copy(quietStartMinute = m) } } }
                    Box(Modifier.weight(1f)) { TimeField("Quiet until", draft.quietEndMinute) { m -> viewModel.edit { it.copy(quietEndMinute = m) } } }
                }
                Hint("Quiet hours, DND, silent mode (bina headphones) ya call ke dauraan JARVIS khud se nahi bolta — sirf notification. Aapke apne reminders raat mein bhi bolte hain.")
                CardDivider()
                ToggleRow(
                    "Turant commands (bina AI)",
                    "Torch, volume, Wi-Fi / Bluetooth, timer, gaana roko, app kholo, home / back / lock — Classic mode mein usi pal, bina internet aur bina quota ke. Live mode mein awaaz seedhi Gemini ko jaati hai, wahan ye lagu nahi.",
                    draft.instantCommands,
                ) { v -> viewModel.edit { it.copy(instantCommands = v) } }
                ToggleRow(
                    "Aadat se sujhaav",
                    "Jo kaam aap aksar isi waqt karwate hain (app kholna, gaana, Wi-Fi), JARVIS ek baar pooch leta hai. Din mein zyada se zyada 3.",
                    draft.suggestHabits,
                ) { v -> viewModel.edit { it.copy(suggestHabits = v) } }
                ToggleRow(
                    "Roz ki diary",
                    "Raat 11:40 pe din bhar ka chhota saar likh leta hai. Poochhiye: \"kal maine kya kiya tha?\"",
                    draft.diaryEnabled,
                ) { v -> viewModel.edit { it.copy(diaryEnabled = v) } }
                Hint("Rules (\"jab charger lage tab…\", \"roz 10 baje…\", \"jab Papa ka message aaye…\") awaaz se banaiye. Ye tabhi chalte hain jab always-on chalu ho.")
                CardDivider()
                ToggleRow(
                    "Incoming call batana",
                    "Kaun call kar raha hai bolta hai, phir \"uthao\" ya \"kaat do\" sunta hai. Full edition + Notification access. Silent/DND mein chup.",
                    draft.announceCalls,
                ) { v -> viewModel.edit { it.copy(announceCalls = v) } }
                ToggleRow("Important notifications bolna", "Chune hue apps/logon ke message ka chhota summary. OTP/bank kabhi nahi.", draft.notificationAlerts) { v ->
                    viewModel.edit { it.copy(notificationAlerts = v) }
                }
                HudField("Important apps (comma)", draft.importantApps, { v -> viewModel.edit { it.copy(importantApps = v) } })
                HudField("Important people (comma)", draft.importantContacts, { v -> viewModel.edit { it.copy(importantContacts = v) } })
                Hint("Jaise: Mummy, Papa, Boss. In logon ka message kisi bhi app se aaye to JARVIS batayega.")
                ToggleRow("Low battery warning", "20% se neeche (charging nahi) — aur 10% pe dobara.", draft.lowBatteryAlert) { v ->
                    viewModel.edit { it.copy(lowBatteryAlert = v) }
                }
            }

            ControlCard("EMERGENCY SOS", "Musibat mein kisko khabar jaaye", anchor("sos"), accent = Hud.Amber) {
                HudField("Emergency contacts (comma)", draft.emergencyContacts, { v -> viewModel.edit { it.copy(emergencyContacts = v) } })
                Hint("Jaise: Mummy, Papa, 9876543210 (max 5). \"Jarvis, SOS\" bolne par ek baar confirm karke in sabko aapki location ke saath SMS jaata hai. Full edition + SMS aur Location permission chahiye. SMS ka charge aapke SIM plan ke hisaab se.")
            }

            ControlCard("MEMORY", "Yaadein aur backup — sab sirf aapke phone pe", anchor("memory")) {
                Hint("JARVIS baat-cheet se khud zaruri baatein yaad rakhta hai (sirf phone pe). Yahan dekho, badlo, mitao.")
                ActionRow { HudButton("Open memory", onOpenMemory) }
                CardDivider()
                BackupBlock(viewModel)
            }

            ControlCard("EXTRAS", "Mini-reactor, awaaz effects, battery aur boot", anchor("extras")) {
                ToggleRow("Floating mini-reactor", "Doosre apps ke upar chhota reactor jab JARVIS sun/bol raha ho (\"Display over other apps\" chahiye). Tap = kholo, long-press = rok do.", draft.bubbleEnabled) { v ->
                    viewModel.edit { it.copy(bubbleEnabled = v) }
                }
                ToggleRow("Mic open/close chime", "Mic khulne aur band hone par chhoti si awaaz. OFF = sirf halka vibration.", draft.wakeChime) { v ->
                    viewModel.edit { it.copy(wakeChime = v) }
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
            }

            ControlCard("DIAGNOSTICS", "Errors aur JARVIS ka flight recorder", anchor("diag")) {
                DiagnosticsBlock(viewModel)
            }

            ControlCard("PERSONALITY", "JARVIS ka mizaaj — system prompt", anchor("personality")) {
                OutlinedTextField(
                    value = draft.systemPrompt,
                    onValueChange = { v -> viewModel.edit { it.copy(systemPrompt = v) } },
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = hudFieldColors(),
                    shape = HudShapes.Chip,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
                )
                ActionRow {
                    HudTextButton("Reset personality", viewModel::resetPrompt)
                    HudTextButton("Clear chat memory", viewModel::clearConversation)
                }
            }

            // Signature footer.
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Wordmark(fontSize = 13)
                BuiltByDev(Modifier.padding(top = 6.dp))
            }
        }

        SaveBar(saved = saved, saveFailed = saveFailed, onSave = viewModel::save)
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

private fun roopAccent(persona: String): Color =
    if (com.adesh.devjarvis.brain.Persona.isPartner(persona)) Hud.Pink else Hud.Cyan

/** Horizontal strip of section names; tap one to jump to its card. */
@Composable
private fun SectionRail(onJump: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SECTIONS.forEach { (key, label) ->
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.5.sp),
                color = Hud.Ice,
                maxLines = 1,
                modifier = Modifier
                    .glass(Hud.Cyan, HudShapes.Pill, tint = 0.5f, edge = 0.6f)
                    .clickable { onJump(key) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** Sticky bottom bar: save state on the left, SAVE on the right. */
@Composable
private fun SaveBar(saved: Boolean, saveFailed: Boolean, onSave: () -> Unit) {
    val color = when {
        saveFailed -> Hud.Amber
        saved -> Hud.Green
        else -> Hud.Dim
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .glass(Hud.Cyan, HudShapes.Card, tint = 0.8f)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color, size = 5)
        Text(
            when {
                saveFailed -> "Save fail hua — Diagnostics dekho"
                saved -> "Saved ✓"
                else -> "Badlaav SAVE dabane par lagu honge"
            },
            style = if (saved || saveFailed) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
            color = if (saveFailed) Hud.Amber else if (saved) Hud.Cyan else Hud.Dim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp),
        )
        Button(
            onClick = onSave,
            shape = HudShapes.Chip,
            colors = ButtonDefaults.buttonColors(containerColor = Hud.Cyan, contentColor = Hud.Background),
        ) { Text("SAVE", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp)) }
    }
}

/** One settings group: glass card, accent tick, title, a one-line caption, then the controls. */
@Composable
private fun ControlCard(
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
    accent: Color = Hud.Cyan,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .glass(accent, HudShapes.Panel, tint = 0.55f, edge = 0.5f)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 28.dp)
                    .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.2f))), RoundedCornerShape(2.dp)),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(caption, style = MaterialTheme.typography.bodySmall, color = Hud.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        CardDivider()
        content()
    }
}

@Composable
private fun CardDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(Hud.Line, Hud.Line.copy(alpha = 0.2f)))),
    )
}

/** Small heading inside a card, e.g. "GEMINI" or "Abhi ka roop". */
@Composable
private fun SubLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.8.sp),
        color = Hud.Ice.copy(alpha = 0.85f),
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Buttons that wrap onto the next line instead of running off small screens. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ActionRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun HudButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = HudShapes.Chip,
        border = BorderStroke(1.dp, Hud.Cyan.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Hud.Cyan.copy(alpha = 0.06f)),
    ) { Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Hud.Cyan) }
}

@Composable
private fun HudTextButton(text: String, onClick: () -> Unit, color: Color = Hud.Cyan) {
    TextButton(onClick = onClick, shape = HudShapes.Chip) {
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

@Composable
private fun VoicePicker(voices: List<VoiceInfo>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Hud.Cyan) } },
        title = { Text("VOICE", style = MaterialTheme.typography.titleMedium, color = Hud.Cyan) },
        containerColor = Hud.Panel,
        shape = HudShapes.Panel,
        text = {
            if (voices.isEmpty()) {
                Text(
                    "Koi English voice nahi mili. Play Store se 'Speech Services by Google' install karo, " +
                        "phir Settings > Text-to-speech mein English (India/UK) voice data download karo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(voices, key = { it.name }) { v ->
                        val selected = v.name == current
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(if (selected) Hud.Cyan.copy(alpha = 0.10f) else Color.Transparent, HudShapes.Chip)
                                .clickable { onPick(v.name) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
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
private fun Hint(text: String) {
    // Two lines, tap for the rest: the explanations were walls of text between the controls.
    var open by remember { mutableStateOf(false) }
    val long = text.length > 110
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Hud.Dim,
        maxLines = if (open || !long) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = if (long) Modifier.clickable { open = !open } else Modifier,
    )
}

/** Clear button that opens a URL in the browser (e.g. "Get free Gemini key"). */
@Composable
private fun OpenLinkButton(text: String, url: String) {
    val context = LocalContext.current
    // A light link, not another big button.
    Text(
        "$text  ↗",
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = Hud.Cyan,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            .padding(vertical = 6.dp, horizontal = 2.dp),
    )
}

@Composable
private fun hudFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Hud.Cyan,
    unfocusedBorderColor = Hud.Line,
    cursorColor = Hud.Cyan,
    focusedLabelColor = Hud.Cyan,
    unfocusedLabelColor = Hud.Dim,
    focusedTextColor = Hud.White,
    unfocusedTextColor = Hud.Ice,
    focusedContainerColor = Hud.Abyss.copy(alpha = 0.55f),
    unfocusedContainerColor = Hud.Abyss.copy(alpha = 0.35f),
)

@Composable
private fun HudField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = hudFieldColors(),
        shape = HudShapes.Chip,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * One box per key ("Gemini API key 1" ... "5"). They are stored together; an empty box is simply skipped.
 */
@Composable
private fun KeySlots(label: String, value: String, rejected: Set<String>, count: Int = 5, onChange: (String) -> Unit) {
    val pool = remember { com.adesh.devjarvis.core.KeyPool() }
    val slots = remember {
        // Each key stays in the box it was typed in (empty boxes keep their place).
        val saved = pool.boxes(value)
        androidx.compose.runtime.mutableStateListOf<String>().apply { repeat(maxOf(count, saved.size)) { add(saved.getOrElse(it) { "" }) } }
    }
    // Show the filled boxes plus one empty one; "+ aur key" opens the next. Five empty boxes per service
    // made this card three screens long.
    val filled = slots.indexOfLast { it.isNotBlank() } + 1
    var shown by remember { mutableStateOf(maxOf(1, minOf(slots.size, filled + 1))) }
    slots.forEachIndexed { index, key ->
        if (index >= shown) return@forEachIndexed
        // Red only when the service REJECTED this key; a key that is just at its limit stays normal.
        val wrong = key.isNotBlank() && com.adesh.devjarvis.core.BadKeys.fingerprint(key) in rejected
        SecretField(
            label = "$label ${index + 1}" + if (wrong) " — GALAT KEY" else if (index == 0) "" else " (optional)",
            value = key,
            wrong = wrong,
            onChange = { v ->
                slots[index] = v.filterNot { it.isWhitespace() }
                onChange(pool.store(slots))
            },
        )
    }
    if (shown < slots.size) {
        Text(
            "+ aur $label jodo (${slots.size - shown} baaki)",
            style = MaterialTheme.typography.labelMedium,
            color = Hud.Cyan,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { shown++ }.padding(vertical = 6.dp, horizontal = 4.dp),
        )
    }
}

@Composable
private fun SecretField(label: String, value: String, wrong: Boolean = false, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        isError = wrong,
        supportingText = if (wrong) { { Text("Service ne ye key reject ki hai. Ise hata do ya nayi key daalo.", color = MaterialTheme.colorScheme.error) } } else null,
        textStyle = MaterialTheme.typography.bodyMedium,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "HIDE" else "SHOW", style = MaterialTheme.typography.labelSmall, color = Hud.Cyan)
            }
        },
        colors = hudFieldColors(),
        shape = HudShapes.Chip,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun UpdatesBlock(updater: AppUpdater) {
    val state by updater.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Came back from "Install unknown apps" settings? Continue the install.
        if ((state as? AppUpdater.State.Installing)?.needsPermission == true) updater.retryInstall()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("INSTALLED", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Hud.Dim)
        Text(
            updater.currentVersion,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Hud.Ice,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    val status = when (val st = state) {
        AppUpdater.State.Idle -> "Naya version dekhne ke liye Check dabao."
        AppUpdater.State.Checking -> "Checking…"
        AppUpdater.State.UpToDate -> "Sab latest hai, Sir. ✓"
        is AppUpdater.State.Available -> "Naya version ${st.versionName} (${st.versionCode}) — ~${st.sizeMb} MB"
        is AppUpdater.State.Downloading -> "Downloading… ${st.percent}%"
        is AppUpdater.State.Installing ->
            if (st.needsPermission) "Ek baar: JARVIS ke liye \"Allow from this source\" ON karo, phir wapas aao."
            else "Download / installer khula hai — file pe tap karke Update / Install dabao."
        is AppUpdater.State.Failed -> "Update fail: ${st.message}"
    }
    Text(
        status,
        style = MaterialTheme.typography.bodySmall,
        color = when (state) {
            is AppUpdater.State.Available -> Hud.Cyan
            is AppUpdater.State.Failed -> Hud.Amber
            else -> Hud.Dim
        },
    )
    ActionRow {
        HudButton("Check for update", updater::check)
        if (state is AppUpdater.State.Available) {
            Button(
                onClick = { updater.downloadAndInstall() },
                shape = HudShapes.Chip,
                colors = ButtonDefaults.buttonColors(containerColor = Hud.Cyan, contentColor = Hud.Background),
            ) { Text("INSTALL", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
        }
    }
    if (!Edition.isFull) {
        CardDivider()
        SubLabel("Get Full edition")
        Hint(
            "Full mein screen agent, screen padhna, lock/screenshot aur notifications (alerts, reply) bhi hain. " +
                "Google Play Protect browser/file se install hone wale aise apps (Accessibility + Notification access) block karta hai, " +
                "isliye Full ek baar in do mein se kisi tareeke se install karna hoga — data same rehta hai:"
        )
        Hint(
            "1) Laptop + USB: `adb install -r JARVIS.apk` (ADB se install block nahi hota).\n" +
                "2) Ya kuch der ke liye: Play Store > profile photo > Play Protect > ⚙ > \"Scan apps with Play Protect\" OFF → " +
                "neeche wala button → install → Play Protect wapas ON."
        )
        ActionRow { HudButton("Download Full edition", { updater.downloadAndInstall(full = true) }) }
    }
}

@Composable
private fun DiagnosticsBlock(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var log by remember { mutableStateOf(viewModel.diagnostics()) }
    if (log == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(Hud.Green, size = 5)
            Text(
                "Koi crash nahi. Sab systems nominal, Sir.",
                style = MaterialTheme.typography.bodySmall,
                color = Hud.Ice,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        return
    }
    Text(
        log!!.takeLast(2500),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        color = Hud.Dim,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .background(Hud.Abyss.copy(alpha = 0.55f), HudShapes.Chip)
            .border(1.dp, Hud.Line, HudShapes.Chip)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    )
    ActionRow {
        HudButton("Share log", {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, log)
            runCatching { context.startActivity(Intent.createChooser(send, "Share JARVIS log")) }
        })
        HudTextButton("Clear", { viewModel.clearDiagnostics(); log = null }, color = Hud.Dim)
    }
    Hint("Isme errors aur JARVIS ke andar ke events (jaagna, Live connect, kitni der lagi) hote hain — API keys ya baat-cheet ka text nahi. Kuch gadbad lage to turant yahan aake Share log dabao.")
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
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = hudFieldColors(),
        shape = HudShapes.Chip,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Segmented choice pills; wraps onto more lines on small screens. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, accent: Color = Hud.Cyan, onPick: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val on = selected == value
            val fill by animateColorAsState(if (on) accent else Hud.Abyss.copy(alpha = 0.35f), tween(200), label = "pill")
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                color = if (on) Hud.Background else Hud.Ice,
                maxLines = 1,
                modifier = Modifier
                    .background(fill, HudShapes.Chip)
                    .border(1.dp, if (on) accent else Hud.Line, HudShapes.Chip)
                    .clickable { onPick(value) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = if (checked) Hud.White else Hud.Ice)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Hud.Dim)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Hud.Background,
                checkedTrackColor = Hud.Cyan,
                checkedBorderColor = Hud.Cyan,
                uncheckedThumbColor = Hud.Dim,
                uncheckedTrackColor = Hud.Abyss,
                uncheckedBorderColor = Hud.Line,
            ),
        )
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Hud.Ice,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (range.endInclusive > 2f) "%.0f".format(value) else "%.2f".format(value),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = Hud.Cyan,
                modifier = Modifier
                    .background(Hud.Cyan.copy(alpha = 0.10f), HudShapes.Pill)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Hud.Cyan, activeTrackColor = Hud.Cyan, inactiveTrackColor = Hud.Faint),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Backup file: memories, routines, notes, diary, learned skills, recent chat, role and voice — never the API keys.
 * After a reinstall pick the same file and everything comes back; then only the keys need typing.
 */
@Composable
private fun BackupBlock(viewModel: SettingsViewModel) {
    val report by viewModel.backupReport.collectAsStateWithLifecycle()
    val save = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportBackup(uri) }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importBackup(uri) }
    SubLabel("Backup")
    Hint("Backup: saari yaadein, routines, notes, diary, seekhe skills, haal ki baat-cheet, roop aur awaaz ek file mein (API keys nahi). Reinstall ke baad \"Backup se wapas lao\" — sab wapas, sirf keys daalni hongi. Phone ka Google backup on ho to ye apne aap bhi wapas aata hai.")
    ActionRow {
        HudButton("Backup file banao", { save.launch("dev-jarvis-backup.json") })
        HudButton("Backup se wapas lao", { open.launch(arrayOf("application/json", "text/plain", "*/*")) })
    }
    report?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Hud.Ice) }
}
