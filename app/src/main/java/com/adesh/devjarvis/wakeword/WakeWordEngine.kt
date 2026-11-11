package com.adesh.devjarvis.wakeword

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.adesh.devjarvis.core.JarvisConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline wake word — Vosk keyword spotting (chhoti grammar + "[unk]").
 *
 * Kyun Vosk (openWakeWord nahi): "Hello Dev" ek custom phrase hai. openWakeWord ko har naye phrase ke liye
 * model train karna padta hai; Vosk grammar mode mein koi bhi phrase bas likh do. Small model ~36 MB,
 * ek CPU core ka chhota hissa leta hai, aur poora offline chalta hai.
 *
 * Do modes:
 *  - WAKE: standby mein "Hello Dev" / "Jarvis" sunna.
 *  - BARGE_IN: JARVIS bol raha ho tab "Jarvis" / "stop" / "ruko" sunna (echo canceller ke saath).
 *
 * Ek hi background thread; mode badalne pe sirf grammar badalti hai, mic band nahi hota.
 */
@Singleton
class WakeWordEngine @Inject constructor(
    @ApplicationContext context: Context,
    private val models: VoskModelManager,
    private val speakerModels: SpeakerModelManager,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** A video / song is coming out of the phone's own loudspeaker right now (so the mic hears it loudly). */
    private fun phoneMediaOnSpeaker(): Boolean = runCatching {
        val am = audioManager ?: return false
        am.isMusicActive && am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }.getOrDefault(false)


    enum class Mode { WAKE, BARGE_IN }

    data class Detection(val mode: Mode, val phrase: String)

    enum class Problem { NO_MODEL, MIC_UNAVAILABLE, MIC_SILENCED, ENGINE_ERROR }

    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 8)
    val detections: SharedFlow<Detection> = _detections.asSharedFlow()

    /** Wake phrase heard but refused (too soft / too short / lost in background): for the problem journal. */
    val rejected = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** What Vosk last heard — shown dimly on the HUD so you can tune sensitivity. */
    private val _lastHeard = MutableStateFlow("")
    val lastHeard: StateFlow<String> = _lastHeard.asStateFlow()

    private val _problem = MutableStateFlow<Problem?>(null)
    val problem: StateFlow<Problem?> = _problem.asStateFlow()

    @Volatile var sensitivity: Float = JarvisConfig.WAKE_SENSITIVITY_DEFAULT

    /** Wake phrases in use. Sir can replace them by voice ("ab se Hello Shiv pe jaagna"). */
    @Volatile var wakePhrases: List<String> = JarvisConfig.WAKE_PHRASES

    // Speaker lock ("sirf meri awaaz") — set from settings. Only used for the WAKE phrase, never barge-in.
    @Volatile var speakerLock: Boolean = false
    @Volatile var speakerProfile: FloatArray? = null
    @Volatile var speakerThreshold: Float = SpeakerVerification.DEFAULT_THRESHOLD
    /** Last speaker-check result for the HUD: "you" / "not you" / "". */
    private val _lastSpeaker = MutableStateFlow("")
    val lastSpeaker: StateFlow<String> = _lastSpeaker.asStateFlow()

    @Volatile private var mode: Mode = Mode.WAKE
    @Volatile private var stopRequested = false
    private var thread: Thread? = null
    private var model: Model? = null
    private var spkModel: SpeakerModel? = null

    /** Start listening (or just switch mode if already running). Safe to call repeatedly. */
    @Synchronized
    fun start(mode: Mode) {
        this.mode = mode
        val old = thread
        if (old?.isAlive == true) {
            if (!stopRequested) return
            old.join(1_500) // still releasing the mic from a previous stop()
        }
        if (!models.isInstalled()) {
            _problem.value = Problem.NO_MODEL
            return
        }
        stopRequested = false
        _problem.value = null
        thread = Thread(::loop, "jarvis-wakeword").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    /** Ask the thread to release the mic. Use [awaitStopped] if you need the mic right away. */
    @Synchronized
    fun stop() {
        stopRequested = true
    }

    suspend fun awaitStopped() = withContext(Dispatchers.IO) {
        thread?.join(1_500)
    }

    @SuppressLint("MissingPermission") // Caller checks RECORD_AUDIO; SecurityException is caught below.
    private fun loop() {
        _running.value = true
        var record: AudioRecord? = null
        val effects = mutableListOf<AudioEffect>()
        var recognizer: Recognizer? = null
        try {
            val m = model ?: run {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                Model(models.modelDir.absolutePath).also { model = it }
            }
            record = openMic() ?: run {
                _problem.value = Problem.MIC_UNAVAILABLE
                return
            }
            effects += attachEffects(record.audioSessionId)

            var currentMode = mode
            // Attach the speaker model only when the lock is on and Sir is enrolled — then results carry an "spk" vector.
            // Enrolled = the voiceprint is always computed: needed for the lock, and to tell Sir from a video's voice.
            val useSpeaker = speakerProfile != null && speakerModels.isInstalled()
            val spk = if (useSpeaker) {
                (spkModel ?: runCatching { SpeakerModel(speakerModels.modelDir.absolutePath) }.getOrNull()?.also { spkModel = it })
            } else null
            // Grammar in the constructor, speaker model attached before any audio: the only order the native
            // library accepts. Changing either on a recognizer that is already decoding aborts the whole app.
            fun build(forMode: Mode): Recognizer = Recognizer(m, SAMPLE_RATE.toFloat(), grammarFor(forMode)).apply {
                if (spk != null) setSpeakerModel(spk)
                setWords(true)
                setEndpointerMode(Recognizer.EndpointerMode.SHORT)
            }
            recognizer = build(currentMode)
            val buffer = ShortArray(SAMPLE_RATE / 10) // 100 ms
            var silentChunks = 0
            var cooldownUntil = 0L
            val gate = EnergyGate()
            var partialStreak = 0
            val levels = ArrayDeque<Float>() // RMS of the last ~4 s, to compare the phrase with what came before

            while (!stopRequested) {
                if (mode != currentMode) {
                    // A fresh recognizer for the new phrases. Calling setGrammar() on this one while it is in the
                    // middle of an utterance (TV, music, Sir typing while the room is noisy) crashed the app natively.
                    currentMode = mode
                    runCatching { recognizer?.close() }
                    recognizer = build(currentMode)
                    partialStreak = 0
                }
                val n = record.read(buffer, 0, buffer.size)
                if (n < 0) {
                    _problem.value = Problem.MIC_UNAVAILABLE
                    break
                }
                if (n == 0) continue

                // Android background mein mic "silence" kar deta hai (sirf zeros). Pakdo aur batao.
                silentChunks = if (buffer.allZero(n)) silentChunks + 1 else 0
                if (silentChunks >= SILENCED_CHUNKS) {
                    _problem.value = Problem.MIC_SILENCED
                    break
                }

                levels.addLast(rms(buffer, n))
                while (levels.size > 40) levels.removeFirst()

                // Battery: only decode when there's sound (with pre-roll so the first word isn't clipped).
                val chunks = when (val g = gate.offer(buffer, n)) {
                    is EnergyGate.Result.Feed -> g.chunks
                    EnergyGate.Result.Closed -> { recognizer.reset(); continue }
                    EnergyGate.Result.Skip -> continue
                }
                for (chunk in chunks) {
                    var isFinal = recognizer.acceptWaveForm(chunk, chunk.size)
                    var raw = if (isFinal) recognizer.result else recognizer.partialResult
                    var heard = WakePhraseMatcher.parse(raw, isFinal)
                    if (heard == null) {
                        partialStreak = 0
                        continue
                    }
                    if (isFinal) {
                        _lastHeard.value = heard.text
                        partialStreak = 0
                    }

                    val now = System.currentTimeMillis()
                    if (now < cooldownUntil) continue
                    val overMedia = currentMode == Mode.WAKE && phoneMediaOnSpeaker()
                    val lockOnThisTurn = spk != null && currentMode == Mode.WAKE && (speakerLock || overMedia)
                    if (!isFinal) {
                        // Don't wait for Sir to go silent (that made JARVIS wake late, on the words after "Hello Dev").
                        // Once the phrase has held for ~300 ms, close the utterance ourselves: the forced final result
                        // carries word confidences (and the voiceprint), so noise still can't wake him.
                        val stable = currentMode == Mode.WAKE && WakePhraseMatcher.matchedPhrase(heard.text, phrasesFor(currentMode)) != null
                        partialStreak = if (stable) partialStreak + 1 else 0
                        if (partialStreak < STABLE_PARTIALS) continue
                        partialStreak = 0
                        raw = recognizer.finalResult
                        heard = WakePhraseMatcher.parse(raw, true)
                        if (heard == null) continue
                        isFinal = true
                        _lastHeard.value = heard.text
                    }
                    // Barge-in words are short commands over JARVIS's own voice: no loudness comparison there.
                    val loudness = if (currentMode == Mode.WAKE) {
                        WakePhraseMatcher.loudnessOverBackground(levels.toList(), ((heard.duration ?: 0.8f) * 10).toInt() + 3)
                    } else null
                    // With Sir's voiceprint the speaker check below decides; without it, he must be louder than the video.
                    val phrase = if (lockOnThisTurn && overMedia) {
                        WakePhraseMatcher.shouldTrigger(heard, phrasesFor(currentMode), sensitivity, null)
                    } else {
                        WakePhraseMatcher.shouldTrigger(heard, phrasesFor(currentMode), sensitivity, loudness, overPhoneMedia = overMedia)
                    }
                    if (phrase == null) {
                        if (isFinal && WakePhraseMatcher.matchedPhrase(heard.text, phrasesFor(currentMode)) != null) {
                            rejected.tryEmit("'${heard.text}' conf=${heard.weakest} dur=${heard.duration} lead=${heard.leadingUnknown} loud=$loudness")
                        }
                        continue
                    }

                    if (lockOnThisTurn) {
                        val profile = speakerProfile
                        val candidate = SpeakerVerification.parseVector(raw)
                        if (profile != null && candidate != null) {
                            val ok = SpeakerVerification.matches(profile, candidate, speakerThreshold)
                            _lastSpeaker.value = if (ok) "you ✓" else "not you"
                            if (!ok) { recognizer.reset(); continue } // someone else — ignore the wake
                        }
                        // No vector: fail open (wake anyway) so Sir is never locked out — except over the phone's own
                        // video / music, where an unverified "Jarvis" is most likely the video itself.
                        if ((profile == null || candidate == null) && overMedia) { recognizer.reset(); continue }
                    }
                    recognizer.reset()
                    cooldownUntil = now + COOLDOWN_MS
                    _lastHeard.value = heard.text
                    _detections.tryEmit(Detection(currentMode, phrase))
                    break
                }
            }
        } catch (e: SecurityException) {
            _problem.value = Problem.MIC_UNAVAILABLE
        } catch (e: Throwable) {
            Log.w(TAG, "wake word engine stopped: ${e.javaClass.simpleName}")
            _problem.value = Problem.ENGINE_ERROR
        } finally {
            runCatching { record?.stop() }
            runCatching { record?.release() }
            effects.forEach { runCatching { it.release() } }
            runCatching { recognizer?.close() }
            _running.value = false
        }
    }

    /** Mic kabhi-kabhi abhi bhi SpeechRecognizer ke paas hota hai — thodi der retry karo. */
    @SuppressLint("MissingPermission")
    private fun openMic(): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        repeat(10) {
            if (stopRequested) return null
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, SAMPLE_RATE / 2 * 2), // >= 0.5 s
            )
            if (rec.state == AudioRecord.STATE_INITIALIZED) {
                rec.startRecording()
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) return rec
            }
            rec.release()
            Thread.sleep(300)
        }
        return null
    }

    /** Echo canceller + noise suppressor, taaki JARVIS apni hi awaaz se na jaage. */
    private fun attachEffects(sessionId: Int): List<AudioEffect> = buildList {
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(sessionId)?.let { it.enabled = true; add(it) }
        }
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(sessionId)?.let { it.enabled = true; add(it) }
        }
    }

    private fun phrasesFor(mode: Mode) =
        if (mode == Mode.WAKE) wakePhrases else JarvisConfig.BARGE_IN_PHRASES

    private fun grammarFor(mode: Mode) = WakePhraseMatcher.grammarJson(phrasesFor(mode))

    private fun rms(a: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) { val v = a[i].toDouble(); sum += v * v }
        return kotlin.math.sqrt(sum / n.coerceAtLeast(1)).toFloat()
    }

    private fun ShortArray.allZero(n: Int): Boolean {
        for (i in 0 until n) if (this[i].toInt() != 0) return false
        return true
    }

    private companion object {
        const val TAG = "JarvisWake"
        const val SAMPLE_RATE = 16_000
        const val COOLDOWN_MS = 1_500L
        /** Consecutive partial results (≈100 ms each) containing the phrase before we wake. */
        const val STABLE_PARTIALS = 3
        /** ~4 s of pure digital silence = the OS has muted our mic. */
        const val SILENCED_CHUNKS = 40
    }
}
