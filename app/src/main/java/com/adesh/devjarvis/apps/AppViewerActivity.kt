package com.adesh.devjarvis.apps

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
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
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = WebViewClient() // links stay inside the app
        val bridge = PhoneBridge(this, label).also { this.bridge = it }
        web.addJavascriptInterface(bridge, PhoneBridge.NAME) // the app kit's jarvis.vibrate / speak / notify / toast
        web.webChromeClient = object : WebChromeClient() { // alert / confirm work
            // <input type="file" accept="image/*" capture>: camera or gallery
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return runCatching { startActivityForResult(params.createIntent(), PICK_FILE) }.isSuccess.also { ok ->
                    if (!ok) { fileCallback = null; callback.onReceiveValue(null) }
                }
            }
            // getUserMedia: the mic / camera JARVIS itself already has permission for
            override fun onPermissionRequest(request: PermissionRequest) {
                val allowed = request.resources.filter { r ->
                    (r == PermissionRequest.RESOURCE_AUDIO_CAPTURE && has(android.Manifest.permission.RECORD_AUDIO)) ||
                        (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE && has(android.Manifest.permission.CAMERA))
                }
                if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
            }
        }
        setContentView(web)
        web.loadUrl("file://" + file.absolutePath)
    }

    private var web: WebView? = null
    private var bridge: PhoneBridge? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private fun has(p: String) = checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        }
    }

    override fun onDestroy() {
        bridge?.close()
        web?.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    companion object {
        const val EXTRA_FILE = "file"
        private const val PICK_FILE = 41
    }
}
