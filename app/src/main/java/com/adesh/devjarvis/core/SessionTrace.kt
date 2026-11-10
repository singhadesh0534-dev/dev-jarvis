package com.adesh.devjarvis.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Flight recorder": pichhle ~200 events (jaagna, Live connect, pehli awaaz kitni der mein, fallback, tools).
 * Sirf phone ki memory mein; Settings > Diagnostics se dekh/share kar sakte ho. Baat-cheet ka text isme nahi jaata.
 */
object SessionTrace {
    private const val MAX = 200
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.ENGLISH)

    @Synchronized
    fun add(event: String) {
        lines.addLast("${fmt.format(Date())}  $event")
        while (lines.size > MAX) lines.removeFirst()
    }

    @Synchronized
    fun dump(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() = lines.clear()
}
