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
) {
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
    }
}
