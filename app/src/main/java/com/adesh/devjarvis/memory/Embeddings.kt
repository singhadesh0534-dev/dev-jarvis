package com.adesh.devjarvis.memory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.math.sqrt

/**
 * Meaning-based memory: Gemini text embeddings (free with Sir's key), so "maa ka janamdin" finds "Sir's mother was born
 * on 12 March" even with no shared word. Requests, parsing, storage format and similarity. Pure, unit-tested.
 */
object Embeddings {
    const val MODEL = "gemini-embedding-001"
    const val DIMS = 256

    fun batchUrl(model: String = MODEL) = "https://generativelanguage.googleapis.com/v1beta/models/$model:batchEmbedContents"

    /** taskType RETRIEVAL_DOCUMENT for facts, RETRIEVAL_QUERY for what Sir asks. */
    fun batchBody(texts: List<String>, query: Boolean, model: String = MODEL): String = buildJsonObject {
        putJsonArray("requests") {
            texts.forEach { t ->
                addJsonObject {
                    put("model", "models/$model")
                    putJsonObject("content") { putJsonArray("parts") { addJsonObject { put("text", t.take(2_000)) } } }
                    put("taskType", if (query) "RETRIEVAL_QUERY" else "RETRIEVAL_DOCUMENT")
                    put("outputDimensionality", DIMS)
                }
            }
        }
    }.toString()

    fun parseBatch(body: String): List<FloatArray>? = runCatching {
        ((Json.parseToJsonElement(body) as JsonObject)["embeddings"] as JsonArray).map { e ->
            val values = (e as JsonObject)["values"] as JsonArray
            normalize(FloatArray(values.size) { i -> (values[i] as JsonPrimitive).floatOrNull ?: 0f })
        }
    }.getOrNull()

    fun normalize(v: FloatArray): FloatArray {
        var n = 0.0
        for (x in v) n += x * x
        val len = sqrt(n).toFloat()
        return if (len == 0f) v else FloatArray(v.size) { v[it] / len }
    }

    /** Cosine of two unit vectors (dot product); 0 when sizes differ. */
    fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size) return 0.0
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    fun encode(v: FloatArray): String {
        val buf = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach { buf.putFloat(it) }
        return Base64.getEncoder().encodeToString(buf.array())
    }

    fun decode(s: String): FloatArray? = runCatching {
        val b = ByteBuffer.wrap(Base64.getDecoder().decode(s)).order(ByteOrder.LITTLE_ENDIAN)
        FloatArray(b.remaining() / 4) { b.float }
    }.getOrNull()

    /** One line per fact: "<id> <sha of text> <vector>" — a changed fact gets a new vector. */
    fun line(id: Long, textKey: String, v: FloatArray) = "$id $textKey ${encode(v)}"

    data class Entry(val id: Long, val textKey: String, val vector: FloatArray)

    fun parseLine(line: String): Entry? {
        val p = line.trim().split(' ')
        if (p.size != 3) return null
        val id = p[0].toLongOrNull() ?: return null
        return decode(p[2])?.let { Entry(id, p[1], it) }
    }

    fun textKey(text: String): String = Integer.toHexString(text.trim().lowercase().hashCode())
}
