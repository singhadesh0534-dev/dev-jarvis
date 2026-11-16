package com.adesh.devjarvis.brain

/**
 * How Sir seems right now, from his last few lines (Hinglish words), so the reply matches him: gentle when he is low,
 * short and practical when he is stressed or tired, calm when he is angry, light when he is happy. Pure, unit-tested.
 */
object MoodSense {
    enum class Mood { LOW, STRESSED, ANGRY, HAPPY, NEUTRAL }

    private val LOW = Regex("""(?i)\b(udaas|udas|dukhi|akela|akele|rona|ro raha|sad|depressed|man nahi|mann nahi|bura lag|bekar raha|bekaar raha|tut gaya|toot gaya|miss kar)\b""")
    private val STRESSED = Regex("""(?i)\b(thak|thaka|thak gaya|tired|stress|tension|pareshan|neend|so nahi|kaam bahut|deadline|exam|ghabra|dar lag)\b""")
    private val ANGRY = Regex("""(?i)\b(gussa|irritate|dimag kharab|dimaag kharab|bakwas|pagal ho|chup kar|faltu|bekar ho tum|bekaar ho tum)\b""")
    private val HAPPY = Regex("""(?i)\b(khush|maza|mazaa|great|awesome|badhiya|zabardast|party|jeet|jeet gaya|selection|promotion|yay|wah)\b""")

    fun of(lines: List<String>): Mood {
        val t = lines.takeLast(3).joinToString(" ")
        return when {
            ANGRY.containsMatchIn(t) -> Mood.ANGRY
            LOW.containsMatchIn(t) -> Mood.LOW
            STRESSED.containsMatchIn(t) -> Mood.STRESSED
            HAPPY.containsMatchIn(t) -> Mood.HAPPY
            else -> Mood.NEUTRAL
        }
    }

    fun hint(mood: Mood): String = when (mood) {
        Mood.LOW -> "Sir sounds low right now: be gentle and warm, ask what happened in one line, listen; no jokes, no lectures."
        Mood.STRESSED -> "Sir sounds stressed or tired right now: short, calm, practical replies; offer to take one thing off his plate."
        Mood.ANGRY -> "Sir sounds angry right now: stay calm, don't argue or over-apologise; fix the thing or ask one clear question."
        Mood.HAPPY -> "Sir sounds happy right now: share the joy, a little playful is welcome."
        Mood.NEUTRAL -> ""
    }
}
