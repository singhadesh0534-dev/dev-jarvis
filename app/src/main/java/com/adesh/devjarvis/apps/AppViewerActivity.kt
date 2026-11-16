package com.adesh.devjarvis.apps

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

/** Opens an app JARVIS built (a local HTML file) full screen, with its data kept between runs. */
class AppViewerActivity : Activity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = intent.getStringExtra(EXTRA_FILE)?.let(::File)
        if (file == null || !file.exists() || !file.canonicalPath.startsWith(File(filesDir, "apps").canonicalPath)) {
            finish(); return
        }
        val label = file.nameWithoutExtension.replace('-', ' ').replaceFirstChar(Char::uppercase)
        title = label
        // Its own name, icon and colour in Recents, like an installed app.
        runCatching {
            @Suppress("DEPRECATION")
            setTaskDescription(android.app.ActivityManager.TaskDescription(label, AppInstaller.icon(label, 192), AppInstaller.palette(label).first))
        }
        val web = WebView(this)
        this.web = web
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true // localStorage: the app's data stays
        web.settings.allowFileAccess = true
        web.webViewClient = WebViewClient() // links stay inside the app
        web.webChromeClient = WebChromeClient() // alert / confirm work
        setContentView(web)
        web.loadUrl("file://" + file.absolutePath)
    }

    private var web: WebView? = null

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    companion object {
        const val EXTRA_FILE = "file"
    }
}
