package com.adesh.devjarvis.brain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Deep research: split Sir's question into a few search angles, search them all (Google-grounded), then write one
 * careful answer that says where each fact came from and where sources disagree. Prompts and parsing; pure, tested.
 */
object Research {
    val PLAN = """
        Sir asked a question that needs real research. Write 3 or 4 different web search queries that together cover it:
        the core fact, the latest news or numbers, an opposing or comparative view, and an expert / official source.
        Use the language that searches best (English usually; Hindi for Indian local topics). Reply JSON only:
        {"queries":["...","...","..."]}
    """.trimIndent()

    val SYNTH = """
        You are JARVIS, a careful researcher. Below are Sir's question and findings from several web searches, each with
        the sites it came from. Write the answer Sir will hear: the direct answer first, then the key facts with numbers
        and dates, then differences / pros and cons if relevant, and say plainly where sources disagree or something is
        uncertain. Name sources by site (e.g. "Wikipedia aur The Hindu ke mutabik"). Use Sir's language (Hinglish if he
        used it, Hindi words in Roman script). 6 to 10 spoken sentences, no markdown, no lists, no links.
    """.trimIndent()

    fun parseQueries(raw: String?, fallback: String, max: Int = 4): List<String> {
        val start = raw?.indexOf('{') ?: -1
        val end = raw?.lastIndexOf('}') ?: -1
        val list = if (raw != null && start >= 0 && end > start) runCatching {
            ((Json.parseToJsonElement(raw.substring(start, end + 1)) as JsonObject)["queries"] as JsonArray)
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }.filter { it.length > 2 }
        }.getOrDefault(emptyList()) else emptyList()
        return (list.ifEmpty { listOf(fallback) }).distinct().take(max)
    }

    /** "wikipedia.org" from a grounding title, or the host of a link (never the long redirect URL). */
    fun siteName(title: String, uri: String): String {
        val t = title.trim()
        if (t.isNotEmpty() && t.length <= 60 && " " !in t) return t.removePrefix("www.")
        if (t.isNotEmpty() && t.length <= 40) return t
        val host = uri.substringAfter("://", "").substringBefore('/').removePrefix("www.")
        return if ("grounding-api-redirect" in uri || "vertexaisearch" in host) t.take(40) else host
    }

    fun findingsBlock(findings: List<Triple<String, String, List<String>>>): String = findings.joinToString("\n\n") { (q, text, sites) ->
        "SEARCH: $q\nFOUND: ${text.take(1_500)}\nSITES: ${sites.joinToString(", ").ifEmpty { "(none)" }}"
    }
}
