package com.rhandy.ztedash

import android.widget.EditText
import android.widget.TextView

class WifiActivity : NeonActivity() {
    override val screenTitle = "WI-FI SETTINGS"
    override val screenSub = "Name, password and security for each band"

    private class Band(
        val chip: String, val label: String,
        val ssid: EditText, val pass: EditText, val auth: Ui.Chips, val hidden: Ui.Chips, val max: EditText, val note: TextView
    )

    private val bands = ArrayList<Band>()

    private fun addBand(chip: String, label: String, color: Int) {
        val c = ui.card(col, color, label)
        val note = ui.text("Loading…", 11f, Neon.DIM)
        c.addView(note)
        c.addView(ui.label("WI-FI NAME (SSID)")); val ssid = ui.field("SSID"); c.addView(ssid)
        c.addView(ui.label("SECURITY"))
        val auth = Ui.Chips(ui, listOf(
            "OPEN" to "OPEN", "WPA2" to "WPA2PSK", "WPA3" to "WPA3PSK", "WPA2/WPA3" to "WPA2PSKWPA3PSK"
        ), false, 2)
        c.addView(auth.view)
        c.addView(ui.label("PASSWORD (8-63 characters)")); val pass = ui.field("Wi-Fi password"); c.addView(pass)
        c.addView(ui.label("NETWORK NAME"))
        val hidden = Ui.Chips(ui, listOf("VISIBLE" to "0", "HIDDEN" to "1"), false, 2)
        hidden.set(listOf("0")); c.addView(hidden.view)
        c.addView(ui.label("MAX DEVICES")); val max = ui.field("e.g. 10", "", true); c.addView(max)
        val b = Band(chip, label, ssid, pass, auth, hidden, max, note)
        bands.add(b)
        c.addView(ui.button("APPLY " + label, color) { apply(b) }, ui.lp(14))
    }

    override fun build() {
        addBand("0", "2.4 GHz", Neon.CYAN)
        addBand("1", "5 GHz", Neon.MAGENTA)
        load()
    }

    private fun load() {
        val body = "var l=s.getWifiAccessPointInfo()||[];var o=[];for(var i=0;i<l.length;i++){var e=l[i];" +
            "var pw='';try{pw=Base64.decode(e.Password);}catch(x){}" +
            "o.push({chip:String(e.ChipIndex),ap:String(e.AccessPointIndex),ssid:e.SSID,auth:e.AuthMode,pass:pw," +
            "hidden:e.ApBroadcastDisabled,max:e.ApMaxStationNumber,iso:e.ApIsolate,on:e.AccessPointSwitchStatus});}" +
            "done({list:o});"
        svc.run(body) { r ->
            val a = r?.optJSONArray("list")
            if (a == null) {
                bands.forEach { it.note.text = "Could not read Wi-Fi settings (log in first)" }
                return@run
            }
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (o.optString("ap") != "0") continue
                val b = bands.firstOrNull { it.chip == o.optString("chip") } ?: continue
                b.ssid.setText(o.optString("ssid"))
                b.pass.setText(o.optString("pass"))
                b.auth.set(listOf(o.optString("auth")))
                b.hidden.set(listOf(if (o.optString("hidden") == "1") "1" else "0"))
                b.max.setText(o.optString("max"))
                b.note.text = if (o.optString("on") == "1") "Radio is ON" else "Radio is OFF"
                b.note.setTag(o.optString("iso"))
            }
        }
    }

    private fun apply(b: Band) {
        val ssid = b.ssid.text.toString().trim()
        val pw = b.pass.text.toString()
        val auth = b.auth.selected.firstOrNull() ?: "WPA2PSK"
        val max = b.max.text.toString().trim().ifEmpty { "10" }
        if (ssid.isEmpty() || ssid.length > 32) { toast("Name must be 1-32 characters"); return }
        if (auth != "OPEN" && (pw.length < 8 || pw.length > 63)) { toast("Password must be 8-63 characters"); return }
        if (!Regex("[0-9]{1,3}").matches(max)) { toast("Max devices must be a number"); return }
        val iso = (b.note.tag as? String) ?: "0"
        ui.confirm("Apply Wi-Fi settings for " + b.label + "? Connected devices (including this phone) will be disconnected and must reconnect.") {
            val cipher = if (auth == "OPEN") "NONE" else "CCMP"
            val body = "s.setWifiAccessPointInfo({ChipIndex:'" + b.chip + "',AccessPointIndex:'0',AuthMode:" + q(auth) +
                ",Password:" + q(pw) + ",SSID:" + q(ssid) + ",ApMaxStationNumber:'" + max + "',ApIsolate:'" + iso +
                "',ApBroadcastDisabled:'" + (b.hidden.selected.firstOrNull() ?: "0") + "',cipher:'" + cipher +
                "'},function(r){done(r);});"
            svc.run(body) { reportResult(it, "Applied. Reconnect to the Wi-Fi if you were disconnected.") }
        }
    }
}
