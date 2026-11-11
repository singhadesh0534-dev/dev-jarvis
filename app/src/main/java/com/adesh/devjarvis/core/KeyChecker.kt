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

    private fun code(url: String, header: String, value: String): Int = runCatching {
        http.newCall(Request.Builder().url(url).header(header, value).build()).execute().use { it.code }
    }.getOrDefault(0)

    private fun line(name: String, codes: List<Int>): String {
        if (codes.isEmpty()) return "$name: koi key nahi"
        val bad = codes.withIndex().filter { it.value !in 200..299 && it.value != 429 }.map { "#${it.index + 1}" + if (it.value == 0) " (network)" else " (${it.value})" }
        val good = codes.count { it in 200..299 || it == 429 }
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
