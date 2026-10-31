package com.adesh.devjarvis.proactive

/**
 * Morning briefing ke ingredients + bina LLM ke fallback text. Pure, unit-tested.
 */
object BriefingTemplate {

    data class Inputs(
        val hour: Int,
        val dateText: String,
        val weather: String?,
        val reminders: List<String>,
        val events: List<String>,
        val batteryPercent: Int,
        val charging: Boolean,
        val facts: List<String>,
    )

    fun greeting(hour: Int) = when (hour) {
        in 4..11 -> "Good morning, Sir."
        in 12..16 -> "Good afternoon, Sir."
        else -> "Good evening, Sir."
    }

    /** Spoken fallback when no LLM is available. Original lines only. */
    fun fallback(i: Inputs): String = buildString {
        append(greeting(i.hour)).append(' ')
        append("Aaj ${i.dateText} hai. ")
        i.weather?.let { append("Mausam: $it. ") }
        when {
            i.events.isEmpty() && i.reminders.isEmpty() -> append("Calendar khaali hai — aaj ka din aapka. ")
            else -> {
                if (i.events.isNotEmpty()) append("Aaj ${i.events.size} event: ${i.events.take(3).joinToString(", ")}. ")
                if (i.reminders.isNotEmpty()) append("Reminders: ${i.reminders.take(3).joinToString(", ")}. ")
            }
        }
        if (i.batteryPercent in 0..30 && !i.charging) append("Battery ${i.batteryPercent}% hai, charger saath rakhiye. ")
        append("Systems taiyaar hain, coffee aapki zimmedari.")
    }.trim()

    fun llmPrompt(i: Inputs): String = buildString {
        appendLine("Compose Sir's spoken morning briefing as JARVIS. Hinglish, at most 5 short sentences, no lists or markdown.")
        appendLine("Start with a greeting, mention weather, today's events and reminders (only if any), battery only if low,")
        appendLine("and end with one original dry-witty line. Use the facts only if naturally relevant.")
        appendLine("Hour: ${i.hour}. Date: ${i.dateText}.")
        appendLine("Weather: ${i.weather ?: "unknown"}")
        appendLine("Events today: ${i.events.ifEmpty { listOf("none") }.joinToString("; ")}")
        appendLine("Reminders today: ${i.reminders.ifEmpty { listOf("none") }.joinToString("; ")}")
        appendLine("Battery: ${i.batteryPercent}%${if (i.charging) " charging" else ""}")
        if (i.facts.isNotEmpty()) appendLine("Facts about Sir: ${i.facts.joinToString("; ")}")
    }
}
