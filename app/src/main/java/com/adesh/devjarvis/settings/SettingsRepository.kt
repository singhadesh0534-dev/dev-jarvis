package com.adesh.devjarvis.settings

import android.content.Context
import com.adesh.devjarvis.core.CrashReporter
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.adesh.devjarvis.brain.SystemPrompt
import com.adesh.devjarvis.core.JarvisConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class JarvisSettings(
    val geminiApiKey: String = "",
    val groqApiKey: String = "",
    val geminiModel: String = JarvisConfig.GEMINI_MODEL,
    val systemPrompt: String = SystemPrompt.DEFAULT,
    val sttLanguage: String = JarvisConfig.STT_LANGUAGE_DEFAULT,
    /** Blank = auto-pick the deepest preferred male voice. */
    val ttsVoiceName: String = "",
    val ttsPitch: Float = JarvisConfig.TTS_PITCH_DEFAULT,
    val ttsRate: Float = JarvisConfig.TTS_RATE_DEFAULT,
    /** Background mein "Hello Dev" sunta rahe (foreground service). */
    val alwaysOn: Boolean = false,
    /** 0 = sirf saaf bola hua wake word, 1 = jaldi trigger (zyada false alarms). */
    val wakeSensitivity: Float = JarvisConfig.WAKE_SENSITIVITY_DEFAULT,
    /** Jawab ke baad ~8 sec follow-up sunna. */
    val continuousConversation: Boolean = true,
    /** JARVIS bolte waqt "Jarvis"/"stop"/"ruko" bolke rokna. */
    val voiceBargeIn: Boolean = true,
    val setupDone: Boolean = false,
    /** Weather / briefing default city. */
    val homeCity: String = "",
    /** "live" = Gemini Live (falls back to classic automatically), "classic" = STT -> text -> TTS. */
    val voiceEngine: String = VOICE_LIVE,
    val liveModel: String = JarvisConfig.GEMINI_LIVE_MODEL,
    val liveVoice: String = JarvisConfig.GEMINI_LIVE_VOICE,
    val groqModel: String = JarvisConfig.GROQ_CHAT_MODEL,
    /** Classic-mode ears: "android" (Google speech) or "groq" (Whisper, better Hinglish; needs Groq key). */
    val sttEngine: String = STT_ANDROID,
    // ---- Proactive (Phase 5) ----
    val briefingEnabled: Boolean = true,
    /** Minutes after midnight. 7:30 AM default. */
    val briefingMinute: Int = 7 * 60 + 30,
    /** Quiet hours: no proactive speech (Sir's own reminders still speak). */
    val quietStartMinute: Int = 23 * 60,
    val quietEndMinute: Int = 7 * 60,
    val notificationAlerts: Boolean = true,
    /** Comma-separated app names whose notifications JARVIS speaks (filtered, never OTP/bank). */
    val importantApps: String = "WhatsApp, Messages, Phone",
    /** Comma-separated sender names that are always important (e.g. "Mummy, Papa"). */
    val importantContacts: String = "",
    val lowBatteryAlert: Boolean = true,
    // ---- Polish (Phase 7) ----
    /** Floating mini-reactor over other apps while JARVIS listens/speaks (needs "Display over other apps"). */
    val bubbleEnabled: Boolean = true,
    val soundEffects: Boolean = true,
    /** Pause the wake word below 15% battery (not charging). */
    val pauseWakeOnLowBattery: Boolean = true,
    val bootAnimation: Boolean = true,
    // ---- Speaker lock (best-effort "sirf meri awaaz") ----
    val speakerLockEnabled: Boolean = false,
    /** Enrolled voiceprint, comma-separated floats. Blank = not enrolled yet. */
    val speakerProfile: String = "",
    /** 0 (loose) .. 1 (strict). */
    val speakerThreshold: Float = com.adesh.devjarvis.wakeword.SpeakerVerification.DEFAULT_THRESHOLD,
) {
    val speakerEnrolled: Boolean get() = speakerProfile.isNotBlank()
    val hasGroqKey: Boolean get() = groqApiKey.isNotBlank()

    companion object {
        const val VOICE_LIVE = "live"
        const val VOICE_CLASSIC = "classic"
        const val STT_ANDROID = "android"
        const val STT_GROQ = "groq"
    }

    val hasGeminiKey: Boolean get() = geminiApiKey.isNotBlank()
}

