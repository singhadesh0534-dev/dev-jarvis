package com.adesh.devjarvis.tools.impl

import android.content.Context
import com.adesh.devjarvis.BuildConfig
import com.adesh.devjarvis.accessibility.JarvisAccessibilityService
import com.adesh.devjarvis.brain.Persona
import com.adesh.devjarvis.core.Edition
import com.adesh.devjarvis.core.KeyPool
import com.adesh.devjarvis.core.KeyRing
import com.adesh.devjarvis.service.ServiceController
import com.adesh.devjarvis.settings.JarvisSettings
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JARVIS apne baare mein: apni settings batana aur awaaz se badalna, aur roop (Jarvis / dost / gf) badalna.
 * API keys kabhi boli ya dikhayi nahi jaati — sirf kitni saved hain aur kitni abhi kaam kar rahi hain.
 */
@Singleton
class SelfTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val keys: KeyRing,
    private val modes: com.adesh.devjarvis.core.ConversationMode,
) {
    private class Item(
        val name: String,
        val about: String,
        val get: (JarvisSettings) -> String,
        /** Returns the changed settings, or null when the value is not acceptable. */
        val set: (JarvisSettings, String) -> JarvisSettings?,
    )

    private fun onOff(v: Boolean) = if (v) "on" else "off"

    private fun bool(raw: String): Boolean? = when (raw.lowercase().trim()) {
        "on", "true", "yes", "1", "chalu", "enable", "enabled" -> true
        "off", "false", "no", "0", "band", "disable", "disabled" -> false
        else -> null
    }

    private fun flag(name: String, about: String, get: (JarvisSettings) -> Boolean, set: (JarvisSettings, Boolean) -> JarvisSettings) =
        Item(name, "$about (on / off)", { onOff(get(it)) }, { s, raw -> bool(raw)?.let { set(s, it) } })

    private fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

    /** "7:30", "07.30", "730", "21" -> minutes after midnight. */
    private fun minuteOf(raw: String): Int? {
        val digits = raw.filter { it.isDigit() || it == ':' || it == '.' }.replace('.', ':')
        val (h, m) = when {
            ':' in digits -> digits.split(':').let { (it[0].toIntOrNull() ?: return null) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
            digits.length <= 2 -> (digits.toIntOrNull() ?: return null) to 0
            else -> (digits.dropLast(2).toIntOrNull() ?: return null) to (digits.takeLast(2).toIntOrNull() ?: return null)
        }
        var hour = h
        val lower = raw.lowercase()
        if (("pm" in lower || "shaam" in lower || "raat" in lower) && hour < 12) hour += 12
        return if (hour in 0..23 && m in 0..59) hour * 60 + m else null
    }

    private val items: List<Item> = listOf(
        flag("always_on", "Listen for the wake word in the background", { it.alwaysOn }) { s, v -> s.copy(alwaysOn = v) },
        Item("voice_mode", "live (natural voice call) or classic (speech-to-text, then spoken reply)", { it.voiceEngine },
            { s, v -> v.lowercase().trim().takeIf { it == JarvisSettings.VOICE_LIVE || it == JarvisSettings.VOICE_CLASSIC }?.let { s.copy(voiceEngine = it) } }),
        Item("live_voice", "Live voice name: Charon, Puck, Fenrir, Orus (male), Kore, Aoede, Leda, Zephyr (female)", { it.liveVoice },
            { s, v -> v.trim().takeIf { it.matches(Regex("[A-Za-z]{3,20}")) }?.let { s.copy(liveVoice = it.lowercase().replaceFirstChar(Char::uppercase)) } }),
        Item("wake_sensitivity", "How easily the wake word triggers, 0 (only a clear call) to 100 (very easily)", { (it.wakeSensitivity * 100).toInt().toString() },
            { s, v -> v.filter(Char::isDigit).toIntOrNull()?.takeIf { it in 0..100 }?.let { s.copy(wakeSensitivity = it / 100f) } }),
        Item("noise_filter", "Live noise filter: 0 off, 1 normal, 2 strong (only a near, clearly louder voice gets through)", { it.noiseFilter.toString() },
            { s, v -> (v.filter(Char::isDigit).toIntOrNull() ?: when (v.lowercase().trim()) { "off" -> 0; "normal" -> 1; "strong" -> 2; else -> null })?.takeIf { it in 0..2 }?.let { s.copy(noiseFilter = it) } }),
        Item("only_my_voice", "Voice lock: wake only on Sir's own voice (he must record his voice once in Settings > Voice lock)", { onOff(it.speakerLockEnabled) + if (it.speakerEnrolled) "" else " (voice not recorded yet)" },
            { s, v -> bool(v)?.let { on -> if (on && !s.speakerEnrolled) null else s.copy(speakerLockEnabled = on) } }),
        Item("voice_lock_strictness", "How closely the voice must match, 0 (loose) to 100 (strict)", { (it.speakerThreshold * 100).toInt().toString() },
            { s, v -> v.filter(Char::isDigit).toIntOrNull()?.takeIf { it in 0..100 }?.let { s.copy(speakerThreshold = it / 100f) } }),
        flag("follow_up_listening", "Keep listening a few seconds after each reply", { it.continuousConversation }) { s, v -> s.copy(continuousConversation = v) },
        flag("interrupt_by_voice", "Say Jarvis / stop / ruko to cut JARVIS off while he speaks (classic mode)", { it.voiceBargeIn }) { s, v -> s.copy(voiceBargeIn = v) },
        flag("full_volume_voice", "JARVIS always speaks at full volume", { it.fullVoiceVolume }) { s, v -> s.copy(fullVoiceVolume = v) },
        flag("hd_voice", "Live voice in full quality on the media stream", { it.liveHdAudio }) { s, v -> s.copy(liveHdAudio = v) },
        flag("instant_commands", "Torch, volume, timer, open app... done at once without the AI", { it.instantCommands }) { s, v -> s.copy(instantCommands = v) },
        flag("sound_effects", "Small sounds for wake / done", { it.soundEffects }) { s, v -> s.copy(soundEffects = v) },
        flag("wake_chime", "Chime when the mic opens and closes", { it.wakeChime }) { s, v -> s.copy(wakeChime = v) },
        flag("floating_bubble", "Small reactor over other apps while JARVIS listens or speaks", { it.bubbleEnabled }) { s, v -> s.copy(bubbleEnabled = v) },
        flag("boot_animation", "Start-up animation", { it.bootAnimation }) { s, v -> s.copy(bootAnimation = v) },
        flag("morning_briefing", "Morning briefing", { it.briefingEnabled }) { s, v -> s.copy(briefingEnabled = v) },
        Item("morning_briefing_time", "Time of the morning briefing, HH:MM", { clock(it.briefingMinute) }, { s, v -> minuteOf(v)?.let { s.copy(briefingMinute = it) } }),
        flag("night_summary", "Night summary", { it.nightSummaryEnabled }) { s, v -> s.copy(nightSummaryEnabled = v) },
        Item("night_summary_time", "Time of the night summary, HH:MM", { clock(it.nightSummaryMinute) }, { s, v -> minuteOf(v)?.let { s.copy(nightSummaryMinute = it) } }),
        Item("quiet_hours_start", "From this time JARVIS does not speak on his own, HH:MM", { clock(it.quietStartMinute) }, { s, v -> minuteOf(v)?.let { s.copy(quietStartMinute = it) } }),
        Item("quiet_hours_end", "Quiet hours end, HH:MM", { clock(it.quietEndMinute) }, { s, v -> minuteOf(v)?.let { s.copy(quietEndMinute = it) } }),
        flag("notification_alerts", "Speak important notifications (never OTP or banking)", { it.notificationAlerts }) { s, v -> s.copy(notificationAlerts = v) },
        Item("important_apps", "Apps whose notifications are spoken, comma separated", { it.importantApps }, { s, v -> s.copy(importantApps = v.take(200)) }),
        Item("important_contacts", "People whose messages are always spoken, comma separated", { it.importantContacts }, { s, v -> s.copy(importantContacts = v.take(200)) }),
        flag("announce_calls", "Say who is calling", { it.announceCalls }) { s, v -> s.copy(announceCalls = v) },
        flag("low_battery_alert", "Warn at low battery", { it.lowBatteryAlert }) { s, v -> s.copy(lowBatteryAlert = v) },
        flag("pause_on_low_battery", "Stop listening for the wake word below 15% battery", { it.pauseWakeOnLowBattery }) { s, v -> s.copy(pauseWakeOnLowBattery = v) },
        flag("habit_suggestions", "Offer what Sir usually does at this time", { it.suggestHabits }) { s, v -> s.copy(suggestHabits = v) },
        flag("diary", "Write a short diary entry every night", { it.diaryEnabled }) { s, v -> s.copy(diaryEnabled = v) },
        Item("home_city", "City for weather and briefings", { it.homeCity.ifBlank { "(not set)" } }, { s, v -> s.copy(homeCity = v.take(60)) }),
        Item("emergency_contacts", "Names or numbers that get the SOS message, comma separated", { it.emergencyContacts.ifBlank { "(none)" } }, { s, v -> s.copy(emergencyContacts = v.take(200)) }),
        Item("speech_speed", "Classic voice speed, 50 (slow) to 200 (fast), 100 normal", { (it.ttsRate * 100).toInt().toString() },
            { s, v -> v.filter(Char::isDigit).toIntOrNull()?.takeIf { it in 50..200 }?.let { s.copy(ttsRate = it / 100f) } }),
        Item("speech_pitch", "Classic voice pitch, 50 (deep) to 150 (high), 100 normal", { (it.ttsPitch * 100).toInt().toString() },
            { s, v -> v.filter(Char::isDigit).toIntOrNull()?.takeIf { it in 50..150 }?.let { s.copy(ttsPitch = it / 100f) } }),
        Item("listening_engine", "Classic ears: android (Google speech) or groq (Whisper, better Hinglish, needs a Groq key)", { it.sttEngine },
            { s, v -> v.lowercase().trim().takeIf { it == "android" || it == "groq" }?.let { s.copy(sttEngine = it) } }),
        Item("dost_voice", "Live voice of the dost role: " + Persona.HIS_VOICES.joinToString(", "), { it.dostVoice.ifBlank { it.liveVoice } },
            { s, v -> Persona.HIS_VOICES.firstOrNull { it.equals(v.trim(), ignoreCase = true) }?.let { s.copy(dostVoice = it) } }),
        Item("reply_length", "How long replies are: short, normal or detailed", { it.replyStyle },
            { s, v -> v.lowercase().trim().let { t -> when { "short" in t || "chhot" in t -> "short"; "detail" in t || "lamb" in t || "long" in t -> "detailed"; "normal" in t -> "normal"; else -> null } }?.let { s.copy(replyStyle = it) } }),
        Item("follow_up_seconds", "Seconds to keep listening after a reply, 3 to 30", { it.followUpSeconds.toString() },
            { s, v -> v.filter(Char::isDigit).toIntOrNull()?.takeIf { it in 3..30 }?.let { s.copy(followUpSeconds = it) } }),
        Item("gf_voice", "Live voice of the girlfriend role: " + Persona.HER_VOICES.joinToString(", "), { it.gfVoice },
            { s, v -> Persona.HER_VOICES.firstOrNull { it.equals(v.trim(), ignoreCase = true) }?.let { s.copy(gfVoice = it) } }),
        Item("role", "Who JARVIS is being: jarvis, dost or gf", { Persona.label(it.persona) }, { s, v -> s.copy(persona = Persona.normalize(v)) }),
    )

    private val byName = items.associateBy { it.name }

    private fun keyLine(label: String, pool: KeyPool, raw: String): String {
        val saved = pool.parse(raw).size
        return "$label keys: $saved saved" + if (saved > 0) ", ${pool.readyCount(raw)} usable right now" else ""
    }

    private fun status(s: JarvisSettings): String = buildString {
        append("JARVIS ${BuildConfig.VERSION_NAME} (${Edition.label})\n")
        append(keyLine("Gemini", keys.gemini, s.geminiApiKey)).append("; Live usable: ").append(keys.live.readyCount(s.geminiApiKey)).append('\n')
        append(keyLine("Groq", keys.groq, s.groqApiKey)).append('\n')
        append(keyLine("ElevenLabs", keys.eleven, s.elevenApiKey)).append('\n')
        append("Third brain: ").append(if (s.hasExtraBrain) "set" else "not set").append('\n')
        append("Accessibility (screen control): ").append(if (JarvisAccessibilityService.isConnected) "on" else "OFF").append('\n')
        append("Wake phrases: ").append(s.customWakePhrases.ifBlank { "hello dev, jarvis (default)" }).append('\n')
        items.forEach { append(it.name).append(" = ").append(it.get(s)).append('\n') }
    }

    private val jarvisSettings = object : SimpleTool(
        "jarvis_settings",
        "Read or change JARVIS's OWN settings by voice (not the phone's settings: those are set_toggle / open_settings). " +
            "action=get with no name returns every setting with its current value, the app version, how many API keys are saved " +
            "and usable, and whether screen control is on: use it whenever Sir asks anything about you, your settings, your keys " +
            "or why something isn't working. action=set changes one. Settings: " +
            "always_on, voice_mode, live_voice, wake_sensitivity, noise_filter, only_my_voice, voice_lock_strictness, " +
            "follow_up_listening, interrupt_by_voice, full_volume_voice, hd_voice, instant_commands, sound_effects, wake_chime, " +
            "floating_bubble, boot_animation, morning_briefing, morning_briefing_time, night_summary, night_summary_time, " +
            "quiet_hours_start, quiet_hours_end, notification_alerts, important_apps, important_contacts, announce_calls, " +
            "low_battery_alert, pause_on_low_battery, habit_suggestions, diary, home_city, emergency_contacts, speech_speed, " +
            "speech_pitch, listening_engine, gf_voice, dost_voice, reply_length, follow_up_seconds, role. API keys can never be read or changed here: Sir types them in Settings. " +
            "The wake word itself is changed with set_wake_word.",
        params {
            string("action", "get | set", required = true, enum = listOf("get", "set"))
            string("name", "Setting name (omit with get to list everything)")
            string("value", "New value for set: on / off, a number, a time like 07:30, or text")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val s = settings.current
            val name = args.string("name")?.lowercase()?.replace(' ', '_')?.replace('-', '_')
            if (args.enum("action", listOf("get", "set")) != "set") {
                if (name == null) return ToolResult.ok(status(s) + "Tell Sir only what he asked about, in one or two spoken lines.")
                val item = byName[name] ?: return ToolResult.fail("no setting '$name'. Settings: ${items.joinToString { it.name }}")
                return ToolResult.ok("${item.name} = ${item.get(s)} (${item.about})")
            }
            val item = byName[name ?: return ToolResult.fail("which setting? give its name")]
                ?: return ToolResult.fail("no setting '$name'. Settings: ${items.joinToString { it.name }}")
            val value = args.string("value") ?: return ToolResult.fail("what value? ${item.about}")
            if (item.name == "only_my_voice" && bool(value) == true && !s.speakerEnrolled) {
                return ToolResult.fail("Sir's voice is not recorded yet. He must open JARVIS Settings > Voice lock once and say the wake word three times; then this can be turned on.")
            }
            val changed = item.set(s, value) ?: return ToolResult.fail("'$value' is not valid for ${item.name}: ${item.about}")
            settings.update { item.set(it, value) ?: it }
            if (item.name == "always_on") {
                if (changed.alwaysOn) ServiceController.start(context) else ServiceController.stop(context)
            }
            val note = when (item.name) {
                "live_voice", "voice_mode", "noise_filter", "hd_voice", "gf_voice", "dost_voice" -> " It takes effect from the next conversation."
                "role" -> " " + Persona.switched(changed.persona)
                else -> ""
            }
            return ToolResult.ok("${item.name} is now ${item.get(changed)}.$note")
        }
    }

    private val setPersona = object : SimpleTool(
        "set_persona",
        "Change who you are being when Sir asks: \"dost ban ke baat karo\" -> dost, \"gf / girlfriend ban ke baat karo\" -> gf, " +
            "\"Jarvis ban jao / normal ho jao\" -> jarvis. The role stays until he changes it again, across conversations. " +
            "Memory, tools and safety rules are the same in every role.",
        params { string("persona", "jarvis | dost | gf", required = true, enum = Persona.ALL) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val persona = Persona.normalize(args.requireString("persona"))
            // Only on Sir's own words: the role must not change because the model felt like it.
            if (persona != Persona.normalize(settings.current.persona) && !Persona.mentions(modes.lastUserWords, persona)) {
                return ToolResult.fail("Sir did not ask for the ${Persona.label(persona)} role just now. Stay exactly as you are and answer what he said.")
            }
            settings.update { it.copy(persona = persona) }
            return ToolResult.ok(Persona.switched(persona))
        }
    }

    val tools: List<JarvisTool> = listOf(jarvisSettings, setPersona)
}
