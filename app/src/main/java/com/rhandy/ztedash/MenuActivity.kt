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
        item(col, "APN Settings", "Profiles, default APN, automatic mode", Neon.CYAN) {
            startActivity(Intent(this, ApnActivity::class.java))
        }
        item(col, "Wi-Fi Settings", "2.4 GHz and 5 GHz name, password, security", Neon.MAGENTA) {
            startActivity(Intent(this, WifiActivity::class.java))
        }
        item(col, "Connected Devices", "Rename or block devices", Neon.LIME) {
            startActivity(Intent(this, DevicesActivity::class.java))
        }
        item(col, "Data Usage", "Monthly usage, limit, alerts", Neon.CYAN) {
            startActivity(Intent(this, DataUsageActivity::class.java))
        }
        item(col, "SMS", "Read, send and delete messages", Neon.MAGENTA) {
            startActivity(Intent(this, SmsActivity::class.java))
        }

        col.addView(ui.text("ROUTER PAGES (NEON THEME)", 11f, Neon.DIM).apply { setPadding(0, ui.dp(22), 0, 0) })
        val pages = listOf(
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
