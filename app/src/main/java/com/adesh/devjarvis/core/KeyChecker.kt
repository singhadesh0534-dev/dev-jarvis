package com.adesh.devjarvis.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings > "Keys check karo": har key ko uski apni service se ek chhota sa sawaal (models ki list) pooch ke
 * batata hai ki kaun si key chal rahi hai aur kaun si galat hai. Key kabhi dikhai ya log nahi hoti — sirf uska number.
 */
@Singleton
class KeyChecker @Inject constructor(private val http: OkHttpClient) {

    /** HTTP code and the service's short reason (e.g. API_KEY_INVALID). The key itself is never part of it. */
    private fun code(url: String, header: String, value: String): Pair<Int, String> = runCatching {
        http.newCall(Request.Builder().url(url).header(header, value).build()).execute().use { r ->
            val body = if (r.isSuccessful) "" else r.body?.string().orEmpty().take(2_000)
            val reason = Regex("\"reason\"\\s*:\\s*\"([A-Z_]+)\"").find(body)?.groupValues?.get(1)
                ?: Regex("\"status\"\\s*:\\s*\"([A-Z_]+)\"").find(body)?.groupValues?.get(1)
                ?: ""
            r.code to reason
        }
    }.getOrDefault(0 to "")

    private fun line(name: String, results: List<Pair<Int, String>>): String {
        if (results.isEmpty()) return "$name: koi key nahi"
        val ok = { c: Int -> c in 200..299 || c == 429 }
        val bad = results.withIndex().filter { !ok(it.value.first) }.map { (i, r) ->
            "#${i + 1}" + when {
                r.first == 0 -> " (network)"
                r.second.isNotEmpty() -> " (${r.first} ${r.second})"
                else -> " (${r.first})"
            }
        }
        val good = results.count { ok(it.first) }
        return "$name: $good sahi" + if (bad.isEmpty()) "" else ", galat: ${bad.joinToString(", ")}"
    }

    suspend fun check(gemini: String, groq: String, eleven: String, extraKey: String, extraUrl: String): String = withContext(Dispatchers.IO) {
        val pool = KeyPool()
        listOf(
            line("Gemini", pool.parse(gemini).map { code("${JarvisConfig.GEMINI_BASE_URL}/models?pageSize=1", "x-goog-api-key", it) }),
            line("Groq", pool.parse(groq).map { code("${JarvisConfig.GROQ_BASE_URL}/models", "Authorization", "Bearer $it") }),
            line("ElevenLabs", pool.parse(eleven).map { code("${JarvisConfig.ELEVEN_BASE_URL}/voices", "xi-api-key", it) }),
        ).plus(
            if (extraKey.isBlank()) emptyList()
            else listOf(line("Third brain", pool.parse(extraKey).map { code("${extraUrl.trimEnd('/')}/models", "Authorization", "Bearer $it") })),
        ).joinToString("\n")
    }
}
