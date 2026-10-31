package com.adesh.devjarvis.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.adesh.devjarvis.core.JarvisConfig
import com.adesh.devjarvis.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android TextToSpeech, JARVIS style: deep English (UK/India) male voice, thoda low pitch, rate ~0.95.
 */
@Singleton
class AndroidTts @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: SettingsRepository,
) : SpeechOutput {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ready = CompletableDeferred<Boolean>()
    private val pending = AtomicInteger(0)
    private val utteranceIds = AtomicInteger(0)
    /** Bumped on every stop(), so late callbacks from cancelled utterances are ignored. */
    private val generation = AtomicInteger(0)
    private var decayJob: Job? = null

    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        val ok = status == TextToSpeech.SUCCESS
        if (ok) configure() else _error.value = "Text-to-speech engine start nahi hua. Google TTS install/enable karo."
        ready.complete(ok)
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (!isCurrent(utteranceId)) return
                _speaking.value = true
                _level.value = 0.8f
            }
            override fun onDone(utteranceId: String?) = finished(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (!isCurrent(utteranceId)) return
                // Har naye shabd pe reactor "pulse" karta hai — bolne ka amplitude jaisa feel.
                _level.value = 1f
            }
        })
    }

    private fun configure() {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        applySettings()
    }

    override fun applySettings() {
        if (!ready.isCompleted) return
        val s = settings.current
        val voice = pickVoice(s.ttsVoiceName)
        if (voice != null) tts.voice = voice else tts.language = Locale("en", "IN")
        tts.setPitch(s.ttsPitch)
        tts.setSpeechRate(s.ttsRate)
    }

    private fun pickVoice(preferredName: String): Voice? {
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == "en" && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
        if (voices.isEmpty()) return null
        if (preferredName.isNotBlank()) voices.firstOrNull { it.name == preferredName }?.let { return it }
        for (prefix in JarvisConfig.PREFERRED_VOICE_PREFIXES) {
            val matches = voices.filter { it.name.startsWith(prefix) }
            // Offline ("-local") version pehle: tez aur bina internet.
            (matches.firstOrNull { !it.isNetworkConnectionRequired } ?: matches.firstOrNull())?.let { return it }
        }
        // No preferred male voice installed: pick a British accent first (closest to the JARVIS feel),
        // then India, preferring higher-quality (often network) voices.
        fun best(country: String) = voices.filter { it.locale.country == country }
            .maxByOrNull { it.quality }
        return best("GB") ?: best("IN") ?: voices.maxByOrNull { it.quality } ?: voices.firstOrNull()
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        val gen = generation.get()
        pending.incrementAndGet()
        _speaking.value = true
        startDecay()
        scope.launch {
            if (!ready.await()) {
                finishedOne()
                return@launch
            }
            if (gen != generation.get()) return@launch // stop() was called meanwhile
            val id = "g$gen-${utteranceIds.incrementAndGet()}"
            val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, id)
            if (result != TextToSpeech.SUCCESS) finishedOne()
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        pending.set(0)
        tts.stop()
        _speaking.value = false
        _level.value = 0f
    }

    override suspend fun awaitIdle() {
        speaking.first { !it }
    }

    override fun availableVoices(): List<VoiceInfo> =
        runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == "en" && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.isNetworkConnectionRequired }, { it.name }))
            .map { VoiceInfo(it.name, it.locale.toLanguageTag(), !it.isNetworkConnectionRequired) }

    override fun activeVoiceName(): String? = runCatching { tts.voice?.name }.getOrNull()

    private fun isCurrent(utteranceId: String?): Boolean =
        utteranceId?.substringBefore('-') == "g${generation.get()}"

    private fun finished(utteranceId: String?) {
        if (isCurrent(utteranceId)) finishedOne()
    }

    private fun finishedOne() {
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            _speaking.value = false
            _level.value = 0f
        }
    }

    private fun startDecay() {
        if (decayJob?.isActive == true) return
        decayJob = scope.launch {
            while (isActive && _speaking.value) {
                _level.value = (_level.value * 0.88f).coerceAtLeast(0.25f)
                delay(32)
            }
            _level.value = 0f
        }
    }
}
