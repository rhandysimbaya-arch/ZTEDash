package com.rhandy.ztedash

import android.widget.EditText
import android.widget.LinearLayout
import org.json.JSONObject

class ApnActivity : NeonActivity() {
    override val screenTitle = "APN SETTINGS"
    override val screenSub = "Access point profiles for mobile data"

    data class P(val name: String, val apn: String, val auth: String, val user: String, val pass: String, val pdp: String)

    private val profiles = ArrayList<P>()
    private var autos: List<P> = emptyList()
    private var current = ""
    private var sel = -1

    private lateinit var status: android.widget.TextView
    private lateinit var listBox: LinearLayout
    private lateinit var fName: EditText
    private lateinit var fApn: EditText
    private lateinit var fUser: EditText
    private lateinit var fPass: EditText
    private lateinit var auth: Ui.Chips
    private lateinit var pdp: Ui.Chips

    private fun parse(raw: String): List<P> {
        if (raw.length < 6) return emptyList()
        return raw.split("||").filter { it.isNotEmpty() }.mapNotNull {
            val f = it.split("(\$)")
            if (f.size < 8) null else P(f[0], f[1], f[4].lowercase().ifEmpty { "none" }, f[5], f[6], f[7].ifEmpty { "IP" })
        }
    }

    override fun build() {
        val top = ui.card(col, Neon.CYAN, "CURRENT")
        status = ui.text("Loading…", 15f, Neon.WHITE, true)
        top.addView(status)

        val lc = ui.card(col, Neon.PURPLE, "PROFILES (tap to select)")
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lc.addView(listBox)

        val ed = ui.card(col, Neon.MAGENTA, "PROFILE DETAILS")
        ed.addView(ui.label("PROFILE NAME")); fName = ui.field("e.g. Smart"); ed.addView(fName)
        ed.addView(ui.label("APN")); fApn = ui.field("e.g. internet"); ed.addView(fApn)
        ed.addView(ui.label("AUTHENTICATION"))
        auth = Ui.Chips(ui, listOf("NONE" to "none", "CHAP" to "chap", "PAP" to "pap"), false)
        auth.set(listOf("none")); ed.addView(auth.view)
        ed.addView(ui.label("USER NAME")); fUser = ui.field("(optional)"); ed.addView(fUser)
        ed.addView(ui.label("PASSWORD")); fPass = ui.field("(optional)"); ed.addView(fPass)
        ed.addView(ui.label("IP TYPE"))
        pdp = Ui.Chips(ui, listOf("IPv4" to "IP", "IPv6" to "IPv6", "IPv4v6" to "IPv4v6"), false)
        pdp.set(listOf("IP")); ed.addView(pdp.view)

        ed.addView(ui.button("SAVE & SET AS DEFAULT", Neon.CYAN) { saveAndDefault() }, ui.lp(14))
        ed.addView(ui.button("SET SELECTED AS DEFAULT", Neon.LIME) { setDefaultOnly() }, ui.lp(8))
        ed.addView(ui.button("NEW PROFILE", Neon.MAGENTA) { newProfile() }, ui.lp(8))
        ed.addView(ui.button("DELETE PROFILE", Neon.RED) { deleteSel() }, ui.lp(8))
        ed.addView(ui.button("USE AUTOMATIC APN", Neon.PURPLE) { useAuto() }, ui.lp(8))
        load()
    }

    private fun load() {
        svc.run("done(s.getApnSettings());") { r ->
            if (r == null || r.has("error")) {
                status.text = "Could not read APN (log in first)"
                return@run
            }
            profiles.clear()
            profiles.addAll(parse(r.optString("APNs")))
            autos = parse(r.optString("autoApns"))
            current = r.optString("profileName")
            val mode = r.optString("apnMode")
            status.text = (if (mode == "auto") "AUTO · " else "MANUAL · ") + (if (current.isEmpty()) "--" else current)
            sel = profiles.indexOfFirst { it.name == current }
            renderList()
            if (sel >= 0) fill(sel)
        }
    }

