package com.adesh.devjarvis.ui.hud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adesh.devjarvis.brain.BrainError
import com.adesh.devjarvis.brain.JarvisBrain
import com.adesh.devjarvis.brain.SentenceChunker
import com.adesh.devjarvis.brain.SpeechSanitizer
import com.adesh.devjarvis.core.DeviceStatus
import com.adesh.devjarvis.core.NetworkKind
import com.adesh.devjarvis.core.UsageTracker
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.voice.SpeechInput
import com.adesh.devjarvis.voice.SpeechOutput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class JarvisState(val label: String) {
    IDLE("STANDBY"), LISTENING("LISTENING"), THINKING("PROCESSING"), SPEAKING("SPEAKING")
}

data class TranscriptLine(val id: Long, val fromUser: Boolean, val text: String, val isSystem: Boolean = false)

data class HudUiState(
    val state: JarvisState = JarvisState.IDLE,
    val transcript: List<TranscriptLine> = emptyList(),
    val partial: String = "",
    val micLevel: Float = 0f,
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val network: NetworkKind = NetworkKind.NONE,
    val fuelPercent: Int = 100,
    val hasKey: Boolean = false,
    /** Voice pipeline in use. Phase 4 adds "LIVE" (Gemini Live) and "FALLBACK" (Groq). */
    val mode: String = "STREAM",
)

