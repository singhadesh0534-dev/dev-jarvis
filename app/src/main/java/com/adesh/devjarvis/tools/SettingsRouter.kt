package com.adesh.devjarvis.tools

/**
 * Koi bhi phone setting, naam se: "screen timeout 2 minute", "font bada karo", "developer options kholo",
 * "keyboard vibration band". Teen raaste, is kram mein:
 *  1. DIRECT — jo Android app ko khud likhne deta hai (screen timeout, font size, touch sounds...).
 *  2. PAGE — us setting ka apna page (Android ke settings "deep link").
 *  3. SEARCH — Settings ka apna search: naam likho, pehla result kholo, switch dhoondho.
 * Factory reset / erase jaisi cheezein kabhi nahi. Pure, unit-tested.
 */
object SettingsRouter {
    enum class Direct { SCREEN_TIMEOUT, FONT_SCALE, TOUCH_SOUNDS, HAPTICS, DIAL_TONES, LOCK_SOUND }

    data class Route(
        val direct: Direct?,
        /** Settings intents to try, best first. */
        val pages: List<String>,
        /** Words the right row / switch carries (for the search and the switch finder). */
        val words: List<String>,
        val forbidden: Boolean = false,
    )

    private val FORBIDDEN = Regex("""\b(factory|reset phone|erase|wipe|format phone|reset all|reset options)\b""")

    private data class Entry(val keys: List<String>, val pages: List<String>, val words: List<String>, val direct: Direct? = null)

