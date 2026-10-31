package com.adesh.devjarvis.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.adesh.devjarvis.core.JarvisState
import kotlin.math.abs
import kotlin.math.min

/**
 * Doosre apps ke upar chhota floating arc reactor — jab JARVIS sun/bol raha ho.
 * Tap = JARVIS app kholo, long-press = rok do, drag = kahin bhi rakho (position yaad rehti hai).
 */
class BubbleOverlay(
    private val context: Context,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val prefs = context.getSharedPreferences("bubble", Context.MODE_PRIVATE)
    private var view: ReactorBubbleView? = null
    private val sizePx = (64 * context.resources.displayMetrics.density).toInt()

    val isShowing: Boolean get() = view != null

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null || !canShow() || wm == null) return
        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", context.resources.displayMetrics.widthPixels - sizePx - 24)
            y = prefs.getInt("y", context.resources.displayMetrics.heightPixels / 3)
        }
        val v = ReactorBubbleView(context)
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        val longPress = Runnable { if (!moved) onLongPress() }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; moved = false
                    v.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        v.removeCallbacks(longPress)
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress)
                    if (moved) prefs.edit().putInt("x", params.x).putInt("y", params.y).apply()
                    else if (e.eventTime - e.downTime < ViewConfiguration.getLongPressTimeout()) onTap()
                }
                MotionEvent.ACTION_CANCEL -> v.removeCallbacks(longPress)
            }
            true
        }
        runCatching { wm.addView(v, params) }.onSuccess { view = v }
    }

    fun update(state: JarvisState, level: Float) {
        view?.let { it.state = state; it.level = level }
    }

    fun hide() {
        val v = view ?: return
        view = null
        runCatching { wm?.removeView(v) }
    }
}

/** Mini reactor: glow core + segmented ring, spins faster while thinking, pulses with the voice. */
private class ReactorBubbleView(context: Context) : View(context) {
    var state: JarvisState = JarvisState.IDLE
    var level: Float = 0f
    private var angle = 0f
    private var shown = 0f
    private val cyan = Color.rgb(0, 229, 255)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = cyan; strokeCap = Paint.Cap.ROUND }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 3, 7, 15) }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) / 2f
        val cx = width / 2f
        val cy = height / 2f
        shown += (level - shown) * 0.25f
        angle = (angle + if (state == JarvisState.THINKING) 9f else 2.5f) % 360f
        val energy = (0.35f + 0.65f * shown).coerceIn(0f, 1f)

        canvas.drawCircle(cx, cy, r, bg)
        ring.strokeWidth = r * 0.12f
        ring.alpha = (140 + 115 * energy).toInt()
        val rr = r * 0.78f
        oval.set(cx - rr, cy - rr, cx + rr, cy + rr)
        for (i in 0 until 6) canvas.drawArc(oval, angle + i * 60f, 42f, false, ring)

        val coreR = r * (0.28f + 0.22f * energy)
        core.shader = RadialGradient(
            cx, cy, coreR * 1.5f,
            intArrayOf(Color.WHITE, Color.rgb(155, 239, 255), Color.argb(0, 0, 229, 255)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, coreR * 1.5f, core)
        postInvalidateOnAnimation()
    }
}