/**
 * Saari settings EncryptedSharedPreferences mein (Android Keystore se encrypted).
 * API keys kabhi log nahi hoti, code mein hardcode nahi hoti.
 */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs: SharedPreferences = openPrefs()
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<JarvisSettings> = _settings.asStateFlow()

    val current: JarvisSettings get() = _settings.value

    /** Small atomic update, e.g. `update { it.copy(alwaysOn = true) }`. */
    @Synchronized
    fun update(change: (JarvisSettings) -> JarvisSettings) = save(change(current))

    /** Returns true if it was written to disk. Never throws — a storage failure must not crash JARVIS. */
    fun save(new: JarvisSettings): Boolean {
        val cleaned = new.copy(
            geminiApiKey = new.geminiApiKey.trim(),
            groqApiKey = new.groqApiKey.trim(),
            geminiModel = new.geminiModel.trim().ifBlank { JarvisConfig.GEMINI_MODEL },
            systemPrompt = new.systemPrompt.ifBlank { SystemPrompt.DEFAULT },
        )
        // Update in-memory state first, so the UI reflects the change even if the disk write fails.
        _settings.value = cleaned
        return try {
            writeToDisk(cleaned)
            true
        } catch (e: Exception) {
            CrashReporter.record(context, "settings-save", e)
            false
        }
    }

    private fun writeToDisk(cleaned: JarvisSettings) {
        prefs.edit()
            .putString(K_GEMINI, cleaned.geminiApiKey)
            .putString(K_GROQ, cleaned.groqApiKey)
            .putString(K_MODEL, cleaned.geminiModel)
            .putString(K_PROMPT, cleaned.systemPrompt)
            .putString(K_STT_LANG, cleaned.sttLanguage)
            .putString(K_VOICE, cleaned.ttsVoiceName)
            .putFloat(K_PITCH, cleaned.ttsPitch)
            .putFloat(K_RATE, cleaned.ttsRate)
            .putBoolean(K_ALWAYS_ON, cleaned.alwaysOn)
            .putFloat(K_WAKE_SENS, cleaned.wakeSensitivity)
            .putBoolean(K_CONTINUOUS, cleaned.continuousConversation)
            .putBoolean(K_BARGE_IN, cleaned.voiceBargeIn)
            .putBoolean(K_SETUP_DONE, cleaned.setupDone)
            .putString(K_HOME_CITY, cleaned.homeCity.trim())
            .putString(K_VOICE_ENGINE, cleaned.voiceEngine)
            .putString(K_LIVE_MODEL, cleaned.liveModel.trim().ifBlank { JarvisConfig.GEMINI_LIVE_MODEL })
            .putString(K_LIVE_VOICE, cleaned.liveVoice.trim().ifBlank { JarvisConfig.GEMINI_LIVE_VOICE })
            .putString(K_GROQ_MODEL, cleaned.groqModel.trim().ifBlank { JarvisConfig.GROQ_CHAT_MODEL })
            .putString(K_STT_ENGINE, cleaned.sttEngine)
            .putBoolean(K_BRIEFING_ON, cleaned.briefingEnabled)
            .putInt(K_BRIEFING_MIN, cleaned.briefingMinute.coerceIn(0, 1439))
            .putInt(K_QUIET_START, cleaned.quietStartMinute.coerceIn(0, 1439))
            .putInt(K_QUIET_END, cleaned.quietEndMinute.coerceIn(0, 1439))
            .putBoolean(K_NOTIF_ALERTS, cleaned.notificationAlerts)
            .putString(K_IMPORTANT_APPS, cleaned.importantApps)
            .putString(K_IMPORTANT_CONTACTS, cleaned.importantContacts)
            .putBoolean(K_LOW_BATTERY, cleaned.lowBatteryAlert)
            .putBoolean(K_BUBBLE, cleaned.bubbleEnabled)
            .putBoolean(K_SFX, cleaned.soundEffects)
            .putBoolean(K_LOW_POWER_PAUSE, cleaned.pauseWakeOnLowBattery)
            .putBoolean(K_BOOT_ANIM, cleaned.bootAnimation)
            .putBoolean(K_SPK_LOCK, cleaned.speakerLockEnabled)
            .putString(K_SPK_PROFILE, cleaned.speakerProfile)
            .putFloat(K_SPK_THRESHOLD, cleaned.speakerThreshold)
            // commit (not apply) so a disk/keystore failure is thrown here and caught, not swallowed on a
            // background thread — that's what made "save" silently do nothing on some devices.
            .commit()
    }

    private fun load(): JarvisSettings = try {
        loadFromPrefs()
    } catch (e: Exception) {
        CrashReporter.record(context, "settings-load", e)
        JarvisSettings()
    }

    private fun loadFromPrefs(): JarvisSettings {
        val d = JarvisSettings()
        return JarvisSettings(
            geminiApiKey = prefs.getString(K_GEMINI, d.geminiApiKey).orEmpty(),
            groqApiKey = prefs.getString(K_GROQ, d.groqApiKey).orEmpty(),
            geminiModel = prefs.getString(K_MODEL, d.geminiModel).orEmpty().ifBlank { d.geminiModel },
            systemPrompt = prefs.getString(K_PROMPT, d.systemPrompt).orEmpty().ifBlank { d.systemPrompt },
            sttLanguage = prefs.getString(K_STT_LANG, d.sttLanguage).orEmpty().ifBlank { d.sttLanguage },
            ttsVoiceName = prefs.getString(K_VOICE, d.ttsVoiceName).orEmpty(),
            ttsPitch = prefs.getFloat(K_PITCH, d.ttsPitch),
            ttsRate = prefs.getFloat(K_RATE, d.ttsRate),
            alwaysOn = prefs.getBoolean(K_ALWAYS_ON, d.alwaysOn),
            wakeSensitivity = prefs.getFloat(K_WAKE_SENS, d.wakeSensitivity),
            continuousConversation = prefs.getBoolean(K_CONTINUOUS, d.continuousConversation),
            voiceBargeIn = prefs.getBoolean(K_BARGE_IN, d.voiceBargeIn),
            setupDone = prefs.getBoolean(K_SETUP_DONE, d.setupDone),
            homeCity = prefs.getString(K_HOME_CITY, d.homeCity).orEmpty(),
            voiceEngine = prefs.getString(K_VOICE_ENGINE, d.voiceEngine).orEmpty().ifBlank { d.voiceEngine },
            liveModel = prefs.getString(K_LIVE_MODEL, d.liveModel).orEmpty().ifBlank { d.liveModel },
            liveVoice = prefs.getString(K_LIVE_VOICE, d.liveVoice).orEmpty().ifBlank { d.liveVoice },
            groqModel = prefs.getString(K_GROQ_MODEL, d.groqModel).orEmpty().ifBlank { d.groqModel },
            sttEngine = prefs.getString(K_STT_ENGINE, d.sttEngine).orEmpty().ifBlank { d.sttEngine },
            briefingEnabled = prefs.getBoolean(K_BRIEFING_ON, d.briefingEnabled),
            briefingMinute = prefs.getInt(K_BRIEFING_MIN, d.briefingMinute),
            quietStartMinute = prefs.getInt(K_QUIET_START, d.quietStartMinute),
            quietEndMinute = prefs.getInt(K_QUIET_END, d.quietEndMinute),
            notificationAlerts = prefs.getBoolean(K_NOTIF_ALERTS, d.notificationAlerts),
            importantApps = prefs.getString(K_IMPORTANT_APPS, d.importantApps).orEmpty(),
            importantContacts = prefs.getString(K_IMPORTANT_CONTACTS, d.importantContacts).orEmpty(),
            lowBatteryAlert = prefs.getBoolean(K_LOW_BATTERY, d.lowBatteryAlert),
            bubbleEnabled = prefs.getBoolean(K_BUBBLE, d.bubbleEnabled),
            soundEffects = prefs.getBoolean(K_SFX, d.soundEffects),
            pauseWakeOnLowBattery = prefs.getBoolean(K_LOW_POWER_PAUSE, d.pauseWakeOnLowBattery),
            bootAnimation = prefs.getBoolean(K_BOOT_ANIM, d.bootAnimation),
            speakerLockEnabled = prefs.getBoolean(K_SPK_LOCK, d.speakerLockEnabled),
            speakerProfile = prefs.getString(K_SPK_PROFILE, d.speakerProfile).orEmpty(),
            speakerThreshold = prefs.getFloat(K_SPK_THRESHOLD, d.speakerThreshold),
        )
    }

    /** True if we fell back to plain (unencrypted) storage because the Keystore wouldn't cooperate. */
    @Volatile var usingPlainFallback = false
        private set

    private fun openPrefs(): SharedPreferences {
        // Try encrypted. Keystore corrupts after restore/reinstall, or on some XOS/Infinix builds plain refuses
        // to work at all — in that case fall back to normal prefs so JARVIS saves settings instead of crashing.
        return try {
            create()
        } catch (e: Exception) {
            runCatching { context.deleteSharedPreferences(FILE) }
            try {
                create()
            } catch (e2: Exception) {
                CrashReporter.record(context, "settings-encrypted", e2)
                usingPlainFallback = true
                context.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)
            }
        }
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val FILE = "jarvis_secure_settings"
        const val PLAIN_FILE = "jarvis_settings_plain"
        const val K_SPK_LOCK = "speaker_lock"
        const val K_SPK_PROFILE = "speaker_profile"
        const val K_SPK_THRESHOLD = "speaker_threshold"
        const val K_GEMINI = "gemini_api_key"
        const val K_GROQ = "groq_api_key"
        const val K_MODEL = "gemini_model"
        const val K_PROMPT = "system_prompt"
        const val K_STT_LANG = "stt_language"
        const val K_VOICE = "tts_voice"
        const val K_PITCH = "tts_pitch"
        const val K_RATE = "tts_rate"
        const val K_ALWAYS_ON = "always_on"
        const val K_WAKE_SENS = "wake_sensitivity"
        const val K_CONTINUOUS = "continuous_conversation"
        const val K_BARGE_IN = "voice_barge_in"
        const val K_SETUP_DONE = "setup_done"
        const val K_HOME_CITY = "home_city"
        const val K_VOICE_ENGINE = "voice_engine"
        const val K_LIVE_MODEL = "live_model"
        const val K_LIVE_VOICE = "live_voice"
        const val K_GROQ_MODEL = "groq_model"
        const val K_STT_ENGINE = "stt_engine"
        const val K_BRIEFING_ON = "briefing_on"
        const val K_BRIEFING_MIN = "briefing_minute"
        const val K_QUIET_START = "quiet_start"
        const val K_QUIET_END = "quiet_end"
        const val K_NOTIF_ALERTS = "notification_alerts"
        const val K_IMPORTANT_APPS = "important_apps"
        const val K_IMPORTANT_CONTACTS = "important_contacts"
        const val K_LOW_BATTERY = "low_battery_alert"
        const val K_BUBBLE = "bubble"
        const val K_SFX = "sound_effects"
        const val K_LOW_POWER_PAUSE = "low_power_pause"
        const val K_BOOT_ANIM = "boot_animation"
    }
}
