package io.github.sospd.quick

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import io.github.sospd.domain.ItemRules
import io.github.sospd.domain.Models.DungeonReport
import io.github.sospd.domain.Models.Category
import io.github.sospd.domain.Models.FoundItem
import kotlin.math.min
import kotlin.math.roundToInt

internal class QuickResultWindow(private val context: Context, private val host: OverlayHost,
                                 private val identify: () -> Unit, private val close: () -> Unit) {
    private var report: DungeonReport? = null
    private var hidden: Set<String> = emptySet()
    private var index = 0
    private var collapsed = false
    private var busy = false
    private var message = "打开种子码页面，点击识别"
    private var list: LinearLayout? = null
    private var scroll: ScrollView? = null
    private var status: TextView? = null
    private var page: TextView? = null
    private var previous: Button? = null
    private var next: Button? = null
    private var scan: Button? = null

    private fun text(value: String, size: Float = 11f, color: Int = Color.WHITE) = context.overlayText(value, size, color).apply {
        includeFontPadding = false; setPadding(0, context.dp(1), 0, context.dp(1))
    }
    private fun button(value: String, action: () -> Unit) = context.overlayButton(value, action).apply {
        textSize = 11f; includeFontPadding = false
        minHeight = context.dp(26); minimumHeight = context.dp(26)
        setPadding(context.dp(5), context.dp(2), context.dp(5), context.dp(2))
    }
    private fun Button.available(value: Boolean) { isEnabled = value; alpha = if (value) 1f else 0.35f }

    fun show() {
        if (collapsed) {
            val button = button("快查") { collapsed = false; show() }
            button.contentDescription = "展开快速探查"
            button.background = context.overlayPanel().background
            host.show(button, context.dp(44), context.dp(34)); host.draggable(button) { collapsed = false; show() }
            return
        }
        val panel = context.overlayPanel().apply { setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6)) }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = text("快速探查", 12f).apply { setTypeface(null, Typeface.BOLD) }
        header.addView(title, LinearLayout.LayoutParams(-2, -2))
        header.addView(button("收起") { collapsed = true; show() }, LinearLayout.LayoutParams(-2, context.dp(26)).apply {
            marginStart = context.dp(6)
        })
        panel.addView(header)
        val actions = LinearLayout(context)
        scan = button("识别", identify)
        actions.addView(scan, LinearLayout.LayoutParams(context.dp(40), context.dp(26)))
        actions.addView(button("关闭", close), LinearLayout.LayoutParams(context.dp(40), context.dp(26)).apply { marginStart = context.dp(4) })
        panel.addView(actions)
        status = text(message, 10f, 0xFFD8D8D8.toInt())
        panel.addView(status)
        val pages = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT }
        previous = button("−") { index--; populate() }.apply { contentDescription = "上一层" }
        next = button("+") { index++; populate() }.apply { contentDescription = "下一层" }
        page = text("—/—", 11f).apply { gravity = Gravity.CENTER }
        pages.addView(previous, LinearLayout.LayoutParams(context.dp(24), context.dp(26)))
        pages.addView(page, LinearLayout.LayoutParams(context.dp(32), -2))
        pages.addView(next, LinearLayout.LayoutParams(context.dp(24), context.dp(26)))
        panel.addView(pages)
        list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val screen = context.displaySize()
        val limit = min(context.dp(180), (screen.y * 0.30).toInt())
        scroll = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST))
            }
        }.apply {
            addView(list)
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
            scrollBarSize = context.dp(2)
            verticalScrollbarThumbDrawable = GradientDrawable().apply {
                setColor(0xBBCCCCCC.toInt()); cornerRadius = context.dp(1).toFloat()
            }
            verticalScrollbarTrackDrawable = GradientDrawable().apply {
                setColor(0x33FFFFFF); cornerRadius = context.dp(1).toFloat()
            }
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        host.show(panel, min(context.dp(200), screen.x - context.dp(16)))
        host.draggable(header)
        populate()
    }

    fun status(text: String, calculating: Boolean = false) {
        message = text; busy = calculating
        status?.text = text; scan?.available(!busy)
    }

    fun result(value: DungeonReport, hiddenCategories: Set<String>) {
        report = value; hidden = hiddenCategories; index = 0
        message = value.seedCode(); busy = false
        if (!collapsed) populate()
    }

    private fun populate() {
        status?.text = message; scan?.available(!busy)
        val value = report
        if (value == null) {
            previous?.available(false); next?.available(false)
            host.resizeWidth(context.dp(130)); return
        }
        index = index.coerceIn(0, value.floors().lastIndex)
        val floor = value.floors()[index]
        page?.text = "${floor.depth()}/${value.floors().size}"
        previous?.available(index > 0); next?.available(index < value.floors().lastIndex)
        list?.removeAllViews()
        val items = floor.items().filter { !ItemRules.excluded(it.id()) && it.category().name !in hidden }
        if (items.isEmpty()) list?.addView(text("这一层没有符合显示分类的物品"))
        val paint = text("").paint
        val nameWidth = ((items.maxOfOrNull { paint.measureText((if (it.reliable()) "" else "*") + it.name()) }
            ?: 0f) + context.dp(2)).roundToInt().coerceIn(context.dp(40), context.dp(100))
        val sourcePaint = text("", 10f).paint
        val sourceWidth = ((items.maxOfOrNull { sourcePaint.measureText(detail(it)) } ?: 0f) + context.dp(2)).roundToInt()
        host.resizeWidth((nameWidth + sourceWidth + context.dp(20) + (scroll?.verticalScrollbarWidth ?: 0)).coerceIn(context.dp(112),
            min(context.dp(200), context.displaySize().x - context.dp(16))))
        for (item in items) {
            val row = LinearLayout(context).apply { gravity = Gravity.TOP }
            val name = text((if (item.reliable()) "" else "*") + item.name(), 11f,
                if (item.cursed()) 0xFFFF7777.toInt() else Color.WHITE)
            val source = text(detail(item), 10f, 0xFFBBBBBB.toInt()).apply { gravity = Gravity.LEFT; setPadding(context.dp(4), context.dp(1), 0, context.dp(1)) }
            row.addView(name, LinearLayout.LayoutParams(nameWidth, -2))
            row.addView(source, LinearLayout.LayoutParams(0, -2, 1f))
            list?.addView(row)
        }
        scroll?.scrollTo(0, 0)
    }

    private fun detail(item: FoundItem) = if (item.category() == Category.POTION || item.category() == Category.SCROLL)
        item.appearance().ifBlank { "外观未知" } else item.source()
}
