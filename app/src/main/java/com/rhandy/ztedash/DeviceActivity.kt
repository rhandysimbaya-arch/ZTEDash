package com.rhandy.ztedash

class DeviceActivity : NeonActivity() {
    override val screenTitle = "DEVICE CONTROLS"
    override val screenSub = "Performance · LED · USB · restart · reset"

    private fun switchRow(
        title: String, stroke: Int, onLabel: String, offLabel: String,
        getBody: String, key: String, setBody: (String) -> String,
        restart: Boolean, ok: String
    ) {
        val c = ui.card(col, stroke, title)
        val chips = Ui.Chips(ui, listOf(onLabel to "1", offLabel to "0"), false, 2)
        c.addView(chips.view)
        svc.run(getBody) { r ->
            val v = r?.optString(key, "") ?: ""
            if (v == "1" || v == "0") chips.set(listOf(v))
        }
        c.addView(ui.button("APPLY", stroke) {
            val v = chips.selected.firstOrNull()
            if (v == null) { toast("Choose a value first"); return@button }
            val go = {
                svc.run(setBody(v)) { reportResult(it, ok) }
            }
            if (restart) ui.confirm("$title will change and the router will restart. Continue?") { go() } else go()
        }, ui.lp(10))
    }

    override fun build() {
        switchRow("PERFORMANCE MODE", Neon.LIME, "ENABLE", "DISABLE",
            "done(s.getPerformanceMode());", "performance_mode",
            { v -> "s.setPerformanceMode({performance_mode:'" + v + "'},function(r){if(r&&r.result==='success'){s.restart({},function(){});}done(r);});" },
            true, "Applied. Router is restarting…")
        switchRow("INDICATOR LIGHT", Neon.CYAN, "ON", "OFF",
            "done(s.getIndicatorLight());", "indicator_light_switch",
            { v -> "s.setIndicatorLight({indicator_light_switch:'" + v + "'},function(r){done(r);});" },
            false, "LED setting saved")
        switchRow("USB PORT", Neon.MAGENTA, "ON", "OFF",
            "done(s.getUsbPort());", "usb_port_switch",
            { v -> "s.setUsbPortSwitch({usb_port_switch:'" + v + "'},function(r){done(r);});" },
            false, "USB port setting saved")

        val sys = ui.card(col, Neon.PURPLE, "SYSTEM")
        sys.addView(ui.button("RESTART ROUTER", Neon.PURPLE) {
            ui.confirm("Restart the router now?") {
                svc.run("s.restart({},function(r){done(r);});") { reportResult(it, "Restarting…") }
            }
        })
        sys.addView(ui.button("FACTORY RESET", Neon.RED) {
            ui.confirm("FACTORY RESET erases ALL router settings (Wi-Fi name, passwords, APN). Are you sure?") {
                ui.confirm("Last warning: this cannot be undone. Reset now?") {
                    svc.run("s.restoreFactorySettings({delete_user_data:'0'},function(r){done(r);});") {
                        reportResult(it, "Resetting to factory settings…")
                    }
                }
            }
        }, ui.lp(10))
    }
}
