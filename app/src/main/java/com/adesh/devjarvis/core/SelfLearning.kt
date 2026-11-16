package com.adesh.devjarvis.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * JARVIS learns from his own mistakes every night: the day's self-caught problems (Sir unhappy, false "done", failed
 * tools, thinking aloud) become at most 3 short, concrete "Lesson:" memories, which are kept in view in every
 * conversation. Only new lessons; never vague ones. Pure, unit-tested.
 */
object SelfLearning {
    val PROMPT = """
        You are JARVIS reviewing your own mistakes from today with Sir (your owner). Below are the problems your
        self-check caught, and the lessons you already have. Write at most 3 NEW lessons that would have prevented
        these mistakes. Each lesson: one short, concrete rule starting with "Lesson:", saying WHEN and WHAT to do
        (e.g. "Lesson: when Sir says 'ye nahi bola tha', say what you now think he meant before acting again").
        No vague advice ("be careful", "listen better"), nothing already covered by an existing lesson, nothing about
        payments, passwords or safety rules (those never change). Reply JSON only: {"lessons":["Lesson: ...", "..."]}
    """.trimIndent()

    private val KEEP = setOf("SIR_UNHAPPY", "FALSE_CLAIM", "TOOL_FAILED", "THINKING_ALOUD", "SELF_INTERRUPT?", "FALSE_WAKE?")

    /** Today's problem lines (journal format "dd MMM HH:mm:ss  [KIND] detail"), at most [max]. */
    fun todaysProblems(journal: List<String>, dayPrefix: String, max: Int = 25): List<String> =
        journal.filter { it.startsWith(dayPrefix) }
            .mapNotNull { l -> Regex("""\[([A-Z_?]+)]\s*(.*)$""").find(l)?.let { it.groupValues[1] to it.groupValues[2] } }
            .filter { it.first in KEEP }
            .map { "[${it.first}] ${it.second.take(200)}" }
            .distinct().takeLast(max)

    fun userPrompt(problems: List<String>, existingLessons: List<String>): String =
        "TODAY'S PROBLEMS:\n" + problems.joinToString("\n") + "\n\nLESSONS YOU ALREADY HAVE:\n" +
            existingLessons.ifEmpty { listOf("(none)") }.joinToString("\n")

    /** New, concrete lessons only: start with "Lesson:", 25-220 chars, not near-copies of existing ones, max 3. */
    fun parse(raw: String?, existing: List<String>): List<String> {
        val start = raw?.indexOf('{') ?: -1
        val end = raw?.lastIndexOf('}') ?: -1
        if (raw == null || start < 0 || end <= start) return emptyList()
        val list = runCatching {
            ((Json.parseToJsonElement(raw.substring(start, end + 1)) as JsonObject)["lessons"] as JsonArray)
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
        }.getOrDefault(emptyList())
        val known = existing.map { norm(it) }
        return list.filter { it.startsWith("Lesson:") && it.length in 25..220 }
            .filter { l -> known.none { k -> overlap(norm(l), k) >= 0.7 } }
            .distinctBy { norm(it) }.take(3)
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(' ').filter { it.length > 2 }.toSet()

    private fun overlap(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / minOf(a.size, b.size)
}
