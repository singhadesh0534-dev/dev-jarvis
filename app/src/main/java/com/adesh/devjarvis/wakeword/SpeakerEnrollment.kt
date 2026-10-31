package com.adesh.devjarvis.wakeword

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import com.adesh.devjarvis.core.JarvisConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Sir ki awaaz ko "enroll" karna: wo kuch baar bolte hain, har baar ka voiceprint (x-vector) lete hain,
 * aur unka average profile banate hain. Phir wahi profile wake word pe match hota hai.
 */
@Singleton
class SpeakerEnrollment @Inject constructor(
    private val models: VoskModelManager,
    private val speakerModels: SpeakerModelManager,
) {
    sealed interface Event {
        /** Listening for sample number [index] of [total]. */
        data class Listening(val index: Int, val total: Int) : Event
        data class Level(val value: Float) : Event
        /** Captured sample [index]; keep going. */
        data class Captured(val index: Int, val total: Int) : Event
        /** All done — the averaged profile, ready to save. */
        data class Done(val profile: FloatArray) : Event
        data class Failed(val reason: String) : Event
    }

    fun modelsReady(): Boolean = models.isInstalled() && speakerModels.isInstalled()

    /** Captures [SpeakerVerification.ENROLL_SAMPLES] voiceprints. Cancel the flow to stop. */
    @SuppressLint("MissingPermission")
    fun enroll(): Flow<Event> = flow {
        if (!modelsReady()) { emit(Event.Failed("models not downloaded")); return@flow }
        val m = runCatching { LibVosk.setLogLevel(LogLevel.WARNINGS); Model(models.modelDir.absolutePath) }.getOrNull()
            ?: run { emit(Event.Failed("wake model error")); return@flow }
        val spk = runCatching { SpeakerModel(speakerModels.modelDir.absolutePath) }.getOrNull()
            ?: run { m.close(); emit(Event.Failed("speaker model error")); return@flow }
        val rate = JarvisConfig.LIVE_INPUT_RATE
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate))
        }.getOrNull()
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release(); m.close(); spk.close()
            emit(Event.Failed("mic busy")); return@flow
        }
        val ns = if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } else null
        val total = SpeakerVerification.ENROLL_SAMPLES
        val collected = mutableListOf<FloatArray>()
        val buffer = ShortArray(rate / 10)
        try {
            record.startRecording()
            for (i in 1..total) {
                emit(Event.Listening(i, total))
                val recognizer = Recognizer(m, rate.toFloat(), spk)
                var got: FloatArray? = null
                var elapsedMs = 0
                while (got == null && elapsedMs < PER_SAMPLE_TIMEOUT_MS) {
                    coroutineContext.ensureActive()
                    val n = record.read(buffer, 0, buffer.size)
                    if (n <= 0) continue
                    elapsedMs += n * 1000 / rate
                    emit(Event.Level(rms(buffer, n)))
                    if (recognizer.acceptWaveForm(buffer, n)) {
                        got = SpeakerVerification.parseVector(recognizer.result)
                    }
                }
                if (got == null) got = SpeakerVerification.parseVector(recognizer.finalResult)
                recognizer.close()
                if (got != null) {
                    collected += got
                    emit(Event.Captured(i, total))
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            ns?.release()
            m.close()
            spk.close()
        }
        val profile = SpeakerVerification.average(collected)
        if (profile != null && collected.size >= 2) emit(Event.Done(profile))
        else emit(Event.Failed("saaf se ${total} baar bolna pada — dobara koshish karo"))
    }.flowOn(Dispatchers.IO)

    private fun rms(a: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) { val v = a[i].toDouble(); sum += v * v }
        return (kotlin.math.sqrt(sum / n) / 4000.0).coerceIn(0.0, 1.0).toFloat()
    }

    private companion object {
        const val PER_SAMPLE_TIMEOUT_MS = 6_000
    }
}
