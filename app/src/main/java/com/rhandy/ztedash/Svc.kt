package com.rhandy.ztedash

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/**
 * Hidden WebView on the router's own origin. Lets native screens call the router's own
 * `service` module (same login session, same security tokens as the stock web page).
 */
class Svc(act: Activity, host: String) {
    private val ui = Handler(Looper.getMainLooper())
    private var ready = false
    private val queue = ArrayList<() -> Unit>()
    private val cbs = HashMap<String, (JSONObject?) -> Unit>()
    private var n = 0

    @SuppressLint("SetJavaScriptEnabled")
    val web: WebView = WebView(act).apply {
        alpha = 0f
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        addJavascriptInterface(Bridge(), "SvcBridge")
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) {
                if (!ready) {
                    ready = true
                    val q = ArrayList(queue)
                    queue.clear()
                    q.forEach { it() }
                }
            }
        }
        loadUrl("http://$host/index.html")
    }

    inner class Bridge {
        @JavascriptInterface
        fun res(id: String, t: String) {
            ui.post {
                val cb = cbs.remove(id)
                cb?.invoke(try { JSONObject(t) } catch (e: Exception) { null })
            }
        }
    }

    /** body runs inside require(['service']) with `s` = service and `done(obj)` to return a result. */
    fun run(body: String, cb: (JSONObject?) -> Unit) {
        val go = {
            val id = "c" + (n++)
            cbs[id] = cb
            val js = "require(['service'],function(s){var done=function(x){SvcBridge.res('" + id +
                "',JSON.stringify(x===undefined?{}:x));};try{if(window.rd0===''||window.rd1===''){" +
                "var l=s.getLanguage();window.rd0=l.rd_params0;window.rd1=l.rd_params1;}" + body +
                "}catch(e){done({error:String(e)});}});"
            web.evaluateJavascript(js, null)
        }
        if (ready) go() else queue.add(go)
    }

    fun destroy() {
        web.destroy()
    }
}
