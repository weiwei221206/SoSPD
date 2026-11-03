package io.github.sospd.quick

import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.*
import android.widget.*
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun Context.dp(value: Int) = (resources.displayMetrics.density * value).roundToInt()

@Suppress("DEPRECATION")
internal fun Context.displaySize(): Point {
    val manager = getSystemService(WindowManager::class.java)
    return if (Build.VERSION.SDK_INT >= 30) manager.maximumWindowMetrics.bounds.let { Point(it.width(), it.height()) }
    else Point().also { manager.defaultDisplay.getRealSize(it) }
}

internal fun Context.overlayText(value: String, size: Float = 14f, color: Int = Color.WHITE) = TextView(this).apply {
    text = value; textSize = size; setTextColor(color); setPadding(0, dp(4), 0, dp(4))
}

internal fun Context.overlayButton(value: String, action: () -> Unit) = Button(this).apply {
    text = value; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
    minWidth = 0; minimumWidth = 0; minHeight = dp(38); minimumHeight = dp(38)
    setPadding(dp(10), dp(3), dp(10), dp(3))
    background = GradientDrawable().apply { setColor(0x35FFFFFF); cornerRadius = dp(8).toFloat() }
    setOnClickListener { action() }
}

internal fun Context.overlayPanel() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10))
    background = GradientDrawable().apply { setColor(0xBB000000.toInt()); cornerRadius = dp(14).toFloat() }
}

/** One bounded, non-focusable window: touches outside the panel continue to the game. */
internal class OverlayHost(private val context: Context) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var x = context.dp(12)
    private var y = context.dp(120)

    fun show(view: View, width: Int, height: Int = WindowManager.LayoutParams.WRAP_CONTENT, fullscreen: Boolean = false) {
        remove()
        val layout = WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, android.graphics.PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            if (fullscreen) { x = 0; y = 0 } else { x = this@OverlayHost.x; y = this@OverlayHost.y }
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        manager.addView(view, layout)
        root = view; params = layout
        view.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) clampPosition()
        }
        view.post { clampPosition() }
    }

    fun draggable(handle: View, tap: (() -> Unit)? = null) {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        if (tap != null) handle.setOnClickListener { tap() }
        handle.setOnTouchListener { v, event ->
            val layout = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = layout.x; startY = layout.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop) moved = true
                    if (moved) {
                        layout.x = startX + (event.rawX - downX).roundToInt()
                        layout.y = startY + (event.rawY - downY).roundToInt()
                        clampPosition()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) v.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun clampPosition() {
        val view = root ?: return
        val layout = params ?: return
        if (layout.width == WindowManager.LayoutParams.MATCH_PARENT) return
        val screen = context.displaySize()
        layout.x = layout.x.coerceIn(0, (screen.x - view.width).coerceAtLeast(0))
        layout.y = layout.y.coerceIn(context.dp(24), (screen.y - view.height - context.dp(24)).coerceAtLeast(context.dp(24)))
        x = layout.x; y = layout.y
        manager.updateViewLayout(view, layout)
    }

    fun visible(show: Boolean) { root?.visibility = if (show) View.VISIBLE else View.INVISIBLE }
    fun remove() {
        root?.let { if (it.isAttachedToWindow) manager.removeViewImmediate(it) }
        root = null; params = null
    }
}
