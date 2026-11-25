package com.rhandy.ztedash

import android.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class SmsActivity : NeonActivity() {
    override val screenTitle = "SMS"
    override val screenSub = "Messages on the router's SIM"

    private lateinit var number: EditText
    private lateinit var body: EditText
    private lateinit var listBox: LinearLayout
    private lateinit var info: TextView

    override fun build() {
        val c = ui.card(col, Neon.CYAN, "NEW MESSAGE")
        number = ui.field("Phone number")
        number.inputType = android.text.InputType.TYPE_CLASS_PHONE
        c.addView(number)
        body = ui.field("Message")
        body.minLines = 3
        body.gravity = android.view.Gravity.TOP
        c.addView(body, ui.lp(8))
        c.addView(ui.button("SEND", Neon.CYAN) { send() }, ui.lp(10))

        val l = ui.card(col, Neon.MAGENTA, "INBOX & SENT (newest first)")
        info = ui.text("Loading…", 12f, Neon.DIM)
        l.addView(info)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        l.addView(listBox)
        l.addView(ui.button("REFRESH", Neon.MAGENTA) { load() }, ui.lp(10))
        load()
    }

    private fun load() {
        val js = "s.getSMSMessages({page:0,smsCount:500,nMessageStoreType:1,tags:10,orderBy:'order by id desc'}," +
            "function(r){var o=[];var m=r.messages||[];for(var i=0;i<m.length;i++){var x=m[i];" +
            "o.push({id:x.id,number:x.number,content:x.content,time:x.time,tag:x.tag,isNew:x.isNew});}done({list:o});}," +
            "function(){done({list:[]});});"
        svc.run(js) { r ->
            val a = r?.optJSONArray("list")
            listBox.removeAllViews()
            if (a == null || r.has("error")) { info.text = "Could not read messages (log in first)"; return@run }
            info.text = if (a.length() == 0) "No messages" else a.length().toString() + " message(s)"
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val tag = o.optString("tag")
                val sent = tag == "2" || tag == "3"
                val unread = o.optBoolean("isNew", false)
                val id = o.optString("id")
                val num = o.optString("number")
                val content = o.optString("content")
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ui.bg(Neon.BG, if (unread) Neon.LIME else if (sent) Neon.PURPLE else Neon.CYAN, 10)
                    setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
                    setOnClickListener { open(id, num, content, unread) }
                }
                row.addView(ui.text((if (sent) "⬆ " else "⬇ ") + num + if (unread) "   NEW" else "", 14f,
                    if (unread) Neon.LIME else Neon.WHITE, true))
                row.addView(ui.text(content.take(160), 13f, Neon.WHITE))
                row.addView(ui.text(o.optString("time"), 10f, Neon.DIM))
                listBox.addView(row, ui.lp(6))
            }
        }
    }

    private fun open(id: String, num: String, content: String, unread: Boolean) {
        if (unread) svc.run("s.setSmsRead({ids:[" + q(id) + "]},function(r){done({});});") { }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(num)
            .setMessage(content)
            .setPositiveButton("Reply") { _, _ -> number.setText(num); body.requestFocus() }
            .setNeutralButton("Delete") { _, _ -> delete(id) }
            .setNegativeButton("Close") { _, _ -> if (unread) load() }
            .show()
    }

    private fun delete(id: String) {
        ui.confirm("Delete this message?") {
            svc.run("s.deleteMessage({ids:[" + q(id) + "]},function(r){done({ok:true});},function(){done({ok:false});});") {
                toast(if (it?.optBoolean("ok") == true) "Deleted" else "Delete failed")
                load()
            }
        }
    }

    private fun send() {
        val n = number.text.toString().trim()
        val m = body.text.toString()
        if (n.isEmpty() || m.isEmpty()) { toast("Enter a number and a message"); return }
        toast("Sending…")
        svc.run("s.sendSMS({number:" + q(n) + ",message:" + q(m) + ",id:-1},function(r){done({ok:true});},function(e){done({ok:false});});") {
            if (it?.optBoolean("ok") == true) { toast("Message sent"); body.setText("") } else toast("Sending failed")
            load()
        }
    }
}
