package com.adesh.devjarvis.safety

import java.util.Locale

/** Emergency SOS ka text aur contact list. Pure, unit-tested. */
object Sos {
    const val MAX_CONTACTS = 5

    /** "Mummy, 98765 43210; Papa" -> [Mummy, 98765 43210, Papa] */
    fun contacts(raw: String): List<String> =
        raw.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(MAX_CONTACTS)

    /** One SMS-sized message. Location is optional: without it the text says so instead of guessing. */
    fun message(lat: Double?, lon: Double?, accuracyMeters: Float?, timeText: String, note: String? = null): String = buildString {
        append("EMERGENCY: mujhe madad chahiye / I need help.")
        note?.trim()?.takeIf { it.isNotEmpty() }?.let { append(' ').append(it.take(120)) }
        if (lat != null && lon != null) {
            append(" Meri location: https://maps.google.com/?q=")
            append(String.format(Locale.US, "%.5f,%.5f", lat, lon))
            accuracyMeters?.takeIf { it > 0 }?.let { append(" (+/- ${it.toInt()} m)") }
        } else {
            append(" Location nahi mil payi - please call me.")
        }
        append(" Time: ").append(timeText).append('.')
    }
}
