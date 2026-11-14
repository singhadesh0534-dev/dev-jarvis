package com.adesh.devjarvis.tools

import com.adesh.devjarvis.brain.Persona

/**
 * "Awaaz badlo" done right: the voice Sir hears belongs to the role JARVIS is in (JARVIS, dost or gf each have their own),
 * and pitch / speed words ("high", "thoda bhaari", "+10") become real numbers. Pure, unit-tested.
 */
object VoiceChoice {
    /** Every Live voice name, men's and women's. */
    val ALL: List<String> = Persona.HIS_VOICES + Persona.HER_VOICES

    /** What each Live voice sounds like, so a disliked voice is swapped for a clearly different one. */
    val CHARACTER: Map<String, String> = mapOf(
        "Charon" to "calm, informative", "Orus" to "firm, deep", "Fenrir" to "excitable, energetic", "Iapetus" to "clear",
        "Algenib" to "gravelly, heavy", "Rasalgethi" to "informative", "Alnilam" to "firm", "Schedar" to "even, steady",
        "Enceladus" to "breathy, soft", "Umbriel" to "easy-going", "Puck" to "upbeat, young", "Achird" to "friendly",
        "Sadaltager" to "knowledgeable",
        "Kore" to "firm", "Leda" to "youthful", "Aoede" to "breezy", "Zephyr" to "bright", "Callirrhoe" to "easy-going",
        "Autonoe" to "bright", "Despina" to "smooth", "Erinome" to "clear", "Laomedeia" to "upbeat", "Achernar" to "soft",
        "Sulafat" to "warm", "Vindemiatrix" to "gentle", "Gacrux" to "mature", "Pulcherrima" to "forward",
    )

    /** "Charon (calm, informative), Orus (firm, deep), …" for a tool description. */
    fun described(names: List<String>): String = names.joinToString(", ") { n -> CHARACTER[n]?.let { "$n ($it)" } ?: n }

    /** The setting that holds the voice of this role. */
    fun itemFor(role: String?): String = when (Persona.normalize(role)) {
        Persona.DOST -> "dost_voice"
        Persona.PARTNER -> "gf_voice"
        else -> "live_voice"
    }

    /** The exact voice name Sir asked for (any case), or null when it isn't one of the Live voices. */
    fun voiceName(raw: String, allowed: List<String> = ALL): String? {
        val t = raw.trim()
        return allowed.firstOrNull { it.equals(t, ignoreCase = true) }
            ?: allowed.firstOrNull { Regex("""\b${it.lowercase()}\b""").containsMatchIn(t.lowercase()) }
    }

    private val UP = listOf("high", "upar", "badha", "tez", "zyada", "jyada", "patli", "more", "increase", "fast", "up")
    private val DOWN = listOf("low", "deep", "neeche", "niche", "kam", "ghata", "bhaari", "bhari", "heavy", "moti", "slow", "decrease", "down")
    private val NORMAL = listOf("normal", "default", "reset", "pehle jaisa", "pehle jaisi")

    /**
     * A pitch / speed value on a scale [min]..[max] (100 = normal) from what the model passed: "120", "+10", "-15",
     * "high", "thoda bhaari", "normal". Null when nothing sensible can be read.
     */
    fun level(raw: String, current: Int, min: Int, max: Int, step: Int = 15): Int? {
        val t = raw.lowercase().trim()
        Regex("""^([+-])\s*(\d{1,3})""").find(t)?.let { m ->
            val n = m.groupValues[2].toInt()
            return (if (m.groupValues[1] == "+") current + n else current - n).coerceIn(min, max)
        }
        t.filter(Char::isDigit).toIntOrNull()?.let { n ->
            return when {
                n in min..max -> n
                n in 0..10 -> min + (max - min) * n / 10
                else -> null
            }
        }
        return when {
            NORMAL.any { it in t } -> 100.coerceIn(min, max)
            UP.any { it in t } -> (current + step).coerceIn(min, max)
            DOWN.any { it in t } -> (current - step).coerceIn(min, max)
            else -> null
        }
    }
}
