package com.adesh.devjarvis.brain

import com.adesh.devjarvis.core.JarvisConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/** Groq: backup brain (OpenAI-style chat + tools, streaming) aur Whisper speech-to-text. */
@Singleton
class GroqClient @Inject constructor(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Streams one chat completion. [messages] / [tools] are OpenAI-format JSON. */
    fun streamChat(
        apiKey: String,
        model: String,
        messages: JsonArray,
        tools: JsonArray?,
        jsonMode: Boolean = false,
    ): Flow<GroqStreamParser.Delta> = channelFlow {
        if (apiKey.isBlank()) throw BrainError.MissingKey()
        val body = buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("stream", true)
            put("temperature", JarvisConfig.GEMINI_TEMPERATURE)
            put("max_tokens", JarvisConfig.GEMINI_MAX_OUTPUT_TOKENS)
            if (jsonMode) put("response_format", buildJsonObject { put("type", "json_object") })
            if (tools != null && tools.isNotEmpty()) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
        }
        val request = Request.Builder()
            .url("${JarvisConfig.GROQ_BASE_URL}/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val call = AtomicReference<Call?>(null)
        val reader = launch(Dispatchers.IO) {
            val c = http.newCall(request).also { call.set(it) }
            val resp = try { c.execute() } catch (e: IOException) { throw mapIo(e) }
            resp.use {
                if (!it.isSuccessful) throw mapHttp(it.code, GroqStreamParser.errorMessage(it.body?.string().orEmpty()))
                val source = it.body?.source() ?: throw BrainError.Server(it.code, "empty body")
                try {
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        GroqStreamParser.parseLine(line)?.let { d -> send(d) }
                    }
                } catch (e: IOException) {
                    throw mapIo(e)
                }
            }
        }
        awaitClose { call.get()?.cancel(); reader.cancel() }
    }

    /** Whisper on Groq: 16 kHz mono WAV in, text out. Good with Hinglish. */
    suspend fun transcribe(apiKey: String, wav: ByteArray, language: String?): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw BrainError.MissingKey()
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", JarvisConfig.GROQ_STT_MODEL)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0")
            .apply { language?.let { addFormDataPart("language", it) } }
            .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        val request = Request.Builder()
            .url("${JarvisConfig.GROQ_BASE_URL}/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(form)
            .build()
        try {
            http.newCall(request).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw mapHttp(r.code, GroqStreamParser.errorMessage(text))
                json.parseToJsonElement(text).jsonObject["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            }
        } catch (e: IOException) {
            throw mapIo(e)
        }
    }

    private fun mapHttp(code: Int, message: String): BrainError = when (code) {
        429 -> BrainError.RateLimited(message)
        401, 403 -> BrainError.InvalidKey()
        else -> BrainError.Server(code, message)
    }

    private fun mapIo(e: IOException): Exception = when (e) {
        is UnknownHostException, is ConnectException, is SSLException, is SocketTimeoutException -> BrainError.NoNetwork()
        else -> e
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
