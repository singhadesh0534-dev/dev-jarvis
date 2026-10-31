package com.adesh.devjarvis.wakeword

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.sqrt

/**
 * "Sirf meri awaaz" — best-effort speaker check. Vosk ke speaker model se har utterance ka 128-number
 * "voiceprint" (x-vector) milta hai; enrolled voiceprint se cosine similarity nikaal ke decide karte hain.
 * Pure Kotlin, unit-tested. (Bheed/shor mein 100% pakka nahi — isliye best-effort.)
 */
object SpeakerVerification {

    /** x-vectors Sir ko enroll karte waqt kitne samples chahiye. */
    const val ENROLL_SAMPLES = 3
    /** 0 (sabko maane) .. 1 (bahut sakht). Default thoda narm taaki asli awaaz miss na ho. */
    const val DEFAULT_THRESHOLD = 0.45f

    private val json = Json { ignoreUnknownKeys = true }

    /** Vosk ke final-result JSON se "spk" x-vector nikaalo. */
    fun parseVector(resultJson: String): FloatArray? {
        val arr = runCatching { json.parseToJsonElement(resultJson).jsonObject["spk"] as? JsonArray }.getOrNull() ?: return null
        if (arr.isEmpty()) return null
        return FloatArray(arr.size) { i -> arr[i].jsonPrimitive.floatOrNull ?: 0f }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return -1f
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        if (na == 0.0 || nb == 0.0) return -1f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }

    /** Enrollment ke samples ka average voiceprint (phir normalise). */
    fun average(vectors: List<FloatArray>): FloatArray? {
        val valid = vectors.filter { it.isNotEmpty() }
        if (valid.isEmpty()) return null
        val dim = valid.first().size
        if (valid.any { it.size != dim }) return null
        val sum = DoubleArray(dim)
        valid.forEach { v -> for (i in 0 until dim) sum[i] += v[i] }
        return FloatArray(dim) { i -> (sum[i] / valid.size).toFloat() }
    }

    fun matches(enrolled: FloatArray, candidate: FloatArray, threshold: Float): Boolean =
        cosine(enrolled, candidate) >= threshold

    /** Profile ko text mein save/load karna. */
    fun encode(vector: FloatArray): String = vector.joinToString(",")
    fun decode(text: String?): FloatArray? {
        if (text.isNullOrBlank()) return null
        val parts = text.split(',').mapNotNull { it.trim().toFloatOrNull() }
        return if (parts.size >= 16) parts.toFloatArray() else null
    }
}
