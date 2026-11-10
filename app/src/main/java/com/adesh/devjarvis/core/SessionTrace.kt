package com.adesh.devjarvis.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A small capped text log on disk. Survives app restarts. */
open class DiskLog(private val fileName: String, private val maxBytes: Long) {
    private var file: File? = null
    private val fmt = SimpleDateFormat("dd MMM HH:mm:ss", Locale.ENGLISH)

    @Synchronized
    fun init(dir: File) {
        dir.mkdirs()
        file = File(dir, fileName)
    }

    @Synchronized
    protected fun write(line: String) {
        val f = file ?: return
        runCatching {
            if (f.length() > maxBytes) f.writeText(f.readText().takeLast((maxBytes / 2).toInt()).substringAfter('\n'))
            f.appendText("${fmt.format(Date())}  $line\n")
        }
    }

    @Synchronized
    fun dump(lastLines: Int = 200): String =
        runCatching { file?.takeIf { it.exists() }?.readLines()?.takeLast(lastLines)?.joinToString("\n") }.getOrNull().orEmpty()

    @Synchronized
    fun clear() {
        runCatching { file?.delete() }
    }
}

/**
 * "Flight recorder": JARVIS ke andar ke events (jaagna, Live connect, pehli awaaz kitni der mein, fallback, tools).
 * Phone pe file mein; Settings > Diagnostics se dekh/share kar sakte ho.
 */
object SessionTrace : DiskLog("trace.log", 96 * 1024) {
    fun add(event: String) = write(event)
}

/**
 * Problem journal: jo gadbad JARVIS khud pakad leta hai (galat jaagna, Live fail, der se jawab, tool fail,
 * Sir ka "ye galat hai" bolna), wo yahan apne aap likhi jaati hai. Sir ise Diagnostics se share karke fix karwa sakte hain.
 */
object ProblemJournal : DiskLog("problems.log", 96 * 1024) {
    fun add(kind: String, detail: String) {
        write("[$kind] $detail")
        SessionTrace.add("PROBLEM [$kind] $detail")
    }
}
