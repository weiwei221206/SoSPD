package io.github.sospd.quick

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*
import io.github.sospd.domain.Models.ScanRegion
import kotlin.math.*

internal class RegionSelector(private val context: Context, private val host: OverlayHost,
                              private val save: (ScanRegion) -> Unit, private val cancel: () -> Unit) {
    fun launch() {
        val panel = context.overlayPanel()
        val title = context.overlayText("切到游戏画面后，点击开始框选", 14f)
        panel.addView(title)
        val row = LinearLayout(context)
        row.addView(context.overlayButton("开始框选") { select() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(context.overlayButton("取消", cancel), LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(row)
        host.show(panel, min(context.dp(300), context.displaySize().x - context.dp(24)))
        host.draggable(title)
    }

    private fun select() {
        val frame = FrameLayout(context)
        val selection = SelectionView(context)
        frame.addView(selection, FrameLayout.LayoutParams(-1, -1))
        val panel = context.overlayPanel()
        val hint = context.overlayText("拖动框选标准种子码所在区域", 16f)
        panel.addView(hint)
        val row = LinearLayout(context)
        val saveButton = context.overlayButton("保存范围") {
            try { save(selection.region()) }
            catch (e: IllegalArgumentException) { hint.text = e.message }
        }.apply { isEnabled = false }
        row.addView(saveButton, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(context.overlayButton("取消", cancel), LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(row)
        selection.changed = { valid -> saveButton.isEnabled = valid }
        frame.addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(context.dp(12), 0, context.dp(12), context.dp(48))
        })
        host.show(frame, -1, -1, fullscreen = true)
    }

    private class SelectionView(context: Context) : View(context) {
        var changed: (Boolean) -> Unit = {}
        private val rect = RectF()
        private var startX = 0f; private var startY = 0f
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8FD5FF.toInt(); style = Paint.Style.STROKE; strokeWidth = context.dp(2).toFloat() }
        private val dim = Paint().apply { color = 0x44000000 }
        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), rect.top, dim)
            canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), dim)
            canvas.drawRect(0f, rect.top, rect.left, rect.bottom, dim)
            canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, dim)
            canvas.drawRect(rect, line)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x.coerceIn(0f, width.toFloat()); val y = event.y.coerceIn(0f, height.toFloat())
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = x; startY = y; rect.set(x, y, x, y) }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    rect.set(min(startX, x), min(startY, y), max(startX, x), max(startY, y))
                    changed(rect.width() >= 16 && rect.height() >= 16)
                    if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                }
                MotionEvent.ACTION_CANCEL -> { rect.setEmpty(); changed(false) }
            }
            invalidate(); return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
        fun region(): ScanRegion {
            val location = IntArray(2); getLocationOnScreen(location)
            val screen = context.displaySize()
            return ScanRegion.fromPixels((rect.left + location[0]).roundToInt(), (rect.top + location[1]).roundToInt(),
                (rect.right + location[0]).roundToInt(), (rect.bottom + location[1]).roundToInt(), screen.x, screen.y)
        }
    }
}
