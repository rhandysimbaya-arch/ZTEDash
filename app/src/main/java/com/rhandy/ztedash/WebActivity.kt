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
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView, url: String, icon: Bitmap?) { bar.visibility = android.view.View.VISIBLE }
                override fun onPageFinished(v: WebView, url: String) { bar.visibility = android.view.View.GONE }
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

    override fun onPause() {
        super.onPause()
        android.webkit.CookieManager.getInstance().flush()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else finish()
    }
}
