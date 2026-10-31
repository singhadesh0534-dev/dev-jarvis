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

    @Synchronized
    fun clear() = turns.clear()
}
