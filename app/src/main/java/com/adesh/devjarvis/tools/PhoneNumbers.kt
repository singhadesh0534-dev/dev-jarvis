package com.adesh.devjarvis.tools

/** Number helpers. Pure, unit-tested. */
object PhoneNumbers {
    /** WhatsApp needs the country code without '+'. Bare 10-digit Indian numbers get 91. */
    fun forWhatsApp(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            raw.trim().startsWith("+") -> digits
            digits.length == 10 -> "91$digits"
            digits.length == 11 && digits.startsWith("0") -> "91${digits.drop(1)}"
            else -> digits
        }
    }
}
