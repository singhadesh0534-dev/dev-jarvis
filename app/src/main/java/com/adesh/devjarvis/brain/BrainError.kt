package com.adesh.devjarvis.brain

/** Brain ke saare failure cases — har ek ka JARVIS-style spoken jawab ViewModel deta hai. */
sealed class BrainError(message: String) : Exception(message) {
    class MissingKey : BrainError("Gemini API key not set")
    class NoNetwork : BrainError("No internet")
    class RateLimited(detail: String = "") : BrainError("Free-tier quota hit (HTTP 429) $detail")
    class InvalidKey : BrainError("API key rejected")
    class Blocked(reason: String) : BrainError("Response blocked: $reason")
    class Server(val code: Int, detail: String) : BrainError("HTTP $code: $detail")
}
