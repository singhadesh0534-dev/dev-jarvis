package com.adesh.devjarvis.core

/**
 * Ek hi jagah saare model names, endpoints aur limits.
 * Google/Groq naye model laate rehte hain — yahan naam badlo, baaki code chhoone ki zarurat nahi.
 * (Gemini model ko Settings screen se bhi override kar sakte ho.)
 *
 * Last checked: Oct 2026, against the official google-gemini/cookbook quickstarts.
 */
object JarvisConfig {

    // ---- Brain: Gemini (primary) ----
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    /** Default free-tier Flash model. Settings > Model se badal sakte ho. */
    const val GEMINI_MODEL = "gemini-3.8-flash"
    /**
     * Gemini 3.x "thinking" level. "minimal" = fastest first word (voice ke liye best).
     * Agar model ye field reject kare to client automatically bina iske retry karta hai.
     * Blank string = field bhejo hi mat.
     */
    const val GEMINI_THINKING_LEVEL = "minimal"
    const val GEMINI_TEMPERATURE = 0.8
    const val GEMINI_MAX_OUTPUT_TOKENS = 1024

    /**
     * Fuel gauge ke liye approx daily request budget. Free-tier limits model ke hisaab se
     * badalti hain — apne AI Studio "Rate limits" page pe dekh ke yahan set karo.
     */
    const val GEMINI_DAILY_REQUEST_BUDGET = 1000

    // ---- Phase 4 (abhi use nahi ho rahe, bas yahan ready hain) ----
    const val GEMINI_LIVE_MODEL = "gemini-3.8-live"
    const val GROQ_BASE_URL = "https://api.groq.com/openai/v1"
    const val GROQ_CHAT_MODEL = "llama-3.3-70b-versatile"
    const val GROQ_STT_MODEL = "whisper-large-v3-turbo"

    // ---- Conversation ----
    /** Kitne pichhle turns (user + jarvis = 2 messages) brain ko bheje jaate hain. */
    const val HISTORY_TURNS = 20

    // ---- Voice ----
    const val STT_LANGUAGE_DEFAULT = "en-IN"
    val STT_LANGUAGES = listOf("en-IN", "hi-IN", "en-GB", "en-US")
    const val TTS_PITCH_DEFAULT = 0.85f
    const val TTS_RATE_DEFAULT = 0.95f

    /**
     * Google TTS ke deep male English voices, pehli wali sabse pasand.
     * Android voice ka gender nahi batata, isliye naam se match karte hain.
     * Settings mein manually bhi voice chun sakte ho.
     */
    val PREFERRED_VOICE_PREFIXES = listOf(
        "en-gb-x-rjs", "en-gb-x-gbd", "en-gb-x-gbb",
        "en-in-x-ene", "en-in-x-end",
    )

    // ---- Links shown in Settings ----
    const val GEMINI_KEY_URL = "https://aistudio.google.com/apikey"
    const val GROQ_KEY_URL = "https://console.groq.com/keys"
}
