package com.adesh.devjarvis.core

import com.adesh.devjarvis.tools.Translit

/**
 * Seedhe-saade commands jo bina AI ke, usi pal ho jaate hain: torch, volume, Wi-Fi / Bluetooth, timer, gaana roko,
 * app kholo, home / back / lock. Na internet chahiye, na free quota kharch hota hai.
 * Jo yahan na pakda jaaye (ya do kaam ek saath hon) wo pehle ki tarah dimaag ke paas jaata hai. Pure, unit-tested.
 */
object QuickCommands {
    /** [say] = what to say when it worked; null = say the tool's own result. */
    data class Quick(val tool: String, val args: Map<String, String> = emptyMap(), val say: String? = null)

    private val ON = setOf("on", "chalu", "jalao", "jala", "jalado", "start", "shuru", "enable", "lagao")
    private val OFF = setOf("off", "band", "bandh", "bund", "bujhao", "bujha", "bujhado", "disable", "hatao")
    private val UP = setOf("badhao", "badha", "badhado", "up", "zyada", "jyada", "tez", "increase", "upar", "high", "full")
    private val DOWN = setOf("kam", "ghatao", "ghata", "down", "dheere", "dheema", "dhima", "low", "neeche", "niche", "decrease")
    private val FILLER = setOf(
        "jarvis", "hey", "hello", "dev", "please", "plz", "zara", "jara", "to", "do", "dijiye", "dena", "de", "karo", "kar", "kardo", "karna",
        "kijiye", "ko", "ka", "ki", "ke", "the", "my", "mera", "meri", "mere", "phone", "abhi", "ek", "baar", "na", "bhai", "sir", "ab",
    )
    /** More than one job, or something that needs understanding: leave it to the brain. */
    private val COMPLEX = Regex("""\b(aur|and|phir|fir|baad|then|search|dhundo|dhoondo|bhejo|message|msg|call|likho|batao kyun|kyun|kaise|remind|yaad|agar|jab|pe|par|mein|me|whatsapp ko)\b""")

    private fun normalize(raw: String): List<String> =
        Translit.toLatin(raw).lowercase().replace(Regex("""[^\p{L}\p{N} ]"""), " ").split(' ').filter { it.isNotBlank() }

