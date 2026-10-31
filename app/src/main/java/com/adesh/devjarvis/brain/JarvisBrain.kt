package com.adesh.devjarvis.brain

import com.adesh.devjarvis.core.DeviceStatus
import com.adesh.devjarvis.core.JarvisConfig
import com.adesh.devjarvis.core.Provider
import com.adesh.devjarvis.core.UsageTracker
import com.adesh.devjarvis.settings.JarvisSettings
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.tools.ToolRegistry
import com.adesh.devjarvis.tools.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
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
 * Dimaag: personality + live context + history jod ke jawab laata hai, aur jab model tool maange to
 * tool chala ke result wapas deta hai (function calling loop).
 *
 * Primary: Gemini. Gemini 429 / server error de (aur abhi tak kuch bola na ho) to chupchaap Groq pe switch.
 */
@Singleton
class JarvisBrain @Inject constructor(
    private val gemini: GeminiClient,
    private val groq: GroqClient,
    private val settings: SettingsRepository,
    private val device: DeviceStatus,
    private val usage: UsageTracker,
    private val registry: ToolRegistry,
) {
    private val conversation = Conversation(JarvisConfig.HISTORY_TURNS)

    private val _active = MutableStateFlow(Provider.GEMINI)
    /** Which text brain answered last — the HUD shows STREAM / FALLBACK. */
    val active: StateFlow<Provider> = _active.asStateFlow()

    private class Fallback(cause: BrainError) : Exception(cause)

    /**
     * Streams JARVIS's reply. [runTool] executes a tool call (the session supplies confirmation UI).
     * Throws [BrainError] on known failures.
     */
    fun reply(
        userText: String,
        runTool: suspend (name: String, args: JsonObject) -> ToolResult,
    ): Flow<BrainEvent> = flow {
        val s = settings.current
        if (!s.hasGeminiKey && !s.hasGroqKey) throw BrainError.MissingKey()
        if (!device.isOnline()) throw BrainError.NoNetwork()
        val system = systemPrompt(s, forLive = false)

        val useGemini = s.hasGeminiKey && !usage.isExhausted(Provider.GEMINI)
        if (useGemini) {
            try {
                _active.value = Provider.GEMINI
                geminiLoop(s, system, userText, runTool)
                return@flow
            } catch (f: Fallback) {
                if (!s.hasGroqKey) throw f.cause as BrainError
            }
        } else if (!s.hasGroqKey) {
            throw BrainError.RateLimited()
        }
        _active.value = Provider.GROQ
        groqLoop(s, system, userText, runTool)
    }

    /** Full system instruction: personality + format/tool rules + live context (+ recent chat for Live). */
    fun systemPrompt(s: JarvisSettings = settings.current, forLive: Boolean): String = buildString {
        append(s.systemPrompt.trim())
        append("\n\n")
        append(if (forLive) SystemPrompt.LIVE_RULES else SystemPrompt.SPEECH_FORMAT_RULES)
        append("\n\n")
        append(ContextBlock.build(device.snapshot(), s.homeCity))
        if (forLive) {
            val recent = conversation.snapshot()
            if (recent.isNotEmpty()) {
                append("\n\n[Earlier in today's conversation]\n")
                recent.takeLast(12).forEach { append(if (it.role == Role.USER) "Sir: " else "You: ").append(it.text).append('\n') }
            }
        }
    }

    private suspend fun FlowCollector<BrainEvent>.geminiLoop(
        s: JarvisSettings,
        system: String,
        userText: String,
        runTool: suspend (String, JsonObject) -> ToolResult,
    ) {
        val contents = (conversation.snapshot().map { GeminiContents.text(it.role, it.text) } +
            GeminiContents.text(Role.USER, userText)).toMutableList()
        var spokeAnything = false
        for (round in 0 until MAX_TOOL_ROUNDS) {
            val rawParts = mutableListOf<JsonObject>()
            val calls = mutableListOf<GeminiStreamParser.FunctionCall>()
            usage.recordGeminiRequest()
            try {
                gemini.streamTurn(s.geminiApiKey, s.geminiModel, system, JsonArray(contents), registry.geminiTools)
                    .collect { chunk ->
                        rawParts += chunk.rawParts
                        calls += chunk.calls
                        if (chunk.text.isNotEmpty()) {
                            spokeAnything = true
                            emit(BrainEvent.Text(chunk.text))
                        }
                    }
            } catch (e: BrainError) {
                if (e is BrainError.RateLimited) usage.markExhausted(Provider.GEMINI, cooldownFor(e))
                // Switch only if nothing was said or done yet — never repeat an action on the backup.
                val switchable = e is BrainError.RateLimited || (e is BrainError.Server && e.code >= 500)
                if (switchable && !spokeAnything && round == 0) throw Fallback(e)
                throw e
            }
            if (calls.isEmpty()) return
            spokeAnything = true
            contents += GeminiContents.modelTurn(rawParts)
            val results = calls.map { call ->
                emit(BrainEvent.ToolStarted(call.name, call.args))
                val result = runTool(call.name, call.args)
                emit(BrainEvent.ToolFinished(call.name, call.args, result))
                call to result
            }
            contents += GeminiContents.functionResponses(results)
        }
        emit(BrainEvent.Text(TOO_MANY_ROUNDS))
    }

    private suspend fun FlowCollector<BrainEvent>.groqLoop(
        s: JarvisSettings,
        system: String,
        userText: String,
        runTool: suspend (String, JsonObject) -> ToolResult,
    ) {
        val messages = mutableListOf(GroqMessages.text("system", system))
        conversation.snapshot().forEach { messages += GroqMessages.text(if (it.role == Role.USER) "user" else "assistant", it.text) }
        messages += GroqMessages.text("user", userText)
        for (round in 0 until MAX_TOOL_ROUNDS) {
            val text = StringBuilder()
            val assembler = GroqStreamParser.ToolCallAssembler()
            usage.recordGroqRequest()
            try {
                groq.streamChat(s.groqApiKey, s.groqModel, JsonArray(messages), registry.groqTools).collect { d ->
                    d.toolFragments.forEach(assembler::add)
                    if (d.text.isNotEmpty()) {
                        text.append(d.text)
                        emit(BrainEvent.Text(d.text))
                    }
                }
            } catch (e: BrainError.RateLimited) {
                usage.markExhausted(Provider.GROQ, cooldownFor(e))
                throw e
            }
            val calls = assembler.calls()
            if (calls.isEmpty()) return
            messages += GroqMessages.assistantWithTools(text.toString(), calls)
            for (call in calls) {
                val args = call.args()
                emit(BrainEvent.ToolStarted(call.name, args))
                val result = runTool(call.name, args)
                emit(BrainEvent.ToolFinished(call.name, args, result))
                messages += GroqMessages.toolResult(call.id, result.ok, result.message)
            }
        }
        emit(BrainEvent.Text(TOO_MANY_ROUNDS))
    }

    /** Per-minute limits clear quickly; daily ones don't. We can't always tell, so be modest. */
    private fun cooldownFor(e: BrainError): Long {
        val msg = e.message.orEmpty().lowercase()
        val daily = "day" in msg || "daily" in msg
        return System.currentTimeMillis() + if (daily) 12 * 3_600_000L else 60_000L
    }

    /** Call after a reply finished (or was interrupted) so the next turn has context. */
    fun remember(userText: String, jarvisText: String) {
        if (userText.isNotBlank() && jarvisText.isNotBlank()) conversation.addExchange(userText, jarvisText)
    }

    fun forgetConversation() = conversation.clear()

    private companion object {
        /** Max model<->tool round-trips per request (chained actions). */
        const val MAX_TOOL_ROUNDS = 6
        const val TOO_MANY_ROUNDS = "Sir, ye kaam thoda zyada ulajh gaya — main yahin rukta hoon."
    }
}
