package com.adesh.devjarvis.vision

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.adesh.devjarvis.accessibility.JarvisAccessibilityService
import com.adesh.devjarvis.core.ConversationMode
import com.adesh.devjarvis.core.SessionTrace
import com.adesh.devjarvis.screen.ScreenSafety
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.JarvisTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JARVIS ki "live aankhein": camera ya screen ke frames Live baat-cheet mein bhejna.
 * Sirf Sir ke kehne par chalu hoti hain (live_camera / screen_watch tools) aur baat-cheet khatam hote hi band.
 * Background mein kabhi nahi: camera ki apni screen khuli rehti hai, screen-watch har frame se pehle
 * password / payment screen check karta hai aur aisi screen ka frame nahi bhejta.
 */
@Singleton
class LiveEyes @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modes: ConversationMode,
) {
    /** Where frames go; set by the session while a Live conversation is open, null otherwise. */
    @Volatile var sink: ((ByteArray) -> Unit)? = null

    val liveSessionOpen: Boolean get() = sink != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var screenJob: Job? = null

    /** One picture straight into the open Live conversation (the `look` tools). False when no Live session is open. */
    fun show(jpeg: ByteArray): Boolean = sink?.let { it(jpeg); it(jpeg); true } ?: false // twice: a single realtime frame can be dropped

    fun offerCameraFrame(jpeg: ByteArray) {
        if (modes.liveVision) sink?.invoke(jpeg)
    }

    fun startCamera(front: Boolean): Intent {
        modes.liveVision = true
        SessionTrace.add("EYES camera on front=$front")
        return Intent(context, LiveCameraActivity::class.java).putExtra(LiveCameraActivity.EXTRA_FRONT, front)
    }

    fun stopCamera() {
        if (modes.liveVision) SessionTrace.add("EYES camera off")
        modes.liveVision = false // the camera screen watches this flag and closes itself
    }

    fun startScreenWatch() {
        modes.screenWatch = true
        if (screenJob?.isActive == true) return
        SessionTrace.add("EYES screen watch on")
        screenJob = scope.launch {
            while (modes.screenWatch && sink != null) {
                val svc = JarvisAccessibilityService.get() ?: break
                val snap = runCatching { svc.snapshot() }.getOrNull()
                // Never JARVIS's own HUD, never a password / payment / OTP screen.
                val isPrivate = snap != null && ScreenSafety.check(snap) is ScreenSafety.Verdict.Blocked
                if (snap?.packageName != context.packageName && !isPrivate) {
                    val bmp = runCatching { svc.screenshot() }.getOrNull()
                    if (bmp != null) {
                        val jpeg = withContext(Dispatchers.Default) {
                            runCatching { ImageUtil.toJpeg(bmp, maxSide = FRAME_SIDE, quality = FRAME_QUALITY) }.getOrNull()
                        }
                        bmp.recycle()
                        if (jpeg != null && modes.screenWatch) sink?.invoke(jpeg)
                    }
                }
                delay(SCREEN_INTERVAL_MS)
            }
            modes.screenWatch = false
            SessionTrace.add("EYES screen watch off")
        }
    }

    fun stopScreenWatch() {
        modes.screenWatch = false
        screenJob?.cancel()
        screenJob = null
    }

    /** Conversation over: everything off. */
    fun stopAll() {
        stopCamera()
        stopScreenWatch()
    }

    fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun eyes(): LiveEyes
        fun modes(): ConversationMode
    }

    companion object {
        const val FRAME_SIDE = 768
        const val FRAME_QUALITY = 60
        const val CAMERA_INTERVAL_MS = 1_000L
        const val SCREEN_INTERVAL_MS = 2_500L
    }
}

/**
 * Live camera screen: preview dikhta rehta hai aur har second ek chhota frame JARVIS ko jaata hai.
 * "Band karo" bolne, CLOSE dabane, back dabane ya baat-cheet khatam hone par band.
 */
class LiveCameraActivity : ComponentActivity() {

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var lastFrameAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val access = EntryPointAccessors.fromApplication(applicationContext, LiveEyes.Access::class.java)
        val eyes = access.eyes()
        val modes = access.modes()
        if (!eyes.hasCameraPermission() || !modes.liveVision) {
            eyes.stopCamera()
            finish()
            return
        }
        val front = intent.getBooleanExtra(EXTRA_FRONT, false)
        setContent {
            JarvisTheme {
                Box(Modifier.fillMaxSize().background(Hud.Background)) {
                    AndroidView(factory = { ctx -> PreviewView(ctx).also { bind(it, front, eyes, modes) } }, modifier = Modifier.fillMaxSize())
                    Text(
                        "JARVIS LIVE DEKH RAHA HAI",
                        style = MaterialTheme.typography.titleMedium,
                        color = Hud.Cyan,
                        modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(16.dp),
                    )
                    OutlinedButton(
                        onClick = { eyes.stopCamera(); finish() },
                        modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(24.dp),
                    ) { Text("CLOSE", color = Hud.Cyan) }
                }
            }
        }
    }

    private fun bind(view: PreviewView, front: Boolean, eyes: LiveEyes, modes: ConversationMode) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener finish()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(analysisExecutor) { image ->
                try {
                    if (!modes.liveVision) {
                        runOnUiThread { finish() } // Sir said "band karo", or the conversation ended
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - lastFrameAt >= LiveEyes.CAMERA_INTERVAL_MS) {
                            lastFrameAt = now
                            runCatching {
                                val bmp = image.toBitmap()
                                val jpeg = ImageUtil.toJpeg(bmp, image.imageInfo.rotationDegrees, LiveEyes.FRAME_SIDE, LiveEyes.FRAME_QUALITY)
                                bmp.recycle()
                                eyes.offerCameraFrame(jpeg)
                            }
                        }
                    }
                } finally {
                    image.close()
                }
            }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, analysis)
            } catch (e: Exception) {
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        // Camera closed for any reason = live vision is over.
        runCatching { EntryPointAccessors.fromApplication(applicationContext, LiveEyes.Access::class.java).eyes().stopCamera() }
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FRONT = "front"
    }
}
