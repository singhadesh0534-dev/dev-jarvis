package com.adesh.devjarvis.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import com.adesh.devjarvis.core.SessionTrace
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * Lets Chrome open the apps JARVIS builds, for testing: a tiny web server on 127.0.0.1 (this phone only, never the
 * network) serving files/apps/<slug>.html at http://127.0.0.1:<port>/<slug>. A real http origin, so the app's
 * localStorage works and keeps its data in Chrome. No caching: a changed app shows at once on reload.
 */
@Singleton
class LocalAppServer @Inject constructor(@ApplicationContext private val context: Context) {
    private var server: ServerSocket? = null
    private val dir get() = File(context.filesDir, "apps")

    @Synchronized
    fun ensureStarted(): Int? {
        server?.takeIf { !it.isClosed }?.let { return it.localPort }
        for (port in PORTS) {
            val s = runCatching { ServerSocket(port, 16, InetAddress.getByName("127.0.0.1")) }.getOrNull() ?: continue
            server = s
            thread(name = "jarvis-apps-http", isDaemon = true) { serve(s) }
            SessionTrace.add("APPS local server on $port")
            return port
        }
        return null
    }

    private fun serve(s: ServerSocket) {
        while (!s.isClosed) {
            val socket = runCatching { s.accept() }.getOrNull() ?: break
            thread(name = "jarvis-apps-req", isDaemon = true) { runCatching { handle(socket) }; runCatching { socket.close() } }
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 10_000
        val input = socket.getInputStream().bufferedReader()
        val requestLine = input.readLine() ?: return
        while (true) { val h = input.readLine() ?: break; if (h.isEmpty()) break } // skip headers
        val out = socket.getOutputStream()
        val path = LocalAppRoutes.path(requestLine)
        val file = path?.let { LocalAppRoutes.slugFor(it) }?.let { File(dir, "$it.html") }?.takeIf { it.isFile }
        val (status, body) = when {
            path == null -> "405 Method Not Allowed" to "Only GET".toByteArray()
            file != null -> "200 OK" to file.readBytes()
            else -> "404 Not Found" to "<h3>JARVIS: is naam ka app nahi mila.</h3>".toByteArray()
        }
        val head = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray())
        if (!requestLine.startsWith("HEAD")) out.write(body)
        out.flush()
    }

    /** Opens a built app in Chrome (or the phone's browser if Chrome isn't there). False if nothing could open it. */
    fun openInChrome(file: File): Boolean {
        val port = ensureStarted() ?: return false
        val uri = Uri.parse("http://127.0.0.1:$port/${file.nameWithoutExtension}")
        fun intent(pkg: String?) = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName) // reuse the same tab each time
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (pkg != null) setPackage(pkg) }
        return runCatching { context.startActivity(intent(CHROME)) }.isSuccess ||
            runCatching { context.startActivity(intent(null)) }.isSuccess
    }

    private companion object {
        const val CHROME = "com.android.chrome"
        val PORTS = 47310..47319
    }
}
