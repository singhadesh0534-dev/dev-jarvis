package com.adesh.devjarvis.voice

import kotlinx.coroutines.flow.StateFlow

data class VoiceInfo(val name: String, val locale: String, val offline: Boolean)

/**
 * JARVIS ki "awaaz" ka interface. Abhi Android TextToSpeech; baad mein koi aur engine
 * (Gemini Live native audio, Piper, etc.) isi interface ke peeche plug ho jayega.
 */
interface SpeechOutput {
    /** True while anything is queued or playing. */
    val speaking: StateFlow<Boolean>
    /** 0f..1f speech "amplitude" for the arc reactor (word-boundary driven). */
    val level: StateFlow<Float>
    /** Non-null if the engine failed to start. */
    val error: StateFlow<String?>

    /** Queue one sentence. Returns immediately. */
    fun speak(text: String)
    /** Stop now and drop the queue (barge-in). */
    fun stop()
    /** Suspends until the queue is empty. */
    suspend fun awaitIdle()

    fun availableVoices(): List<VoiceInfo>
    fun activeVoiceName(): String?
    /** Re-read voice/pitch/rate from settings. */
    fun applySettings()
}
