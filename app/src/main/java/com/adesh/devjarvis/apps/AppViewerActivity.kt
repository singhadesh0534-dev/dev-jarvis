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
        title = file.nameWithoutExtension
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true // localStorage: the app's data stays
        web.settings.allowFileAccess = true
        web.webViewClient = WebViewClient() // links stay inside the app
        web.webChromeClient = WebChromeClient() // alert / confirm work
        setContentView(web)
        web.loadUrl("file://" + file.absolutePath)
    }

    companion object {
        const val EXTRA_FILE = "file"
    }
}
