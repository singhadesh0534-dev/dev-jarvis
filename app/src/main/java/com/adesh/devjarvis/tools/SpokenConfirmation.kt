package com.adesh.devjarvis.tools

/** Thrown by ToolContext.confirm in Live mode: JARVIS must ask Sir in his own voice, then call the tool again. */
class ConfirmationNeeded(val question: String, val again: Boolean) : Exception("confirmation needed")

/**
 * Live mode confirmation, in JARVIS's own Live voice (no second TTS voice):
 *  1. tool asks -> [request] returns Ask -> the model speaks the question,
 *  2. Sir answers; the app (not the model) reads his words from the transcript,
 *  3. the model calls the tool again -> [request] returns Answer(yes/no).
 * Only words Sir said AFTER JARVIS asked count, so the original command ("...call karo") can't confirm itself.
 * Pure, unit-tested.
 */
class SpokenConfirmation(private val validForMs: Long = 120_000, private val minGapMs: Long = 1_500) {

    sealed interface Outcome {
        data class Ask(val question: String, val again: Boolean) : Outcome
        data class Answer(val yes: Boolean) : Outcome
    }

    private var question: String? = null
    private var askedAt = 0L
    private var modelSpoke = false
    private val heard = StringBuilder()
    private var tapped: Boolean? = null

    val pendingQuestion: String? get() = question

    @Synchronized
    fun request(q: String, now: Long): Outcome {
        if (question == q && now - askedAt < validForMs) {
            val answer = tapped ?: if (modelSpoke && now - askedAt >= minGapMs) {
                when (ConfirmationParser.parse(heard.toString())) {
                    ConfirmationParser.Answer.YES -> true
                    ConfirmationParser.Answer.NO -> false
                    ConfirmationParser.Answer.UNCLEAR -> null
                }
            } else null
            if (answer != null) {
                clear()
                return Outcome.Answer(answer)
            }
            val saidSomething = heard.isNotBlank()
            heard.clear()
            return Outcome.Ask(q, again = saidSomething)
        }
        question = q
        askedAt = now
        modelSpoke = false
        heard.clear()
        tapped = null
        return Outcome.Ask(q, again = false)
    }

    /** JARVIS started speaking after the request (i.e. he is asking the question). */
    @Synchronized
    fun onModelSpoke() {
        if (question != null) modelSpoke = true
    }

    @Synchronized
    fun onUserSaid(text: String) {
        if (question != null && modelSpoke) heard.append(' ').append(text)
    }

    /** YES / NO button on the HUD. */
    @Synchronized
    fun onTap(yes: Boolean): Boolean {
        if (question == null) return false
        tapped = yes
        return true
    }

    @Synchronized
    fun clear() {
        question = null
        modelSpoke = false
        heard.clear()
        tapped = null
    }
}