    fun match(raw: String): Quick? {
        val all = normalize(raw)
        if (all.isEmpty() || all.size > 8) return null
        val text = all.joinToString(" ")
        // Time is asked in many small words, so check it before the "needs understanding" filter.
        if (Regex("""\b(time|samay|baje|baj|taim)\b""").containsMatchIn(text) && all.any { it in setOf("kya", "kitne", "kitna", "what", "batao", "bata") } &&
            all.size <= 6 && "timer" !in all && "alarm" !in all
        ) {
            return Quick("get_time")
        }
        if (COMPLEX.containsMatchIn(text)) return null
        val w = all.filter { it !in FILLER }
        if (w.isEmpty()) return null
        val set = w.toSet()
        fun any(vararg words: String) = words.any { it in set }
        val on = set.any { it in ON }
        val off = set.any { it in OFF }
        val number = w.firstNotNullOfOrNull { it.toIntOrNull() }

        // ---- torch
        if (any("torch", "flashlight", "flash", "batti", "tarch", "torchlight")) {
            return when {
                off -> Quick("toggle_flashlight", mapOf("on" to "false"), "Torch band, Sir.")
                on -> Quick("toggle_flashlight", mapOf("on" to "true"), "Torch chalu, Sir.")
                else -> Quick("toggle_flashlight", emptyMap(), "Ho gaya, Sir.")
            }
        }
        // ---- timer
        if ("timer" in set && number != null) {
            val unit = when {
                any("second", "seconds", "sec", "secand") -> 1
                any("ghante", "ghanta", "hour", "hours", "ghanton") -> 3600
                else -> 60
            }
            val name = when (unit) { 1 -> "second"; 3600 -> "ghante"; else -> "minute" }
            return Quick("set_timer", mapOf("seconds" to (number * unit).toString()), "$number $name ka timer laga diya, Sir.")
        }
        // ---- Wi-Fi, Bluetooth and friends (only with a clear on / off)
        val target = when {
            any("wifi") || "wi fi" in text -> "wifi" to "Wi-Fi"
            any("bluetooth", "blutooth", "blootooth") -> "bluetooth" to "Bluetooth"
            any("hotspot") -> "hotspot" to "Hotspot"
            any("location", "gps") -> "location" to "Location"
            any("airplane", "aeroplane", "flight") -> "airplane" to "Airplane mode"
            any("data", "internet", "net") -> "mobile_data" to "Mobile data"
            else -> null
        }
        if (target != null && (on || off) && w.size <= 4) {
            return Quick("set_toggle", mapOf("target" to target.first, "on" to on.toString()), "${target.second} ${if (on) "chalu" else "band"} kar diya, Sir.")
        }
        // ---- volume
        if (any("volume", "awaaz", "awaz", "awaj", "sound")) {
            return when {
                number != null -> Quick("set_volume", mapOf("action" to "set", "level" to number.coerceIn(0, 100).toString()), "Volume $number, Sir.")
                any("unmute") -> Quick("set_volume", mapOf("action" to "unmute"), "Awaaz wapas, Sir.")
                any("mute", "silent", "chup") || off -> Quick("set_volume", mapOf("action" to "mute"), "Mute kar diya, Sir.")
                set.any { it in UP } -> Quick("set_volume", mapOf("action" to "up"), "Volume badha diya, Sir.")
                set.any { it in DOWN } -> Quick("set_volume", mapOf("action" to "down"), "Volume kam kar diya, Sir.")
                else -> null
            }
        }
        // ---- brightness (needs a number)
        if (any("brightness", "roshni", "chamak")) {
            return when {
                number != null -> Quick("set_brightness", mapOf("level" to number.coerceIn(0, 100).toString()), "Brightness $number, Sir.")
                any("auto") -> Quick("set_brightness", mapOf("auto" to "true"), "Brightness auto pe, Sir.")
                else -> null
            }
        }
        // ---- whatever is playing
        val media = any("gaana", "gana", "gaane", "music", "song", "video", "reel")
        if (w.size <= 3) {
            when {
                any("pause", "roko", "rok", "ruko") || (media && off) || (media && any("stop")) -> return Quick("media_control", mapOf("action" to "pause"), "Rok diya, Sir.")
                any("next", "agla", "agli", "aage") && (media || w.size <= 2) -> return Quick("media_control", mapOf("action" to "next"), "Agla, Sir.")
                any("previous", "pichla", "pichhla", "pichli") && (media || w.size <= 2) -> return Quick("media_control", mapOf("action" to "previous"), "Pichhla, Sir.")
                any("resume") || (media && any("play", "chalao", "chala", "bajao") && w.size <= 2) -> return Quick("media_control", mapOf("action" to "play"), "Chala diya, Sir.")
            }
        }
        // ---- getting around
        if (w.size <= 3) {
            when {
                "screenshot" in set -> return Quick("take_screenshot", emptyMap(), "Screenshot le liya, Sir.")
                "lock" in set -> return Quick("lock_screen", emptyMap(), "Lock kar diya, Sir.")
                "home" in set && any("jao", "chalo", "go", "screen", "home") -> return Quick("go_home", emptyMap(), "Home screen, Sir.")
                any("back", "peeche", "piche", "wapas") && !any("home") -> return Quick("go_back", emptyMap(), "Peeche aa gaya, Sir.")
            }
        }
        // ---- open an app: "YouTube kholo", "open camera"
        val opener = setOf("kholo", "khol", "kholdo", "open", "chalao", "launch")
        val name = when {
            w.first() in opener -> w.drop(1)
            w.last() in opener -> w.dropLast(1)
            else -> return null
        }
        if (name.isEmpty() || name.size > 3 || name.any { it in opener }) return null
        val app = name.joinToString(" ")
        return Quick("open_app", mapOf("name" to app), "${app.replaceFirstChar { it.uppercase() }} khol diya, Sir.")
    }
}
