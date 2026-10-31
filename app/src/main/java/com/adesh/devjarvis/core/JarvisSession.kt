package com.adesh.devjarvis.core

import android.content.Context
import android.media.AudioManager
import com.adesh.devjarvis.brain.BrainError
import com.adesh.devjarvis.brain.BrainEvent
import com.adesh.devjarvis.brain.JarvisBrain
import com.adesh.devjarvis.brain.SentenceChunker
import com.adesh.devjarvis.brain.SpeechSanitizer
import com.adesh.devjarvis.live.LiveAudio
import com.adesh.devjarvis.live.LiveConnection
import com.adesh.devjarvis.live.LiveMessages
import com.adesh.devjarvis.memory.MemoryExtractor
import com.adesh.devjarvis.settings.JarvisSettings
import com.adesh.devjarvis.screen.ScreenTools
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.tools.ConfirmationParser
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolRegistry
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.voice.AudioFocusHelper
import com.adesh.devjarvis.voice.Earcons
import com.adesh.devjarvis.voice.GroqWhisperInput
import com.adesh.devjarvis.voice.SpeechInput
import com.adesh.devjarvis.voice.SpeechOutput
import com.adesh.devjarvis.wakeword.SpeakerVerification
import com.adesh.devjarvis.wakeword.VoskModelManager
import com.adesh.devjarvis.wakeword.WakePhraseMatcher
import com.adesh.devjarvis.wakeword.WakeWordEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

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
    /** Non-null while JARVIS waits for "haan / nahi" (HUD shows YES / NO buttons). */
    val pendingConfirmation: String? = null,
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
    private val registry: ToolRegistry,
    private val activityLog: ActivityLog,
    private val groqWhisper: GroqWhisperInput,
    private val http: OkHttpClient,
    private val memoryExtractor: MemoryExtractor,
    private val screenTools: ScreenTools,
    @ApplicationContext context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val appContext = context.applicationContext

    /** A bug inside a conversation must not kill always-on JARVIS: log it, reset, carry on. */
    private val recover = CoroutineExceptionHandler { _, e ->
        CrashReporter.record(appContext, "session", e)
        runCatching {
            liveConn?.close()
            speechOut.stop()
            focus.release()
            _ui.update { it.copy(state = JarvisState.IDLE, inConversation = false, pendingConfirmation = null, partial = "", micLevel = 0f) }
            addLine(fromUser = false, system = true, text = "Ek chhota glitch hua — maine khud ko reset kar liya, Sir. (${e.javaClass.simpleName})")
            syncMic()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + recover)

    private val _ui = MutableStateFlow(HudUiState())
    val ui: StateFlow<HudUiState> = _ui.asStateFlow()

    private val liveOutLevel = MutableStateFlow(0f)
    /** Speech amplitude for the reactor: Android TTS (classic) or Live audio output. */
    val speechLevel: StateFlow<Float> = combine(speechOut.level, liveOutLevel) { a, b -> maxOf(a, b) }
        .stateIn(scope, SharingStarted.Eagerly, 0f)

    private val _signals = MutableSharedFlow<SessionSignal>(extraBufferCapacity = 4)
    val signals: SharedFlow<SessionSignal> = _signals.asSharedFlow()

    private var listenJob: Job? = null
    private var replyJob: Job? = null
    private var nextId = 0L
    private var serviceActive = false
    /** Text JARVIS is currently saying — used to ignore him "hearing" his own barge-in words. */
    private var speakingText = ""
    /** Set by a tool like make_call: close the conversation after this reply instead of listening on. */
    private var endAfterReply = false
    private var confirmAnswer: CompletableDeferred<Boolean>? = null

    /** Wake word paused to save a nearly empty battery (tap / home long-press still work). */
    private var lowPowerPause = false
    private var conversationStartedAt = 0L
    private var memoryJob: Job? = null

    // Live mode
    private var liveJob: Job? = null
    private var liveConn: LiveConnection? = null
    private var liveAudio: LiveAudio? = null
    private var liveClosingByUs = false
    /** Don't retry Live before this time (after a non-quota failure). */
    private var liveRetryAt = 0L
    private val liveActive: Boolean get() = liveJob?.isActive == true

    private val toolContext = object : ToolContext {
        override suspend fun confirm(question: String): Boolean = confirmByVoice(question)
    }

    init {
        scope.launch {
            var prevSpeakerKey = ""
            settings.settings.collect { s ->
                wake.sensitivity = s.wakeSensitivity
                wake.speakerLock = s.speakerLockEnabled
                wake.speakerProfile = SpeakerVerification.decode(s.speakerProfile)
                wake.speakerThreshold = s.speakerThreshold
                _ui.update { it.copy(hasKey = s.hasGeminiKey || s.hasGroqKey) }
                // Speaker-lock changes need the wake engine rebuilt (it picks the recognizer type at start).
                val speakerKey = "${s.speakerLockEnabled}|${s.speakerProfile.length}|${s.speakerThreshold}"
                if (speakerKey != prevSpeakerKey && prevSpeakerKey.isNotEmpty()) wake.stop()
                prevSpeakerKey = speakerKey
                syncMic()
                updateMode()
            }
        }
        scope.launch { usage.fuel.collect { updateMode() } }
        scope.launch { brain.active.collect { updateMode() } }
        scope.launch { wake.lastHeard.collect { h -> _ui.update { it.copy(wakeHeard = h) } } }
        scope.launch { wake.detections.collect(::onDetection) }
        scope.launch { screenTools.progress.collect { line -> addLine(fromUser = false, text = line, system = true) } }
        scope.launch { wake.problem.collect(::onWakeProblem) }
        // Call shuru/khatam hone par turant react: beech mein JARVIS turant chup aur mic chhod de.
        focus.watchCallState { scope.launch { onCallStateChanged() } }
        scope.launch {
            models.state.collect { st ->
                if (st is VoskModelManager.State.Ready) syncMic()
                updateWakeNotice()
            }
        }
        scope.launch {
            while (isActive) {
                if (AppVisibility.isForeground) refreshDevice() // HUD hidden? don't bother.
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

    fun setLowPowerPause(paused: Boolean) {
        if (lowPowerPause == paused) return
        lowPowerPause = paused
        addLine(
            fromUser = false, system = true,
            text = if (paused) "Battery bachane ke liye wake word pause — tap karke baat kar sakte hain." else "Wake word wapas online.",
        )
        syncMic()
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
        if (liveActive) {
            // Live mode: interrupting is done by voice; a tap ends the Live conversation.
            closeLive(chime = false)
            return
        }
        when (_ui.value.state) {
            JarvisState.LISTENING -> {
                listenJob?.cancel()
                if (_ui.value.pendingConfirmation != null) interrupt()
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
        memoryJob?.cancel()
        if (conversationStartedAt == 0L) conversationStartedAt = System.currentTimeMillis()
        listenJob?.cancel()
        // Typed chat: no follow-up listening afterwards (mic shouldn't open on its own).
        _ui.update { it.copy(inConversation = false) }
        ask(text.trim())
    }

    /** YES / NO buttons on the HUD while a confirmation is pending. */
    fun answerConfirmation(yes: Boolean) {
        confirmAnswer?.complete(yes)
    }

    /** Proactive line that should only be shown, not spoken (quiet hours, DND…). */
    fun note(text: String) = addLine(fromUser = false, text = text, system = true)

    /** Proactive line (e.g. a reminder). Spoken only if JARVIS is idle and no call is going on. */
    fun announce(text: String) {
        addLine(fromUser = false, text = text)
        if (_ui.value.state != JarvisState.IDLE || focus.inCall()) return
        earcons.tick()
        say(text)
    }

    fun stopEverything() {
        closeLive(chime = false)
        listenJob?.cancel()
        interrupt()
        endConversation(chime = false)
    }

    /** A phone call started or ended. During a call JARVIS goes fully silent and gives up the mic. */
    private fun onCallStateChanged() {
        if (focus.inCall()) {
            if (_ui.value.state != JarvisState.IDLE || _ui.value.inConversation || liveActive) stopEverything()
        }
        syncMic() // call started -> wake stops; call ended -> wake restarts
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
        if (liveActive) {
            wake.stop() // Live owns the mic.
            return
        }
        if (!serviceActive || lowPowerPause || !models.isInstalled() || focus.inCall()) {
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
        memoryJob?.cancel() // still talking — extract later, all at once
        if (!_ui.value.inConversation) conversationStartedAt = System.currentTimeMillis()
        focus.acquire()
        _ui.update { it.copy(inConversation = true) }
        if (shouldUseLive(settings.current)) startLive() else startListening(followUp = false)
    }

    private fun endConversation(chime: Boolean) {
        scheduleMemoryExtraction()
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
                classicListener().collect { ev ->
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
        val firstLineId = nextId++
        setState(JarvisState.THINKING)
        endAfterReply = false
        replyJob = scope.launch {
            val chunker = SentenceChunker()
            val full = StringBuilder()
            var lineId = firstLineId
            var lineStart = 0
            try {
                brain.reply(userText, ::runTool).collect { ev ->
                    when (ev) {
                        is BrainEvent.Text -> {
                            full.append(ev.delta)
                            speakingText = full.toString()
                            upsertLine(lineId, SpeechSanitizer.clean(full.substring(lineStart)))
                            chunker.feed(ev.delta).forEach(::speakPiece)
                        }
                        is BrainEvent.ToolStarted -> {
                            // Say what was already streamed before the action, then start a fresh line after it.
                            chunker.flush()?.let(::speakPiece)
                            if (full.isNotEmpty()) full.append(' ')
                            lineId = nextId++
                            lineStart = full.length
                        }
                        is BrainEvent.ToolFinished -> Unit
                    }
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
            !endAfterReply &&
            settings.current.continuousConversation &&
            !ConversationRules.isEnder(userText)
        if (keepGoing) startListening(followUp = true) else endConversation(chime = false)
    }

    // ---------------------------------------------------------------- tools

    private suspend fun runTool(name: String, args: JsonObject): ToolResult {
        setState(JarvisState.THINKING)
        val argText = args.entries.joinToString(", ") { (k, v) -> "$k=${v.toString().trim('"')}" }
        val lineId = nextId++
        upsertLine(lineId, "⚙ $name($argText)", system = true)
        val result = registry.execute(name, args, toolContext)
        if (result.ok) earcons.success() else earcons.error()
        if (result.endsConversation) endAfterReply = true
        upsertLine(lineId, (if (result.ok) "✓ " else "✗ ") + "$name: ${result.message}", system = true)
        activityLog.add(name, argText, result.ok, result.message)
        return result
    }

    /** Ask out loud, then wait for "haan / nahi" by voice or a YES / NO tap. No answer = no. */
    private suspend fun confirmByVoice(question: String): Boolean {
        speechOut.awaitIdle()
        while (liveAudio?.isPlaying() == true) delay(100)
        val live = liveAudio
        live?.pauseMic()
        try {
            return confirmByVoiceInner(question)
        } finally {
            live?.resumeMic()
        }
    }

    private suspend fun confirmByVoiceInner(question: String): Boolean {
        addLine(fromUser = false, text = question)
        speakingText = question
        speechOut.speak(question)
        setState(JarvisState.SPEAKING)
        speechOut.awaitIdle()

        val answer = CompletableDeferred<Boolean>()
        confirmAnswer = answer
        _ui.update { it.copy(pendingConfirmation = question) }
        val voice = scope.launch {
            repeat(2) { attempt ->
                setState(JarvisState.LISTENING)
                wake.stop()
                wake.awaitStopped()
                val heard = listenOnce()
                if (heard != null) addLine(fromUser = true, text = heard)
                when (ConfirmationParser.parse(heard)) {
                    ConfirmationParser.Answer.YES -> { answer.complete(true); return@launch }
                    ConfirmationParser.Answer.NO -> { answer.complete(false); return@launch }
                    ConfirmationParser.Answer.UNCLEAR -> if (attempt == 0 && heard != null) {
                        val again = "Haan ya naa, Sir?"
                        addLine(fromUser = false, text = again)
                        speechOut.speak(again)
                        setState(JarvisState.SPEAKING)
                        speechOut.awaitIdle()
                    }
                }
            }
            answer.complete(false)
        }
        return try {
            withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { answer.await() } ?: false
        } finally {
            voice.cancel()
            confirmAnswer = null
            _ui.update { it.copy(pendingConfirmation = null, partial = "", micLevel = 0f) }
            setState(JarvisState.THINKING)
        }
    }

    /** One recognizer pass; returns the text or null if nothing usable was heard. */
    private suspend fun listenOnce(): String? {
        var text: String? = null
        classicListener().collect { ev ->
            when (ev) {
                is SpeechInput.Event.Level -> _ui.update { it.copy(micLevel = ev.value) }
                is SpeechInput.Event.Partial -> _ui.update { it.copy(partial = ev.text) }
                is SpeechInput.Event.Final -> text = ev.text
                else -> Unit
            }
        }
        _ui.update { it.copy(partial = "", micLevel = 0f) }
        return text
    }

    /**
     * 15 s after a conversation ends, ask the LLM what's worth remembering. Debounced so a burst of
     * typed messages costs one request, not ten.
     */
    private fun scheduleMemoryExtraction() {
        memoryJob?.cancel()
        val started = conversationStartedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        memoryJob = scope.launch {
            delay(15_000)
            conversationStartedAt = 0L
            withContext(Dispatchers.IO) { runCatching { memoryExtractor.run(started) } }
        }
    }

    // ---------------------------------------------------------------- engines & mode

    /** Classic-mode ears: Groq Whisper if chosen and possible, else Android's recognizer. */
    private fun classicListener() = settings.current.let { s ->
        if (s.sttEngine == JarvisSettings.STT_GROQ && s.hasGroqKey && device.isOnline() && !usage.isExhausted(Provider.GROQ)) {
            groqWhisper.listen(s.sttLanguage)
        } else {
            speechIn.listen(s.sttLanguage)
        }
    }

    private fun shouldUseLive(s: JarvisSettings): Boolean =
        s.voiceEngine == JarvisSettings.VOICE_LIVE && s.hasGeminiKey && device.isOnline() &&
            !usage.isExhausted(Provider.LIVE) && System.currentTimeMillis() >= liveRetryAt

    /** HUD chip + fuel gauge follow whichever engine is (or would be) doing the work. */
    private fun updateMode() {
        val s = settings.current
        val provider = when {
            liveActive || (shouldUseLive(s) && s.voiceEngine == JarvisSettings.VOICE_LIVE) -> Provider.LIVE
            brain.active.value == Provider.GROQ || (!s.hasGeminiKey && s.hasGroqKey) || usage.isExhausted(Provider.GEMINI) -> Provider.GROQ
            else -> Provider.GEMINI
        }
        val label = when (provider) {
            Provider.LIVE -> "LIVE"
            Provider.GROQ -> "FALLBACK"
            Provider.GEMINI -> "STREAM"
        }
        _ui.update { it.copy(mode = label, fuelPercent = usage.fuel.value[provider] ?: 100) }
    }

    // ---------------------------------------------------------------- Live mode

    /**
     * Gemini Live: our mic -> WebSocket -> native audio back. Interruption is native (just speak).
     * Fails before the first reply (quota, model, key, network)? Fall back to classic for this conversation.
     */
    private fun startLive() {
        listenJob?.cancel()
        liveClosingByUs = false
        val s = settings.current
        _ui.update { it.copy(partial = "", micLevel = 0f) }
        liveJob = scope.launch {
            setState(JarvisState.LISTENING) // syncMic(): Live owns the mic now
            wake.stop()
            wake.awaitStopped()
            focus.ownCommunicationMode = true
            updateMode()

            val conn = LiveConnection(http, s.geminiApiKey, LiveConnection.setup(s.liveModel, s.liveVoice, brain.systemPrompt(s, forLive = true), registry.declarations))
            val audio = LiveAudio(audioManager) { bytes, n -> conn.sendAudio(bytes, n) }
            liveConn = conn
            liveAudio = audio
            val startedAt = System.currentTimeMillis()
            var anyReply = false
            var closeKind: LiveMessages.CloseKind? = null
            var closeDetail = ""
            val userSaid = StringBuilder()
            val jarvisSaid = StringBuilder()
            var userLine = -1L
            var jarvisLine = -1L
            var endAfterTurn = false
            var followUp: Job? = null

            fun commitTurn() {
                val u = userSaid.toString().trim()
                val j = jarvisSaid.toString().trim()
                if (u.isNotEmpty() || j.isNotEmpty()) brain.remember(u.ifEmpty { "…" }, j.ifEmpty { "…" })
                if (u.isNotEmpty() && ConversationRules.isEnder(u)) endAfterTurn = true
                userSaid.clear(); jarvisSaid.clear()
                userLine = -1; jarvisLine = -1
                speakingText = ""
            }

            /** After JARVIS finishes speaking, keep listening ~8 s; silence -> close. */
            fun armFollowUp() {
                followUp?.cancel()
                followUp = launch {
                    while (audio.isPlaying()) delay(100)
                    if (endAfterTurn || endAfterReply) {
                        closeLive(chime = false)
                        return@launch
                    }
                    setState(JarvisState.LISTENING)
                    delay(JarvisConfig.FOLLOW_UP_WINDOW_MS)
                    closeLive(chime = true)
                }
            }

            val meters = launch {
                while (isActive) {
                    _ui.update { it.copy(micLevel = audio.micLevel) }
                    liveOutLevel.value = audio.outLevel
                    delay(50)
                }
            }
            try {
                conn.connect()
                audio.start()
                armFollowUp()
                for (ev in conn.events) {
                    if (ev is LiveConnection.Event.Closed) {
                        closeKind = ev.kind
                        closeDetail = ev.detail
                        break
                    }
                    when (val e = (ev as LiveConnection.Event.Server).event) {
                        is LiveMessages.ServerEvent.InputText -> {
                            followUp?.cancel()
                            userSaid.append(e.text)
                            if (userLine < 0) userLine = nextId++
                            upsertLine(userLine, userSaid.toString().trim(), fromUser = true)
                            if (_ui.value.state != JarvisState.SPEAKING) setState(JarvisState.LISTENING)
                        }
                        is LiveMessages.ServerEvent.Audio -> {
                            anyReply = true
                            followUp?.cancel()
                            audio.enqueue(e.pcm)
                            setState(JarvisState.SPEAKING)
                        }
                        is LiveMessages.ServerEvent.OutputText -> {
                            anyReply = true
                            jarvisSaid.append(e.text)
                            speakingText = jarvisSaid.toString()
                            if (jarvisLine < 0) jarvisLine = nextId++
                            upsertLine(jarvisLine, jarvisSaid.toString().trim())
                        }
                        LiveMessages.ServerEvent.Interrupted -> {
                            // Sir spoke over JARVIS: stop the speaker right now.
                            audio.flushPlayback()
                            commitTurn()
                            setState(JarvisState.LISTENING)
                        }
                        LiveMessages.ServerEvent.TurnComplete -> {
                            commitTurn()
                            armFollowUp()
                        }
                        is LiveMessages.ServerEvent.ToolCalls -> {
                            anyReply = true
                            followUp?.cancel()
                            val results = e.calls.map { c -> c to runTool(c.name, c.args) }
                            conn.sendToolResponse(LiveMessages.toolResponse(results))
                            setState(JarvisState.LISTENING)
                        }
                        is LiveMessages.ServerEvent.GoAway -> Unit // server ends the session soon; we close on Closed
                        is LiveMessages.ServerEvent.ToolCancelled, LiveMessages.ServerEvent.SetupComplete -> Unit
                    }
                }
            } finally {
                followUp?.cancel()
                meters.cancel()
                commitTurn()
                audio.stop()
                conn.close()
                liveConn = null
                liveAudio = null
                liveOutLevel.value = 0f
                focus.ownCommunicationMode = false
                usage.recordLiveSeconds(((System.currentTimeMillis() - startedAt) / 1000).toInt())
            }
            onLiveEnded(closeKind, closeDetail, anyReply)
        }
    }

    private fun onLiveEnded(kind: LiveMessages.CloseKind?, detail: String, anyReply: Boolean) {
        liveJob = null
        updateMode()
        if (liveClosingByUs) return // closeLive() already handled the ending
        when (kind) {
            LiveMessages.CloseKind.QUOTA -> usage.markExhausted(Provider.LIVE)
            LiveMessages.CloseKind.AUTH, LiveMessages.CloseKind.MODEL, LiveMessages.CloseKind.OTHER ->
                if (!anyReply) liveRetryAt = System.currentTimeMillis() + JarvisConfig.LIVE_RETRY_AFTER_MS
            else -> Unit
        }
        updateMode()
        if (!anyReply && _ui.value.inConversation && kind != LiveMessages.CloseKind.NETWORK) {
            // Auto-switch A -> B: Live couldn't start, carry on in classic mode without making Sir repeat the wake word.
            addLine(fromUser = false, text = "Live mode unavailable ($detail) — classic mode.", system = true)
            startListening(followUp = false)
            return
        }
        if (kind == LiveMessages.CloseKind.NETWORK && !anyReply) {
            say("Sir, network gayab hai, abhi offline hoon.")
            return
        }
        endConversation(chime = false)
    }

    /** End the Live conversation from our side (follow-up timeout, ender phrase, tap). */
    private fun closeLive(chime: Boolean) {
        val job = liveJob ?: return
        if (liveClosingByUs) return
        liveClosingByUs = true
        liveConn?.close()
        scope.launch {
            job.join()
            liveJob = null
            updateMode()
            endConversation(chime = chime)
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

    private fun upsertLine(id: Long, text: String, system: Boolean = false, fromUser: Boolean = false) {
        _ui.update { st ->
            val existing = st.transcript.indexOfFirst { it.id == id }
            val list = if (existing >= 0) {
                st.transcript.toMutableList().also { it[existing] = it[existing].copy(text = text) }
            } else {
                (st.transcript + TranscriptLine(id, fromUser = fromUser, text = text, isSystem = system)).takeLast(MAX_LINES)
            }
            st.copy(transcript = list)
        }
    }

    private companion object {
        const val MAX_LINES = 100
        const val CONFIRM_TIMEOUT_MS = 25_000L
    }
}
