package com.adesh.devjarvis.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What JARVIS reads about himself before every conversation: what he actually did for Sir lately (so "kya kiya?"
 * gets a true answer, also after a restart), and the mistakes he made lately (so he does not repeat them). Pure.
 */
object SelfReview {
    data class Done(val timeMillis: Long, val tool: String, val args: String, val ok: Boolean, val result: String)

    private val clock = SimpleDateFormat("HH:mm", Locale.ENGLISH)

    /** The last actions within [withinMs], oldest first, one short line each. */
    fun recentActions(all: List<Done>, now: Long, limit: Int = 12, withinMs: Long = 8 * 60 * 60 * 1000L): String =
        all.filter { now - it.timeMillis in 0..withinMs }
            .sortedBy { it.timeMillis }
            .takeLast(limit)
            .joinToString("\n") { d ->
                val mark = if (d.ok) "done" else "FAILED"
                "- ${clock.format(Date(d.timeMillis))} ${d.tool}(${d.args.take(70)}) $mark: ${d.result.lineSequence().firstOrNull().orEmpty().take(110)}"
            }

    private val KEEP = setOf("SIR_UNHAPPY", "FALSE_CLAIM", "TOOL_FAILED", "THINKING_ALOUD", "SELF_INTERRUPT?")
    private val LINE = Regex("""^.*?\[([A-Z_?]+)]\s*(.*)$""")

    /** The latest distinct mistakes from the problem journal, newest last. */
    fun lessons(journalLines: List<String>, limit: Int = 6): String {
        val seen = HashSet<String>()
        val picked = ArrayList<String>()
        for (line in journalLines.asReversed()) {
            val m = LINE.find(line) ?: continue
            val kind = m.groupValues[1]
            if (kind !in KEEP) continue
            val detail = m.groupValues[2].trim()
            val key = kind + "|" + detail.take(50).lowercase()
            if (!seen.add(key)) continue
            picked += "- [$kind] ${detail.take(170)}"
            if (picked.size >= limit) break
        }
        return picked.asReversed().joinToString("\n")
    }
}
