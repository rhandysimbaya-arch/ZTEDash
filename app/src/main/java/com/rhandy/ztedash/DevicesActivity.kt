package com.rhandy.ztedash

import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

class DevicesActivity : NeonActivity() {
    override val screenTitle = "CONNECTED DEVICES"
    override val screenSub = "Tap a device to rename or block it"

    private lateinit var listBox: LinearLayout
    private lateinit var blockBox: LinearLayout
    private lateinit var count: TextView
    private var userIp = ""
    private var blackMac = ""
    private var blackName = ""
    private var whiteMac = ""
    private var whiteName = ""

    override fun build() {
        val c = ui.card(col, Neon.CYAN, "ONLINE NOW")
        count = ui.text("Loading…", 12f, Neon.DIM)
        c.addView(count)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(listBox)
        c.addView(ui.button("REFRESH", Neon.CYAN) { load() }, ui.lp(10))

        val b = ui.card(col, Neon.RED, "BLOCKED DEVICES")
        blockBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        b.addView(blockBox)
        load()
    }

    private fun load() {
        val body = "var ip='';try{ip=s.getUserIPAddr().user_ip_addr;}catch(x){}" +
            "var hn=[];try{hn=s.getHostNameList({}).devices||[];}catch(x){}" +
            "s.getCurrentlyAttachedDevicesInfo({},function(a){var o=[];var l=a.attachedDevices||[];" +
            "for(var i=0;i<l.length;i++){var d=l[i];var nm=d.hostName;" +
            "for(var k=0;k<hn.length;k++){if(hn[k].mac==d.macAddress){nm=hn[k].hostname;}}" +
            "o.push({name:nm,mac:d.macAddress,ip:d.ipAddress});}" +
            "s.getDeviceAccessControlList({},function(acl){done({ip:ip,list:o,acl:acl});},function(){done({ip:ip,list:o,acl:{}});});" +
            "},function(){done({error:'x'});});"
        svc.run(body) { r ->
            if (r == null || r.has("error")) {
                count.text = "Could not read devices (log in first)"
                return@run
            }
            userIp = r.optString("ip")
            val acl = r.optJSONObject("acl") ?: JSONObject()
            blackMac = acl.optString("BlackMacList"); blackName = acl.optString("BlackNameList")
            whiteMac = acl.optString("WhiteMacList"); whiteName = acl.optString("WhiteNameList")
            val a = r.optJSONArray("list")
            listBox.removeAllViews()
            count.text = (a?.length() ?: 0).toString() + " device(s) connected"
            for (i in 0 until (a?.length() ?: 0)) {
                val o = a!!.optJSONObject(i) ?: continue
                val name = o.optString("name").ifEmpty { "Unknown" }
                val ip = o.optString("ip")
                val mac = o.optString("mac")
                val me = ip == userIp && ip.isNotEmpty()
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ui.bg(Neon.BG, Neon.PURPLE, 10)
                    setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
                    setOnClickListener { actions(name, mac, me) }
                }
                row.addView(ui.text(name + if (me) "   (this phone)" else "", 15f, Neon.WHITE, true))
                row.addView(ui.text("$ip · $mac", 11f, Neon.DIM))
                listBox.addView(row, ui.lp(6))
            }
            renderBlocked()
        }
    }

    private fun renderBlocked() {
        blockBox.removeAllViews()
        val macs = blackMac.split(";").filter { it.isNotEmpty() }
        val names = blackName.split(";")
        if (macs.isEmpty()) blockBox.addView(ui.text("No blocked devices", 13f, Neon.DIM))
        macs.forEachIndexed { i, m ->
            val nm = names.getOrNull(i) ?: ""
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ui.bg(Neon.BG, Neon.RED, 10)
                setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
                setOnClickListener { ui.confirm("Unblock " + nm.ifEmpty { m } + "?") { unblock(m) } }
            }
            row.addView(ui.text(nm.ifEmpty { "Unknown" }, 15f, Neon.WHITE, true))
            row.addView(ui.text(m + "  ·  tap to unblock", 11f, Neon.DIM))
            blockBox.addView(row, ui.lp(6))
        }
    }

    private fun actions(name: String, mac: String, me: Boolean) {
        val items = arrayOf("Rename", "Block")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(name)
            .setItems(items) { _, which ->
                if (which == 0) rename(name, mac) else if (me) toast("You can't block this phone") else block(name, mac)
            }
            .show()
    }

    private fun rename(name: String, mac: String) {
        val f = ui.field("New name", name)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Rename device")
            .setView(f)
            .setPositiveButton("Save") { _, _ ->
                val n = f.text.toString().trim()
                if (n.isEmpty()) { toast("Name can't be empty"); return@setPositiveButton }
                svc.run("s.editHostName({hostname:" + q(n) + ",mac:" + q(mac) + "},function(r){done({ok:true});},function(){done({ok:false});});") {
                    toast(if (it?.optBoolean("ok") == true) "Renamed" else "Rename failed")
                    load()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun aclCall(bm: String, bn: String, ok: String) {
        val body = "s.setDeviceAccessControlList({AclMode:'2',WhiteMacList:" + q(whiteMac) + ",WhiteNameList:" + q(whiteName) +
            ",BlackMacList:" + q(bm) + ",BlackNameList:" + q(bn) + "},function(r){done(r);},function(){done({});});"
        svc.run(body) {
            reportResult(it, ok)
            load()
        }
    }

    private fun block(name: String, mac: String) {
        if (blackMac.split(";").contains(mac)) { toast("Already blocked"); return }
        ui.confirm("Block $name? It will lose Wi-Fi access until you unblock it.") {
            aclCall(blackMac + mac + ";", blackName + name.replace(";", " ") + ";", "Blocked $name")
        }
    }

    private fun unblock(mac: String) {
        val macs = blackMac.split(";").filter { it.isNotEmpty() }
        val names = blackName.split(";")
        val nm = StringBuilder()
        val mm = StringBuilder()
        macs.forEachIndexed { i, m ->
            if (m != mac) { mm.append(m).append(';'); nm.append(names.getOrNull(i) ?: "").append(';') }
        }
        aclCall(mm.toString(), nm.toString(), "Unblocked")
    }
}
