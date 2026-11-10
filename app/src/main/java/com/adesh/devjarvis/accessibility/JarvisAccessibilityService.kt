package com.adesh.devjarvis.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.adesh.devjarvis.screen.ScreenElement
import com.adesh.devjarvis.screen.ScreenSnapshot
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * JARVIS ke "haath": sirf aapke command pe screen padhna, tap/type/scroll, screenshot, global actions.
 * Background mein kuch nahi padhta ya bhejta. Bonus: iske ON hone se Android JARVIS ko background se apps
 * kholne deta hai.
 */
class JarvisAccessibilityService : AccessibilityService() {

    /** Nodes of the last snapshot, by element id — valid until the next snapshot. */
    private var lastNodes: Map<Int, AccessibilityNodeInfo> = emptyMap()

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- reading

    fun snapshot(): ScreenSnapshot? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString().orEmpty()
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        val nodes = LinkedHashMap<Int, AccessibilityNodeInfo>()
        val elements = mutableListOf<ScreenElement>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40 || elements.size >= 250) return
            if (!n.isVisibleToUser) return
            val text = n.text?.toString().orEmpty()
            val desc = n.contentDescription?.toString().orEmpty()
            val interesting = text.isNotBlank() || desc.isNotBlank() || n.isClickable || n.isEditable || n.isScrollable
            if (interesting) {
                val id = elements.size + 1
                nodes[id] = n
                elements += ScreenElement(
                    id = id,
                    text = if (n.isPassword) "" else text, // never read password contents
                    description = desc,
                    className = n.className?.toString().orEmpty(),
                    viewId = n.viewIdResourceName.orEmpty(),
                    clickable = n.isClickable,
                    editable = n.isEditable,
                    scrollable = n.isScrollable,
                    password = n.isPassword,
                    focused = n.isFocused,
                )
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        lastNodes = nodes
        return ScreenSnapshot(pkg, label, elements)
    }

    // ---------------------------------------------------------------- acting

    /** Click element [id] from the last snapshot: accessibility click, else nearest clickable parent, else a tap gesture. */
    suspend fun tap(id: Int): Boolean {
        val node = lastNodes[id] ?: return false
        var n: AccessibilityNodeInfo? = node
        var hops = 0
        while (n != null && hops < 6) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            hops++
        }
        val r = Rect().also { node.getBoundsInScreen(it) }
        return r.width() > 0 && tapAt(r.exactCenterX(), r.exactCenterY())
    }

    /** Type into [id] (or the focused / first input). Replaces the field's text. */
    fun type(id: Int?, text: String): Boolean {
        val target = id?.let { lastNodes[it] }?.takeIf { it.isEditable }
            ?: lastNodes.values.firstOrNull { it.isEditable && it.isFocused }
            ?: lastNodes.values.firstOrNull { it.isEditable }
            ?: return false
        if (target.isPassword) return false
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    suspend fun scroll(down: Boolean): Boolean {
        val scrollable = lastNodes.values.filter { it.isScrollable }.maxByOrNull { n ->
            Rect().also { n.getBoundsInScreen(it) }.let { it.width() * it.height() }
        }
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (scrollable?.performAction(action) == true) return true
        // Fallback: swipe gesture in the middle of the screen.
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val (from, to) = if (down) dm.heightPixels * 0.7f to dm.heightPixels * 0.3f else dm.heightPixels * 0.3f to dm.heightPixels * 0.7f
        return swipe(x, from, x, to)
    }

    /** Swipe across the middle of the screen. "left" = finger moves right-to-left (next page / next reel). */
    suspend fun swipe(direction: String): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        return when (direction) {
            "left" -> swipe(w * 0.85f, h * 0.5f, w * 0.15f, h * 0.5f)
            "right" -> swipe(w * 0.15f, h * 0.5f, w * 0.85f, h * 0.5f)
            "up" -> swipe(w * 0.5f, h * 0.75f, w * 0.5f, h * 0.25f)
            "down" -> swipe(w * 0.5f, h * 0.25f, w * 0.5f, h * 0.75f)
            else -> false
        }
    }

    /** Long-press element [id] from the last snapshot (opens selection / context menus). */
    suspend fun longPress(id: Int): Boolean {
        val node = lastNodes[id] ?: return false
        var n: AccessibilityNodeInfo? = node
        var hops = 0
        while (n != null && hops < 4) {
            if (n.isLongClickable && n.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) return true
            n = n.parent
            hops++
        }
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (r.width() <= 0) return false
        return gesture(GestureDescription.StrokeDescription(Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }, 0, 750))
    }

    private suspend fun tapAt(x: Float, y: Float): Boolean =
        gesture(GestureDescription.StrokeDescription(Path().apply { moveTo(x, y) }, 0, 60))

    private suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean =
        gesture(GestureDescription.StrokeDescription(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, 0, 350))

    private suspend fun gesture(stroke: GestureDescription.StrokeDescription): Boolean = suspendCancellableCoroutine { cont ->
        val ok = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            },
            null,
        )
        if (!ok && cont.isActive) cont.resume(false)
    }

    /** Screenshot of the current screen (Android 11+). */
    suspend fun screenshot(): Bitmap? {
        if (Build.VERSION.SDK_INT < 30) return null
        return suspendCancellableCoroutine { cont ->
            takeScreenshot(Display.DEFAULT_DISPLAY, Executor { it.run() }, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val bmp = runCatching {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        hw?.copy(Bitmap.Config.ARGB_8888, false).also { result.hardwareBuffer.close() }
                    }.getOrNull()
                    if (cont.isActive) cont.resume(bmp)
                }
                override fun onFailure(errorCode: Int) { if (cont.isActive) cont.resume(null) }
            })
        }
    }

    /**
     * Turn the main on/off switch of the settings page that is open now to [on].
     * @return "changed", "already", or null when no switch was found.
     */
    fun setSwitch(on: Boolean, hints: List<String>): String? {
        val root = rootInActiveWindow ?: return null
        val switches = ArrayList<android.view.accessibility.AccessibilityNodeInfo>()
        fun walk(n: android.view.accessibility.AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40) return
            val cls = n.className?.toString().orEmpty()
            if (n.isVisibleToUser && (cls.contains("Switch") || cls.contains("ToggleButton") || (n.isCheckable && !cls.contains("Radio")))) switches += n
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        if (switches.isEmpty()) return null
        fun labelOf(n: android.view.accessibility.AccessibilityNodeInfo): String {
            val own = listOfNotNull(n.text, n.contentDescription).joinToString(" ")
            val p = n.parent
            val around = if (p == null) "" else (0 until p.childCount).mapNotNull { p.getChild(it)?.text }.joinToString(" ")
            return "$own $around".lowercase()
        }
        val target = switches.firstOrNull { s -> hints.any { it in labelOf(s) } } ?: switches.first()
        if (target.isChecked == on) return "already"
        var node: android.view.accessibility.AccessibilityNodeInfo? = target
        repeat(4) {
            val n = node ?: return@repeat
            if (n.isClickable && n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return "changed"
            node = n.parent
        }
        return null
    }

    companion object {
        @Volatile private var instance: JarvisAccessibilityService? = null

        val isConnected: Boolean get() = instance != null

        fun get(): JarvisAccessibilityService? = instance

        /** One of [AccessibilityService.GLOBAL_ACTION_HOME] etc. False if the service is off. */
        fun global(action: Int): Boolean = instance?.performGlobalAction(action) ?: false
    }
}
