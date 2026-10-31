package com.adesh.devjarvis.notifications

/**
 * OTP, password, bank/payment notifications ko kabhi bolna, dikhana ya LLM ko bhejna nahi.
 * Pure, unit-tested.
 */
object SensitiveFilter {

    /** Banks, wallets, UPI and card apps (package prefixes). */
    private val sensitivePackages = listOf(
        "com.google.android.apps.nbu.paisa", "net.one97.paytm", "com.phonepe", "in.org.npci", "com.mobikwik",
        "com.amazon.mShop.android.shopping.pay", "com.freecharge", "com.sbi", "com.csam.icici", "com.snapwork.hdfc",
        "com.hdfcbank", "com.axis", "com.msf.kbank", "com.kotak", "com.bankofbaroda", "com.pnb", "com.canarabank",
        "com.unionbank", "com.idbi", "com.yesbank", "com.indusind", "com.cred", "com.dreamplug", "com.whatsapp.payments",
        "com.paypal", "com.google.android.apps.walletnfcrel", "com.authy", "com.google.android.apps.authenticator",
        "com.microsoft.msa.authenticator",
    )

    private val sensitiveText = Regex(
        """(?i)\b(otp|one[\s-]?time[\s-]?password|verification code|security code|login code|auth(entication)? code|""" +
            """password|passcode|\bpin\b|cvv|debited|credited|a/c|acct|account no|upi|transaction|txn|bank|balance|""" +
            """card ending|net ?banking|do not share|don't share|kisi ke saath share na)\b|""" +
            """\b(code|otp)\s*(is|:)?\s*\d{4,8}\b|\b\d{4,8}\s+is your\b|(₹|rs\.?|inr)\s*\d"""
    )

    fun isSensitive(packageName: String, title: String?, text: String?): Boolean {
        if (sensitivePackages.any { packageName.startsWith(it) }) return true
        if (packageName.contains("bank", ignoreCase = true) || packageName.contains("wallet", ignoreCase = true)) return true
        val all = listOfNotNull(title, text).joinToString(" ")
        return sensitiveText.containsMatchIn(all)
    }
}