@HiltViewModel
class HudViewModel @Inject constructor(
    private val brain: JarvisBrain,
    private val speechIn: SpeechInput,
    private val speechOut: SpeechOutput,
    private val device: DeviceStatus,
    private val usage: UsageTracker,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(HudUiState())
    val ui: StateFlow<HudUiState> = _ui.asStateFlow()

    /** Speech amplitude straight from the TTS engine, for the reactor. */
    val speechLevel: StateFlow<Float> = speechOut.level

    private var listenJob: Job? = null
    private var replyJob: Job? = null
    private var nextId = 0L

    init {
        viewModelScope.launch { settings.settings.collect { s -> _ui.update { it.copy(hasKey = s.hasGeminiKey) } } }
        viewModelScope.launch { usage.fuelPercent.collect { f -> _ui.update { it.copy(fuelPercent = f) } } }
        viewModelScope.launch {
            while (isActive) {
                refreshDevice()
                delay(30_000)
            }
        }
        addLine(fromUser = false, text = "JARVIS online. Reactor tap kijiye aur boliye, Sir.", system = true)
    }

    fun refreshDevice() {
        val d = device.snapshot()
        usage.refresh()
        _ui.update { it.copy(batteryPercent = d.batteryPercent, charging = d.charging, network = d.network) }
    }

    /** Reactor tap: idle -> listen, listening -> stop, thinking/speaking -> interrupt and listen (barge-in). */
    fun onReactorTap() {
        when (_ui.value.state) {
            JarvisState.LISTENING -> {
                listenJob?.cancel()
                setState(JarvisState.IDLE)
            }
            JarvisState.THINKING, JarvisState.SPEAKING -> {
                interrupt()
                startListening()
            }
            JarvisState.IDLE -> startListening()
        }
    }

    fun onMicPermissionDenied() {
        say("Sir, bina microphone permission ke main sun nahi sakta. Tab tak type kar dijiye.")
    }

    /** Typed message from the text box. */
    fun onTextSubmitted(text: String) {
        if (text.isBlank()) return
        listenJob?.cancel()
        ask(text.trim())
    }

    fun stopEverything() {
        listenJob?.cancel()
        interrupt()
        setState(JarvisState.IDLE)
    }

    private fun startListening() {
        listenJob?.cancel()
        _ui.update { it.copy(state = JarvisState.LISTENING, partial = "", micLevel = 0f) }
        listenJob = viewModelScope.launch {
            var retriedBusy = false
            while (true) {
                var busy = false
                speechIn.listen(settings.current.sttLanguage).collect { ev ->
                    when (ev) {
                        SpeechInput.Event.Ready -> Unit
                        is SpeechInput.Event.Level -> _ui.update { it.copy(micLevel = ev.value) }
                        is SpeechInput.Event.Partial -> _ui.update { it.copy(partial = ev.text) }
                        is SpeechInput.Event.Final -> {
                            _ui.update { it.copy(partial = "", micLevel = 0f) }
                            ask(ev.text)
                        }
                        is SpeechInput.Event.Failed -> {
                            _ui.update { it.copy(partial = "", micLevel = 0f) }
                            if (ev.reason == SpeechInput.Reason.BUSY && !retriedBusy) busy = true
                            else onListenFailed(ev.reason)
                        }
                    }
                }
                if (!busy) break
                retriedBusy = true
                delay(400)
            }
        }
    }

    private fun onListenFailed(reason: SpeechInput.Reason) {
        when (reason) {
            SpeechInput.Reason.NOTHING_HEARD -> setState(JarvisState.IDLE)
            SpeechInput.Reason.NETWORK -> say("Sir, network gayab hai, abhi offline hoon.")
            SpeechInput.Reason.NO_PERMISSION -> onMicPermissionDenied()
            SpeechInput.Reason.UNAVAILABLE -> say(
                "Sir, is phone pe speech recognition service nahi mili. Google app update kar dijiye, tab tak type kijiye."
            )
            SpeechInput.Reason.BUSY, SpeechInput.Reason.OTHER -> say("Sir, kaan mein thodi khich-khich hui. Ek baar phir boliye.")
        }
    }

    private fun ask(userText: String) {
        interrupt()
        addLine(fromUser = true, text = userText)
        val lineId = nextId++
        setState(JarvisState.THINKING)
        replyJob = viewModelScope.launch {
            val chunker = SentenceChunker()
            val full = StringBuilder()
            try {
                brain.reply(userText).collect { delta ->
                    full.append(delta)
                    upsertLine(lineId, SpeechSanitizer.clean(full.toString()))
                    chunker.feed(delta).forEach(::speakPiece)
                }
                chunker.flush()?.let(::speakPiece)
                brain.remember(userText, full.toString())
                if (full.isBlank()) say("Sir, is baar mere paas koi jawab nahi aaya. Ek baar phir poochiye.")
                speechOut.awaitIdle()
                setState(JarvisState.IDLE)
            } catch (e: CancellationException) {
                // Barge-in: jitna bola, utna yaad rakho.
                brain.remember(userText, full.toString())
                throw e
            } catch (e: BrainError) {
                brain.remember(userText, full.toString())
                say(errorLine(e), detail = (e as? BrainError.Server)?.message)
                speechOut.awaitIdle()
                setState(JarvisState.IDLE)
            } catch (e: Exception) {
                say("Sir, kuch gadbad ho gayi. Ek baar phir try karte hain.", detail = e.javaClass.simpleName)
                speechOut.awaitIdle()
                setState(JarvisState.IDLE)
            }
        }
    }

    private fun errorLine(e: BrainError): String = when (e) {
        is BrainError.MissingKey -> "Sir, mera dimaag abhi plug-in nahi hua. Settings mein Gemini API key daal dijiye."
        is BrainError.NoNetwork -> "Sir, network gayab hai, abhi offline hoon."
        is BrainError.RateLimited -> "Sir, aaj ka free fuel khatam ho gaya. Thodi der baad phir koshish karte hain."
        is BrainError.InvalidKey -> "Sir, ye API key Google ko pasand nahi aayi. Settings mein ek baar check kar lijiye."
        is BrainError.Blocked -> "Sir, is sawaal pe mere safety filters ne haath khade kar diye. Kuch aur poochiye."
        is BrainError.Server -> "Sir, Google ke servers thoda nakhre kar rahe hain. Ek baar phir boliye."
    }

    private fun speakPiece(piece: String) {
        val clean = SpeechSanitizer.clean(piece)
        if (clean.isEmpty()) return
        speechOut.speak(clean)
        setState(JarvisState.SPEAKING)
    }

    /** JARVIS's own line (errors, notices): shown and spoken. */
    private fun say(text: String, detail: String? = null) {
        addLine(fromUser = false, text = text)
        detail?.let { addLine(fromUser = false, text = it, system = true) }
        speechOut.speak(text)
        setState(JarvisState.SPEAKING)
        viewModelScope.launch {
            speechOut.awaitIdle()
            if (_ui.value.state == JarvisState.SPEAKING && replyJob?.isActive != true) setState(JarvisState.IDLE)
        }
    }

    private fun interrupt() {
        replyJob?.cancel()
        replyJob = null
        speechOut.stop()
    }

    private fun setState(state: JarvisState) = _ui.update { it.copy(state = state) }

    private fun addLine(fromUser: Boolean, text: String, system: Boolean = false) {
        val line = TranscriptLine(nextId++, fromUser, text, system)
        _ui.update { it.copy(transcript = (it.transcript + line).takeLast(MAX_LINES)) }
    }

    private fun upsertLine(id: Long, text: String) {
        _ui.update { st ->
            val existing = st.transcript.indexOfFirst { it.id == id }
            val list = if (existing >= 0) {
                st.transcript.toMutableList().also { it[existing] = it[existing].copy(text = text) }
            } else {
                (st.transcript + TranscriptLine(id, fromUser = false, text = text)).takeLast(MAX_LINES)
            }
            st.copy(transcript = list)
        }
    }

    override fun onCleared() {
        speechOut.stop()
    }

    private companion object {
        const val MAX_LINES = 100
    }
}
