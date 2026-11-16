package com.adesh.devjarvis.apps

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.adesh.devjarvis.core.SessionTrace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JARVIS tests an app before Sir sees it: runs it in a hidden browser of its own (a separate, throwaway origin, so
 * Sir's real app data is never touched), fills the inputs, taps every button once and collects every JavaScript
 * error. An empty result means it ran clean.
 */
@Singleton
class AppTester @Inject constructor(@ApplicationContext private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun errors(html: String): List<String> = withContext(Dispatchers.Main) {
        val found = Collections.synchronizedList(mutableListOf<String>())
        val finished = CompletableDeferred<Unit>()
        val web = WebView(context)
        try {
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.layout(0, 0, 1080, 2340) // a phone-sized page, even though it is never shown
            web.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    val text = m.message().orEmpty()
                    if ("JARVIS_TEST_DONE" in text) finished.complete(Unit)
                    else if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) found += if ("(line" in text) text else "$text (line ${m.lineNumber()})"
                    return true
                }
            }
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    // give the app a moment to start, then tap through it
                    view.postDelayed({ view.evaluateJavascript(AppMaker.TEST_CLICKS, null) }, 900)
                }
            }
            // A unique pretend origin: its own empty localStorage for every test run.
            val origin = "https://test-${System.nanoTime()}.jarvis.local/"
            web.loadDataWithBaseURL(origin, AppMaker.withTestHooks(html), "text/html", "utf-8", null)
            withTimeoutOrNull(12_000) { finished.await() } ?: found.add("The app did not finish its tap-through test in 12 s (stuck loop or freeze?)")
            SessionTrace.add("APPTEST ${found.size} errors")
            found.toList()
        } finally {
            web.stopLoading()
            web.destroy()
        }
    }
}
