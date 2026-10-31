package com.adesh.devjarvis.screen

import com.adesh.devjarvis.notifications.SensitiveFilter
import com.adesh.devjarvis.tools.FuzzyMatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Screen ka ek element (Accessibility node se bana, par Android se independent — taaki test ho sake). */
data class ScreenElement(
    val id: Int,
    val text: String,
    val description: String,
    val className: String,
    val viewId: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val password: Boolean,
    val focused: Boolean,
) {
    val label: String get() = text.ifBlank { description }.ifBlank { viewId.substringAfterLast('/').replace('_', ' ') }
    val kind: String get() = when {
        editable -> "input"
        className.endsWith("Button") || className.endsWith("ImageButton") -> "button"
        className.endsWith("CheckBox") || className.endsWith("Switch") -> "toggle"
        scrollable -> "list"
        clickable -> "item"
        else -> "text"
    }
}

data class ScreenSnapshot(val packageName: String, val appLabel: String, val elements: List<ScreenElement>)

/** Screen ko LLM ke liye chhote text mein badalna. Pure, unit-tested. */
object ScreenFormatter {
    fun compact(s: ScreenSnapshot, max: Int = 80): String = buildString {
        appendLine("App: ${s.appLabel} (${s.packageName})")
        val useful = s.elements.filter { it.label.isNotBlank() || it.editable || it.scrollable }
        useful.take(max).forEach { e ->
            append("[${e.id}] ${e.kind}")
            if (e.label.isNotBlank()) append(" \"${e.label.take(80)}\"")
            if (e.editable && e.focused) append(" (focused)")
            if (e.clickable && e.kind == "text") append(" (tappable)")
            appendLine()
        }
        if (useful.size > max) appendLine("… ${useful.size - max} more elements")
    }.trimEnd()

    /** Plain reading for "screen pe kya likha hai?" — visible text only, deduped. */
    fun readable(s: ScreenSnapshot, maxChars: Int = 1500): String =
        s.elements.map { it.text.ifBlank { it.description }.trim() }.filter { it.isNotEmpty() }.distinct()
            .joinToString(" · ").take(maxChars)
}

/** Password / OTP / payment screen pe JARVIS rukta hai. Pure, unit-tested. */
object ScreenSafety {
    private val paymentWords = Regex(
        """(?i)\b(upi pin|enter pin|mpin|cvv|card number|expiry|net ?banking|pay ₹|pay rs|proceed to pay|""" +
            """confirm payment|make payment|place order|buy now|transfer money|send money|otp|one[\s-]?time password|""" +
            """verification code|enter password|password)\b"""
    )

    sealed interface Verdict {
        data object Ok : Verdict
        data class Blocked(val reason: String) : Verdict
    }

    fun check(s: ScreenSnapshot): Verdict {
        if (s.elements.any { it.password }) return Verdict.Blocked("password field on screen")
        if (SensitiveFilter.isSensitive(s.packageName, null, null)) return Verdict.Blocked("banking / payment app")
        val texts = s.elements.joinToString(" ") { it.text + " " + it.description }
        paymentWords.find(texts)?.let { return Verdict.Blocked("sensitive screen (${it.value.lowercase()})") }
        return Verdict.Ok
    }
}

/**
 * Kaunsa tap "final" hai (bhejna/post karna → pehle Sir se poochho) aur kaunsa mana hai (paise).
 * Pure, unit-tested.
 */
object FinalActionDetector {
    enum class Kind { NORMAL, NEEDS_CONFIRMATION, FORBIDDEN }

    private val forbidden = Regex("""(?i)\b(pay|buy|purchase|order|checkout|transfer|donate|subscribe|place order|add money|recharge)\b""")
    private val final = Regex("""(?i)\b(send|post|share|publish|tweet|reply|submit|upload|delete|remove|confirm|bhejo|bhej|done)\b""")

    fun classify(label: String): Kind = when {
        forbidden.containsMatchIn(label) -> Kind.FORBIDDEN
        final.containsMatchIn(label) -> Kind.NEEDS_CONFIRMATION
        else -> Kind.NORMAL
    }
}

