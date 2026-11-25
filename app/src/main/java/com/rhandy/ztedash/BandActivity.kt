package com.rhandy.ztedash

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

class BandActivity : NeonActivity() {
    override val screenTitle = "BAND SELECTION"
    override val screenSub = "4G / 5G band lock · cell lock"

    private lateinit var lte: Ui.Chips
    private lateinit var nr: Ui.Chips
    private lateinit var rat: Ui.Chips
    private lateinit var pci: EditText
    private lateinit var earfcn: EditText
    private lateinit var lockedText: TextView
    private lateinit var neighText: TextView

    private fun field(hint: String) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(Neon.DIM)
        setTextColor(Neon.WHITE)
        inputType = InputType.TYPE_CLASS_NUMBER
        background = ui.bg(Neon.BG, Neon.PURPLE, 10)
        setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
    }

    override fun build() {
        // 4G
        val c4 = ui.card(col, Neon.CYAN, "4G BANDS (tap to select, then apply)")
        lte = Ui.Chips(ui, listOf(1, 3, 5, 8, 34, 38, 39, 40, 41).map { "B$it" to "$it" }, true)
        c4.addView(lte.view)
        c4.addView(ui.button("APPLY 4G BANDS", Neon.CYAN) {
            val v = lte.selected.joinToString(",")
            if (v.isEmpty()) { toast("Select at least one band"); return@button }
            ui.confirm("Apply 4G bands $v? The router will restart.") {
                svc.run("s.setBandSelect({lte_band_lock:'" + v + "'},function(r){if(r&&r.result==='success'){s.restart({},function(){});}done(r);});") {
                    reportResult(it, "Applied. Router is restarting…")
                }
            }
        }, ui.lp(10))

        // 5G
        val c5 = ui.card(col, Neon.MAGENTA, "5G BANDS (SA / NSA)")
        nr = Ui.Chips(ui, listOf(1, 5, 8, 28, 41, 78).map { "n$it" to "$it" }, true)
        c5.addView(nr.view)
        c5.addView(ui.button("APPLY 5G BANDS", Neon.MAGENTA) {
            val v = nr.selected.joinToString(",")
            if (v.isEmpty()) { toast("Select at least one band"); return@button }
            ui.confirm("Apply 5G bands $v? The router will restart.") {
                svc.run("s.setNr5gSaOrNsaBandSelect({nr_band_lock:'" + v + "'},function(r){if(r&&r.result==='success'){s.restart({},function(){});}done(r);});") {
                    reportResult(it, "Applied. Router is restarting…")
                }
            }
        }, ui.lp(10))

        // cell lock
        val cl = ui.card(col, Neon.PURPLE, "CELL LOCK")
        rat = Ui.Chips(ui, listOf("4G" to "12", "5G" to "16"), false, 2)
        rat.set(listOf("12"))
        cl.addView(rat.view)
        pci = field("PCI (0-1007)")
        earfcn = field("EARFCN")
        cl.addView(pci, ui.lp(8))
        cl.addView(earfcn, ui.lp(8))
        cl.addView(ui.button("LOCK TO CELL", Neon.PURPLE) {
            val p = pci.text.toString().trim()
            val e = earfcn.text.toString().trim()
            val r = rat.selected.firstOrNull() ?: "12"
            if (!Regex("[0-9]{1,7}").matches(p) || !Regex("[0-9]{1,7}").matches(e)) {
                toast("Enter numeric PCI and EARFCN"); return@button
            }
            ui.confirm("Lock to PCI $p / EARFCN $e? The router will restart.") {
                svc.run("s.setCellLockSetting({rat:'" + r + "',pci:'" + p + "',earfcn:'" + e +
                    "'},function(x){if(x&&x.result==='success'){s.restart({},function(){});}done(x);});") {
                    reportResult(it, "Cell lock applied. Router is restarting…")
                }
            }
        }, ui.lp(10))

        // locked cells
        val lk = ui.card(col, Neon.LIME, "LOCKED CELLS")
        lockedText = ui.text("--", 13f, Neon.WHITE)
        lk.addView(lockedText)
        lk.addView(ui.button("UNLOCK ALL CELLS", Neon.LIME) {
            ui.confirm("Unlock all cells? The router will restart.") {
                svc.run("s.unlockAllCell({},function(r){if(r&&r.result==='success'){s.restart({},function(){});}done(r);});") {
                    reportResult(it, "Unlocked. Router is restarting…")
                }
            }
        }, ui.lp(10))

        // neighbor cells
        val nb = ui.card(col, Neon.CYAN, "NEIGHBOR CELLS")
        neighText = ui.text("Loading…", 12f, Neon.WHITE)
        nb.addView(neighText)
        nb.addView(ui.button("REFRESH", Neon.CYAN) { loadNeighbors() }, ui.lp(10))

        load()
    }

    private fun load() {
        svc.run("done(s.getParams({nv:['lte_band_lock','nr_band_lock','frequency_support_4g','frequency_support_5g']}));") { r ->
            if (r == null || r.has("error")) {
                toast("Could not read bands. Log in first (gear icon on the dashboard).")
            } else {
                val f4 = list(r, "frequency_support_4g")
                if (f4.isNotEmpty() && f4 != listOf("0")) rebuild4g(f4)
                val f5 = list(r, "frequency_support_5g")
                if (f5.isNotEmpty() && f5 != listOf("0")) rebuild5g(f5)
                lte.set(list(r, "lte_band_lock"))
                nr.set(list(r, "nr_band_lock"))
            }
        }
        svc.run("done(s.getLockedCellInfo());") { r ->
            val a = r?.optJSONArray("locked_cell_info")
            lockedText.text = if (a == null || a.length() == 0) "No locked cells" else
                (0 until a.length()).joinToString("\n") {
                    val o = a.optJSONObject(it) ?: JSONObject()
                    (if (o.optString("rat") == "16") "5G" else "4G") + "  PCI " + o.optString("pci") + "  EARFCN " + o.optString("earfcn")
                }
        }
        loadNeighbors()
    }

    private fun rebuild4g(vals: List<String>) {
        val parent = lte.view.parent as LinearLayout
        val idx = parent.indexOfChild(lte.view)
        parent.removeView(lte.view)
        lte = Ui.Chips(ui, vals.map { "B$it" to it }, true)
        parent.addView(lte.view, idx)
    }

    private fun rebuild5g(vals: List<String>) {
        val parent = nr.view.parent as LinearLayout
        val idx = parent.indexOfChild(nr.view)
        parent.removeView(nr.view)
        nr = Ui.Chips(ui, vals.map { "n$it" to it }, true)
        parent.addView(nr.view, idx)
    }

    private fun loadNeighbors() {
        svc.run("s.getNeighborCellInfo({},function(r){done(r);});") { r ->
            val a = r?.optJSONArray("neighbor_cell_info")
            neighText.text = if (a == null || a.length() == 0) "No neighbor cells reported" else
                (0 until a.length()).joinToString("\n") {
                    val o = a.optJSONObject(it) ?: JSONObject()
                    "B" + o.optString("band") + " · EARFCN " + o.optString("earfcn") + " · PCI " + o.optString("pci") +
                        " · RSRP " + o.optString("rsrp") + " · SINR " + o.optString("sinr")
                }
        }
    }
}
