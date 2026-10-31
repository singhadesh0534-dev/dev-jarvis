package com.adesh.devjarvis.brain

import com.adesh.devjarvis.core.DeviceStatus
import com.adesh.devjarvis.core.JarvisConfig
import com.adesh.devjarvis.core.UsageTracker
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.tools.ToolRegistry
import com.adesh.devjarvis.tools.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BrainEvent {
    data class Text(val delta: String) : BrainEvent
    data class ToolStarted(val name: String, val args: JsonObject) : BrainEvent
    data class ToolFinished(val name: String, val args: JsonObject, val result: ToolResult) : BrainEvent
}

/**
 * Dimaag: personality + live context + history jod ke Gemini se streaming jawab laata hai,
 * aur jab model tool maange to tool chala ke result wapas deta hai (function calling loop).
 * Phase 4 mein yahan Groq fallback, Phase 5 mein memory judegi.
 */
@Singleton
class JarvisBrain @Inject constructor(
    private val gemini: GeminiClient,
    private val settings: SettingsRepository,
    private val device: DeviceStatus,
    private val usage: UsageTracker,
    private val registry: ToolRegistry,
) {
    private val conversation = Conversation(JarvisConfig.HISTORY_TURNS)

    /**
     * Streams JARVIS's reply. [runTool] executes a tool call (the session supplies confirmation UI).
     * Throws [BrainError] on known failures.
     */
    fun reply(
        userText: String,
        runTool: suspend (name: String, args: JsonObject) -> ToolResult,
    ): Flow<BrainEvent> = flow {
        val s = settings.current
        if (!s.hasGeminiKey) throw BrainError.MissingKey()
        if (!device.isOnline()) throw BrainError.NoNetwork()

        val system = buildString {
            append(s.systemPrompt.trim())
            append("\n\n")
            append(SystemPrompt.SPEECH_FORMAT_RULES)
            append("\n\n")
            append(ContextBlock.build(device.snapshot(), s.homeCity))
        }
        val contents = (conversation.snapshot().map { GeminiContents.text(it.role, it.text) } +
            GeminiContents.text(Role.USER, userText)).toMutableList()

        for (round in 0 until MAX_TOOL_ROUNDS) {
            val rawParts = mutableListOf<JsonObject>()
            val calls = mutableListOf<GeminiStreamParser.FunctionCall>()
            gemini.streamTurn(s.geminiApiKey, s.geminiModel, system, JsonArray(contents), registry.geminiTools)
                .onStart { usage.recordGeminiRequest() }
                .catch { e ->
                    if (e is BrainError.RateLimited) usage.markExhausted()
                    throw e
                }
                .collect { chunk ->
                    rawParts += chunk.rawParts
                    calls += chunk.calls
                    if (chunk.text.isNotEmpty()) emit(BrainEvent.Text(chunk.text))
                }
            if (calls.isEmpty()) return@flow

            contents += GeminiContents.modelTurn(rawParts)
            val results = calls.map { call ->
                emit(BrainEvent.ToolStarted(call.name, call.args))
                val result = runTool(call.name, call.args)
                emit(BrainEvent.ToolFinished(call.name, call.args, result))
                call to result
            }
            contents += GeminiContents.functionResponses(results)
        }
        emit(BrainEvent.Text("Sir, ye kaam thoda zyada ulajh gaya — main yahin rukta hoon."))
    }

    /** Call after a reply finished (or was interrupted) so the next turn has context. */
    fun remember(userText: String, jarvisText: String) {
        if (userText.isNotBlank() && jarvisText.isNotBlank()) conversation.addExchange(userText, jarvisText)
    }

    fun forgetConversation() = conversation.clear()

    private companion object {
        /** Max model<->tool round-trips per request (chained actions). */
        const val MAX_TOOL_ROUNDS = 6
    }
}
