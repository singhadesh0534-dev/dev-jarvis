package com.adesh.devjarvis.brain

import android.os.Build
import com.adesh.devjarvis.core.DeviceStatus
import com.adesh.devjarvis.core.Edition
import com.adesh.devjarvis.core.JarvisConfig
import com.adesh.devjarvis.core.SessionTrace
import com.adesh.devjarvis.core.Provider
import com.adesh.devjarvis.core.UsageTracker
import com.adesh.devjarvis.life.LifeStore
import com.adesh.devjarvis.memory.MemoryRepository
import com.adesh.devjarvis.notifications.NotificationStore
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    private val memory: MemoryRepository,
    private val notifications: NotificationStore,
    private val life: LifeStore,
    private val keys: com.adesh.devjarvis.core.KeyRing,
) {
    private val conversation = Conversation(JarvisConfig.HISTORY_TURNS)
    /** Exchanges not yet looked at by the memory extractor. */
    private val pendingForMemory = mutableListOf<Pair<String, String>>()

    private val _active = MutableStateFlow(Provider.GEMINI)
    /** Which text brain answered last — the HUD shows STREAM / FALLBACK. */
    val active: StateFlow<Provider> = _active.asStateFlow()

    private class Fallback(cause: BrainError) : Exception(cause)

    /**
     * One Gemini request with key rotation: if this key's free limit is hit (or the key is rejected) before anything
     * was said, the next key takes over at once. Same model, same prompt, same memory — only the key changes.
     */
    private fun geminiTurn(
        s: JarvisSettings, model: String, system: String, contents: JsonArray, tools: JsonArray?, jsonMode: Boolean = false,
        thinking: String = JarvisConfig.GEMINI_THINKING_LEVEL,
    ): Flow<GeminiStreamParser.Chunk> = flow {
        var tries = 0
        while (true) {
            val key = keys.gemini.current(s.geminiApiKey)
            var started = false
            try {
                gemini.streamTurn(key, model, system, contents, tools, jsonMode, thinking).collect { started = true; emit(it) }
                return@flow
            } catch (e: BrainError) {
                val rotatable = e is BrainError.RateLimited || e is BrainError.InvalidKey
                if (!rotatable || started || ++tries >= MAX_KEY_TRIES || !keys.gemini.rest(s.geminiApiKey, key, restFor(e))) throw e
                SessionTrace.add("KEY gemini rotated (${e.javaClass.simpleName}), ready=${keys.gemini.readyCount(s.geminiApiKey)}")
            }
        }
    }

    private fun groqTurn(
        s: JarvisSettings, model: String, messages: JsonArray, tools: JsonArray?, jsonMode: Boolean = false, extra: Boolean = false,
    ): Flow<GroqStreamParser.Delta> = flow {
        var tries = 0
        val raw = if (extra) s.extraApiKey else s.groqApiKey
        val pool = if (extra) keys.extra else keys.groq
        val baseUrl = if (extra) s.extraBaseUrl else JarvisConfig.GROQ_BASE_URL
        while (true) {
            val key = pool.current(raw)
            var started = false
            try {
                groq.streamChat(key, model, messages, tools, jsonMode, baseUrl).collect { started = true; emit(it) }
                return@flow
            } catch (e: BrainError) {
                val rotatable = e is BrainError.RateLimited || e is BrainError.InvalidKey
                if (!rotatable || started || ++tries >= MAX_KEY_TRIES || !pool.rest(raw, key, restFor(e))) throw e
                SessionTrace.add("KEY ${if (extra) "third" else "groq"} rotated (${e.javaClass.simpleName}), ready=${pool.readyCount(raw)}")
            }
        }
    }

    private fun restFor(e: BrainError): Long =
        if (e is BrainError.InvalidKey) 6 * 3_600_000L else (cooldownFor(e) - System.currentTimeMillis()).coerceAtLeast(30_000L)

    /**
     * Streams JARVIS's reply. [runTool] executes a tool call (the session supplies confirmation UI).
     * Throws [BrainError] on known failures.
     */
    fun reply(
        userText: String,
        runTool: suspend (name: String, args: JsonObject) -> ToolResult,
    ): Flow<BrainEvent> = flow {
        val s = settings.current
        if (!s.hasGeminiKey && !s.hasGroqKey && !s.hasExtraBrain) throw BrainError.MissingKey()
        if (!device.isOnline()) throw BrainError.NoNetwork()
        val system = systemPrompt(s, forLive = false, query = userText)

        val useGemini = s.hasGeminiKey && !usage.isExhausted(Provider.GEMINI)
        if (useGemini) {
            try {
                _active.value = Provider.GEMINI
                geminiLoop(s, system, userText, runTool)
                return@flow
            } catch (f: Fallback) {
                if (!s.hasGroqKey && !s.hasExtraBrain) throw f.cause as BrainError
            }
        } else if (!s.hasGroqKey && !s.hasExtraBrain) {
            throw BrainError.RateLimited()
        }
        _active.value = Provider.GROQ
        val small = systemPrompt(s, forLive = false, query = userText, compact = true)
        if (s.hasGroqKey && (!usage.isExhausted(Provider.GROQ) || !s.hasExtraBrain)) {
            try {
                groqLoop(s, small, userText, runTool, extra = false)
                return@flow
            } catch (f: Fallback) {
                if (!s.hasExtraBrain) throw f.cause as BrainError
            }
        }
        SessionTrace.add("BRAIN third brain in use")
        groqLoop(s, small, userText, runTool, extra = true)
    }

    /**
     * Full system instruction: personality + format/tool rules + live context, relevant memories,
     * top notifications (+ recent chat for Live).
     */
    suspend fun systemPrompt(
        s: JarvisSettings = settings.current, forLive: Boolean, query: String = "",
        /** Backup brains have a small request budget: personality + format rules + context only. */
        compact: Boolean = false,
    ): String = buildString {
        append(s.systemPrompt.trim())
        append("\n\n")
        append(if (forLive) SystemPrompt.LIVE_RULES else SystemPrompt.SPEECH_FORMAT_RULES)
        if (!compact) {
            append("\n\n").append(SystemPrompt.CHARACTER)
            append("\n\n").append(SystemPrompt.BEHAVIOR)
        } else {
            append("\n\n").append(SystemPrompt.CHARACTER_SHORT)
        }
        if (!Edition.isFull) append("\n").append(SystemPrompt.LITE_NOTE)
        append("\n\n")
        append(
            ContextBlock.build(
                device.snapshot(),
                s.homeCity,
                // Live keeps this prompt for the whole conversation, so it gets the wider picture; classic re-ranks each turn.
                memories = memory.relevant(query, limit = if (forLive) 40 else if (compact) 5 else 12).map { it.text },
                notifications = notifications.topForContext(3),
                phone = "${Build.MANUFACTURER} ${Build.MODEL}".trim() + ", Android ${Build.VERSION.RELEASE}" +
                    (if (Build.MANUFACTURER.equals("infinix", ignoreCase = true)) " with the XOS skin (Phone Master, XOS launcher, Folax assistant)" else ""),
            )
        )
        val routines = life.get().routines
        if (routines.isNotEmpty()) {
            append("\n\n[Sir's routines / shortcuts — when he says one of these names, carry out its steps with your tools, in order]\n")
            routines.forEach { (name, steps) -> append("- \"").append(name).append("\": ").append(steps).append('\n') }
        }
        val taught = life.get().macros
        if (taught.isNotEmpty()) {
            append("\n\n[Tasks Sir taught you by showing them once — when he says a name, call run_taught with it]\n")
            taught.forEach { (name, steps) -> append("- \"").append(name).append("\" (").append(steps.size).append(" steps)\n") }
        }
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
        val effort = Effort.forQuery(userText)
        for (round in 0 until MAX_TOOL_ROUNDS) {
            val rawParts = mutableListOf<JsonObject>()
            val calls = mutableListOf<GeminiStreamParser.FunctionCall>()
            usage.recordGeminiRequest()
            try {
                // First round decides how hard to think from Sir's words; tool follow-ups are routine.
                geminiTurn(s, s.geminiModel, system, JsonArray(contents), registry.geminiTools, thinking = if (round == 0) effort else Effort.FAST)
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
        extra: Boolean,
    ) {
        val messages = mutableListOf(GroqMessages.text("system", system))
        conversation.snapshot().takeLast(8).forEach { messages += GroqMessages.text(if (it.role == Role.USER) "user" else "assistant", it.text.take(600)) }
        messages += GroqMessages.text("user", userText)
        for (round in 0 until MAX_TOOL_ROUNDS) {
            val text = StringBuilder()
            val assembler = GroqStreamParser.ToolCallAssembler()
            if (!extra) usage.recordGroqRequest()
            try {
                groqTurn(s, if (extra) s.extraModel else s.groqModel, JsonArray(messages), registry.groqTools, extra = extra).collect { d ->
                    d.toolFragments.forEach(assembler::add)
                    if (d.text.isNotEmpty()) {
                        text.append(d.text)
                        emit(BrainEvent.Text(d.text))
                    }
                }
            } catch (e: BrainError) {
                if (e is BrainError.RateLimited && !extra) usage.markExhausted(Provider.GROQ, cooldownFor(e))
                // Nothing said or done yet: the next brain may take this turn.
                if (!extra && round == 0 && text.isEmpty() && assembler.calls().isEmpty()) throw Fallback(e)
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

    /** JARVIS offered something by himself (habit suggestion): remember it so "haan" can be understood. */
    fun noteOffer(text: String) = conversation.addOffer(text)

    /** Call after a reply finished (or was interrupted) so the next turn has context. */
    fun remember(userText: String, jarvisText: String) {
        if (userText.isNotBlank() && jarvisText.isNotBlank()) {
            conversation.addExchange(userText, jarvisText)
            synchronized(pendingForMemory) { pendingForMemory += userText to jarvisText }
        }
    }

    /** Exchanges since the last memory extraction (and clears the list). */
    fun takeForMemory(): List<Pair<String, String>> = synchronized(pendingForMemory) {
        pendingForMemory.toList().also { pendingForMemory.clear() }
    }

    /** JARVIS ki aankhein: image + question -> short spoken answer (Gemini vision). Null if unavailable. */
    suspend fun see(jpeg: ByteArray, question: String): String? {
        val s = settings.current
        if (!s.hasGeminiKey || !device.isOnline()) return null
        val content = buildJsonObject {
            put("role", "user")
            put("parts", JsonArray(listOf(
                buildJsonObject {
                    put("inlineData", buildJsonObject {
                        put("mimeType", "image/jpeg")
                        put("data", java.util.Base64.getEncoder().encodeToString(jpeg))
                    })
                },
                buildJsonObject { put("text", question.ifBlank { "Ye kya hai?" }) },
            )))
        }
        return try {
            usage.recordGeminiRequest()
            val out = StringBuilder()
            geminiTurn(s, s.geminiModel, SystemPrompt.VISION, JsonArray(listOf(content)), null)
                .collect { out.append(it.text) }
            out.toString().trim().ifEmpty { null }
        } catch (e: BrainError) {
            if (e is BrainError.RateLimited) usage.markExhausted(Provider.GEMINI, cooldownFor(e))
            null
        }
    }

    /** Latest info from the web (Gemini grounded with Google Search). Null if unavailable. */
    suspend fun webAnswer(query: String): String? {
        val s = settings.current
        if (!s.hasGeminiKey || !device.isOnline()) return null
        val tools = JsonArray(listOf(buildJsonObject { put("google_search", buildJsonObject { }) }))
        val content = GeminiContents.text(Role.USER, query)
        val context = ContextBlock.build(device.snapshot(), s.homeCity)
        return try {
            usage.recordGeminiRequest()
            val out = StringBuilder()
            geminiTurn(s, s.geminiModel, SystemPrompt.WEB + "\n\n" + context, JsonArray(listOf(content)), tools)
                .collect { out.append(it.text) }
            out.toString().trim().ifEmpty { null }
        } catch (e: BrainError) {
            if (e is BrainError.RateLimited) usage.markExhausted(Provider.GEMINI, cooldownFor(e))
            null
        }
    }

    /**
     * One-shot JSON / text completion for background jobs (memory extraction, briefing).
     * Gemini first, Groq if Gemini is out of quota. Returns null if neither works.
     */
    suspend fun complete(system: String, user: String, json: Boolean): String? {
        val s = settings.current
        if (!device.isOnline()) return null
        if (s.hasGeminiKey && !usage.isExhausted(Provider.GEMINI)) {
            try {
                usage.recordGeminiRequest()
                val out = StringBuilder()
                geminiTurn(s, s.geminiModel, system, JsonArray(listOf(GeminiContents.text(Role.USER, user))), null, jsonMode = json)
                    .collect { out.append(it.text) }
                return out.toString()
            } catch (e: BrainError) {
                if (e is BrainError.RateLimited) usage.markExhausted(Provider.GEMINI, cooldownFor(e))
            }
        }
        if (s.hasGroqKey && !usage.isExhausted(Provider.GROQ)) {
            try {
                usage.recordGroqRequest()
                val out = StringBuilder()
                val msgs = JsonArray(listOf(GroqMessages.text("system", system), GroqMessages.text("user", user)))
                groqTurn(s, s.groqModel, msgs, null, jsonMode = json).collect { out.append(it.text) }
                return out.toString()
            } catch (e: BrainError) {
                if (e is BrainError.RateLimited) usage.markExhausted(Provider.GROQ, cooldownFor(e))
            }
        }
        return null
    }

    fun forgetConversation() = conversation.clear()

    private companion object {
        /** Max model<->tool round-trips per request (chained actions). */
        const val MAX_TOOL_ROUNDS = 6
        const val MAX_KEY_TRIES = 8
        const val TOO_MANY_ROUNDS = "Sir, ye kaam thoda zyada ulajh gaya — main yahin rukta hoon."
    }
}
