package com.rhandy.ztedash

class NetModeActivity : NeonActivity() {
    override val screenTitle = "NETWORK MODE"
    override val screenSub = "Choose which network types the router may use"

    private lateinit var modes: Ui.Chips

    override fun build() {
        val c = ui.card(col, Neon.CYAN, "PREFERRED NETWORK")
        modes = Ui.Chips(ui, listOf(
            "5G/4G/3G" to "WL_AND_5G",
            "5G NSA" to "LTE_AND_5G",
            "5G SA" to "Only_5G",
            "4G/3G" to "WCDMA_AND_LTE",
            "4G Only" to "Only_LTE",
            "3G Only" to "Only_WCDMA"
        ), false, 2)
        c.addView(modes.view)
        c.addView(ui.button("APPLY", Neon.CYAN) {
            val v = modes.selected.firstOrNull()
            if (v == null) { toast("Choose a mode first"); return@button }
            ui.confirm("Switch network mode? The connection may drop for a moment.") {
                svc.run("s.setBearerPreference({strBearerPreference:'" + v + "'},function(r){done(r);});") {
                    reportResult(it, "Network mode updated")
                }
            }
        }, ui.lp(10))
        svc.run("done(s.getNetSelectInfo());") { r ->
            val cur = r?.optString("net_select", "") ?: ""
            if (cur.isNotEmpty()) modes.set(listOf(cur))
            else toast("Could not read current mode. Log in first (gear icon on the dashboard).")
        }
    }
}
