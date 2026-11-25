package com.rhandy.ztedash

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Small helpers for building neon screens in code. */
class Ui(val ctx: Context) {
    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun bg(fill: Int, stroke: Int, radius: Int = 14) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }

    fun lp(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    fun page(title: String, sub: String, onBack: () -> Unit): Pair<ScrollView, LinearLayout> {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text("‹", 34f, Neon.MAGENTA, true).apply {
            setPadding(0, 0, dp(16), dp(4))
            setOnClickListener { onBack() }
        })
        val t = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        t.addView(text(title, 22f, Neon.CYAN, true).apply { setShadowLayer(16f, 0f, 0f, Neon.CYAN) })
        if (sub.isNotEmpty()) t.addView(text(sub, 11f, Neon.DIM))
        head.addView(t)
        col.addView(head)
        val sv = ScrollView(ctx).apply {
            setBackgroundColor(Neon.BG)
            isVerticalScrollBarEnabled = false
            addView(col)
        }
        return Pair(sv, col)
    }

    fun card(parent: LinearLayout, stroke: Int, title: String?): LinearLayout {
        val c = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(Neon.CARD, stroke)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        if (title != null) c.addView(text(title, 11f, Neon.DIM).apply { letterSpacing = 0.2f })
        parent.addView(c, lp(12))
        return c
    }

    fun button(label: String, color: Int, onClick: () -> Unit) = text(label, 14f, Neon.BG, true).apply {
        gravity = Gravity.CENTER
        letterSpacing = 0.1f
        background = bg(color, color, 12)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { onClick() }
    }

    fun confirm(msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage(msg)
            .setPositiveButton("Yes") { _, _ -> onYes() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class Chips(private val ui: Ui, options: List<Pair<String, String>>, private val multi: Boolean, perRow: Int = 3) {
        val selected = LinkedHashSet<String>()
        private val views = LinkedHashMap<String, TextView>()
        val view = LinearLayout(ui.ctx).apply { orientation = LinearLayout.VERTICAL }

        init {
            for (chunk in options.chunked(perRow)) {
                val row = LinearLayout(ui.ctx).apply { orientation = LinearLayout.HORIZONTAL }
                for ((label, value) in chunk) {
                    val tv = ui.text(label, 14f, Neon.WHITE, true).apply {
                        gravity = Gravity.CENTER
                        setPadding(ui.dp(6), ui.dp(10), ui.dp(6), ui.dp(10))
                        setOnClickListener { toggle(value) }
                    }
                    views[value] = tv
                    row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { setMargins(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3)) })
                }
                for (i in chunk.size until perRow) {
                    row.addView(View(ui.ctx), LinearLayout.LayoutParams(0, 1, 1f))
                }
                view.addView(row)
            }
            refresh()
        }

        private fun toggle(v: String) {
            if (multi) {
                if (!selected.add(v)) selected.remove(v)
            } else {
                selected.clear()
                selected.add(v)
            }
            refresh()
        }

        fun set(values: Collection<String>) {
            selected.clear()
            selected.addAll(values.filter { views.containsKey(it) })
            refresh()
        }

        private fun refresh() {
            for ((v, tv) in views) {
                val on = selected.contains(v)
                tv.setTextColor(if (on) Neon.BG else Neon.WHITE)
                tv.background = if (on) ui.bg(Neon.CYAN, Neon.CYAN, 10) else ui.bg(Neon.BG, Neon.PURPLE, 10)
            }
        }
    }
}
