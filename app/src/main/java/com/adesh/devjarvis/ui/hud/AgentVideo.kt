package com.adesh.devjarvis.ui.hud

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Plays one of the robot's clips, silent, filling the panel (centre-crop around [focusX] / [focusY], 0..1), on a
 * TextureView so it clips to the panel's shape. The view stays invisible until the first frame is drawn, so a clip
 * change never flashes black (the picture underneath shows meanwhile). [onEnd] fires when a one-shot clip finishes.
 */
@Composable
fun AgentVideo(
    source: AgentClips.Source?,
    loop: Boolean,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    focusX: Float = 0.5f,
    focusY: Float = 0.35f,
) {
    val context = LocalContext.current
    val player = remember { MediaPlayer() }
    var surface by remember { mutableStateOf<Surface?>(null) }
    var shown by remember { mutableStateOf(false) }
    var videoW by remember { mutableStateOf(0) }
    var videoH by remember { mutableStateOf(0) }
    val end by rememberUpdatedState(onEnd)

    DisposableEffect(Unit) {
        onDispose { runCatching { player.release() } }
    }
    LaunchedEffect(source, surface, loop) {
        shown = false
        val s = surface ?: return@LaunchedEffect
        runCatching {
            player.reset()
            if (source == null) return@runCatching
            player.setSurface(s)
            when (source) {
                is AgentClips.Source.FromFile -> player.setDataSource(source.file.absolutePath)
                is AgentClips.Source.FromRaw -> context.resources.openRawResourceFd(source.resId).use {
                    player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }
            player.isLooping = loop
            player.setVolume(0f, 0f)
            player.setOnVideoSizeChangedListener { _, w, h -> videoW = w; videoH = h }
            player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) shown = true
                false
            }
            player.setOnCompletionListener { if (!loop) end() }
            player.setOnErrorListener { _, _, _ -> shown = false; end(); true }
            player.setOnPreparedListener { it.start() }
            player.prepareAsync()
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                // The view gets its size after the first update: fit the video again whenever it is laid out.
                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> (v as TextureView).fit(videoW, videoH, focusX, focusY) }
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { surface = Surface(st) }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        runCatching { player.setSurface(null) }
                        surface = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
        update = { tv ->
            tv.alpha = if (shown && source != null) 1f else 0f
            tv.fit(videoW, videoH, focusX, focusY)
        },
        modifier = modifier,
    )
}

/** Centre-crop: the video fills the view, its aspect kept, cut around the focus point. */
private fun TextureView.fit(videoW: Int, videoH: Int, focusX: Float, focusY: Float) {
    val vw = width.toFloat()
    val vh = height.toFloat()
    if (videoW <= 0 || videoH <= 0 || vw <= 0f || vh <= 0f) return
    val s = maxOf(vw / videoW, vh / videoH)
    val m = Matrix()
    m.setScale(videoW * s / vw, videoH * s / vh, vw * focusX, vh * focusY)
    setTransform(m)
}
