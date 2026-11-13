package com.adesh.devjarvis.brain

enum class Role(val geminiRole: String) { USER("user"), JARVIS("model") }

data class Turn(val role: Role, val text: String)

/** Short-term memory: sirf pichhle [maxTurns] exchanges (tez aur free-limit ke andar). */
class Conversation(private val maxTurns: Int) {
    private val turns = ArrayDeque<Turn>()

    @Synchronized
    fun snapshot(): List<Turn> = turns.toList()

    @Synchronized
    fun addExchange(user: String, jarvis: String) {
        turns.addLast(Turn(Role.USER, user))
        turns.addLast(Turn(Role.JARVIS, jarvis))
        while (turns.size > maxTurns * 2) turns.removeFirst()
    }

    /** Something JARVIS said on his own (a suggestion): kept so Sir's "haan, kar do" has something to refer to. */
    @Synchronized
    fun addOffer(jarvis: String) = addExchange("[Nobody spoke. You offered this yourself.]", jarvis)

    @Synchronized
    fun clear() = turns.clear()

    /** One line per turn: "U<TAB>text" / "J<TAB>text" (tabs and line breaks inside a turn become spaces). */
    @Synchronized
    fun encode(): String = turns.joinToString("\n") { t ->
        (if (t.role == Role.USER) "U" else "J") + "\t" + t.text.replace('\t', ' ').replace('\n', ' ')
    }

    /** Puts back what [encode] wrote (after an app restart), keeping the newest turns. */
    @Synchronized
    fun restore(text: String?) {
        turns.clear()
        text.orEmpty().lineSequence().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab != 1) return@mapNotNull null
            Turn(if (line[0] == 'U') Role.USER else Role.JARVIS, line.substring(2))
        }.filter { it.text.isNotBlank() }.forEach { turns.addLast(it) }
        while (turns.size > maxTurns * 2) turns.removeFirst()
    }
}
