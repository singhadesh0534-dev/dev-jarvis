package com.adesh.devjarvis.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class ToolArgError(message: String) : Exception(message)

/**
 * LLM ke diye arguments ko safely padhna. Model kabhi 6 bhejta hai, kabhi "6", kabhi 6.0 —
 * sab ko sambhalta hai. Pure Kotlin, unit-tested.
 */
class ToolArgs(private val json: JsonObject) {

    private fun prim(name: String): JsonPrimitive? = json[name] as? JsonPrimitive

    fun string(name: String): String? = prim(name)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    fun requireString(name: String): String = string(name) ?: throw ToolArgError("missing '$name'")

    fun int(name: String): Int? {
        val p = prim(name) ?: return null
        p.doubleOrNull?.let { return Math.round(it).toInt() }
        return p.contentOrNull?.trim()?.toDoubleOrNull()?.let { Math.round(it).toInt() }
    }

    fun bool(name: String): Boolean? {
        val p = prim(name) ?: return null
        p.booleanOrNull?.let { return it }
        return when (p.contentOrNull?.trim()?.lowercase()) {
            "true", "yes", "on", "1", "haan" -> true
            "false", "no", "off", "0", "nahi" -> false
            else -> null
        }
    }

    /** Case-insensitive enum; returns the canonical allowed value. */
    fun enum(name: String, allowed: List<String>): String? {
        val v = string(name)?.lowercase() ?: return null
        return allowed.firstOrNull { it.lowercase() == v }
    }

    /** "2026-10-31T18:30", "2026-10-31 18:30", "18:30", "6:30 PM". Time-only = next occurrence. */
    fun dateTime(name: String, now: LocalDateTime): LocalDateTime? = string(name)?.let { parseDateTime(it, now) }

    /** Seconds from 90, "90", "90s", "5m", "1h30m", "PT5M", "00:05:00". */
    fun durationSeconds(name: String): Int? {
        prim(name)?.doubleOrNull?.let { return it.toInt() }
        return string(name)?.let { parseDuration(it) }
    }

    companion object {
        private val dateTimeFormats = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
        )
        private val timeFormats = listOf(
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("H:mm:ss"),
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h a", Locale.ENGLISH),
        )

        fun parseDateTime(raw: String, now: LocalDateTime): LocalDateTime? {
            val s = raw.trim().removeSuffix("Z")
            for (f in dateTimeFormats) {
                try { return LocalDateTime.parse(s, f) } catch (_: DateTimeParseException) {}
            }
            try { return LocalDate.parse(s).atTime(9, 0) } catch (_: DateTimeParseException) {}
            val upper = s.uppercase(Locale.ENGLISH).replace(".", "")
            for (f in timeFormats) {
                try {
                    val t = LocalTime.parse(upper, f)
                    val today = now.toLocalDate().atTime(t)
                    return if (today.isAfter(now)) today else today.plusDays(1)
                } catch (_: DateTimeParseException) {}
            }
            return null
        }

        private val unitPattern = Regex("""(\d+(?:\.\d+)?)\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)""")

        fun parseDuration(raw: String): Int? {
            val s = raw.trim().lowercase()
            s.toDoubleOrNull()?.let { return it.toInt() }
            if (s.startsWith("pt")) {
                return runCatching { java.time.Duration.parse(s.uppercase()).seconds.toInt() }.getOrNull()
            }
            if (s.matches(Regex("""\d{1,2}:\d{2}(:\d{2})?"""))) {
                val p = s.split(':').map { it.toInt() }
                return if (p.size == 3) p[0] * 3600 + p[1] * 60 + p[2] else p[0] * 60 + p[1]
            }
            val parts = unitPattern.findAll(s).toList()
            if (parts.isEmpty()) return null
            return parts.sumOf { m ->
                val n = m.groupValues[1].toDouble()
                when (m.groupValues[2].first()) {
                    'h' -> n * 3600
                    'm' -> n * 60
                    else -> n
                }
            }.toInt()
        }
    }
}
