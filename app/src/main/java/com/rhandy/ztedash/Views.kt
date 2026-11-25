package com.rhandy.ztedash

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import kotlin.math.max
import kotlin.math.min

object Neon {
    const val BG = 0xFF06050F.toInt()
    const val CARD = 0xFF0E0C22.toInt()
    const val CYAN = 0xFF00F0FF.toInt()
    const val MAGENTA = 0xFFFF2BD6.toInt()
    const val LIME = 0xFFB6FF00.toInt()
    const val PURPLE = 0xFF8A2BFF.toInt()
    const val DIM = 0xFF7C7A9E.toInt()
    const val WHITE = 0xFFF2F0FF.toInt()
    const val RED = 0xFFFF3860.toInt()
}

/** 270 degree arc gauge with neon glow. */
class GaugeView(c: Context) : View(c) {
    private var shown = 0f
    private var anim: ValueAnimator? = null
    var valueText = "--"
    var unitText = "dBm"
    var labelText = "SIGNAL"

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFF1B1838.toInt()
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Neon.WHITE
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Neon.DIM
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.2f
    }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    fun setPercent(p: Float) {
        anim?.cancel()
        val a = ValueAnimator.ofFloat(shown, p.coerceIn(0f, 100f))
        a.duration = 600
        a.addUpdateListener { shown = it.animatedValue as Float; invalidate() }
        a.start()
        anim = a
    }

    override fun onDraw(cv: Canvas) {
        val s = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val sw = s * 0.075f
        val r = s / 2f - sw * 1.6f
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        track.strokeWidth = sw
        cv.drawArc(rect, 135f, 270f, false, track)

        val shader = SweepGradient(
            cx, cy,
            intArrayOf(Neon.CYAN, Neon.PURPLE, Neon.MAGENTA, Neon.MAGENTA),
            floatArrayOf(0f, 0.4f, 0.75f, 1f)
        )
        shader.setLocalMatrix(Matrix().apply { postRotate(135f, cx, cy) })
        arc.shader = shader
        arc.strokeWidth = sw
        arc.setShadowLayer(sw * 0.9f, 0f, 0f, Neon.CYAN)
        if (shown > 0.5f) cv.drawArc(rect, 135f, 270f * shown / 100f, false, arc)

        big.textSize = s * 0.22f
        cv.drawText(valueText, cx, cy + s * 0.05f, big)
        small.textSize = s * 0.062f
        cv.drawText(unitText, cx, cy + s * 0.14f, small)
        cv.drawText(labelText, cx, cy - s * 0.17f, small)
    }
}

/** Two-line live throughput graph (download = cyan, upload = magenta). */
class SparkView(c: Context) : View(c) {
    private val n = 60
    private val down = FloatArray(n)
    private val up = FloatArray(n)

    private val grid = Paint().apply { color = 0xFF1B1838.toInt(); strokeWidth = 2f }
    private val lineD = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f; color = Neon.CYAN; strokeJoin = Paint.Join.ROUND
        setShadowLayer(12f, 0f, 0f, Neon.CYAN)
    }
    private val lineU = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f; color = Neon.MAGENTA; strokeJoin = Paint.Join.ROUND
        setShadowLayer(12f, 0f, 0f, Neon.MAGENTA)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    fun push(d: Float, u: Float) {
        System.arraycopy(down, 1, down, 0, n - 1)
        System.arraycopy(up, 1, up, 0, n - 1)
        down[n - 1] = d
        up[n - 1] = u
        invalidate()
    }

    private fun path(a: FloatArray, w: Float, h: Float, top: Float, close: Boolean): Path {
        val p = Path()
        for (i in 0 until n) {
            val x = w * i / (n - 1)
            val y = h - (a[i] / top) * (h - 8f) - 4f
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        if (close) { p.lineTo(w, h); p.lineTo(0f, h); p.close() }
        return p
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 1..3) cv.drawLine(0f, h * i / 4f, w, h * i / 4f, grid)
        var top = 50_000f
        for (i in 0 until n) top = max(top, max(down[i], up[i]))
        top *= 1.15f
        fill.shader = LinearGradient(0f, 0f, 0f, h, 0x5500F0FF, 0x0000F0FF, Shader.TileMode.CLAMP)
        cv.drawPath(path(down, w, h, top, true), fill)
        cv.drawPath(path(down, w, h, top, false), lineD)
        cv.drawPath(path(up, w, h, top, false), lineU)
    }
}
