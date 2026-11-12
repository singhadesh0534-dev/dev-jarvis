package com.adesh.devjarvis.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.adesh.devjarvis.brain.JarvisBrain
import com.adesh.devjarvis.brain.SystemPrompt
import com.adesh.devjarvis.core.CrashReporter
import com.adesh.devjarvis.core.JarvisConfig
import com.adesh.devjarvis.core.ProblemJournal
import com.adesh.devjarvis.core.SessionTrace
import com.adesh.devjarvis.proactive.BriefingScheduler
import com.adesh.devjarvis.settings.JarvisSettings
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.update.AppUpdater
import com.adesh.devjarvis.voice.SpeechOutput
import com.adesh.devjarvis.voice.VoiceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val speech: SpeechOutput,
    private val brain: JarvisBrain,
    private val briefing: BriefingScheduler,
    @ApplicationContext private val context: Context,
    val updater: AppUpdater,
    private val keyChecker: com.adesh.devjarvis.core.KeyChecker,
) : ViewModel() {
    /** Last crash + recovered errors, for Settings > Diagnostics (no keys or personal data inside). */
    fun diagnostics(): String? = listOfNotNull(
        CrashReporter.lastCrash(context),
        CrashReporter.recoveredLog(context),
        SessionTrace.dump().takeIf { it.isNotBlank() }?.let { "FLIGHT RECORDER (latest events)\n$it" },
        ProblemJournal.dump(60).takeIf { it.isNotBlank() }?.let { "PROBLEMS JARVIS NOTICED BY HIMSELF\n$it" },
    ).joinToString("\n\n").ifBlank { null }

    fun clearDiagnostics() {
        CrashReporter.clear(context)
        SessionTrace.clear()
        ProblemJournal.clear()
    }


    /** Editable copy; written to encrypted storage only on Save. */
    private val _draft = MutableStateFlow(repo.current)
    val draft: StateFlow<JarvisSettings> = _draft.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** Fingerprints of keys their service rejected: those boxes are shown in red. */
    val rejectedKeys = MutableStateFlow(com.adesh.devjarvis.core.BadKeys.bad).also { flow ->
        com.adesh.devjarvis.core.BadKeys.listener = { flow.value = it }
    }

    override fun onCleared() {
        com.adesh.devjarvis.core.BadKeys.listener = null
    }

    /** Result of "Keys check karo"; null = not run yet. */
    val keyReport = MutableStateFlow<String?>(null)

    fun checkKeys() {
        save()
        keyReport.value = "Check kar raha hoon…"
        viewModelScope.launch {
            val s = repo.current
            keyReport.value = runCatching { keyChecker.check(s.geminiApiKey, s.groqApiKey, s.elevenApiKey, s.extraApiKey, s.extraBaseUrl) }
                .getOrElse { "Check nahi ho paya (${it.javaClass.simpleName})." }
        }
    }

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    fun edit(change: (JarvisSettings) -> JarvisSettings) {
        _draft.update(change)
        _saved.value = false
    }

    fun resetPrompt() = edit { it.copy(systemPrompt = SystemPrompt.DEFAULT) }
    fun resetModel() = edit { it.copy(geminiModel = JarvisConfig.GEMINI_MODEL) }
    fun resetVoice() = edit {
        it.copy(ttsVoiceName = "", ttsPitch = JarvisConfig.TTS_PITCH_DEFAULT, ttsRate = JarvisConfig.TTS_RATE_DEFAULT)
    }

    fun save() {
        // alwaysOn / setupDone are owned by the HUD switch and the setup wizard — don't overwrite them.
        val cur = repo.current
        val ok = repo.save(
            _draft.value.copy(
                alwaysOn = cur.alwaysOn, setupDone = cur.setupDone,
                // Owned by the voice-lock screen and the set_wake_word tool, not this form.
                speakerLockEnabled = cur.speakerLockEnabled, speakerProfile = cur.speakerProfile,
                speakerThreshold = cur.speakerThreshold, customWakePhrases = cur.customWakePhrases,
            )
        )
        _draft.value = repo.current
        runCatching { speech.applySettings() }
        runCatching { briefing.reschedule() }
        _saved.value = ok
        _saveFailed.value = !ok
    }

    fun testVoice() {
        save()
        speech.stop()
        speech.speak("Good evening, Sir. Saare systems online hain, aur main hamesha ki tarah taiyaar hoon.")
    }

    fun voices(): List<VoiceInfo> = speech.availableVoices()
    fun activeVoice(): String? = speech.activeVoiceName()

    fun clearConversation() = brain.forgetConversation()

    /** Settings > "Play briefing now" — handy to test without waiting till morning. */
    fun playBriefingNow() {
        save()
        briefing.runNow()
    }

    fun playNightSummaryNow() {
        save()
        briefing.runNow(night = true)
    }
}