/** Screen agent ka ek kadam, LLM ke JSON se. Pure, unit-tested. */
sealed interface AgentAction {
    data class Tap(val id: Int) : AgentAction
    data class Type(val id: Int?, val text: String) : AgentAction
    data class Scroll(val down: Boolean) : AgentAction
    data object Back : AgentAction
    data object Home : AgentAction
    data class OpenApp(val name: String) : AgentAction
    data object Wait : AgentAction
    data class Done(val summary: String) : AgentAction
    data class Fail(val reason: String) : AgentAction
}

object AgentProtocol {
    const val MAX_STEPS = 15

    fun systemPrompt(): String = """
        You operate Sir's Android phone through accessibility to finish ONE goal. Each turn you get the goal,
        the steps already taken, and the current screen as numbered elements.
        Reply with JSON only, one action per turn:
        {"action":"tap","id":12} | {"action":"type","id":5,"text":"..."} | {"action":"scroll","direction":"down"|"up"} |
        {"action":"back"} | {"action":"home"} | {"action":"open_app","app":"WhatsApp"} | {"action":"wait"} |
        {"action":"done","note":"what was achieved, one short sentence for Sir"} | {"action":"fail","note":"why"}
        Rules:
        - Use only ids that exist on the current screen. Prefer the most direct path; open_app is faster than hunting for icons.
        - Never type passwords, PINs, OTPs, card or bank details. Never pay, buy, order, recharge or transfer money.
          If the screen asks for any of that, reply fail.
        - If the same thing didn't work twice, try another way or fail. Don't loop.
        - When the goal is achieved (e.g. the message is sent), reply done.
    """.trimIndent()

    fun userPrompt(goal: String, history: List<String>, screen: String): String = buildString {
        appendLine("Goal: $goal")
        appendLine("Steps so far: ${if (history.isEmpty()) "none" else history.takeLast(8).joinToString(" -> ")}")
        appendLine("Current screen:")
        append(screen)
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String?): AgentAction? {
        if (raw.isNullOrBlank()) return null
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = runCatching { json.parseToJsonElement(raw.substring(start, end + 1)) as JsonObject }.getOrNull() ?: return null
        fun str(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.trim()
        fun int(k: String) = o[k]?.jsonPrimitive?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
        return when (str("action")?.lowercase()) {
            "tap", "click" -> int("id")?.let { AgentAction.Tap(it) }
            "type" -> str("text")?.let { AgentAction.Type(int("id"), it) }
            "scroll" -> AgentAction.Scroll(down = str("direction")?.lowercase() != "up")
            "back" -> AgentAction.Back
            "home" -> AgentAction.Home
            "open_app" -> str("app")?.takeIf { it.isNotEmpty() }?.let { AgentAction.OpenApp(it) }
            "wait" -> AgentAction.Wait
            "done" -> AgentAction.Done(str("note").orEmpty())
            "fail" -> AgentAction.Fail(str("note").orEmpty())
            else -> null
        }
    }

    fun describe(a: AgentAction, screen: ScreenSnapshot?): String = when (a) {
        is AgentAction.Tap -> "tap '${screen?.elements?.firstOrNull { it.id == a.id }?.label ?: "#${a.id}"}'"
        is AgentAction.Type -> "type '${a.text.take(40)}'"
        is AgentAction.Scroll -> if (a.down) "scroll down" else "scroll up"
        AgentAction.Back -> "back"
        AgentAction.Home -> "home"
        is AgentAction.OpenApp -> "open ${a.name}"
        AgentAction.Wait -> "wait"
        is AgentAction.Done -> "done"
        is AgentAction.Fail -> "fail"
    }

    /** Same action 3 times in a row = stuck. */
    fun isLooping(history: List<String>): Boolean =
        history.size >= 3 && history.takeLast(3).distinct().size == 1

    /** Best element for "tap Send" style requests. */
    fun findByLabel(s: ScreenSnapshot, label: String): ScreenElement? =
        FuzzyMatcher.rank(label, s.elements.filter { it.label.isNotBlank() }, threshold = 0.7) { listOf(it.label) }
            .sortedWith(compareByDescending<FuzzyMatcher.Match<ScreenElement>> { it.score }.thenByDescending { it.item.clickable })
            .firstOrNull()?.item
}

