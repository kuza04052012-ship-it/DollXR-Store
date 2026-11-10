package com.d0ll1ii.dollxrstore

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import com.d0ll1ii.dollxrstore.bridge.DollXRBridge

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var bridge: DollXRBridge
    private var pageReady = false
    private var insetsPx = intArrayOf(0, 0, 0, 0)

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        bridge.onImagePicked(uri)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.parseColor("#060406")) }

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.parseColor("#060406"))
            overScrollMode = WebView.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Load bundled UI through WebViewAssetLoader instead of the less-safe file:// origin.
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = false
                setSupportZoom(false)
                builtInZoomControls = false
                textZoom = 100
                cacheMode = WebSettings.LOAD_DEFAULT
            }
        }
        root.addView(webView)
        setContentView(root)

        bridge = DollXRBridge(this, webView) { pickImage.launch("image/*") }
        webView.addJavascriptInterface(bridge, "DollXRAndroid")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val message = "${consoleMessage?.message()} [Line ${consoleMessage?.lineNumber()} in ${consoleMessage?.sourceId()}]"
                when (consoleMessage?.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Log.e("DollXRJS", message)
                    ConsoleMessage.MessageLevel.WARNING -> Log.w("DollXRJS", message)
                    else -> Log.d("DollXRJS", message)
                }
                return true
            }
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                assetLoader.shouldInterceptRequest(request.url)

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                Log.i("DollXRWebView", "Started loading UI: $url")
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == ASSET_HOST) return false
                if (uri.scheme == "https" || uri.scheme == "http") bridge.openExternal(uri.toString())
                return true
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                Log.e("DollXRWebView", "Render process gone! Crash: ${detail?.didCrash()}")
                view?.destroy()
                return true
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                Log.e("DollXRWebView", "Load error: ${error?.description} (code ${error?.errorCode}) for URL: ${request?.url}")
                if (request?.isForMainFrame == true && view != null) {
                    val message = "Не удалось загрузить интерфейс DollXR Store: ${error?.description ?: "ошибка WebView"}"
                    val escaped = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    view.loadDataWithBaseURL(
                        HOME_URL,
                        "<meta name='viewport' content='width=device-width, initial-scale=1'><body style='margin:0;background:#060406;color:#f5f1f1;font:16px sans-serif;padding:24px'><h2>DollXR Store</h2><p>$escaped</p></body>",
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                pageReady = true
                pushInsets()
                view.evaluateJavascript(
                    "(function(){var o=document.getElementById('onboarding'),m=document.getElementById('main'),s=document.querySelector('.step.active');return JSON.stringify({ready:document.readyState,app:!!document.getElementById('app'),onboardingVisible:!!o&&!o.classList.contains('hidden')&&getComputedStyle(o).display!=='none',activeStepVisible:!!s&&getComputedStyle(s).visibility==='visible',mainVisible:!!m&&!m.classList.contains('hidden')&&getComputedStyle(m).display!=='none',bodyTextLength:(document.body.innerText||'').length})})()"
                ) { result -> Log.i("DollXRWebView", "UI DOM probe: $result") }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, wi ->
            val bars = wi.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = wi.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.setPadding(0, 0, 0, ime)
            insetsPx = intArrayOf(bars.top, bars.right, if (ime > 0) 0 else bars.bottom, bars.left)
            pushInsets()
            wi
        }

        webView.loadUrl(HOME_URL)
    }

    private fun pushInsets() {
        if (!pageReady) return
        val density = resources.displayMetrics.density
        val topDp = insetsPx[0] / density
        val rightDp = insetsPx[1] / density
        val bottomDp = insetsPx[2] / density
        val leftDp = insetsPx[3] / density
        val js = "if(window.DollXRBridge){window.DollXRBridge.setInsets($topDp,$rightDp,$bottomDp,$leftDp)}"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    private companion object {
        const val ASSET_HOST = "appassets.androidplatform.net"
        const val HOME_URL = "https://$ASSET_HOST/assets/index.html"
    }
}
