package com.adesh.devjarvis.proactive

/**
 * JARVIS khud se kab bole? Pure function, unit-tested.
 *  - Call / ring chal raha: kabhi nahi.
 *  - DND on: sirf notification.
 *  - Raat (quiet hours, default 23:00–07:00): sirf notification — Sir ka khud set kiya reminder chhod ke.
 *  - Silent/vibrate mode: tabhi bolo jab headphones lage hon (awaaz sirf Sir sunenge).
 *  - JARVIS pehle se baat kar raha ho: notification, beech mein nahi bolna.
 */
object ProactivePolicy {

    enum class Kind { REMINDER, BRIEFING, BATTERY, NOTIFICATION }
    enum class Decision { SPEAK, NOTIFY }

    data class Situation(
        val minuteOfDay: Int,
        val quietStartMinute: Int,
        val quietEndMinute: Int,
        val dnd: Boolean,
        val ringerSilent: Boolean,
        val headphones: Boolean,
        val inCall: Boolean,
        val busy: Boolean,
        val alwaysOnRunning: Boolean,
    )

    fun inQuietHours(minute: Int, start: Int, end: Int): Boolean =
        if (start == end) false else if (start < end) minute in start until end else minute >= start || minute < end

    fun decide(kind: Kind, s: Situation): Decision = when {
        !s.alwaysOnRunning -> Decision.NOTIFY
        s.inCall -> Decision.NOTIFY
        s.dnd -> Decision.NOTIFY
        s.busy -> Decision.NOTIFY
        kind != Kind.REMINDER && inQuietHours(s.minuteOfDay, s.quietStartMinute, s.quietEndMinute) -> Decision.NOTIFY
        s.ringerSilent && !s.headphones -> Decision.NOTIFY
        else -> Decision.SPEAK
    }
}
