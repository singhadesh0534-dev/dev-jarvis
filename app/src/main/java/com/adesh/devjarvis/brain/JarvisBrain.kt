package com.adesh.devjarvis.brain

import com.adesh.devjarvis.core.DeviceStatus
import com.adesh.devjarvis.core.JarvisConfig
import com.adesh.devjarvis.core.UsageTracker
import com.adesh.devjarvis.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dimaag: personality + live context + short-term history jod ke Gemini se streaming jawab laata hai.
 * Phase 3 mein yahan tool calling, Phase 4 mein Groq fallback, Phase 5 mein memory judegi.
 */
@Singleton
class JarvisBrain @Inject constructor(
    private val gemini: GeminiClient,
    private val settings: SettingsRepository,
    private val device: DeviceStatus,
    private val usage: UsageTracker,
) {
    private val conversation = Conversation(JarvisConfig.HISTORY_TURNS)

    /** Streams JARVIS's reply as text deltas. Throws [BrainError] on known failures. */
    fun reply(userText: String): Flow<String> {
        val s = settings.current
        if (!s.hasGeminiKey) return flow { throw BrainError.MissingKey() }
        if (!device.isOnline()) return flow { throw BrainError.NoNetwork() }

        val system = buildString {
            append(s.systemPrompt.trim())
            append("\n\n")
            append(SystemPrompt.SPEECH_FORMAT_RULES)
            append("\n\n")
            append(ContextBlock.build(device.snapshot()))
        }
        val history = conversation.snapshot() + Turn(Role.USER, userText)

        return gemini.streamReply(s.geminiApiKey, s.geminiModel, system, history)
            .onStart { usage.recordGeminiRequest() }
            .catch { e ->
                if (e is BrainError.RateLimited) usage.markExhausted()
                throw e
            }
    }

    /** Call after a reply finished (or was interrupted) so the next turn has context. */
    fun remember(userText: String, jarvisText: String) {
        if (userText.isNotBlank() && jarvisText.isNotBlank()) conversation.addExchange(userText, jarvisText)
    }

    fun forgetConversation() = conversation.clear()
}
