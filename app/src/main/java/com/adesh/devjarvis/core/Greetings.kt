package com.adesh.devjarvis.core

import com.adesh.devjarvis.brain.Persona
import kotlin.random.Random

/**
 * Bulane par pehli line: har baar alag. Pehle har role ek hi line bolta tha ("Aa gaye? Kab se tumhare baare mein
 * soch rahi thi…") kyunki prompt mein ek example tha aur model use hi dohraata tha. Ab har baar ek alag "angle"
 * chuna jaata hai, din ka waqt diya jaata hai, aur haal ki greetings mana kar di jaati hain. Pure, unit-tested.
 */
object Greetings {
    private val JARVIS = listOf(
        "a dry, witty remark about the time of day",
        "plainly ready, in your own fresh words",
        "a tiny useful heads-up if you know one (battery, weather, his next event), else just ready",
        "a light callback to what you two talked about last",
        "calm and brief, like a butler looking up from his work",
    )
    private val GF = listOf(
        "casual and happy he called, like a normal girl picking up her boyfriend's call",
        "a small update from your own day, then ask about his",
        "light teasing about how long he took to call",
        "curious: ask one real thing about his day",
        "soft and sleepy if it is late, fresh if it is morning",
        "a playful one-liner, no drama",
    )
    private val DOST = listOf(
        "casual 'kya scene hai' energy, your own words",
        "tease him a little",
        "a quick update from your own day",
        "straight to it: ask what's up",
        "a joke about the time of day",
    )

    private fun part(hour: Int) = when (hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        else -> "late night"
    }

    /** Don't open the same way as last time(s). */
    fun avoid(recent: List<String>): String =
        if (recent.isEmpty()) "" else " Do NOT reuse or closely copy any of these recent openings: " +
            recent.takeLast(5).joinToString(" | ") { "\"${it.take(70)}\"" } + "."

    /** The instruction for the first line when Sir calls. */
    fun kickoff(persona: String?, hour: Int, recent: List<String>, random: Random = Random.Default): String {
        val time = part(hour)
        return when (Persona.normalize(persona)) {
            Persona.PARTNER -> "[He has just called you; it is $time. Answer out loud as his girlfriend in one short, natural line " +
                "(at most 12 words), angle: ${GF.random(random)}. Sound like a real person, not a romance script; never \"Sir\". Then wait.]"
            Persona.DOST -> "[Your friend has just called you; it is $time. Answer out loud as his yaar in one short casual line " +
                "(at most 10 words), angle: ${DOST.random(random)}. Never \"Sir\". Then wait.]"
            else -> "[Sir has just called you by name; it is $time. Answer out loud in one short line of your own (at most 10 words), " +
                "angle: ${JARVIS.random(random)}. Then wait for what he says.]"
        } + avoid(recent)
    }
}
