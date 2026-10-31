package com.adesh.devjarvis.core

import com.adesh.devjarvis.brain.BrainError
import com.adesh.devjarvis.brain.JarvisBrain
import com.adesh.devjarvis.brain.SentenceChunker
import com.adesh.devjarvis.brain.SpeechSanitizer
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.voice.AudioFocusHelper
import com.adesh.devjarvis.voice.Earcons
import com.adesh.devjarvis.voice.SpeechInput
import com.adesh.devjarvis.voice.SpeechOutput
import com.adesh.devjarvis.wakeword.VoskModelManager
import com.adesh.devjarvis.wakeword.WakePhraseMatcher
import com.adesh.devjarvis.wakeword.WakeWordEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

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
    /** Foreground service running and listening for "Hello Dev". */
    val alwaysOn: Boolean = false,
    /** True while a conversation (incl. the follow-up window) is open. */
    val inConversation: Boolean = false,
    val wakeHeard: String = "",
    val wakeNotice: String? = null,
    /** Voice pipeline in use. Phase 4 adds "LIVE" (Gemini Live) and "FALLBACK" (Groq). */
    val mode: String = "STREAM",
)

/** One-off things the background service must act on. */
sealed interface SessionSignal {
    /** Android muted our mic in the background; user must tap a notification to bring JARVIS back. */
    data object MicSilenced : SessionSignal
}

/**
 * JARVIS ka "nervous system": ek hi jagah jo sunne, sochne, bolne aur mic ka hisaab rakhta hai.
 * HUD (app khula ho) aur JarvisService (background) dono isi ko use karte hain.
 *
 * Mic ka rule (ek waqt pe ek hi cheez mic pakadti hai):
 *  - STANDBY            -> wake word engine ("Hello Dev")
 *  - LISTENING          -> Android speech recognizer
 *  - PROCESSING/SPEAKING -> wake engine barge-in mode ("Jarvis"/"stop"/"ruko") agar ON hai
 */
