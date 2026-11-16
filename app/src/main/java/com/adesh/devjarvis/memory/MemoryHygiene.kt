package com.adesh.devjarvis.memory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Keeps Sir's memory clean, carefully: every few days JARVIS reads all his memories and only merges true duplicates
 * and drops facts that a NEWER fact clearly replaced ("lives in Delhi" -> "moved to Pune"). Lessons are never
 * removed, at most 10 changes per pass, and every change is listed so it can be told / undone. Pure, unit-tested.
 */
object MemoryHygiene {
    val PROMPT = """
        You maintain the long-term memory of JARVIS about his owner, Sir. Below are all memories as "id | date | text".
        Find ONLY:
        1) duplicates: two or more memories that say the same thing -> merge them into one clear sentence;
        2) outdated: an older memory that a NEWER memory clearly replaces (same subject, changed fact) -> remove the older.
        Never remove or merge memories that start with "Lesson:". Never invent facts. When unsure, leave it.
        Reply JSON only: {"merge":[{"ids":[3,8],"text":"one merged sentence"}],"remove":[{"id":5,"because":"replaced by 12"}]}
        Empty lists if nothing needs doing.
    """.trimIndent()

    fun listing(facts: List<Fact>): String = facts.sortedBy { it.created }.joinToString("\n") { f ->
        "${f.id} | ${java.time.Instant.ofEpochMilli(f.created).toString().take(10)} | ${f.text.replace('\n', ' ').take(200)}"
    }

    data class Merge(val ids: List<Long>, val text: String)
    data class Remove(val id: Long, val because: String)
    data class Plan(val merges: List<Merge>, val removes: List<Remove>) {
        val size get() = merges.size + removes.size
    }

    /** The model's plan, checked: only real ids, never a Lesson, no id used twice, at most [max] changes. */
    fun parse(raw: String?, facts: List<Fact>, max: Int = 10): Plan {
        val byId = facts.associateBy { it.id }
        fun ok(id: Long) = byId[id]?.text?.startsWith("Lesson:") == false
        val start = raw?.indexOf('{') ?: -1
        val end = raw?.lastIndexOf('}') ?: -1
        if (raw == null || start < 0 || end <= start) return Plan(emptyList(), emptyList())
        val o = runCatching { Json.parseToJsonElement(raw.substring(start, end + 1)) as JsonObject }.getOrNull()
            ?: return Plan(emptyList(), emptyList())
        val used = HashSet<Long>()
        val merges = (o["merge"] as? JsonArray).orEmpty().mapNotNull { e ->
            val m = e as? JsonObject ?: return@mapNotNull null
            val ids = (m["ids"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.longOrNull }.distinct()
            val text = (m["text"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (ids.size < 2 || text.length < 5 || ids.any { !ok(it) || it in used }) return@mapNotNull null
            used += ids
            Merge(ids, text.take(300))
        }
        val removes = (o["remove"] as? JsonArray).orEmpty().mapNotNull { e ->
            val r = e as? JsonObject ?: return@mapNotNull null
            val id = (r["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            if (!ok(id) || id in used) return@mapNotNull null
            used += id
            Remove(id, (r["because"] as? JsonPrimitive)?.contentOrNull.orEmpty().take(120))
        }
        val m = merges.take(max)
        return Plan(m, removes.take((max - m.size).coerceAtLeast(0)))
    }

    /** Run again? Not more than every [everyMs], and only with enough memories to be worth it. */
    fun due(lastRun: Long, now: Long, factCount: Int, everyMs: Long = 3 * 86_400_000L): Boolean =
        factCount >= 12 && now - lastRun >= everyMs

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}
