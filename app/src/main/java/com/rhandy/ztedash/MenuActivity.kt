package com.rhandy.ztedash

import android.content.Intent
import android.view.Gravity
import android.widget.LinearLayout

class MenuActivity : NeonActivity() {
    override val screenTitle = "ALL SETTINGS"
    override val screenSub = "Every router page, in neon"

    private fun item(parent: LinearLayout, label: String, note: String, color: Int, go: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.bg(Neon.CARD, color)
            setPadding(ui.dp(16), ui.dp(13), ui.dp(16), ui.dp(13))
            setOnClickListener { go() }
        }
        row.addView(ui.text(label, 16f, color, true))
        if (note.isNotEmpty()) row.addView(ui.text(note, 11f, Neon.DIM))
        parent.addView(row, ui.lp(8))
    }

    private fun web(route: String) =
        startActivity(Intent(this, WebActivity::class.java).putExtra("route", route))

    override fun build() {
        col.addView(ui.text("NATIVE SCREENS", 11f, Neon.DIM).apply { setPadding(0, ui.dp(14), 0, 0) })
        item(col, "Band Selection", "4G/5G band lock, cell lock, neighbor cells", Neon.MAGENTA) {
            startActivity(Intent(this, BandActivity::class.java))
        }
        item(col, "Network Mode", "5G / 4G / 3G preference", Neon.CYAN) {
            startActivity(Intent(this, NetModeActivity::class.java))
        }
        item(col, "Device Controls", "Performance mode, LED, USB, restart, reset", Neon.LIME) {
            startActivity(Intent(this, DeviceActivity::class.java))
        }

        col.addView(ui.text("ROUTER PAGES (NEON THEME)", 11f, Neon.DIM).apply { setPadding(0, ui.dp(22), 0, 0) })
        val pages = listOf(
            Triple("APN Settings", "apn_setting", "Access point names"),
            Triple("Wi-Fi Settings", "wifi_main_24G", "Name, password, bands"),
            Triple("Connected Devices", "station_info", "Block / rename devices"),
            Triple("Data Usage & Alerts", "traffic_alert", "Traffic limits"),
            Triple("SMS", "smslist", "Inbox and messages"),
            Triple("SMS Settings", "sms_setting", ""),
            Triple("Dial Settings", "dial_setting", "Connection mode"),
            Triple("Network Selection", "net_select", "Manual operator search"),
            Triple("Router / LAN", "router_setting", "IP, DHCP"),
            Triple("Change Password", "password_management", "Admin password"),
            Triple("PIN Management", "pin_management", "SIM PIN"),
            Triple("Software Update", "ota_update", "Firmware"),
            Triple("Network Info", "network_info", "Signal and cell details"),
            Triple("Diagnosis", "diagnosis", "Logs and tests"),
            Triple("Backup / Restore", "restore", ""),
            Triple("Others", "others", "More device pages"),
            Triple("USB Internet Protocol", "usb_internet_protocol", ""),
            Triple("SIM Switch", "sim_switch", "Dual SIM models"),
            Triple("File Sharing", "file_sharing", "Samba"),
            Triple("Online Authentication", "online_auth", ""),
            Triple("Access License", "access_license", ""),
            Triple("Open full router page", "", "Original home page")
        )
        for ((label, route, note) in pages) {
            item(col, label, note, Neon.PURPLE.let { 0xFFB77CFF.toInt() }) { web(route) }
        }
    }
}
