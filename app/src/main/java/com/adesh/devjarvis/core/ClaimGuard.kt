package com.adesh.devjarvis.core

/**
 * "Ho gaya" bina kuch kiye: JARVIS ne kaha kaam ho gaya, par is baar koi tool chala hi nahi — matlab phone pe
 * kuch nahi badla. Aisa pakad ke use sudharne ko kaha jaata hai (ek baar). Pure, unit-tested.
 */
object ClaimGuard {
    /** Sir asked for something to be DONE (not a question or chat). Roman Hinglish and Hindi script. */
    private val ACTION = Regex(
        """(?i)\b(karo|kar\s?do|kardo|kar\s?de|badlo|badal\s?do|change|set|lagao|laga\s?do|bhejo|bhej\s?do|on|off|band|chalu|kholo|open|call|""" +
            """play|chalao|chala\s?do|delete|hatao|mitao|save|install|uninstall|increase|decrease|badhao|ghatao|kam|turn)\b|""" +
            """करो|कर दो|करदो|बदलो|बदल दो|लगाओ|लगा दो|भेजो|भेज दो|बंद|चालू|खोलो|चलाओ|हटाओ|बढ़ाओ|घटाओ""",
    )

    /** The reply says the job is finished. */
    private val CLAIM = Regex(
        """(?i)\b(ho\s?gaya|ho\s?gayi|ho\s?gaye|kar\s?diya|kar\s?di|kar\s?diye|badal\s?diya|badal\s?di|change\s?kar\s?(diya|di)|changed|""" +
            """done|laga\s?diya|laga\s?di|bhej\s?diya|bhej\s?di|khol\s?diya|khol\s?di|chala\s?diya|chalu\s?kar|band\s?kar\s?(diya|di)|""" +
            """switched|turned\s?(on|off)|updated|set\s?kar\s?(diya|di)|consider it done)\b|""" +
            """हो गया|हो गई|कर दिया|कर दी|बदल दिया|बदल दी|लगा दिया|भेज दिया|खोल दिया|चला दिया|बंद कर दिया|चालू कर दिया""",
    )

    /** True when the reply claims a finished action that no tool performed in this turn. */
    fun falseClaim(userText: String, reply: String, toolsSucceeded: Int, appActed: Boolean = false): Boolean =
        toolsSucceeded == 0 && !appActed && ACTION.containsMatchIn(userText) && CLAIM.containsMatchIn(reply)

    /** What the brain is told when it happened. */
    const val CORRECTION =
        "[CHECK: You just told Sir it is done, but no tool ran in this turn, so NOTHING actually changed on the phone. " +
            "Do it now with the right tool (your own settings and voices: jarvis_settings; phone switches: set_toggle; any " +
            "other phone setting: phone_setting; apps: open_app), then confirm from the tool result. If it cannot be done, " +
            "tell Sir honestly in one line that it is not done and why. Never say done without a tool result.]"
}
