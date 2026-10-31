package com.adesh.devjarvis.brain

import com.adesh.devjarvis.core.DeviceSnapshot
import com.adesh.devjarvis.core.NetworkKind
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Har turn pe brain ko diya jane wala chhota "live context" block.
 * Phase 1: time, battery, network, next alarm. Baad ke phases mein city, notifications, memories.
 */
object ContextBlock {

    private val dateTimeFormat = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy, h:mm a", Locale.ENGLISH)
    private val alarmFormat = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.ENGLISH)

    fun build(
        device: DeviceSnapshot,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): String = buildString {
        appendLine("[Live context — use only if relevant, never read it out as a list]")
        appendLine("Time: ${now.format(dateTimeFormat)} (${now.zone.id})")
        if (device.batteryPercent >= 0) {
            appendLine("Battery: ${device.batteryPercent}%" + if (device.charging) ", charging" else ", not charging")
        }
        appendLine("Network: " + if (device.network == NetworkKind.NONE) "offline" else device.network.label.lowercase())
        device.nextAlarmMillis?.let {
            val alarm = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
            appendLine("Next alarm: ${alarm.format(alarmFormat)}")
        }
    }.trimEnd()
}
