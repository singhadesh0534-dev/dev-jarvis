package com.adesh.devjarvis.brain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * JARVIS apne skill khud likhta hai: jis kaam ka koi skill nahi, ya jahan Sir ne galti batayi, wahan sabse zyada
 * sochne wale mode mein ek pakka playbook banta hai aur yaad rehta hai. Ye file sirf prompt aur parsing hai.
 * Pure, unit-tested.
 */
object SkillWriter {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val ID = Regex("[a-z][a-z_]{2,39}")
    const val MAX_LEARNED = 200

    fun system(toolNames: List<String>): String = """
        You write "skills" for JARVIS, an Android phone assistant that acts through tools. A skill is a short,
        exact playbook the assistant follows every time this kind of request comes. Think it through carefully:
        the steps must work on a real Android phone, in the right order, with the right tools, first time.

        Rules for the playbook:
        - 3 to 8 numbered steps, each one short imperative sentence (max 30 words).
        - Name the exact tools to call, only from this list: ${toolNames.joinToString(", ")}.
        - Fastest reliable route first (a direct tool beats tapping through screens); one fallback when it fails.
        - Check each tool result before the next step; stop and ask Sir after two steps that did not help.
        - Safety is fixed: never payments, banking, OTPs or passwords; ask Sir before calling, messaging, posting or
          deleting; on password / payment screens stop and let him do it.
        - If Sir gave a lesson or correction, it overrides everything else and must be reflected in the steps.
        - No chit-chat inside steps. English steps; app and contact names exactly as Sir says them.

        Reply with JSON only:
        {"id": "snake_case_name", "when": "one line: the kind of request this skill is for", "steps": ["...", "..."]}
    """.trimIndent()

    fun request(task: String, lesson: String?, existing: Skills.Skill?): String = buildString {
        append("Kind of request: ").append(task.trim()).append('\n')
        if (!lesson.isNullOrBlank()) append("Sir's correction / lesson (must be followed): ").append(lesson.trim()).append('\n')
        if (existing != null) {
            append("Improve this existing skill '").append(existing.id).append("' instead of starting over; keep its id:\n")
            append(existing.playbook.trimIndent().trim()).append('\n')
        }
    }

    /** The model's JSON -> a skill, or null if it is not usable. Unknown tools in a step are allowed but noted by the caller. */
    fun parse(raw: String?, keepId: String? = null): Skills.Skill? {
        val body = raw.orEmpty().let { s -> s.substring(s.indexOf('{').coerceAtLeast(0), (s.lastIndexOf('}') + 1).coerceAtLeast(0)) }
        val o = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull() ?: return null
        val id = (keepId ?: (o["id"] as? JsonPrimitive)?.contentOrNull.orEmpty())
            .trim().lowercase().replace(Regex("[^a-z_ ]"), "").replace(' ', '_').trim('_').take(40)
        val whenToUse = (o["when"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().take(200)
        val steps = (o["steps"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.removePrefix("-")?.replace(Regex("^\\d+[.)]\\s*"), "")?.trim() }
            .filter { it.isNotEmpty() }.map { it.take(300) }
        if (!ID.matches(id) || whenToUse.isEmpty() || steps.size !in 3..8) return null
        return Skills.Skill(id, whenToUse, steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"), "learned")
    }

    fun encode(skills: List<Skills.Skill>): String = buildJsonArray {
        skills.forEach { s -> add(buildJsonObject { put("id", s.id); put("when", s.whenToUse); put("steps", s.playbook) }) }
    }.toString()

    fun decode(text: String?): List<Skills.Skill> = runCatching {
        (json.parseToJsonElement(text.orEmpty()) as JsonArray).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Skills.Skill(id, o["when"]?.jsonPrimitive?.contentOrNull.orEmpty(), o["steps"]?.jsonPrimitive?.contentOrNull.orEmpty(), "learned")
        }
    }.getOrDefault(emptyList())

    /** Newest version of a skill replaces the old one; the oldest go when the list is full. */
    fun merge(current: List<Skills.Skill>, new: Skills.Skill): List<Skills.Skill> =
        (current.filter { it.id != new.id } + new).takeLast(MAX_LEARNED)

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}
