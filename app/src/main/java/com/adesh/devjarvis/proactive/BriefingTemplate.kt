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

    /** Raat ke summary ke ingredients. */
    data class NightInputs(
        val dateText: String,
        val tomorrowEvents: List<String>,
        val tomorrowReminders: List<String>,
        /** e.g. "total 450 rupees (food 300, auto 150) over 2 entries"; null = nothing recorded. */
        val expensesToday: String?,
        val batteryPercent: Int,
        val charging: Boolean,
    )

    fun nightFallback(i: NightInputs): String = buildString {
        append("Good evening, Sir. Din ka hisaab: ")
        append(i.expensesToday?.let { "aaj ka kharcha $it. " } ?: "aaj koi kharcha note nahi hua. ")
        when {
            i.tomorrowEvents.isEmpty() && i.tomorrowReminders.isEmpty() -> append("Kal ka calendar khaali hai. ")
            else -> {
                if (i.tomorrowEvents.isNotEmpty()) append("Kal ${i.tomorrowEvents.size} event: ${i.tomorrowEvents.take(3).joinToString(", ")}. ")
                if (i.tomorrowReminders.isNotEmpty()) append("Kal ke reminders: ${i.tomorrowReminders.take(3).joinToString(", ")}. ")
            }
        }
        if (i.batteryPercent in 0..40 && !i.charging) append("Battery ${i.batteryPercent}% hai, sone se pehle charging pe laga dijiye. ")
        append("Aaram kijiye, pehra main de raha hoon.")
    }.trim()

    fun nightPrompt(i: NightInputs): String = buildString {
        appendLine("Compose Sir's spoken end-of-day summary as JARVIS. Hinglish, at most 5 short sentences, no lists or markdown.")
        appendLine("Cover: today's spending (only if any), tomorrow's events and reminders (say the calendar is clear if none),")
        appendLine("battery only if it needs charging overnight, and close with one original, warm, dry-witty good-night line.")
        appendLine("Use only the data below; do not invent anything.")
        appendLine("Date: ${i.dateText}")
        appendLine("Spending today: ${i.expensesToday ?: "none recorded"}")
        appendLine("Events tomorrow: ${i.tomorrowEvents.ifEmpty { listOf("none") }.joinToString("; ")}")
        appendLine("Reminders tomorrow: ${i.tomorrowReminders.ifEmpty { listOf("none") }.joinToString("; ")}")
        appendLine("Battery: ${i.batteryPercent}%${if (i.charging) " charging" else ""}")
    }

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
