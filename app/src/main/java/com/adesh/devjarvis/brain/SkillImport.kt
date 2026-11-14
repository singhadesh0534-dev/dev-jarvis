package com.adesh.devjarvis.brain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Sir ki apni skills: haath se likhi (naam, kab, steps) ya file se import — SKILL.md (frontmatter name /
 * description wali, Claude-style), koi bhi .md / .txt, ya JSON. Zip ke andar ki har aisi file bhi. Pure, unit-tested.
 */
object SkillImport {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    const val MAX_STEPS = 20
    private const val MAX_STEP = 300

    /** A valid skill id from any name ("Shaadi ka budget" -> shaadi_ka_budget). */
    fun idFrom(name: String): String {
        val slug = name.lowercase().replace(Regex("[^a-z]+"), "_").trim('_').take(40).trimEnd('_')
        if (slug.length >= 3 && slug[0].isLetter()) return slug
        // Hindi-script or very short names: a stable letters-only id from the name itself.
        val letters = name.hashCode().toUInt().toString(26).map { c -> if (c.isDigit()) 'a' + (c - '0') else 'k' + (c - 'a') }.joinToString("")
        return "my_skill_" + letters.filter { it in 'a'..'z' }.take(8)
    }

    fun steps(text: String): List<String> = text.split('\n', '|')
        .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim().replace(Regex("""^\d+[.)]\s*"""), "").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it != "---" && !it.startsWith("```") }
        .map { it.take(MAX_STEP) }
        .take(MAX_STEPS)

    /** Sir's own skill from the form. Null when the name or the steps are missing. */
    fun manual(name: String, whenToUse: String, stepsText: String): Skills.Skill? {
        val s = steps(stepsText)
        if (name.isBlank() || s.isEmpty()) return null
        return Skills.Skill(idFrom(name), whenToUse.trim().ifBlank { name.trim() }.take(200), numbered(s), "learned")
    }

    private fun numbered(s: List<String>) = s.mapIndexed { i, step -> "${i + 1}. $step" }.joinToString("\n")

    /** A Markdown / text skill. Frontmatter (name:, description:) is used when present. */
    fun fromMarkdown(fileName: String, text: String): Skills.Skill? {
        var body = text.replace("\r", "")
        var name = ""
        var description = ""
        if (body.trimStart().startsWith("---")) {
            val start = body.indexOf("---")
            val end = body.indexOf("\n---", start + 3)
            if (end > 0) {
                body.substring(start + 3, end).lines().forEach { line ->
                    val k = line.substringBefore(':').trim().lowercase()
                    val v = line.substringAfter(':', "").trim().trim('"', '\'')
                    if (k == "name") name = v
                    if (k == "description") description = v
                }
                body = body.substring(end + 4)
            }
        }
        if (name.isBlank()) name = body.lines().firstOrNull { it.trim().startsWith("#") }?.trim()?.trimStart('#')?.trim().orEmpty()
        if (name.isBlank()) name = fileName.substringAfterLast('/').substringBeforeLast('.').let { if (it.equals("skill", true)) fileName.substringBeforeLast('/').substringAfterLast('/') else it }
        if (description.isBlank()) description = body.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }.orEmpty()
        val s = steps(body)
        if (name.isBlank() || s.isEmpty()) return null
        return Skills.Skill(idFrom(name), description.ifBlank { name }.take(200), numbered(s), "learned")
    }

    /** JSON: one object or an array; fields id / name, when / description / whenToUse, steps (list or text). */
    fun fromJson(text: String): List<Skills.Skill> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val items = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> (root["skills"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: listOf(root)
            else -> emptyList()
        }
        return items.mapNotNull { o ->
            fun str(vararg keys: String) = keys.firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.contentOrNull?.takeIf { v -> v.isNotBlank() } }.orEmpty()
            val name = str("name", "id", "title")
            val stepsText = when (val st = o["steps"] ?: o["playbook"]) {
                is JsonArray -> st.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("\n")
                is JsonPrimitive -> st.contentOrNull.orEmpty()
                else -> ""
            }
            manual(name, str("when", "whenToUse", "description"), stepsText)
        }
    }

    /** Any one file: decides by its extension. */
    fun fromFile(fileName: String, text: String): List<Skills.Skill> = when {
        fileName.endsWith(".json", ignoreCase = true) -> fromJson(text)
        fileName.endsWith(".md", ignoreCase = true) || fileName.endsWith(".txt", ignoreCase = true) ||
            fileName.endsWith(".markdown", ignoreCase = true) -> listOfNotNull(fromMarkdown(fileName, text))
        else -> emptyList()
    }
}
