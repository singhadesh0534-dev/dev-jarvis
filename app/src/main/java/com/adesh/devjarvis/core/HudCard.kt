package com.adesh.devjarvis.core

/** HUD pe dikhne wala result card: mausam, reminder, kharcha, call... Pure, unit-tested. */
data class HudCard(val id: Long, val title: String, val body: String, val ok: Boolean)

object HudCards {
    /** Hands-on-the-screen steps and mode switches: too small or too frequent for a card. */
    private val SKIP = setOf(
        "get_time", "tap", "long_press", "type_text", "scroll", "swipe", "go_back", "go_home", "read_screen",
        "interpreter_mode", "live_camera", "screen_watch", "remember", "forget", "use_skill",
    )
    private val TITLES = mapOf(
        "get_weather" to "WEATHER", "web_answer" to "WEB", "calculate" to "CALCULATOR", "get_schedule" to "SCHEDULE",
        "set_reminder" to "REMINDER", "remind_at_place" to "PLACE REMINDER", "set_alarm" to "ALARM", "set_timer" to "TIMER",
        "make_call" to "CALL", "send_sms" to "SMS", "send_whatsapp" to "WHATSAPP", "emergency_sos" to "EMERGENCY SOS",
        "add_expense" to "EXPENSE", "expense_summary" to "EXPENSES", "add_note" to "NOTE", "get_notes" to "NOTES",
        "list_add" to "LIST", "list_get" to "LIST", "device_status" to "DEVICE", "read_notifications" to "NOTIFICATIONS",
        "look" to "CAMERA", "look_at_screen" to "SCREEN", "do_task_on_screen" to "SCREEN TASK", "save_routine" to "ROUTINE",
    )
    const val MAX_BODY = 320

    fun forTool(id: Long, name: String, ok: Boolean, message: String): HudCard? {
        if (name in SKIP) return null
        val body = message.trim().replace(Regex("\\s+"), " ")
        if (body.isEmpty()) return null
        val title = TITLES[name] ?: name.replace('_', ' ').uppercase()
        return HudCard(id, title, if (body.length > MAX_BODY) body.take(MAX_BODY - 1).trimEnd() + "…" else body, ok)
    }
}
