package com.rhandy.ztedash

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONObject

/** Base for native neon screens that talk to the router through [Svc]. */
abstract class NeonActivity : Activity() {
    lateinit var ui: Ui
    lateinit var svc: Svc
    lateinit var col: LinearLayout

    abstract val screenTitle: String
    open val screenSub: String = ""
    abstract fun build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Neon.BG
        window.navigationBarColor = Neon.BG
        ui = Ui(this)
        val host = getSharedPreferences("zte", MODE_PRIVATE).getString("host", "192.168.0.1")!!
        svc = Svc(this, host)
        val (sv, c) = ui.page(screenTitle, screenSub) { finish() }
        col = c
        val frame = FrameLayout(this)
        frame.addView(sv)
        frame.addView(svc.web, FrameLayout.LayoutParams(1, 1))
        setContentView(frame)
        build()
    }

    override fun onDestroy() {
        super.onDestroy()
        svc.destroy()
    }

    fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    fun reportResult(r: JSONObject?, okMsg: String) {
        if (r != null && r.optString("result") == "success") toast(okMsg)
        else toast("Failed: " + (r?.toString()?.take(120) ?: "no reply from router (are you logged in?)"))
    }

    fun q(s: String): String = JSONObject.quote(s)

    /** Reads a router value that may be a JSON array or a comma separated string. */
    fun list(r: JSONObject?, key: String): List<String> {
        if (r == null) return emptyList()
        val arr = r.optJSONArray(key)
        if (arr != null) return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
        return r.optString(key, "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
