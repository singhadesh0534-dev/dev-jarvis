package com.adesh.devjarvis.life

import com.adesh.devjarvis.tools.FuzzyMatcher
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Jab X ho, tab Y karo". event: charger_on | charger_off | wifi_on | wifi_off | headphones_on | time | message_from.
 * param: time ke liye "HH:mm", message_from ke liye naam / app; baaki mein khaali. action: Sir ke shabdon mein kaam.
 */
data class Trigger(val id: Long, val event: String, val param: String, val action: String, val lastFired: Long = 0)

/** One thing JARVIS did, as kept in the activity log. */
data class Used(val time: Long, val tool: String, val args: String, val ok: Boolean = true)

/** Pure, unit-tested. */
object TriggerLogic {
    val EVENTS = listOf("charger_on", "charger_off", "wifi_on", "wifi_off", "headphones_on", "time", "message_from")
    const val MAX = 20
    private const val COOLDOWN_MS = 60_000L

    /** "7:5", "07:05", "19.30" -> minutes after midnight; null if it isn't a time. */
    fun parseTime(raw: String): Int? {
        val m = Regex("""^\s*(\d{1,2})\s*[:.]\s*(\d{1,2})\s*$""").find(raw) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    fun normalizeParam(event: String, param: String): String? = when (event) {
        "time" -> parseTime(param)?.let { "%02d:%02d".format(it / 60, it % 60) }
        "message_from" -> param.trim().takeIf { it.isNotEmpty() }
        else -> ""
    }

    /**
     * Triggers to run now. [value]: for "time" the current minute of the day (as a number), for "message_from"
     * the sender and app of the message; unused otherwise. A trigger never runs twice within a minute, and a
     * time trigger runs once for its minute (two minutes of slack if the clock tick came late).
     */
    fun due(triggers: List<Trigger>, event: String, value: String, now: Long): List<Trigger> = triggers.filter { t ->
        if (t.event != event || now - t.lastFired < COOLDOWN_MS) return@filter false
        when (event) {
            "time" -> {
                val want = parseTime(t.param) ?: return@filter false
                val minute = value.toIntOrNull() ?: return@filter false
                val late = (minute - want + 1440) % 1440
                late in 0..2 && now - t.lastFired > 10 * 60_000L
            }
            "message_from" -> value.split('|').any { it.isNotBlank() && FuzzyMatcher.score(t.param, it) >= 0.85 }
            else -> true
        }
    }

    fun describe(t: Trigger): String = when (t.event) {
        "charger_on" -> "jab charger lage"
        "charger_off" -> "jab charger hate"
        "wifi_on" -> "jab Wi-Fi jude"
        "wifi_off" -> "jab Wi-Fi kate"
        "headphones_on" -> "jab earphones lagein"
        "time" -> "roz ${t.param} baje"
        "message_from" -> "jab ${t.param} ka message aaye"
        else -> t.event
    } + " -> " + t.action
}

/** Aadat se sujhaav: jo kaam Sir aksar isi waqt karwate hain. Pure, unit-tested. */
object SuggestionLogic {
    data class Suggestion(val key: String, val tool: String, val args: Map<String, String>, val days: Int)

    /** Only harmless, repeatable things are ever suggested. */
    val TOOLS = setOf("open_app", "play_music", "search_in_app", "set_toggle", "run_taught", "set_volume", "set_brightness")
    private const val WINDOW_MIN = 45
    private const val LOOK_BACK_DAYS = 10L
    const val MIN_DAYS = 3

    fun parseArgs(args: String): Map<String, String> =
        args.split(", ").mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it).trim() to p.substring(it + 1).trim() } }.toMap()

    /**
     * Something done on at least [MIN_DAYS] earlier days around this time of day, not done yet today and not
     * suggested yet today ([suggested] holds today's keys).
     */
    fun find(history: List<Used>, now: Long, suggested: Set<String>, zone: ZoneId = ZoneId.systemDefault()): Suggestion? {
        val nowZ = Instant.ofEpochMilli(now).atZone(zone)
        val today = nowZ.toLocalDate()
        val minuteNow = nowZ.hour * 60 + nowZ.minute
        val recent = history.filter { it.ok && it.tool in TOOLS && it.time <= now && it.time >= now - LOOK_BACK_DAYS * 86_400_000L }
        return recent.groupBy { it.tool + "|" + it.args }.mapNotNull { (key, uses) ->
            if (key in suggested) return@mapNotNull null
            val byDay = uses.groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }
            if (today in byDay) return@mapNotNull null // already done today
            val days = byDay.count { (_, list) ->
                list.any { u ->
                    val z = Instant.ofEpochMilli(u.time).atZone(zone)
                    val diff = Math.abs(z.hour * 60 + z.minute - minuteNow)
                    minOf(diff, 1440 - diff) <= WINDOW_MIN
                }
            }
            if (days >= MIN_DAYS) Suggestion(key, uses.first().tool, parseArgs(uses.first().args), days) else null
        }.maxByOrNull { it.days }
    }

    fun phrase(s: Suggestion): String {
        val a = s.args
        val what = when (s.tool) {
            "open_app" -> "${a["name"] ?: a["app"] ?: "wahi app"} khol doon?"
            "play_music" -> "${a["query"] ?: "gaane"} chala doon?"
            "search_in_app" -> "${a["app"] ?: "app"} pe '${a["query"].orEmpty()}' khol doon?"
            "set_toggle" -> "${a["target"] ?: "wo"} ${if (a["on"] == "false") "band" else "chalu"} kar doon?"
            "run_taught" -> "'${a["name"].orEmpty()}' wala kaam kar doon?"
            "set_volume" -> "volume ${a["level"] ?: a["percent"] ?: ""} kar doon?".replace("  ", " ")
            else -> "brightness ${a["level"] ?: a["percent"] ?: ""} kar doon?".replace("  ", " ")
        }
        return "Sir, aap aksar is waqt ye karte hain — $what"
    }
}

