package com.adesh.devjarvis.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import com.adesh.devjarvis.brain.BrainError
import com.adesh.devjarvis.brain.GroqClient
import com.adesh.devjarvis.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Classic-mode "kaan", option 2: hum khud record karte hain (VAD se), phir Groq Whisper se text.
 * Hinglish Google recognizer se behtar samajhta hai. Same events as [SpeechInput].
 */
@Singleton
class GroqWhisperInput @Inject constructor(
    private val groq: GroqClient,
    private val settings: SettingsRepository,
) {
    @SuppressLint("MissingPermission")
    fun listen(language: String): Flow<SpeechInput.Event> = flow {
        val rate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate))
        } catch (e: SecurityException) {
            emit(SpeechInput.Event.Failed(SpeechInput.Reason.NO_PERMISSION)); return@flow
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            emit(SpeechInput.Event.Failed(SpeechInput.Reason.BUSY)); return@flow
        }
        val ns = if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } else null
        val vad = EndpointDetector(rate)
        val all = ShortArray(rate * 16) // max ~16 s
        var total = 0
        val frame = ShortArray(rate / 25) // 40 ms
        try {
            record.startRecording()
            emit(SpeechInput.Event.Ready)
            while (true) {
                coroutineContext.ensureActive()
                val n = record.read(frame, 0, frame.size)
                if (n <= 0) continue
                val copy = minOf(n, all.size - total)
                System.arraycopy(frame, 0, all, total, copy)
                total += copy
                val st = vad.feed(frame, n)
                emit(SpeechInput.Event.Level(vad.level))
                if (st == EndpointDetector.State.NOTHING_HEARD) {
                    emit(SpeechInput.Event.Failed(SpeechInput.Reason.NOTHING_HEARD)); return@flow
                }
                if (st == EndpointDetector.State.DONE || total >= all.size) break
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            ns?.release()
        }
        emit(SpeechInput.Event.Partial("…"))
        val text = try {
            // hi-IN -> force Hindi; otherwise auto-detect (Hinglish may come back partly in Devanagari — the brain
            // understands both). Forcing "en" would make Whisper translate Hindi into English.
            groq.transcribe(settings.current.groqApiKey, Wav.fromPcm16(all, total, rate), language.substringBefore('-').takeIf { it == "hi" })
        } catch (e: BrainError.NoNetwork) {
            emit(SpeechInput.Event.Failed(SpeechInput.Reason.NETWORK)); return@flow
        } catch (e: BrainError) {
            emit(SpeechInput.Event.Failed(SpeechInput.Reason.OTHER)); return@flow
        }
        emit(if (text.isBlank()) SpeechInput.Event.Failed(SpeechInput.Reason.NOTHING_HEARD) else SpeechInput.Event.Final(text))
    }.flowOn(Dispatchers.IO)
}
