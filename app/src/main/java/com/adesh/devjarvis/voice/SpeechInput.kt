package com.adesh.devjarvis.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1 ka "kaan": Android ka built-in SpeechRecognizer (Google app wala, free).
 * Phase 2 mein wake word + Phase 4 mein Gemini Live / Groq Whisper isko replace/extend karenge.
 */
@Singleton
class SpeechInput @Inject constructor(@ApplicationContext private val context: Context) {

    sealed interface Event {
        data object Ready : Event
        /** 0f..1f normalised mic loudness, for the arc reactor. */
        data class Level(val value: Float) : Event
        data class Partial(val text: String) : Event
        data class Final(val text: String) : Event
        data class Failed(val reason: Reason) : Event
    }

    enum class Reason { NOTHING_HEARD, NO_PERMISSION, NETWORK, BUSY, UNAVAILABLE, OTHER }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** One utterance. The flow completes after [Event.Final] or [Event.Failed]; cancel it to stop early. */
    fun listen(language: String): Flow<Event> = callbackFlow {
        if (!isAvailable()) {
            trySend(Event.Failed(Reason.UNAVAILABLE))
            close()
            awaitClose { }
            return@callbackFlow
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(Event.Ready) }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                // Typical range is about -2..10 dB.
                trySend(Event.Level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)))
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                trySend(Event.Failed(mapError(error)))
                close()
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                trySend(if (text.isBlank()) Event.Failed(Reason.NOTHING_HEARD) else Event.Final(text))
                close()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { trySend(Event.Partial(it)) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Thoda ruk ke sochne do, turant cut mat karo.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }
        // Android recognizer har baar start/stop pe "beep" bajata hai — continuous conversation mein
        // ye baar-baar sunai deta hai. Recognition ke dauraan system/notification beeps mute kar dete hain.
        val muted = muteBeeps()
        recognizer.startListening(intent)

        awaitClose {
            unmute(muted)
            recognizer.cancel()
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main) // SpeechRecognizer must live on the main thread.

    private fun mapError(error: Int): Reason = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Reason.NOTHING_HEARD
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Reason.NO_PERMISSION
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> Reason.NETWORK
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Reason.BUSY
        else -> Reason.OTHER
    }

    /** Streams jin par recognizer ki beep bajti hai. Ring/alarm ko haath nahi lagate. */
    private val beepStreams = intArrayOf(AudioManager.STREAM_SYSTEM, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_MUSIC)

    private fun muteBeeps(): List<Int> {
        val am = context.getSystemService(AudioManager::class.java) ?: return emptyList()
        val muted = mutableListOf<Int>()
        for (s in beepStreams) {
            runCatching {
                if (!am.isStreamMute(s)) {
                    am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0)
                    muted += s
                }
            }
        }
        return muted
    }

    private fun unmute(streams: List<Int>) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        streams.forEach { s -> runCatching { am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0) } }
    }
}
