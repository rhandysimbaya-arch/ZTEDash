package com.rhandy.ztedash

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import org.json.JSONObject
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private var host = "192.168.0.1"
    private lateinit var hidden: WebView
    @Volatile private var pageReady = false
    private var lastDevices: List<String> = emptyList()
    private val ui = Handler(Looper.getMainLooper())

    @Volatile private var running = false

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var gauge: GaugeView
    private lateinit var netChip: TextView
    private lateinit var downText: TextView
    private lateinit var upText: TextView
    private lateinit var spark: SparkView
    private lateinit var devicesVal: TextView
        private lateinit var dataVal: TextView
    private lateinit var uptimeVal: TextView
    private lateinit var sinrVal: TextView
    private lateinit var rsrqVal: TextView
    private lateinit var bandVal: TextView
    private lateinit var ipVal: TextView
    private lateinit var deviceList: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Neon.BG
        window.navigationBarColor = Neon.BG
        prefs = getSharedPreferences("zte", MODE_PRIVATE)
        host = prefs.getString("host", "192.168.0.1")!!
        val frame = FrameLayout(this)
        frame.addView(buildUi())
        hidden = buildHidden()
        frame.addView(hidden, FrameLayout.LayoutParams(1, 1))
        setContentView(frame)
    }

    override fun onResume() {
        super.onResume()
        startPolling()
    }

    override fun onPause() {
        super.onPause()
        running = false
        ui.removeCallbacks(tick)
    }

    // ---------- polling ----------

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            poll()
            ui.postDelayed(this, 2000)
        }
    }

    private fun startPolling() {
        if (running) return
        running = true
        if (!pageReady) hidden.loadUrl("http://$host/index.html")
        ui.postDelayed(tick, 800)
    }

    /** Hidden WebView on the router's own origin: same cookies/headers as the stock page. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildHidden(): WebView = WebView(this).apply {
        alpha = 0f
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        addJavascriptInterface(Bridge(), "Bridge")
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) { pageReady = true }
        }
    }

    private fun poll() {
        if (!pageReady) return
        val js = "(function(){function g(c,t){var u='/goform/goform_get_cmd_process?isTest=false&cmd='+" +
            "encodeURIComponent(c)+'&multi_data=1&_='+Date.now();" +
            "fetch(u,{credentials:'include'}).then(function(r){return r.text()})" +
            ".then(function(x){Bridge.onData(t,x)}).catch(function(e){Bridge.onData(t+'_err',String(e))});}" +
            "g('" + ZteApi.MAIN + "','main');g('station_list,lan_station_list','dev');})();"
        hidden.evaluateJavascript(js, null)
    }

    inner class Bridge {
        @JavascriptInterface
        fun onData(tag: String, text: String) {
            ui.post {
                try {
                    when (tag) {
                        "main" -> {
                            val j = JSONObject(text)
                            val li = j.optString("loginfo") == "ok" || j.optString("network_type").isNotEmpty()
                            render(ZteApi.Snap(j, lastDevices, li))
                        }
                        "dev" -> {
                            val j = JSONObject(text)
                            val out = ArrayList<String>()
                            for (key in arrayOf("station_list", "lan_station_list")) {
                                val arr = j.optJSONArray(key) ?: continue
                                for (k in 0 until arr.length()) {
                                    val o = arr.optJSONObject(k) ?: continue
                                    val name = o.optString("hostname").ifEmpty { o.optString("mac_addr") }
                                    val ip = o.optString("ip_addr")
                                    out.add(if (ip.isNotEmpty()) "$name  ·  $ip" else name)
                                }
                            }
                            lastDevices = out
                            if (out.isNotEmpty()) {
                                devicesVal.text = out.size.toString()
                                deviceList.text = out.joinToString("\n")
                            }
                        }
                        else -> showOffline(text)
                    }
                } catch (e: Exception) {
                    showOffline("Unexpected reply (log in via the router page)")
                }
            }
        }
    }

    private fun showOffline(msg: String) {
        statusDot.background = dot(Neon.RED)
        statusText.setTextColor(Neon.RED)
        statusText.text = "OFFLINE · connect to the F50 Wi-Fi"
        netChip.text = msg.take(60)
    }

    private fun render(s: ZteApi.Snap) {
        val j = s.raw
        fun g(k: String) = j.optString(k, "").trim()
        fun first(vararg k: String): String = k.map { g(it) }.firstOrNull { it.isNotEmpty() } ?: ""

        val online = g("ppp_status").contains("connected") && !g("ppp_status").contains("dis") ||
            (g("wan_ipaddr").isNotEmpty() && g("wan_ipaddr") != "0.0.0.0")
        val color = if (online) Neon.LIME else Neon.MAGENTA
        statusDot.background = dot(color)
        statusText.setTextColor(color)
        statusText.text = when {
            !s.loggedIn -> "NOT LOGGED IN · TAP HERE TO LOG IN"
            online -> "ONLINE"
            else -> "NO INTERNET"
        }
        if (!s.loggedIn) statusText.setTextColor(Neon.MAGENTA)

        val nt = g("network_type").uppercase().ifEmpty { "--" }
        val z5 = g("Z5g_rsrp").toFloatOrNull()
        val use5g = z5 != null && z5 != 0f
        val rsrp = if (use5g) z5 else g("lte_rsrp").toFloatOrNull() ?: g("rssi").toFloatOrNull()
        val sinr = if (use5g) first("Z5g_SINR", "Z5g_snr") else first("lte_snr")
        val rsrq = if (use5g) g("Z5g_rsrq") else g("lte_rsrq")
        val band = if (use5g) first("nr5g_action_band", "wan_active_band") else first("lte_ca_pcell_band", "wan_active_band")

        if (rsrp != null && rsrp < 0f) {
            gauge.valueText = rsrp.toInt().toString()
            gauge.setPercent((rsrp + 120f) / 50f * 100f)
        } else {
            gauge.valueText = "--"
            gauge.setPercent(0f)
        }
        gauge.labelText = if (use5g) "5G SIGNAL" else "SIGNAL"
        gauge.invalidate()
        val prov = g("network_provider")
        netChip.text = listOf(nt, band.let { if (it.isEmpty()) "" else "B$it".replace("BB", "B").replace("Bn", "n") }, prov)
            .filter { it.isNotEmpty() }.joinToString("  ·  ")

        val rx = g("realtime_rx_thrpt").toFloatOrNull() ?: 0f
        val tx = g("realtime_tx_thrpt").toFloatOrNull() ?: 0f
        downText.text = rate(rx)
        upText.text = rate(tx)
        spark.push(rx, tx)

        val devCount = if (s.devices.isNotEmpty()) s.devices.size
        else (first("wifi_access_sta_num", "sta_count").toIntOrNull() ?: 0)
        devicesVal.text = devCount.toString()
        deviceList.text = if (s.devices.isEmpty()) "No device details" else s.devices.joinToString("\n")

        val used = (g("monthly_rx_bytes").toDoubleOrNull() ?: 0.0) + (g("monthly_tx_bytes").toDoubleOrNull() ?: 0.0)
        dataVal.text = bytes(used)
        uptimeVal.text = dur(g("realtime_time").toLongOrNull() ?: 0L)

        sinrVal.text = if (sinr.isEmpty()) "--" else "$sinr dB"
        rsrqVal.text = if (rsrq.isEmpty()) "--" else "$rsrq dB"
        bandVal.text = if (band.isEmpty()) "--" else band
        ipVal.text = g("wan_ipaddr").ifEmpty { "--" }
    }

    // ---------- formatting ----------

    private fun rate(b: Float): String = when {
        b >= 1_000_000f -> String.format(Locale.US, "%.2f MB/s", b / 1_000_000f)
        b >= 1_000f -> String.format(Locale.US, "%.1f KB/s", b / 1_000f)
        else -> String.format(Locale.US, "%.0f B/s", b)
    }

    private fun bytes(b: Double): String = when {
        b >= 1e9 -> String.format(Locale.US, "%.2f GB", b / 1e9)
        b >= 1e6 -> String.format(Locale.US, "%.1f MB", b / 1e6)
        else -> String.format(Locale.US, "%.0f KB", b / 1e3)
    }

    private fun dur(s: Long): String {
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m ${s % 60}s"
    }

    // ---------- UI building ----------

    private fun dot(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    private fun cardBg(stroke: Int) = GradientDrawable().apply {
        setColor(Neon.CARD)
        cornerRadius = dp(18).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false, spacing: Float = 0f) =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            letterSpacing = spacing
            if (bold) typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }

    private fun card(stroke: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = cardBg(stroke)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(12) }
    }

    /** Small stat tile; returns (tile, valueTextView). */
    private fun tile(label: String, color: Int): Pair<LinearLayout, TextView> {
        val t = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg(color and 0x00FFFFFF or 0x55000000)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        t.addView(text(label, 10f, Neon.DIM, false, 0.2f))
        val v = text("--", 22f, color, true)
        v.setShadowLayer(14f, 0f, 0f, color)
        t.addView(v)
        return Pair(t, v)
    }

    private fun row(a: View, b: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(12) }
        addView(a, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }

        // header
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = text("ZTE F50", 26f, Neon.CYAN, true, 0.15f)
        title.setShadowLayer(18f, 0f, 0f, Neon.CYAN)
        titles.addView(title)
        val sub = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusDot = View(this).apply { background = dot(Neon.DIM) }
        sub.addView(statusDot, LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(7) })
        statusText = text("CONNECTING…", 11f, Neon.DIM, false, 0.12f)
        statusText.setOnClickListener {
            startActivity(android.content.Intent(this, WebActivity::class.java).putExtra("route", ""))
        }
        sub.addView(statusText)
        titles.addView(sub)
        head.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val gear = text("⚙", 26f, Neon.MAGENTA).apply {
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener { showSettings() }
        }
        head.addView(gear)
        root.addView(head)

        // signal gauge
        val hero = card(Neon.PURPLE)
        gauge = GaugeView(this)
        hero.addView(gauge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)))
        netChip = text("--", 13f, Neon.CYAN, true, 0.1f).apply { gravity = Gravity.CENTER }
        hero.addView(netChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(hero)

        // speed
        val speed = card(Neon.CYAN)
        val sRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dBox.addView(text("▼ DOWNLOAD", 10f, Neon.DIM, false, 0.2f))
        downText = text("0 B/s", 24f, Neon.CYAN, true).apply { setShadowLayer(14f, 0f, 0f, Neon.CYAN) }
        dBox.addView(downText)
        val uBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        uBox.addView(text("UPLOAD ▲", 10f, Neon.DIM, false, 0.2f))
        upText = text("0 B/s", 24f, Neon.MAGENTA, true).apply { setShadowLayer(14f, 0f, 0f, Neon.MAGENTA) }
        uBox.addView(upText)
        sRow.addView(dBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sRow.addView(uBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        speed.addView(sRow)
        spark = SparkView(this)
        speed.addView(spark, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110)).apply { topMargin = dp(10) })
        root.addView(speed)

        // stat tiles
        val (t1, v1) = tile("DEVICES", Neon.CYAN); devicesVal = v1
        val (t2, v2) = tile("PERFORMANCE MODE", Neon.LIME)
        v2.text = "OPEN ›"
        t2.setOnClickListener { openRoute("performance_mode") }
        root.addView(row(t1, t2))
        val (tS, vS) = tile("ADVANCED SETTINGS", Neon.CYAN)
        vS.text = "OPEN ›"
        tS.setOnClickListener { openRoute(prefs.getString("route_settings", "") ?: "") }
        val (tB, vB) = tile("BAND SELECTION", Neon.MAGENTA)
        vB.text = "OPEN ›"
        tB.setOnClickListener { openRoute(prefs.getString("route_band", "network_debug") ?: "") }
        root.addView(row(tS, tB))
        val (t3, v3) = tile("DATA THIS MONTH", Neon.MAGENTA); dataVal = v3
        val (t4, v4) = tile("UPTIME", 0xFFB77CFF.toInt()); uptimeVal = v4
        root.addView(row(t3, t4))
        val (t5, v5) = tile("SINR", Neon.LIME); sinrVal = v5
        val (t6, v6) = tile("RSRQ", Neon.CYAN); rsrqVal = v6
        root.addView(row(t5, t6))
        val (t7, v7) = tile("BAND", Neon.MAGENTA); bandVal = v7
        val (t8, v8) = tile("WAN IP", Neon.CYAN); ipVal = v8
        v8.textSize = 16f
        root.addView(row(t7, t8))

        // devices
        val dev = card(Neon.MAGENTA)
        dev.addView(text("CONNECTED DEVICES", 10f, Neon.DIM, false, 0.2f))
        deviceList = text("--", 13f, Neon.WHITE).apply { setLineSpacing(0f, 1.25f); setPadding(0, dp(6), 0, 0) }
        dev.addView(deviceList)
        root.addView(dev)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Neon.BG)
            isVerticalScrollBarEnabled = false
            addView(root)
        }
        return scroll
    }

    private fun openRoute(route: String) {
        startActivity(android.content.Intent(this, WebActivity::class.java).putExtra("route", route))
    }

    private fun showSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), 0)
        }
        val ip = EditText(this).apply { setText(host); hint = "Router IP (192.168.0.1)"; inputType = InputType.TYPE_CLASS_TEXT }
        box.addView(ip)
        val rb = EditText(this).apply {
            setText(prefs.getString("route_band", "network_debug")); hint = "Band selection page (after #)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val rs = EditText(this).apply {
            setText(prefs.getString("route_settings", "")); hint = "Settings page (after #, blank = home)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        box.addView(rb)
        box.addView(rs)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Router address")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                host = ip.text.toString().trim().ifEmpty { "192.168.0.1" }
                prefs.edit().putString("host", host)
                    .putString("route_band", rb.text.toString().trim().removePrefix("#"))
                    .putString("route_settings", rs.text.toString().trim().removePrefix("#")).apply()
                pageReady = false
                hidden.loadUrl("http://$host/index.html")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
