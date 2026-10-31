package com.adesh.devjarvis.brain

import com.adesh.devjarvis.core.JarvisConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/**
 * Gemini REST streaming client (`:streamGenerateContent?alt=sse`).
 * Text tukde (deltas) aate hi Flow mein emit karta hai, taaki JARVIS pehla sentence turant bol sake.
 * Flow cancel karo (barge-in) to HTTP call bhi turant cancel ho jaati hai.
 */
@Singleton
class GeminiClient @Inject constructor(private val http: OkHttpClient) {

    /** Plain chat helper: history of text turns -> streamed text. */
    fun streamReply(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Turn>,
    ): Flow<String> = streamTurn(apiKey, model, systemInstruction, GeminiContents.fromTurns(history), tools = null)
        .transform { chunk -> if (chunk.text.isNotEmpty()) emit(chunk.text) }

    /**
     * One model turn. Emits parsed chunks (text, function calls, raw parts).
     * [contents] is the full Gemini `contents` array; [tools] the `tools` array or null.
     */
    fun streamTurn(
        apiKey: String,
        model: String,
        systemInstruction: String,
        contents: JsonArray,
        tools: JsonArray?,
        jsonMode: Boolean = false,
    ): Flow<GeminiStreamParser.Chunk> = channelFlow {
        if (apiKey.isBlank()) throw BrainError.MissingKey()

        var useThinking = JarvisConfig.GEMINI_THINKING_LEVEL.isNotBlank()
        val call = AtomicReference<Call?>(null)
        val reader = launch(Dispatchers.IO) {
            while (true) {
                val request = Request.Builder()
                    .url("${JarvisConfig.GEMINI_BASE_URL}/models/$model:streamGenerateContent?alt=sse")
                    // Key header mein — URL mein nahi, taaki kisi log mein na dikhe.
                    .header("x-goog-api-key", apiKey)
                    .post(buildBody(systemInstruction, contents, tools, useThinking, jsonMode).toString().toRequestBody(JSON))
                    .build()
                val current = http.newCall(request).also { call.set(it) }
                val response = try {
                    current.execute()
                } catch (e: IOException) {
                    throw mapIoError(e)
                }
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val message = GeminiStreamParser.errorMessage(body)
                        // Kuch models thinkingLevel support nahi karte — ek baar bina uske retry.
                        if (resp.code == 400 && useThinking && message.contains("think", ignoreCase = true)) {
                            useThinking = false
                            return@use
                        }
                        throw mapHttpError(resp.code, message)
                    }
                    val source = resp.body?.source() ?: throw BrainError.Server(resp.code, "empty body")
                    try {
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            val chunk = GeminiStreamParser.parseLine(line) ?: continue
                            chunk.blockedReason?.let { throw BrainError.Blocked(it) }
                            send(chunk)
                        }
                    } catch (e: IOException) {
                        throw mapIoError(e)
                    }
                    return@launch
                }
            }
        }
        awaitClose { call.get()?.cancel(); reader.cancel() }
    }

    private fun buildBody(systemInstruction: String, contents: JsonArray, tools: JsonArray?, thinking: Boolean, jsonMode: Boolean): JsonObject =
        buildJsonObject {
            putJsonObject("system_instruction") {
                putJsonArray("parts") { addJsonObject { put("text", systemInstruction) } }
            }
            put("contents", contents)
            if (tools != null) put("tools", tools)
            putJsonObject("generationConfig") {
                put("temperature", JarvisConfig.GEMINI_TEMPERATURE)
                put("maxOutputTokens", JarvisConfig.GEMINI_MAX_OUTPUT_TOKENS)
                if (jsonMode) put("responseMimeType", "application/json")
                if (thinking) {
                    putJsonObject("thinkingConfig") { put("thinkingLevel", JarvisConfig.GEMINI_THINKING_LEVEL) }
                }
            }
        }

    private fun mapHttpError(code: Int, message: String): BrainError = when {
        code == 429 -> BrainError.RateLimited(message)
        code == 401 || code == 403 -> BrainError.InvalidKey()
        code == 400 && message.contains("API key", ignoreCase = true) -> BrainError.InvalidKey()
        else -> BrainError.Server(code, message)
    }

    private fun mapIoError(e: IOException): Exception = when (e) {
        is UnknownHostException, is ConnectException, is SSLException -> BrainError.NoNetwork()
        is InterruptedIOException -> if (e.message?.contains("timeout") == true) BrainError.NoNetwork() else e
        else -> e
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