    private val ENTRIES = listOf(
        Entry(listOf("screen timeout", "sleep", "screen off", "auto lock", "display timeout"), listOf("android.settings.DISPLAY_SETTINGS"), listOf("sleep", "screen timeout", "screen off"), Direct.SCREEN_TIMEOUT),
        Entry(listOf("font size", "text size", "font", "text bada", "akshar"), listOf("android.settings.DISPLAY_SETTINGS"), listOf("font size", "text size", "font"), Direct.FONT_SCALE),
        Entry(listOf("touch sound", "tap sound", "click sound"), listOf("android.settings.SOUND_SETTINGS"), listOf("touch sound", "tap sound"), Direct.TOUCH_SOUNDS),
        Entry(listOf("haptic", "touch vibration", "keyboard vibration", "vibration feedback"), listOf("android.settings.SOUND_SETTINGS"), listOf("haptic", "touch vibration", "vibration feedback"), Direct.HAPTICS),
        Entry(listOf("dial pad tone", "dialpad tone", "keypad tone"), listOf("android.settings.SOUND_SETTINGS"), listOf("dial pad", "keypad tone"), Direct.DIAL_TONES),
        Entry(listOf("screen lock sound", "lock sound"), listOf("android.settings.SOUND_SETTINGS"), listOf("screen lock sound", "lock sound"), Direct.LOCK_SOUND),
        Entry(listOf("display size", "screen zoom"), listOf("android.settings.DISPLAY_SETTINGS"), listOf("display size", "screen zoom")),
        Entry(listOf("refresh rate", "120hz", "90hz", "smooth display"), listOf("android.settings.DISPLAY_SETTINGS"), listOf("refresh rate", "smooth")),
        Entry(listOf("wallpaper"), listOf("android.intent.action.SET_WALLPAPER"), listOf("wallpaper")),
        Entry(listOf("ringtone"), listOf("android.settings.SOUND_SETTINGS"), listOf("ringtone", "phone ringtone")),
        Entry(listOf("notification sound"), listOf("android.settings.SOUND_SETTINGS"), listOf("notification sound", "default notification")),
        Entry(listOf("alarm sound"), listOf("android.settings.SOUND_SETTINGS"), listOf("alarm sound")),
        Entry(listOf("language", "bhasha"), listOf("android.settings.LOCALE_SETTINGS"), listOf("language")),
        Entry(listOf("keyboard"), listOf("android.settings.INPUT_METHOD_SETTINGS"), listOf("keyboard", "on-screen keyboard")),
        Entry(listOf("date", "time", "time zone", "24 hour"), listOf("android.settings.DATE_SETTINGS"), listOf("date", "time", "24-hour", "time zone")),
        Entry(listOf("developer", "usb debugging"), listOf("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"), listOf("developer options", "usb debugging")),
        Entry(listOf("about phone", "phone info", "model number", "android version"), listOf("android.settings.DEVICE_INFO_SETTINGS"), listOf("about phone", "android version")),
        Entry(listOf("storage"), listOf("android.settings.INTERNAL_STORAGE_SETTINGS"), listOf("storage")),
        Entry(listOf("battery", "battery usage", "battery percentage"), listOf("android.intent.action.POWER_USAGE_SUMMARY"), listOf("battery percentage", "battery")),
        Entry(listOf("app notification", "notifications"), listOf("android.settings.NOTIFICATION_SETTINGS"), listOf("notifications")),
        Entry(listOf("default apps", "default browser", "default launcher"), listOf("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"), listOf("default apps")),
        Entry(listOf("app permissions", "permission manager"), listOf("android.intent.action.MANAGE_PERMISSIONS"), listOf("permission manager", "permissions")),
        Entry(listOf("apps", "installed apps", "app info"), listOf("android.settings.APPLICATION_SETTINGS", "android.settings.MANAGE_APPLICATIONS_SETTINGS"), listOf("apps")),
        Entry(listOf("accessibility"), listOf("android.settings.ACCESSIBILITY_SETTINGS"), listOf("accessibility")),
        Entry(listOf("privacy"), listOf("android.settings.PRIVACY_SETTINGS"), listOf("privacy")),
        Entry(listOf("security", "screen lock", "fingerprint", "face unlock"), listOf("android.settings.SECURITY_SETTINGS"), listOf("security", "screen lock")),
        Entry(listOf("vpn"), listOf("android.settings.VPN_SETTINGS"), listOf("vpn")),
        Entry(listOf("private dns"), listOf("android.settings.WIRELESS_SETTINGS"), listOf("private dns")),
        Entry(listOf("sim", "sim card", "dual sim", "preferred network", "5g", "network mode", "volte"), listOf("android.settings.NETWORK_OPERATOR_SETTINGS", "android.settings.WIRELESS_SETTINGS"), listOf("sim", "preferred network", "5g", "volte")),
        Entry(listOf("data usage", "data limit"), listOf("android.settings.DATA_USAGE_SETTINGS"), listOf("data usage", "data limit")),
        Entry(listOf("cast", "screen mirroring", "screen cast"), listOf("android.settings.CAST_SETTINGS"), listOf("cast", "screen mirroring")),
        Entry(listOf("printing", "printer"), listOf("android.settings.ACTION_PRINT_SETTINGS"), listOf("printing")),
        Entry(listOf("accounts", "google account", "sync"), listOf("android.settings.SYNC_SETTINGS"), listOf("accounts", "sync")),
        Entry(listOf("system update", "software update"), listOf("android.settings.SYSTEM_UPDATE_SETTINGS"), listOf("system update", "software update")),
        Entry(listOf("captions", "live caption"), listOf("android.settings.CAPTIONING_SETTINGS"), listOf("caption")),
        Entry(listOf("notification access", "special access"), listOf("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), listOf("notification access")),
        Entry(listOf("night light", "eye comfort", "blue light"), listOf("android.settings.NIGHT_DISPLAY_SETTINGS", "android.settings.DISPLAY_SETTINGS"), listOf("night light", "eye comfort", "eye care")),
    )

    fun route(setting: String): Route {
        val t = setting.lowercase().trim()
        if (FORBIDDEN.containsMatchIn(t)) return Route(null, emptyList(), emptyList(), forbidden = true)
        val e = ENTRIES.firstOrNull { entry -> entry.keys.any { it in t } }
        val own = t.replace(Regex("""\b(on|off|karo|kar|do|band|chalu|setting|settings|ka|ki|ke|ko|me|mein|kholo|open)\b"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
        val words = ((e?.words ?: emptyList()) + listOf(own).filter { it.length >= 3 }).distinct()
        return Route(e?.direct, e?.pages.orEmpty(), words)
    }

    /** "2 minute", "30 sec", "1 ghanta", "never" -> milliseconds for the screen timeout; null if not understood. */
    fun timeoutMs(value: String?): Int? {
        val v = value.orEmpty().lowercase()
        if (v.isBlank()) return null
        if ("never" in v || "kabhi nahi" in v) return Int.MAX_VALUE
        val n = Regex("""\d+(?:\.\d+)?""").find(v)?.value?.toDoubleOrNull() ?: return null
        val ms = when {
            Regex("""\b(h|hr|hour|hours|ghanta|ghante)\b""").containsMatchIn(v) -> n * 3_600_000
            Regex("""\b(s|sec|secs|second|seconds|second)\b""").containsMatchIn(v) -> n * 1_000
            else -> n * 60_000 // minutes by default
        }
        return ms.toInt().coerceIn(15_000, 30 * 60_000)
    }

    /** "bada / large / 1.3" -> font scale. */
    fun fontScale(value: String?): Float? {
        val v = value.orEmpty().lowercase()
        Regex("""\d+(?:\.\d+)?""").find(v)?.value?.toFloatOrNull()?.let { n ->
            return (if (n > 3f) n / 100f else n).coerceIn(0.85f, 1.6f)
        }
        return when {
            listOf("sabse bada", "largest", "huge", "bahut bada").any { it in v } -> 1.5f
            listOf("bada", "large", "big", "badhao", "increase").any { it in v } -> 1.3f
            listOf("chhota", "small", "kam", "decrease", "ghatao").any { it in v } -> 0.9f
            listOf("normal", "default").any { it in v } -> 1.0f
            else -> null
        }
    }
}
