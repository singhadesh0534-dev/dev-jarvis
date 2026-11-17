package com.adesh.devjarvis.ui.hud

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One video layer: a player on its own TextureView, faded in and out. */
private class VideoLayer {
    val player = MediaPlayer()
    var surface by mutableStateOf<Surface?>(null)
    var videoW by mutableIntStateOf(0)
    var videoH by mutableIntStateOf(0)
    val alpha = Animatable(0f)
    var playing: AgentClips.Source? = null

    /** Starts [source]; returns once its first frame is on screen (false if it never came). */
    suspend fun start(context: Context, source: AgentClips.Source, loop: Boolean, loopFromMs: Int, onEnd: () -> Unit): Boolean {
        val s = snapshotFlow { surface }.filterNotNull().first()
        val firstFrame = CompletableDeferred<Boolean>()
        runCatching {
            player.reset()
            player.setSurface(s)
            when (source) {
                is AgentClips.Source.FromFile -> player.setDataSource(source.file.absolutePath)
                is AgentClips.Source.FromRaw -> context.resources.openRawResourceFd(source.resId).use {
                    player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }
            player.setVolume(0f, 0f)
            // A clip that starts by turning to Sir loops only its later part, so he stays facing Sir.
            player.isLooping = loop && loopFromMs <= 0
            player.setOnCompletionListener { mp ->
                if (loop && loopFromMs > 0) {
                    mp.seekTo(loopFromMs.toLong(), MediaPlayer.SEEK_CLOSEST)
                    mp.start()
                } else if (!loop) {
                    onEnd()
                }
            }
            player.setOnVideoSizeChangedListener { _, w, h -> videoW = w; videoH = h }
            player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) firstFrame.complete(true)
                false
            }
            player.setOnErrorListener { _, _, _ -> firstFrame.complete(false); onEnd(); true }
            player.setOnPreparedListener { it.start() }
            player.prepareAsync()
        }.onFailure { firstFrame.complete(false) }
        playing = source
        return withTimeoutOrNull(4_000) { firstFrame.await() } ?: false
    }

    fun stop() {
        runCatching { player.stop() }
        playing = null
    }
}

/**
 * Plays the robot's clips, silent, filling the panel (centre-crop around [focusX] / [focusY], 0..1) on TextureViews
 * clipped to the panel. A new clip is prepared on a second layer and cross-faded in once its first frame is drawn,
 * so a change never flashes black or jumps; the picture underneath shows until the first clip is ready.
 * [loopFromMs] > 0 loops from that point (the robot's turn towards Sir plays once). [onEnd] fires when a one-shot
 * clip (the wave) finishes.
 */
@Composable
fun AgentVideo(
    source: AgentClips.Source?,
    loop: Boolean,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    loopFromMs: Int = 0,
    focusX: Float = 0.5f,
    focusY: Float = 0.3f,
) {
    val context = LocalContext.current
    val layers = remember { listOf(VideoLayer(), VideoLayer()) }
    var front by remember { mutableIntStateOf(-1) }
    val end by rememberUpdatedState(onEnd)

    DisposableEffect(Unit) {
        onDispose { layers.forEach { runCatching { it.player.release() } } }
    }
    LaunchedEffect(source, loop, loopFromMs) {
        val current = layers.getOrNull(front)
        if (source == null) {
            current?.alpha?.animateTo(0f, tween(300))
            current?.stop()
            front = -1
            return@LaunchedEffect
        }
        if (current?.playing == source) return@LaunchedEffect
        val nextIndex = if (front == 0) 1 else 0
        val next = layers[nextIndex]
        next.alpha.snapTo(0f)
        if (!next.start(context, source, loop, loopFromMs) { end() }) return@LaunchedEffect
        coroutineScope {
            launch { next.alpha.animateTo(1f, tween(320)) }
            launch { current?.alpha?.animateTo(0f, tween(320)) }
        }
        current?.stop()
        front = nextIndex
    }

    Box(modifier) {
        layers.forEach { layer ->
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        isOpaque = false
                        alpha = 0f
                        addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> (v as TextureView).fit(layer.videoW, layer.videoH, focusX, focusY) }
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { layer.surface = Surface(st) }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                runCatching { layer.player.setSurface(null) }
                                layer.surface = null
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                    }
                },
                update = { tv ->
                    tv.alpha = layer.alpha.value
                    tv.fit(layer.videoW, layer.videoH, focusX, focusY)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
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