@Singleton
class JarvisSession @Inject constructor(
    private val brain: JarvisBrain,
    private val speechIn: SpeechInput,
    private val speechOut: SpeechOutput,
    private val device: DeviceStatus,
    private val usage: UsageTracker,
    private val settings: SettingsRepository,
    private val wake: WakeWordEngine,
    private val models: VoskModelManager,
    private val earcons: Earcons,
    private val focus: AudioFocusHelper,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _ui = MutableStateFlow(HudUiState())
    val ui: StateFlow<HudUiState> = _ui.asStateFlow()

    val speechLevel: StateFlow<Float> = speechOut.level

    private val _signals = MutableSharedFlow<SessionSignal>(extraBufferCapacity = 4)
    val signals: SharedFlow<SessionSignal> = _signals.asSharedFlow()

    private var listenJob: Job? = null
    private var replyJob: Job? = null
    private var nextId = 0L
    private var serviceActive = false
    /** Text JARVIS is currently saying — used to ignore him "hearing" his own barge-in words. */
    private var speakingText = ""

    init {
        scope.launch {
            settings.settings.collect { s ->
                wake.sensitivity = s.wakeSensitivity
                _ui.update { it.copy(hasKey = s.hasGeminiKey) }
                syncMic()
            }
        }
        scope.launch { usage.fuelPercent.collect { f -> _ui.update { it.copy(fuelPercent = f) } } }
        scope.launch { wake.lastHeard.collect { h -> _ui.update { it.copy(wakeHeard = h) } } }
        scope.launch { wake.detections.collect(::onDetection) }
        scope.launch { wake.problem.collect(::onWakeProblem) }
        scope.launch {
            models.state.collect { st ->
                if (st is VoskModelManager.State.Ready) syncMic()
                updateWakeNotice()
            }
        }
        scope.launch {
            while (isActive) {
                refreshDevice()
                delay(30_000)
            }
        }
        addLine(fromUser = false, text = "JARVIS online. Reactor tap kijiye ya \"Hello Dev\" boliye, Sir.", system = true)
    }

    // ---------------------------------------------------------------- public API

    fun refreshDevice() {
        val d = device.snapshot()
        usage.refresh()
        _ui.update { it.copy(batteryPercent = d.batteryPercent, charging = d.charging, network = d.network) }
    }

    /** Called by JarvisService when it enters / leaves the foreground. */
    fun setServiceActive(active: Boolean) {
        serviceActive = active
        _ui.update { it.copy(alwaysOn = active) }
        updateWakeNotice()
        syncMic()
    }

    /** Reactor tap: idle -> listen, listening -> stop, thinking/speaking -> interrupt and listen. */
    fun onReactorTap() {
        when (_ui.value.state) {
            JarvisState.LISTENING -> {
                listenJob?.cancel()
                endConversation(chime = false)
            }
            JarvisState.THINKING, JarvisState.SPEAKING -> {
                interrupt()
                startListening(followUp = false)
            }
            JarvisState.IDLE -> startConversation()
        }
    }

    /** Long-press home / assistant button launched us. */
    fun onAssistLaunch() {
        if (_ui.value.state == JarvisState.IDLE) {
            earcons.wake()
            startConversation()
        }
    }

    fun onMicPermissionDenied() {
        say("Sir, bina microphone permission ke main sun nahi sakta. Tab tak type kar dijiye.")
    }

    fun onTextSubmitted(text: String) {
        if (text.isBlank()) return
        listenJob?.cancel()
        // Typed chat: no follow-up listening afterwards (mic shouldn't open on its own).
        _ui.update { it.copy(inConversation = false) }
        ask(text.trim())
    }

    fun stopEverything() {
        listenJob?.cancel()
        interrupt()
        endConversation(chime = false)
    }

    // ---------------------------------------------------------------- wake word

    private fun onDetection(d: WakeWordEngine.Detection) {
        val state = _ui.value.state
        when (d.mode) {
            WakeWordEngine.Mode.WAKE -> if (state == JarvisState.IDLE) {
                earcons.wake()
                startConversation()
            }
            WakeWordEngine.Mode.BARGE_IN -> if (state == JarvisState.SPEAKING || state == JarvisState.THINKING) {
                // Agar JARVIS khud wahi shabd bol raha tha, to ye uski apni goonj hai — ignore.
                if (WakePhraseMatcher.matchedPhrase(speakingText, listOf(d.phrase)) != null) return
                interrupt()
                earcons.wake()
                startListening(followUp = false)
            }
        }
    }

    private fun onWakeProblem(problem: WakeWordEngine.Problem?) {
        when (problem) {
            WakeWordEngine.Problem.MIC_SILENCED -> if (serviceActive) {
                if (focus.inCall()) {
                    // Call chal rahi hai — mic phone app ke paas hai. Thodi der baad phir try.
                    scope.launch {
                        delay(20_000)
                        syncMic()
                    }
                } else {
                    _signals.tryEmit(SessionSignal.MicSilenced)
                }
            }
            WakeWordEngine.Problem.MIC_UNAVAILABLE -> if (serviceActive) scope.launch {
                delay(5_000)
                syncMic()
            }
            else -> Unit
        }
        updateWakeNotice()
    }

    private fun updateWakeNotice() {
        val notice = when {
            !serviceActive -> null
            models.state.value is VoskModelManager.State.Downloading ->
                "Wake model download ho raha hai… ${(models.state.value as VoskModelManager.State.Downloading).percent}%"
            !models.isInstalled() -> "Wake word model nahi hai — Setup mein download kijiye."
            wake.problem.value == WakeWordEngine.Problem.ENGINE_ERROR -> "Wake engine start nahi hua."
            wake.problem.value == WakeWordEngine.Problem.MIC_UNAVAILABLE -> "Mic abhi kisi aur app ke paas hai."
            else -> null
        }
        _ui.update { it.copy(wakeNotice = notice) }
    }

    /** Decides who owns the mic for the current state. */
    private fun syncMic() {
        if (!serviceActive || !models.isInstalled() || focus.inCall()) {
            wake.stop()
            return
        }
        when (_ui.value.state) {
            JarvisState.IDLE -> wake.start(WakeWordEngine.Mode.WAKE)
            JarvisState.THINKING, JarvisState.SPEAKING ->
                if (settings.current.voiceBargeIn) wake.start(WakeWordEngine.Mode.BARGE_IN) else wake.stop()
            JarvisState.LISTENING -> wake.stop()
        }
    }

    // ---------------------------------------------------------------- conversation

    private fun startConversation() {
        focus.acquire()
        _ui.update { it.copy(inConversation = true) }
        startListening(followUp = false)
    }

    private fun endConversation(chime: Boolean) {
        val wasOpen = _ui.value.inConversation
        focus.release()
        _ui.update { it.copy(inConversation = false, partial = "", micLevel = 0f) }
        setState(JarvisState.IDLE)
        if (chime && wasOpen) earcons.sleep()
    }

    private enum class ListenOutcome { HEARD, NOTHING, BUSY, FAILED }

    private fun startListening(followUp: Boolean) {
        listenJob?.cancel()
        _ui.update { it.copy(partial = "", micLevel = 0f) }
        setState(JarvisState.LISTENING)
        listenJob = scope.launch {
            // Mic wake engine se wapas lo, phir recognizer ko do.
            wake.stop()
            wake.awaitStopped()
            val deadline = System.currentTimeMillis() + JarvisConfig.FOLLOW_UP_WINDOW_MS
            var retriedBusy = false
            while (true) {
                var outcome = ListenOutcome.NOTHING
                var heardText = ""
                var failure: SpeechInput.Reason? = null
                speechIn.listen(settings.current.sttLanguage).collect { ev ->
                    when (ev) {
                        SpeechInput.Event.Ready -> Unit
                        is SpeechInput.Event.Level -> _ui.update { it.copy(micLevel = ev.value) }
                        is SpeechInput.Event.Partial -> _ui.update { it.copy(partial = ev.text) }
                        is SpeechInput.Event.Final -> {
                            outcome = ListenOutcome.HEARD
                            heardText = ev.text
                        }
                        is SpeechInput.Event.Failed -> {
                            failure = ev.reason
                            outcome = when (ev.reason) {
                                SpeechInput.Reason.NOTHING_HEARD -> ListenOutcome.NOTHING
                                SpeechInput.Reason.BUSY -> ListenOutcome.BUSY
                                else -> ListenOutcome.FAILED
                            }
                        }
                    }
                }
                _ui.update { it.copy(partial = "", micLevel = 0f) }
                when (outcome) {
                    ListenOutcome.HEARD -> {
                        ask(heardText)
                        return@launch
                    }
                    ListenOutcome.NOTHING -> {
                        // Follow-up window mein chup rahe to thoda aur suno; warna standby.
                        if (followUp && System.currentTimeMillis() < deadline) continue
                        endConversation(chime = followUp)
                        return@launch
                    }
                    ListenOutcome.BUSY -> if (!retriedBusy) {
                        retriedBusy = true
                        delay(400)
                        continue
                    } else {
                        onListenFailed(SpeechInput.Reason.BUSY)
                        return@launch
                    }
                    ListenOutcome.FAILED -> {
                        onListenFailed(failure ?: SpeechInput.Reason.OTHER)
                        return@launch
                    }
                }
            }
        }
    }

    private fun onListenFailed(reason: SpeechInput.Reason) {
        when (reason) {
            SpeechInput.Reason.NOTHING_HEARD -> endConversation(chime = false)
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
        replyJob = scope.launch {
            val chunker = SentenceChunker()
            val full = StringBuilder()
            try {
                brain.reply(userText).collect { delta ->
                    full.append(delta)
                    speakingText = full.toString()
                    upsertLine(lineId, SpeechSanitizer.clean(full.toString()))
                    chunker.feed(delta).forEach(::speakPiece)
                }
                chunker.flush()?.let(::speakPiece)
                brain.remember(userText, full.toString())
                if (full.isBlank()) say("Sir, is baar mere paas koi jawab nahi aaya. Ek baar phir poochiye.")
                speechOut.awaitIdle()
                afterReply(userText)
            } catch (e: CancellationException) {
                // Barge-in: jitna bola, utna yaad rakho.
                brain.remember(userText, full.toString())
                throw e
            } catch (e: BrainError) {
                brain.remember(userText, full.toString())
                say(errorLine(e), detail = (e as? BrainError.Server)?.message)
                speechOut.awaitIdle()
                endConversation(chime = false)
            } catch (e: Exception) {
                say("Sir, kuch gadbad ho gayi. Ek baar phir try karte hain.", detail = e.javaClass.simpleName)
                speechOut.awaitIdle()
                endConversation(chime = false)
            } finally {
                speakingText = ""
            }
        }
    }

    /** Continuous conversation: jawab ke baad bina wake word ke ~8 sec follow-up suno. */
    private fun afterReply(userText: String) {
        val keepGoing = _ui.value.inConversation &&
            settings.current.continuousConversation &&
            !ConversationRules.isEnder(userText)
        if (keepGoing) startListening(followUp = true) else endConversation(chime = false)
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
        speakingText = text
        speechOut.speak(text)
        setState(JarvisState.SPEAKING)
        scope.launch {
            speechOut.awaitIdle()
            if (_ui.value.state == JarvisState.SPEAKING && replyJob?.isActive != true) endConversation(chime = false)
        }
    }

    private fun interrupt() {
        replyJob?.cancel()
        replyJob = null
        speechOut.stop()
    }

    private fun setState(state: JarvisState) {
        if (_ui.value.state == state) return
        _ui.update { it.copy(state = state) }
        syncMic()
    }

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

    private companion object {
        const val MAX_LINES = 100
    }
}