/** Roz ki diary: din bhar JARVIS ne kya kiya, uska saar. Pure, unit-tested. */
object DiaryLogic {
    const val KEEP_DAYS = 120
    /** Too small or too frequent to be worth a diary line. */
    private val SKIP = setOf(
        "get_time", "tap", "long_press", "type_text", "scroll", "swipe", "go_back", "go_home", "read_screen", "look_at_screen",
        "use_skill", "device_status", "interpreter_mode", "list_routines", "habit_summary", "diary", "media_control",
    )

    fun dayKey(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

    /** "aaj", "kal", "parso", "monday", "2026-11-10" -> that date (weekday names mean the most recent one). */
    fun resolve(query: String, today: LocalDate): LocalDate? {
        val q = query.trim().lowercase()
        runCatching { return LocalDate.parse(q) }
        return when (q) {
            "", "today", "aaj" -> today
            "yesterday", "kal" -> today.minusDays(1)
            "parso", "day before yesterday" -> today.minusDays(2)
            else -> {
                val names = mapOf(
                    "monday" to 1, "somvar" to 1, "somwar" to 1, "tuesday" to 2, "mangal" to 2, "mangalvar" to 2, "mangalwar" to 2,
                    "wednesday" to 3, "budh" to 3, "budhvar" to 3, "budhwar" to 3, "thursday" to 4, "guruvar" to 4, "guruwar" to 4, "brihaspativar" to 4,
                    "friday" to 5, "shukravar" to 5, "shukrawar" to 5, "saturday" to 6, "shanivar" to 6, "shaniwar" to 6,
                    "sunday" to 7, "ravivar" to 7, "raviwar" to 7, "itwar" to 7,
                )
                val dow = names.entries.firstOrNull { it.key in q }?.value ?: return null
                var d = today.minusDays(1)
                while (d.dayOfWeek != DayOfWeek.of(dow)) d = d.minusDays(1)
                d
            }
        }
    }

    fun lines(actions: List<Used>, zone: ZoneId = ZoneId.systemDefault()): List<String> =
        actions.filter { it.ok && it.tool !in SKIP }.sortedBy { it.time }.map { u ->
            val t = Instant.ofEpochMilli(u.time).atZone(zone)
            "%02d:%02d %s(%s)".format(t.hour, t.minute, u.tool, u.args.take(80))
        }

    /** Without AI: the day as a plain list. Null when nothing happened. */
    fun plain(actions: List<Used>, talks: List<String>, spending: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
        val done = lines(actions, zone)
        if (done.isEmpty() && talks.isEmpty() && spending == null) return null
        return buildString {
            if (talks.isNotEmpty()) append("Baatein: ").append(talks.take(6).joinToString("; ")).append(". ")
            if (done.isNotEmpty()) append("Kaam: ").append(done.take(25).joinToString("; ")).append(". ")
            spending?.let { append("Kharcha: ").append(it).append('.') }
        }.trim()
    }

    fun prompt(dateText: String, actions: List<Used>, talks: List<String>, spending: String?, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine("Write Sir's diary entry for $dateText from what his assistant did and discussed with him that day.")
        appendLine("4-6 short sentences, Hinglish in Roman script, third person (\"Sir ne ...\"), times where useful, most important first.")
        appendLine("Only what is listed below; do not invent anything. No lists, no markdown.")
        appendLine("Conversations: ${talks.ifEmpty { listOf("none") }.joinToString("; ")}")
        appendLine("Actions: ${lines(actions, zone).ifEmpty { listOf("none") }.take(40).joinToString("; ")}")
        appendLine("Spending: ${spending ?: "none recorded"}")
    }

    /** Add today's entry and drop the oldest beyond [KEEP_DAYS]. */
    fun put(diary: Map<String, String>, day: String, text: String): Map<String, String> =
        (diary + (day to text)).toSortedMap().entries.toList().takeLast(KEEP_DAYS).associate { it.key to it.value }
}
