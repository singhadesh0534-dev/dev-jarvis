package com.adesh.devjarvis.tools

/** Number helpers. Pure, unit-tested. */
object PhoneNumbers {
    /**
     * Sir said a number, not a name: "101", "1 0 8", "+91 98xxx". Short numbers (police 100, fire 101,
     * ambulance 102/108, 112, helplines like 1091 / 1930) count too: "101" was searched as a contact name.
     */
    fun asDialable(query: String): String? {
        val t = query.trim()
        if (t.isEmpty() || t.any { !(it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')') }) return null
        val digits = t.filter { it.isDigit() || it == '+' }
        return digits.takeIf { it.count(Char::isDigit) >= 3 }
    }

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
