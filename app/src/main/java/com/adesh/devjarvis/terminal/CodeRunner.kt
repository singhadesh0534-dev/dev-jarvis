package com.adesh.devjarvis.terminal

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JARVIS's own terminal, inside the app (no Termux): Python 3 (embedded) and the phone's shell, in a private home
 * folder. Sir uses it on the Terminal screen; JARVIS uses it through run_code and reads the output. Everything that
 * runs shows in one shared log.
 */
@Singleton
class CodeRunner @Inject constructor(@ApplicationContext private val context: Context) {
    val home: File = File(context.filesDir, "home").apply { mkdirs() }

    private val _log = MutableStateFlow(listOf(TermLine("JARVIS terminal — python aur shell commands. Home: ~", TermLine.Kind.INFO)))
    val log: StateFlow<List<TermLine>> = _log.asStateFlow()

    /** True while a program waits for typed input (the Terminal screen's box then answers it). */
    private val _waiting = MutableStateFlow(false)
    val waiting: StateFlow<Boolean> = _waiting.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val typed = LinkedBlockingQueue<String>()
    private val lock = Mutex()

    data class TermLine(val text: String, val kind: Kind) {
        enum class Kind { COMMAND, OUTPUT, ERROR, INFO }
    }

    private fun add(text: String, kind: TermLine.Kind) {
        if (text.isEmpty()) return
        _log.update { (it + TermLine(text, kind)).takeLast(600) }
    }

    /** Lines Sir types while a program is waiting for input. */
    fun answer(line: String) {
        typed.offer(line)
    }

    fun clear() {
        _log.value = emptyList()
    }

    /**
     * Runs one terminal line: `python file.py`, `python -c "..."`, `python3 ...`, or any shell command.
     * [input] feeds input() for a non-interactive run (JARVIS); with [interactive] the Terminal screen answers.
     */
    suspend fun run(line: String, input: List<String> = emptyList(), interactive: Boolean = false, timeoutMs: Long = 60_000): String =
        lock.withLock {
            _busy.value = true
            add("$ $line", TermLine.Kind.COMMAND)
            val out = StringBuilder()
            try {
                val py = Regex("""^\s*python3?\s+(.*)$""", RegexOption.DOT_MATCHES_ALL).find(line)?.groupValues?.get(1)?.trim()
                when {
                    py != null && py.startsWith("-c ") -> python(unquote(py.removePrefix("-c ").trim()), null, input, interactive, timeoutMs, out)
                    py != null && py.isNotEmpty() -> {
                        val f = File(home, py.split(Regex("\\s+")).first())
                        if (!f.exists()) note("python: can't open file '${f.name}'", out, TermLine.Kind.ERROR)
                        else python(null, f, input, interactive, timeoutMs, out)
                    }
                    Regex("""^\s*pip3?\s""").containsMatchIn(line) ->
                        note("pip yahan nahi chalta — JARVIS ke Python mein standard library hai (math, json, random, datetime, csv, sqlite3…)", out, TermLine.Kind.INFO)
                    else -> shell(line, timeoutMs, out)
                }
            } finally {
                _busy.value = false
                _waiting.value = false
            }
            out.toString().trim().ifEmpty { "(no output)" }
        }

    /** Writes a file in the home folder (for code JARVIS writes). */
    suspend fun saveFile(name: String, text: String): File = withContext(Dispatchers.IO) {
        val f = File(home, name.trim().removePrefix("~/").removePrefix("/"))
        require(f.canonicalPath.startsWith(home.canonicalPath)) { "outside home" }
        f.parentFile?.mkdirs()
        f.writeText(text)
        add("(saved ~/${f.relativeTo(home).path}, ${text.lines().size} lines)", TermLine.Kind.INFO)
        f
    }

    private fun note(text: String, out: StringBuilder, kind: TermLine.Kind) {
        out.appendLine(text)
        add(text, kind)
    }

    private suspend fun shell(cmd: String, timeoutMs: Long, out: StringBuilder) = withContext(Dispatchers.IO) {
        val p = ProcessBuilder("/system/bin/sh", "-c", cmd).directory(home).redirectErrorStream(true)
            .apply { environment()["HOME"] = home.absolutePath; environment()["TMPDIR"] = context.cacheDir.absolutePath }
            .start()
        val reader = Thread {
            runCatching { p.inputStream.bufferedReader().forEachLine { l -> out.appendLine(l); add(l, TermLine.Kind.OUTPUT) } }
        }.apply { start() }
        if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            p.destroyForcibly()
            note("(stopped after ${timeoutMs / 1000} s)", out, TermLine.Kind.ERROR)
        }
        reader.join(1_000)
        val code = runCatching { p.exitValue() }.getOrDefault(-1)
        if (code != 0) note("(exit $code)", out, TermLine.Kind.ERROR)
    }

    /** Bridge Python writes to and reads input() from. Called from Python's thread. */
    inner class Io(private val lines: ArrayDeque<String>, private val interactive: Boolean, private val out: StringBuilder) {
        private val pending = StringBuilder()

        fun write(s: String) {
            synchronized(this) {
                out.append(s)
                pending.append(s)
                while (true) {
                    val nl = pending.indexOf('\n')
                    if (nl < 0) break
                    add(pending.substring(0, nl), TermLine.Kind.OUTPUT)
                    pending.delete(0, nl + 1)
                }
            }
        }

        fun readline(): String? {
            flushPartial()
            if (lines.isNotEmpty()) return lines.removeFirst()
            if (!interactive) return null
            _waiting.value = true
            return try { typed.poll(10, TimeUnit.MINUTES) } finally { _waiting.value = false }
        }

        fun flushPartial() {
            synchronized(this) {
                if (pending.isNotEmpty()) { add(pending.toString(), TermLine.Kind.OUTPUT); pending.clear() }
            }
        }
    }

    private suspend fun python(code: String?, file: File?, input: List<String>, interactive: Boolean, timeoutMs: Long, out: StringBuilder) {
        typed.clear()
        val io = Io(ArrayDeque(input), interactive, out)
        // Python can't be interrupted from outside, so it runs on its own thread and we wait for it with a limit.
        val t = Thread {
            runCatching {
                if (!Python.isStarted()) Python.start(AndroidPlatform(context))
                Python.getInstance().getModule("jarvis_runner").callAttr("run", code, file?.absolutePath, home.absolutePath, io)
            }.onFailure { io.write("\n${it.message}\n") }
            io.flushPartial()
        }.apply { isDaemon = true; name = "jarvis-python"; start() }
        val limit = if (interactive) 15 * 60_000L else timeoutMs
        withContext(Dispatchers.IO) { t.join(limit) }
        if (t.isAlive) note("(stopped waiting after ${limit / 1000} s — the program may be stuck in a loop)", out, TermLine.Kind.ERROR)
    }

    private fun unquote(s: String): String {
        val t = s.trim()
        return if (t.length >= 2 && (t.first() == '"' || t.first() == '\'') && t.last() == t.first()) t.substring(1, t.length - 1) else t
    }
}
