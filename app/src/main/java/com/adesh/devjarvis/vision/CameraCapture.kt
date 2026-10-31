package com.adesh.devjarvis.vision

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.JarvisTheme
import kotlinx.coroutines.CompletableDeferred

/** Tool aur camera screen ke beech ka pul: tool wait karta hai, screen photo de deti hai. */
object CaptureBroker {
    @Volatile var pending: CompletableDeferred<ByteArray?>? = null

    fun complete(jpeg: ByteArray?) {
        pending?.complete(jpeg)
        pending = null
    }
}

/**
 * Chhoti si camera screen: preview dikhati hai, ~1.2 s mein photo leti hai, band ho jaati hai.
 * Lock screen ke upar bhi khulti hai ("Jarvis, ye kya hai?" phone uthaate hi).
 */
class CameraCaptureActivity : ComponentActivity() {

    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            finishWith(null)
            return
        }
        val front = intent.getStringExtra(EXTRA_LENS) == "front"
        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()

        setContent {
            JarvisTheme {
                Box(Modifier.fillMaxSize().background(Hud.Background)) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { view -> bind(view, capture, front) }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        "JARVIS IS LOOKING…",
                        style = MaterialTheme.typography.titleMedium,
                        color = Hud.Cyan,
                        modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(16.dp),
                    )
                }
            }
        }
    }

    private fun bind(view: PreviewView, capture: ImageCapture, front: Boolean) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener finishWith(null)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, capture)
            } catch (e: Exception) {
                return@addListener finishWith(null)
            }
            // Give auto-exposure / focus a moment, then shoot.
            view.postDelayed({ shoot(capture) }, 1_200)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun shoot(capture: ImageCapture) {
        capture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val jpeg = runCatching {
                    ImageUtil.toJpeg(image.toBitmap(), image.imageInfo.rotationDegrees)
                }.getOrNull()
                image.close()
                finishWith(jpeg)
            }

            override fun onError(exception: ImageCaptureException) = finishWith(null)
        })
    }

    private fun finishWith(jpeg: ByteArray?) {
        if (done) return
        done = true
        CaptureBroker.complete(jpeg)
        finish()
    }

    override fun onDestroy() {
        if (!done) CaptureBroker.complete(null) // closed early
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LENS = "lens"
    }
}
