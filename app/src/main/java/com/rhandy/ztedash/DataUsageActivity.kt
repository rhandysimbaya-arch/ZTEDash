package com.rhandy.ztedash

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.EditText
import android.widget.TextView
import org.json.JSONObject
import java.util.Locale

class DataUsageActivity : NeonActivity() {
    override val screenTitle = "DATA USAGE"
    override val screenSub = "Monthly usage, limit and alerts"

    private lateinit var usedText: TextView
    private lateinit var timeText: TextView
    private lateinit var bar: Bar
    private lateinit var limitOn: Ui.Chips
    private lateinit var limitVal: EditText
    private lateinit var unit: Ui.Chips
    private lateinit var alertPct: EditText
    private lateinit var clearOn: Ui.Chips
    private lateinit var clearDate: EditText
    private lateinit var calib: EditText
    private var raw: JSONObject = JSONObject()

    inner class Bar(c: android.content.Context) : View(c) {
        var frac = 0f
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(cv: Canvas) {
            val h = height.toFloat()
            p.color = 0xFF1B1838.toInt()
            cv.drawRoundRect(0f, 0f, width.toFloat(), h, h / 2, h / 2, p)
            p.color = if (frac > 0.9f) Neon.RED else if (frac > 0.7f) Neon.MAGENTA else Neon.CYAN
            p.setShadowLayer(10f, 0f, 0f, p.color)
            if (frac > 0f) cv.drawRoundRect(0f, 0f, width * frac.coerceIn(0.02f, 1f), h, h / 2, h / 2, p)
        }
    }

    private fun fmt(b: Double): String = when {
        b >= 1099511627776.0 -> String.format(Locale.US, "%.2f TB", b / 1099511627776.0)
        b >= 1073741824.0 -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
        b >= 1048576.0 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
        else -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    }

    override fun build() {
        val u = ui.card(col, Neon.CYAN, "THIS MONTH")
        usedText = ui.text("--", 26f, Neon.CYAN, true).apply { setShadowLayer(14f, 0f, 0f, Neon.CYAN) }
        u.addView(usedText)
        bar = Bar(this)
        u.addView(bar, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(14)).apply { topMargin = ui.dp(10) })
        timeText = ui.text("", 12f, Neon.DIM).apply { setPadding(0, ui.dp(8), 0, 0) }
        u.addView(timeText)

        val l = ui.card(col, Neon.MAGENTA, "DATA LIMIT")
        limitOn = Ui.Chips(ui, listOf("ON" to "1", "OFF" to "0"), false, 2)
        l.addView(limitOn.view)
        l.addView(ui.label("MONTHLY LIMIT"))
        limitVal = ui.field("amount", "", true)
        l.addView(limitVal)
        unit = Ui.Chips(ui, listOf("MB" to "1", "GB" to "1024", "TB" to "1048576"), false)
        unit.set(listOf("1024"))
        l.addView(unit.view)
        l.addView(ui.label("ALERT AT (% OF LIMIT)"))
        alertPct = ui.field("e.g. 90", "", true)
        l.addView(alertPct)
        l.addView(ui.label("AUTO-CLEAR EACH MONTH"))
        clearOn = Ui.Chips(ui, listOf("ON" to "on", "OFF" to "off"), false, 2)
        l.addView(clearOn.view)
        l.addView(ui.label("CLEAR ON DAY OF MONTH"))
        clearDate = ui.field("1-31", "", true)
        l.addView(clearDate)
        l.addView(ui.button("SAVE LIMIT SETTINGS", Neon.MAGENTA) { save() }, ui.lp(14))

