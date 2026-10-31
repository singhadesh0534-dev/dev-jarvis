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

    // ---- Live mode: Gemini Live API (native audio in/out, real interruption) ----
    const val GEMINI_LIVE_URL =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    /** Settings > Live model se badal sakte ho. */
    const val GEMINI_LIVE_MODEL = "gemini-3.8-live"
    /** Prebuilt Live voices: Charon (deep, calm), Orus (firm), Fenrir, Puck, Kore, Aoede… */
    const val GEMINI_LIVE_VOICE = "Charon"
    const val LIVE_INPUT_RATE = 16_000
    const val LIVE_OUTPUT_RATE = 24_000
    /** Approx free daily Live minutes for the fuel gauge — check your AI Studio rate-limit page. */
    const val LIVE_DAILY_MINUTES_BUDGET = 60
    /** After a non-quota Live failure, use Classic mode for this long before trying Live again. */
    const val LIVE_RETRY_AFTER_MS = 10 * 60_000L

    // ---- Backup brain + better Hinglish ears: Groq (free tier) ----
    const val GROQ_BASE_URL = "https://api.groq.com/openai/v1"
    /** Fast free model with tool calling. Settings > Groq model se badlo (list: GET /openai/v1/models). */
    const val GROQ_CHAT_MODEL = "openai/gpt-oss-120b"
    const val GROQ_STT_MODEL = "whisper-large-v3-turbo"
    const val GROQ_DAILY_REQUEST_BUDGET = 1000

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

    // ---- Wake word (offline, Vosk) ----
    /**
     * Vosk small Indian-English model (~36 MB), first-run pe ek baar download hota hai.
     * Doosra model chahiye to https://alphacephei.com/vosk/models se naam/URL yahan badlo.
     */
    const val VOSK_MODEL_NAME = "vosk-model-small-en-in-0.4"
    const val VOSK_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"

    /**
     * Grammar phrases jo JARVIS ko jagaate hain. "dev" model ki vocabulary mein na ho to
     * Vosk use chupchaap ignore karta hai — isliye milte-julte variants bhi rakhe hain.
     */
    val WAKE_PHRASES = listOf("hello dev", "hello dave", "hey dev", "hello deb", "jarvis", "hey jarvis", "ok jarvis")
    /** JARVIS bol raha ho tab ye bolke rok sakte ho (voice barge-in). */
    val BARGE_IN_PHRASES = listOf("jarvis", "hello dev", "hello dave", "stop", "ruko", "bas", "stop jarvis", "ruko jarvis")

    const val WAKE_SENSITIVITY_DEFAULT = 0.6f

    // ---- Conversation flow ----
    /** Jawab ke baad kitni der bina wake word ke follow-up sunta hai. */
    const val FOLLOW_UP_WINDOW_MS = 8_000L
    /** User ye bole to follow-up window band — seedha standby. */
    val CONVERSATION_ENDERS = listOf(
        "bas", "bas itna", "that's all", "thats all", "that is all", "thank you", "thanks", "shukriya",
        "dhanyavaad", "bye", "ok bye", "goodbye", "good night", "chalo bye", "theek hai bas",
    )

    // ---- Service survival ----
    const val WATCHDOG_INTERVAL_MINUTES = 15L

    // ---- Links shown in Settings ----
    const val GEMINI_KEY_URL = "https://aistudio.google.com/apikey"
    const val GROQ_KEY_URL = "https://console.groq.com/keys"
}
