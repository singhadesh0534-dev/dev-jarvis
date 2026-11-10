package com.adesh.devjarvis.screen

import com.adesh.devjarvis.tools.FuzzyMatcher

/**
 * "Ek baar dikha do": Sir ne haath se jo kiya uske kadam. kind = open (app = package) | tap | long_press | type.
 * Element ko dobara dhoondhne ke liye uska label aur view-id rakhe jaate hain, jagah (x, y) nahi — taaki
 * screen thodi badal jaaye to bhi kaam chale. Pure, unit-tested.
 */
data class MacroStep(val kind: String, val app: String = "", val label: String = "", val viewId: String = "", val text: String = "")

object MacroLogic {
    const val MAX_STEPS = 40

    /** Add one recorded event, dropping the noise a real recording is full of. */
    fun add(steps: List<MacroStep>, step: MacroStep): List<MacroStep> {
        if (steps.size >= MAX_STEPS) return steps
        val last = steps.lastOrNull()
        return when {
            // Typing fires an event per letter: keep only the final text of that box.
            step.kind == "type" && last?.kind == "type" && last.viewId == step.viewId && last.app == step.app -> steps.dropLast(1) + step
            step.kind == "type" && step.text.isBlank() -> steps
            step.kind == "open" && last?.kind == "open" && last.app == step.app -> steps
            // The app was already opened by the step before (dialogs and new screens also report a window change).
            step.kind == "open" && steps.any { it.app == step.app } && last?.app == step.app -> steps
            (step.kind == "tap" || step.kind == "long_press") && step.label.isBlank() && step.viewId.isBlank() -> steps
            else -> steps + step
        }
    }

    fun describe(steps: List<MacroStep>, appName: (String) -> String = { it.substringAfterLast('.') }): String =
        steps.joinToString(" → ") { s ->
            when (s.kind) {
                "open" -> "open ${appName(s.app)}"
                "type" -> "type '${s.text.take(30)}'"
                "long_press" -> "hold '${s.label.ifBlank { s.viewId.substringAfterLast('/') }}'"
                else -> "tap '${s.label.ifBlank { s.viewId.substringAfterLast('/') }}'"
            }
        }

    /** The element of the current screen that [step] was done on, or null if it isn't there (yet). */
    fun locate(snap: ScreenSnapshot, step: MacroStep): ScreenElement? {
        if (step.kind == "type") {
            return snap.elements.firstOrNull { it.editable && step.viewId.isNotEmpty() && it.viewId == step.viewId }
                ?: snap.elements.firstOrNull { it.editable && it.focused }
                ?: snap.elements.firstOrNull { it.editable }
        }
        val sameId = if (step.viewId.isEmpty()) emptyList() else snap.elements.filter { it.viewId == step.viewId }
        if (sameId.size == 1) return sameId[0]
        if (step.label.isNotBlank()) {
            // Rows of a list share one id: the label says which row.
            val pool = sameId.ifEmpty { snap.elements }.filter { it.label.isNotBlank() }
            FuzzyMatcher.rank(step.label, pool, threshold = 0.75) { listOf(it.label) }.firstOrNull()?.let { return it.item }
            return null
        }
        return sameId.firstOrNull()
    }
}