        val c = ui.card(col, Neon.LIME, "CALIBRATE USED DATA")
        c.addView(ui.text("Match this to your carrier's figure (in GB).", 11f, Neon.DIM))
        calib = ui.field("used GB, e.g. 12.5")
        calib.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        c.addView(calib, ui.lp(8))
        c.addView(ui.button("SET USED DATA", Neon.LIME) { calibrate() }, ui.lp(10))
        load()
    }

    private fun load() {
        svc.run("done(s.getTrafficAlertInfo());") { r ->
            if (r == null || r.has("error")) {
                usedText.text = "--"
                timeText.text = "Could not read usage (log in first)"
                return@run
            }
            raw = r
            val used = r.optString("monthlySent", "0").toDoubleOrNull().let { it ?: 0.0 } +
                r.optString("monthlyReceived", "0").toDoubleOrNull().let { it ?: 0.0 }
            usedText.text = fmt(used)
            val parts = r.optString("limitDataMonth", "0_1").split("_")
            val amount = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
            val u = parts.getOrNull(1) ?: "1"
            val limitBytes = amount * when (u) { "1" -> 1048576.0; "1024" -> 1073741824.0; else -> 1099511627776.0 }
            val on = r.optString("dataLimitChecked") != "0"
            if (on && limitBytes > 0) {
                bar.frac = (used / limitBytes).toFloat()
                usedText.text = fmt(used) + "  /  " + fmt(limitBytes)
            } else bar.frac = 0f
            bar.invalidate()
            val secs = r.optString("monthlyConnectedTime", "0").toLongOrNull() ?: 0L
            timeText.text = "Connected time: " + (secs / 3600) + "h " + ((secs % 3600) / 60) + "m"
            limitOn.set(listOf(if (on) "1" else "0"))
            limitVal.setText(if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString())
            unit.set(listOf(if (u == "1" || u == "1024" || u == "1048576") u else "1024"))
            alertPct.setText(r.optString("alertDataReach", "0"))
            clearOn.set(listOf(if (r.optString("autoClearTraffic") == "on") "on" else "off"))
            clearDate.setText(r.optString("traffic_clear_date", ""))
        }
    }

    private fun save() {
        val amount = limitVal.text.toString().trim()
        val pct = alertPct.text.toString().trim().ifEmpty { "0" }
        val day = clearDate.text.toString().trim().ifEmpty { raw.optString("traffic_clear_date", "1") }
        if (limitOn.selected.firstOrNull() == "1" && !Regex("[0-9]{1,6}").matches(amount)) { toast("Enter the monthly limit as a whole number"); return }
        if (!Regex("[0-9]{1,3}").matches(pct) || pct.toInt() > 100) { toast("Alert % must be 0-100"); return }
        if (!Regex("[0-9]{1,2}").matches(day) || day.toInt() !in 1..31) { toast("Clear day must be 1-31"); return }
        val on = limitOn.selected.firstOrNull() ?: "0"
        val body = "s.setTrafficAlertInfo({dataLimitChecked:'" + on + "',wan_auto_clear_flow_data_switch:'" +
            (clearOn.selected.firstOrNull() ?: "off") + "',dataLimitTypeChecked:'1',limitDataMonth:'" +
            (amount.ifEmpty { "0" }) + "_" + (unit.selected.firstOrNull() ?: "1024") + "',alertDataReach:'" + pct +
            "',limitTimeMonth:" + q(raw.optString("limitTimeMonth", "0")) + ",alertTimeReach:" + q(raw.optString("alertTimeReach", "0")) +
            ",traffic_clear_date:'" + day + "'},function(r){done(r);},function(){done({});});"
        svc.run(body) {
            reportResult(it, "Data limit settings saved")
            load()
        }
    }

    private fun calibrate() {
        val gb = calib.text.toString().trim().toDoubleOrNull()
        if (gb == null || gb < 0) { toast("Enter used data in GB"); return }
        val bytes = Math.round(gb * 1073741824.0)
        ui.confirm("Set this month's used data to " + gb + " GB?") {
            svc.run("s.trafficCalibration({way:'data',value:" + bytes + "},function(r){done({ok:true});},function(){done({ok:false});});") {
                toast(if (it?.optBoolean("ok") == true) "Used data updated" else "Failed")
                load()
            }
        }
    }
}
