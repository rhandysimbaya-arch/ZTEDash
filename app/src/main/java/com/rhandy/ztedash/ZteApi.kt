package com.rhandy.ztedash

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Talks to the ZTE goform web API used by the F50's stock admin page. */
class ZteApi(var host: String, password: String) {

    var password: String = password
        set(v) { field = v; loginFailed = false; cookie = "" }

    @Volatile var loginFailed = false
    private var cookie = ""

    data class Snap(val raw: JSONObject, val devices: List<String>, val loggedIn: Boolean)

    private fun request(path: String, post: String?): Pair<String, Map<String?, List<String>>> {
        val c = URL("http://$host$path").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 3000
            c.readTimeout = 4000
            c.setRequestProperty("Referer", "http://$host/index.html")
            c.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
            if (cookie.isNotEmpty()) c.setRequestProperty("Cookie", cookie)
            if (post != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                c.outputStream.use { it.write(post.toByteArray()) }
            }
            val text = c.inputStream.bufferedReader().use { it.readText() }
            return Pair(text, c.headerFields)
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(cmd: String, multi: Boolean = true): JSONObject {
        val q = "isTest=false&cmd=" + enc(cmd) + (if (multi) "&multi_data=1" else "") +
            "&_=" + System.currentTimeMillis()
        val text = request("/goform/goform_get_cmd_process?$q", null).first
        return JSONObject(text)
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }.uppercase()

    private fun login(): Boolean {
        cookie = ""
        val info = try { get("web_attr_if_support_sha256") } catch (e: Exception) { JSONObject() }
        val sha = info.optString("web_attr_if_support_sha256") == "1"
        val pw = if (sha) {
            val ld = get("LD", false).optString("LD").uppercase()
            sha256(sha256(password) + ld)
        } else {
            Base64.encodeToString(password.toByteArray(), Base64.NO_WRAP)
        }
        val (text, headers) = request(
            "/goform/goform_set_cmd_process",
            "isTest=false&goformId=LOGIN&password=" + enc(pw)
        )
        val result = try { JSONObject(text).optString("result") } catch (e: Exception) { "" }
        if (result != "0" && result != "4") return false
        val parts = ArrayList<String>()
        for ((k, v) in headers) {
            if (k != null && k.equals("Set-Cookie", true)) {
                for (line in v) {
                    val kv = line.substringBefore(';').trim()
                    if (kv.contains('=') && !kv.endsWith("=") && !kv.endsWith("=\"\"")) parts.add(kv)
                }
            }
        }
        cookie = parts.joinToString("; ")
        return true
    }

    fun fetch(): Snap {
        var j = get(MAIN)
        var ok = j.optString("loginfo") == "ok"
        if (!ok && password.isNotEmpty() && !loginFailed) {
            if (login()) {
                j = get(MAIN)
                ok = j.optString("loginfo") == "ok"
            } else {
                loginFailed = true
            }
        }
        val devices = ArrayList<String>()
        try {
            val s = get("station_list,lan_station_list")
            for (key in arrayOf("station_list", "lan_station_list")) {
                val arr = s.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("hostname").ifEmpty { o.optString("mac_addr") }
                    val ip = o.optString("ip_addr")
                    devices.add(if (ip.isNotEmpty()) "$name  ·  $ip" else name)
                }
            }
        } catch (_: Exception) {
        }
        return Snap(j, devices, ok)
    }

    companion object {
        const val MAIN = "network_type,signalbar,rssi,Z5g_rsrp,Z5g_rsrq,Z5g_SINR,Z5g_snr," +
            "lte_rsrp,lte_rsrq,lte_snr,nr5g_action_band,lte_ca_pcell_band,wan_active_band," +
            "network_provider,realtime_rx_thrpt,realtime_tx_thrpt,realtime_time," +
            "monthly_rx_bytes,monthly_tx_bytes,battery_vol_percent,battery_value,battery_pers," +
            "battery_charging,wan_ipaddr,ppp_status,sta_count,wifi_access_sta_num,loginfo," +
            "web_attr_if_support_sha256"
    }
}