    private fun renderList() {
        listBox.removeAllViews()
        if (profiles.isEmpty()) listBox.addView(ui.text("No manual profiles", 13f, Neon.DIM))
        profiles.forEachIndexed { i, p ->
            val on = i == sel
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ui.bg(if (on) 0xFF1B1838.toInt() else Neon.BG, if (on) Neon.CYAN else Neon.PURPLE, 10)
                setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
                setOnClickListener { sel = i; renderList(); fill(i) }
            }
            row.addView(ui.text(p.name + if (p.name == current) "   ● DEFAULT" else "", 15f, if (on) Neon.CYAN else Neon.WHITE, true))
            row.addView(ui.text(p.apn + " · " + p.pdp, 11f, Neon.DIM))
            listBox.addView(row, ui.lp(6))
        }
    }

    private fun fill(i: Int) {
        val p = profiles[i]
        fName.setText(p.name); fApn.setText(p.apn); fUser.setText(p.user); fPass.setText(p.pass)
        auth.set(listOf(p.auth)); pdp.set(listOf(p.pdp))
    }

    private fun newProfile() {
        sel = -1
        renderList()
        fName.setText(""); fApn.setText(""); fUser.setText(""); fPass.setText("")
        auth.set(listOf("none")); pdp.set(listOf("IP"))
    }

    private fun profileJs(p: P, index: Int): String {
        val v6 = p.pdp == "IPv4v6"
        return "{profileName:" + q(p.name) + ",pdpType:" + q(p.pdp) + ",index:" + index + ",wanApn:" + q(p.apn) +
            ",authMode:" + q(p.auth) + ",username:" + q(p.user) + ",password:" + q(p.pass) +
            ",dnsMode:'auto',dns1:'',dns2:'',wanApnV6:" + q(if (v6) p.apn else "") +
            ",authModeV6:" + q(if (v6) p.auth else "none") + ",usernameV6:" + q(if (v6) p.user else "") +
            ",passwordV6:" + q(if (v6) p.pass else "") + ",dnsModeV6:'auto',dns1V6:'',dns2V6:''}"
    }

    private fun defaultJs(p: P, index: Int, mode: String) =
        "{index:" + index + ",apnMode:'" + mode + "',pdpType:" + q(p.pdp) + ",profileName:" + q(p.name) +
            ",wanApn:" + q(p.apn) + ",authMode:" + q(p.auth) + ",username:" + q(p.user) + ",password:" + q(p.pass) +
            ",dnsMode:'auto',dns1:'',dns2:''}"

    private fun formProfile(): P? {
        val n = fName.text.toString().trim()
        val a = fApn.text.toString().trim()
        if (n.isEmpty() || a.isEmpty()) { toast("Profile name and APN are required"); return null }
        return P(n, a, auth.selected.firstOrNull() ?: "none", fUser.text.toString(), fPass.text.toString(), pdp.selected.firstOrNull() ?: "IP")
    }

    private fun saveAndDefault() {
        val p = formProfile() ?: return
        if (sel < 0 && profiles.size >= 10) { toast("Maximum 10 profiles. Delete one first."); return }
        if (sel < 0 && profiles.any { it.name == p.name }) { toast("A profile with this name already exists"); return }
        val idx = if (sel >= 0) sel else profiles.size
        ui.confirm("Save \"" + p.name + "\" and use it as the default? Mobile data may reconnect briefly.") {
            val body = "s.addOrEditApn(" + profileJs(p, idx) + ",function(r){if(!r||!r.result){done({ok:false});return;}" +
                "s.setDefaultApn(" + defaultJs(p, idx, "manual") + ",function(r2){done({ok:!!(r2&&r2.result)});},function(){done({ok:false});});},function(){done({ok:false});});"
            svc.run(body) { r ->
                toast(if (r?.optBoolean("ok") == true) "APN saved and set as default" else "Failed to save APN")
                load()
            }
        }
    }

    private fun setDefaultOnly() {
        if (sel < 0) { toast("Select a profile first"); return }
        val p = profiles[sel]
        ui.confirm("Use \"" + p.name + "\" as the default APN?") {
            svc.run("s.setDefaultApn(" + defaultJs(p, sel, "manual") + ",function(r){done({ok:!!(r&&r.result)});},function(){done({ok:false});});") { r ->
                toast(if (r?.optBoolean("ok") == true) "Default APN changed" else "Failed")
                load()
            }
        }
    }

    private fun deleteSel() {
        if (sel < 0) { toast("Select a profile first"); return }
        val p = profiles[sel]
        if (p.name == current) { toast("Can't delete the APN currently in use"); return }
        ui.confirm("Delete profile \"" + p.name + "\"?") {
            svc.run("s.deleteApn({index:" + sel + "},function(r){done({ok:!!(r&&r.result)});},function(){done({ok:false});});") { r ->
                toast(if (r?.optBoolean("ok") == true) "Deleted" else "Failed to delete")
                sel = -1
                load()
            }
        }
    }

    private fun useAuto() {
        val p = autos.firstOrNull()
        if (p == null) { toast("No automatic APN profiles on this SIM"); return }
        ui.confirm("Switch to automatic APN (" + p.name + ")?") {
            svc.run("s.setDefaultApn(" + defaultJs(p, 0, "auto") + ",function(r){done({ok:!!(r&&r.result)});},function(){done({ok:false});});") { r ->
                toast(if (r?.optBoolean("ok") == true) "Automatic APN enabled" else "Failed")
                load()
            }
        }
    }

    @Suppress("unused")
    private fun unused(j: JSONObject?) = j
}
