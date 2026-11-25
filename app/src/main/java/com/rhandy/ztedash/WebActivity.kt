package com.rhandy.ztedash

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar

/** Opens the router's own page (e.g. Performance Mode) inside the app. */
class WebActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Neon.BG
        window.navigationBarColor = Neon.BG
        val host = getSharedPreferences("zte", MODE_PRIVATE).getString("host", "192.168.0.1")!!
        val route = intent.getStringExtra("route") ?: "performance_mode"

        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        web = WebView(this).apply {
            setBackgroundColor(Neon.BG)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView, url: String, icon: Bitmap?) { bar.visibility = android.view.View.VISIBLE }
                override fun onPageFinished(v: WebView, url: String) {
                    bar.visibility = android.view.View.GONE
                    v.evaluateJavascript(skinJs(), null)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(v: WebView, p: Int) { bar.progress = p }
            }
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Neon.BG) }
        root.addView(web, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8))
        setContentView(root)
        web.loadUrl("http://$host/index.html#$route")
    }

    private fun skinJs(): String =
        "(function(){if(document.getElementById('neon-skin'))return;var st=document.createElement('style');" +
            "st.id='neon-skin';st.textContent=" + org.json.JSONObject.quote(NEON_CSS) +
            ";document.head.appendChild(st);})();"

    override fun onPause() {
        super.onPause()
        android.webkit.CookieManager.getInstance().flush()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else finish()
    }

    companion object {
        private const val NEON_CSS = """
html,body{background:#06050F !important;color:#F2F0FF !important;font-family:sans-serif !important}
#topContainer,#indexContainer,#mainContainer,#container,#innerContainer,#footer,.container,#langLogoBar,#languageBar,#batteryModeBar,.row,.col-xs-1,.col-xs-2,.col-xs-3,.col-xs-4,.col-xs-5,.col-xs-6,.col-xs-7,.col-xs-8,.col-xs-9,.col-xs-10,.col-xs-11,.col-xs-12{background:transparent !important;color:#F2F0FF !important}
#footer,#footer a{color:#7C7A9E !important}
body *{border-color:#2A2650 !important}
a,a:visited{color:#00F0FF !important}
h1,h2,h3,h4,.form-title,.form-title h1,.form-title h2{color:#00F0FF !important;text-shadow:0 0 12px rgba(0,240,255,.5)}
p,span,label,li,td,th,div,small{color:#F2F0FF}
.form-body,.content,.modal-body,.panel,.well,.box,fieldset{background:#0E0C22 !important;border:1px solid #2A2650 !important;border-radius:14px !important}
input[type=text],input[type=password],input[type=number],input[type=email],input[type=tel],select,textarea,.form-control,.chosen-container .chosen-single{background:#0E0C22 !important;color:#F2F0FF !important;border:1px solid #8A2BFF !important;border-radius:8px !important;box-shadow:none !important}
option{background:#0E0C22 !important;color:#F2F0FF !important}
.btn,.btn-primary,.btn-default,input[type=button],input[type=submit],button{background:linear-gradient(90deg,#00F0FF,#8A2BFF) !important;color:#06050F !important;border:0 !important;border-radius:10px !important;font-weight:700 !important;text-shadow:none !important}
table,thead,tbody,tr,td,th{background:transparent !important;color:#F2F0FF !important;border-color:#2A2650 !important}
th{color:#00F0FF !important}
hr{border-color:#2A2650 !important}
.error,.colorRed,label.error{color:#FF3860 !important}
#confirm,#confirm_sbm,#loading,#progress,#result-overlay,#popupSettingWindow,#popupModifyFotaSetWindow,.bubbleItem,#simplemodal-container,.simplemodal-wrap{background:#0E0C22 !important;color:#F2F0FF !important;border:1px solid #8A2BFF !important;border-radius:14px !important;box-shadow:0 0 24px rgba(138,43,255,.45) !important}
#simplemodal-overlay{background:#000 !important;opacity:.75 !important}
.header{background:transparent !important;color:#00F0FF !important}
.checkbox,.radiobox,.AttRadio{border:1px solid #8A2BFF !important;background:#06050F !important}
.checkbox_selected,.radiobox_selected,.AttRadioSelected{background:#00F0FF !important}
.progress-bar-container{background:#1B1838 !important}
.progress-bar{background:linear-gradient(90deg,#00F0FF,#FF2BD6) !important}
img{filter:hue-rotate(0deg)}
"""
    }
}
