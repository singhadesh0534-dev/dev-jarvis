package com.adesh.devjarvis.settings

import android.content.Context
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
) {
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

    fun save(new: JarvisSettings) {
        val cleaned = new.copy(
            geminiApiKey = new.geminiApiKey.trim(),
            groqApiKey = new.groqApiKey.trim(),
            geminiModel = new.geminiModel.trim().ifBlank { JarvisConfig.GEMINI_MODEL },
            systemPrompt = new.systemPrompt.ifBlank { SystemPrompt.DEFAULT },
        )
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
            .apply()
        _settings.value = cleaned
    }

    private fun load(): JarvisSettings {
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
        )
    }

    private fun openPrefs(): SharedPreferences = try {
        create()
    } catch (e: Exception) {
        // Keystore kabhi-kabhi corrupt ho jata hai (restore/reinstall ke baad). Fresh start karo.
        context.deleteSharedPreferences(FILE)
        create()
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
    }
}
