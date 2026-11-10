package com.adesh.devjarvis.life

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** life.json in the app's private storage. Small, so read-modify-write is fine. */
@Singleton
class LifeStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "life.json")
    private var cache: LifeData? = null

    @Synchronized
    fun get(): LifeData = cache ?: run {
        val loaded = if (file.exists()) LifeCodec.decode(runCatching { file.readText() }.getOrNull()) else seed()
        cache = loaded
        loaded
    }

    @Synchronized
    fun update(change: (LifeData) -> LifeData): LifeData {
        val next = change(get())
        cache = next
        runCatching { file.writeText(LifeCodec.encode(next)) }
        return next
    }

    /** Two starter routines so "good night" works out of the box; Sir can change or delete them by voice. */
    private fun seed() = LifeData(
        routines = mapOf(
            "good night" to "set media volume to 20; set brightness to 10 if allowed; tell Sir tomorrow's first alarm, reminders and events; wish him good night in one line",
            "good morning" to "tell Sir the weather, today's events and reminders, and battery level, in three short sentences",
        ),
    )
}
