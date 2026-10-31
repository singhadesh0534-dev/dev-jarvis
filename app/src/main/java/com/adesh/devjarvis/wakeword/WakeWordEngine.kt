package com.adesh.devjarvis.wakeword

import android.annotation.SuppressLint
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
class WakeWordEngine @Inject constructor(private val models: VoskModelManager) {

    enum class Mode { WAKE, BARGE_IN }

    data class Detection(val mode: Mode, val phrase: String)

    enum class Problem { NO_MODEL, MIC_UNAVAILABLE, MIC_SILENCED, ENGINE_ERROR }

    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 8)
    val detections: SharedFlow<Detection> = _detections.asSharedFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** What Vosk last heard — shown dimly on the HUD so you can tune sensitivity. */
    private val _lastHeard = MutableStateFlow("")
    val lastHeard: StateFlow<String> = _lastHeard.asStateFlow()

    private val _problem = MutableStateFlow<Problem?>(null)
    val problem: StateFlow<Problem?> = _problem.asStateFlow()

    @Volatile var sensitivity: Float = JarvisConfig.WAKE_SENSITIVITY_DEFAULT

    @Volatile private var mode: Mode = Mode.WAKE
    @Volatile private var stopRequested = false
    private var thread: Thread? = null
    private var model: Model? = null

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
            recognizer = Recognizer(m, SAMPLE_RATE.toFloat(), grammarFor(currentMode)).apply {
                setWords(true)
                setEndpointerMode(Recognizer.EndpointerMode.SHORT)
            }
            val buffer = ShortArray(SAMPLE_RATE / 10) // 100 ms
            var silentChunks = 0
            var cooldownUntil = 0L

            while (!stopRequested) {
                if (mode != currentMode) {
                    currentMode = mode
                    recognizer.setGrammar(grammarFor(currentMode))
                    recognizer.reset()
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

                val isFinal = recognizer.acceptWaveForm(buffer, n)
                val raw = if (isFinal) recognizer.result else recognizer.partialResult
                val heard = WakePhraseMatcher.parse(raw, isFinal) ?: continue
                if (isFinal) _lastHeard.value = heard.text

                val now = System.currentTimeMillis()
                if (now < cooldownUntil) continue
                val phrases = phrasesFor(currentMode)
                val phrase = WakePhraseMatcher.shouldTrigger(heard, phrases, sensitivity) ?: continue
                recognizer.reset()
                cooldownUntil = now + COOLDOWN_MS
                _lastHeard.value = heard.text
                _detections.tryEmit(Detection(currentMode, phrase))
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
        if (mode == Mode.WAKE) JarvisConfig.WAKE_PHRASES else JarvisConfig.BARGE_IN_PHRASES

    private fun grammarFor(mode: Mode) = WakePhraseMatcher.grammarJson(phrasesFor(mode))

    private fun ShortArray.allZero(n: Int): Boolean {
        for (i in 0 until n) if (this[i].toInt() != 0) return false
        return true
    }

    private companion object {
        const val TAG = "JarvisWake"
        const val SAMPLE_RATE = 16_000
        const val COOLDOWN_MS = 1_500L
        /** ~4 s of pure digital silence = the OS has muted our mic. */
        const val SILENCED_CHUNKS = 40
    }
}
