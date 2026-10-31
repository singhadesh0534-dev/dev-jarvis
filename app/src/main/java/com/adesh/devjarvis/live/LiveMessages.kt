package com.adesh.devjarvis.live

import com.adesh.devjarvis.tools.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/**
 * Gemini Live API (BidiGenerateContent WebSocket) ke messages: banana aur padhna. Pure Kotlin, unit-tested.
 * Format: google-gemini/cookbook quickstarts/websockets (Oct 2026).
 */
object LiveMessages {

    data class Call(val id: String, val name: String, val args: JsonObject)

    sealed interface ServerEvent {
        data object SetupComplete : ServerEvent
        class Audio(val pcm: ByteArray) : ServerEvent
        data class InputText(val text: String) : ServerEvent
        data class OutputText(val text: String) : ServerEvent
        data object Interrupted : ServerEvent
        data object TurnComplete : ServerEvent
        data class ToolCalls(val calls: List<Call>) : ServerEvent
        data class ToolCancelled(val ids: List<String>) : ServerEvent
        data class GoAway(val timeLeft: String?) : ServerEvent
    }

    enum class CloseKind { QUOTA, AUTH, MODEL, NETWORK, OTHER }

    private val json = Json { ignoreUnknownKeys = true }

    fun setup(model: String, voice: String, systemInstruction: String, functionDeclarations: List<JsonObject>): String =
        buildJsonObject {
            putJsonObject("setup") {
                put("model", if (model.startsWith("models/")) model else "models/$model")
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") { add(kotlinx.serialization.json.JsonPrimitive("AUDIO")) }
                    putJsonObject("speechConfig") {
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice) }
                        }
                    }
                }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", systemInstruction) } }
                }
                if (functionDeclarations.isNotEmpty()) {
                    putJsonArray("tools") { addJsonObject { put("functionDeclarations", JsonArray(functionDeclarations)) } }
                }
                putJsonObject("inputAudioTranscription") { }
                putJsonObject("outputAudioTranscription") { }
            }
        }.toString()

    fun audio(pcm: ByteArray, length: Int, sampleRate: Int): String = buildJsonObject {
        putJsonObject("realtimeInput") {
            putJsonObject("audio") {
                put("data", Base64.getEncoder().encodeToString(pcm.copyOf(length)))
                put("mimeType", "audio/pcm;rate=$sampleRate")
            }
        }
    }.toString()

    fun toolResponse(results: List<Pair<Call, ToolResult>>): String = buildJsonObject {
        putJsonObject("toolResponse") {
            putJsonArray("functionResponses") {
                results.forEach { (call, r) ->
                    addJsonObject {
                        put("id", call.id)
                        put("name", call.name)
                        putJsonObject("response") {
                            put("ok", r.ok)
                            put("result", r.message)
                        }
                    }
                }
            }
        }
    }.toString()

    /** One server message can carry several things (audio + transcription + turnComplete). Order matters. */
    fun parse(raw: String): List<ServerEvent> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        val out = mutableListOf<ServerEvent>()
        if (root["setupComplete"] != null) out += ServerEvent.SetupComplete

        (root["serverContent"] as? JsonObject)?.let { sc ->
            (sc["inputTranscription"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotEmpty() }?.let { out += ServerEvent.InputText(it) }
            val parts = ((sc["modelTurn"] as? JsonObject)?.get("parts") as? JsonArray).orEmpty()
            for (p in parts) {
                val o = p as? JsonObject ?: continue
                if (o["thought"]?.jsonPrimitive?.booleanOrNull == true) continue
                (o["inlineData"] as? JsonObject)?.get("data")?.jsonPrimitive?.contentOrNull?.let { b64 ->
                    runCatching { Base64.getDecoder().decode(b64) }.getOrNull()?.let { out += ServerEvent.Audio(it) }
                }
            }
            (sc["outputTranscription"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotEmpty() }?.let { out += ServerEvent.OutputText(it) }
            if (sc["interrupted"]?.jsonPrimitive?.booleanOrNull == true) out += ServerEvent.Interrupted
            if (sc["turnComplete"]?.jsonPrimitive?.booleanOrNull == true) out += ServerEvent.TurnComplete
        }

        (root["toolCall"] as? JsonObject)?.let { tc ->
            val calls = (tc["functionCalls"] as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Call(o["id"]?.jsonPrimitive?.contentOrNull ?: name, name, o["args"] as? JsonObject ?: JsonObject(emptyMap()))
            }
            if (calls.isNotEmpty()) out += ServerEvent.ToolCalls(calls)
        }
        (root["toolCallCancellation"] as? JsonObject)?.let { c ->
            out += ServerEvent.ToolCancelled((c["ids"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull })
        }
        (root["goAway"] as? JsonObject)?.let { g -> out += ServerEvent.GoAway(g["timeLeft"]?.jsonPrimitive?.contentOrNull) }
        return out
    }

    /** Why did the server close the socket? Decides whether to fall back and for how long. */
    fun classifyClose(code: Int, reason: String?): CloseKind {
        val r = reason.orEmpty().lowercase()
        return when {
            "quota" in r || "exhaust" in r || "rate limit" in r || "429" in r -> CloseKind.QUOTA
            "api key" in r || "permission" in r || "unauthenticated" in r || code == 1008 && "key" in r -> CloseKind.AUTH
            ("model" in r && ("not found" in r || "not supported" in r)) || "not found" in r -> CloseKind.MODEL
            else -> CloseKind.OTHER
        }
    }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}
