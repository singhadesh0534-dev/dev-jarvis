package com.adesh.devjarvis.reminders

/**
 * A reminder that matters is not just said once: for a task ("dawai le lo", "Mummy ko call karna") JARVIS asks again
 * after 15 and then 30 minutes until Sir says or taps "ho gaya". Events ("meeting 5 baje", birthdays) are said once.
 * Pure, unit-tested.
 */
object ReminderFollowUp {
    const val MAX_ATTEMPTS = 2

    private val EVENTISH = Regex(
        """(?i)\b(meeting|birthday|janamdin|anniversary|match|movie|show|flight|train|bus|event|party|class starts|wedding|shaadi)\b""",
    )

    fun needsFollowUp(text: String) = !EVENTISH.containsMatchIn(text)

    /** Delay before follow-up number [attempt] (1-based), or null when no more. */
    fun delayMs(attempt: Int): Long? = when (attempt) {
        1 -> 15 * 60_000L
        2 -> 30 * 60_000L
        else -> null
    }

    fun spoken(text: String, attempt: Int): String = when (attempt) {
        0 -> "Boss, reminder: $text"
        1 -> "Boss, '$text' ho gaya? Nahi hua to ab kar lijiye — main thodi der baad phir poochhunga."
        else -> "Boss, aakhri baar yaad dila raha hoon: $text."
    }

    /** "Reminder ho gaya", "dawai le li", "kar diya" -> Sir finished the reminder. */
    private val DONE = Regex("""(?i)\b(ho gaya|ho gayi|kar diya|kar li|le li|le liya|done|finished|kha li|pi liya|call kar liya|bhej diya)\b""")
    fun saysDone(words: String) = DONE.containsMatchIn(words)
}
