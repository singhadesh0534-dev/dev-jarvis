package com.adesh.devjarvis.ui.hud

import android.content.Context
import android.net.Uri
import com.adesh.devjarvis.core.AgentMood
import com.adesh.devjarvis.core.JarvisState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The robot's real movements: short video clips of the SAME robot (made from Sir's picture in any image-to-video
 * app), one per situation. JARVIS plays the right one by itself: working while idle or thinking, turning to listen,
 * talking with its hands while speaking, a wave when a conversation starts, and mood clips after a happy / sad reply.
 * Clips built into the app (res/raw/agent_<slot>, made from the picture by scripts/make_agent_clips.py) play by
 * default; a clip Sir picks in Settings replaces that one.
 */
object AgentClips {
    enum class Slot(val key: String, val title: String, val hint: String, val loop: Boolean) {
        WORK("work", "Kaam / soch", "Laptop pe type karta, thodi par haath — chup-chaap kaam (idle + thinking)", true),
        LISTEN("listen", "Sunna", "Camera ki taraf mud ke dhyan se sunta hai", true),
        TALK("talk", "Bolna", "Camera ki taraf dekh ke bolta hai, haath se samjhata hai", true),
        WAVE("wave", "Hello (haath)", "Camera ki taraf haath utha ke hello karta hai — baat shuru hote hi ek baar", false),
        HAPPY("happy", "Khush", "Muskura ke sir hilata hai", true),
        SAD("sad", "Udaas", "Sir jhuka ke udaas", true),
    }

    sealed interface Source {
        data class FromFile(val file: File) : Source
        data class FromRaw(val resId: Int) : Source
    }

    /**
     * Where a looping clip starts again. The built-in Sunna / Bolna clips begin with the robot turning to Sir; after
     * that turn they loop from 1 s, so he keeps facing Sir. Sir's own clips loop from the start.
     */
    fun loopFromMs(slot: Slot, source: Source?): Int =
        if (source is Source.FromRaw && (slot == Slot.LISTEN || slot == Slot.TALK)) 1_000 else 0

    /** Bumps whenever Sir adds or removes a clip, so the HUD re-checks. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun dir(context: Context) = File(context.filesDir, "agent").apply { mkdirs() }
    fun file(context: Context, slot: Slot) = File(dir(context), "${slot.key}.mp4")
    fun hasOwn(context: Context, slot: Slot) = file(context, slot).let { it.exists() && it.length() > 10_000 }

    fun source(context: Context, slot: Slot): Source? {
        val f = file(context, slot)
        if (f.exists() && f.length() > 10_000) return Source.FromFile(f)
        @Suppress("DiscouragedApi")
        val raw = context.resources.getIdentifier("agent_${slot.key}", "raw", context.packageName)
        return if (raw != 0) Source.FromRaw(raw) else null
    }

    fun any(context: Context) = Slot.entries.any { source(context, it) != null }

    suspend fun save(context: Context, slot: Slot, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val ok = runCatching {
            val tmp = File(dir(context), "${slot.key}.tmp")
            context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: return@runCatching false
            tmp.renameTo(file(context, slot))
        }.getOrDefault(false)
        _version.value++
        ok
    }

    fun remove(context: Context, slot: Slot) {
        file(context, slot).delete()
        _version.value++
    }

    /**
     * Which clip fits now. [greeting] is true for the first moments of a conversation (the wave plays once);
     * mood clips show while JARVIS is idle after a happy / sad reply. Missing clips fall back to WORK.
     */
    fun pick(state: JarvisState, mood: AgentMood, greeting: Boolean, has: (Slot) -> Boolean): Slot? {
        val want = when {
            greeting && has(Slot.WAVE) -> Slot.WAVE
            state == JarvisState.LISTENING -> Slot.LISTEN
            state == JarvisState.SPEAKING -> Slot.TALK
            state == JarvisState.THINKING -> Slot.WORK
            mood == AgentMood.HAPPY || mood == AgentMood.LOVE || mood == AgentMood.SURPRISED -> Slot.HAPPY
            mood == AgentMood.SAD || mood == AgentMood.ANGRY -> Slot.SAD
            else -> Slot.WORK
        }
        return when {
            has(want) -> want
            has(Slot.WORK) -> Slot.WORK
            else -> null
        }
    }
}
