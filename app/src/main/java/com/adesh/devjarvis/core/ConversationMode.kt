package com.adesh.devjarvis.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Special conversation modes a tool can switch on (the session reads them).
 * keepOpen: don't close the conversation after the usual 8 s of silence (interpreter, live vision, screen watch).
 */
@Singleton
class ConversationMode @Inject constructor() {
    @Volatile var interpreter: Boolean = false
    @Volatile var liveVision: Boolean = false
    @Volatile var screenWatch: Boolean = false
    /** What Sir said last (a tool may need to check he really asked for it). */
    @Volatile var lastUserWords: String = ""

    val keepOpen: Boolean get() = interpreter || liveVision || screenWatch

    /** How long to wait in silence before closing, per mode. */
    fun followUpMs(defaultMs: Long): Long = if (keepOpen) 120_000L else defaultMs

    fun reset() {
        interpreter = false
        liveVision = false
        screenWatch = false
    }
}
