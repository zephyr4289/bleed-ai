package com.zai.chat.network.transport

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.zai.chat.config.ZaiConfig
import com.zai.chat.data.local.preferences.TokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class WebViewEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager
) {
    private var webView: WebView? = null
    private val initMutex = Mutex()
    private var isBootstrapped = false

    private val pageFinishedDeferred = CompletableDeferred<Unit>()

    // Event channel for SSE stream bridge
    val eventChannel = Channel<String>(Channel.UNLIMITED)

    private val bridge = object {
        @JavascriptInterface
        fun onEvent(payload: String) {
            Log.d("BleedAI-Bridge", "JS Event: ${payload.take(300)}")
            eventChannel.trySend(payload)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun ensureReady(): Boolean = withContext(Dispatchers.Main) {
        initMutex.withLock {
            if (isBootstrapped && webView != null) return@withContext true

            try {
                Log.i("BleedAI-WebView", "ensureReady: Initializing headless WebView runtime...")
                if (webView == null) {
                    val view = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.userAgentString = ZaiConfig.USER_AGENT
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.cacheMode = WebSettings.LOAD_DEFAULT

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        addJavascriptInterface(bridge, TransportScripts.BRIDGE_NAME)

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                                val msg = cm?.message().orEmpty()
                                val line = cm?.lineNumber() ?: 0
                                Log.d("BleedAI-WebViewJS", "[Line $line] $msg")
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                Log.i("BleedAI-WebView", "onPageFinished: $url")
                                if (!pageFinishedDeferred.isCompleted) {
                                    pageFinishedDeferred.complete(Unit)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                Log.w("BleedAI-WebView", "onReceivedError: ${request?.url} -> ${error?.description}")
                            }
                        }
                    }
                    webView = view
                    view.loadUrl(ZaiConfig.BASE_URL)
                }

                // Wait for initial page load (up to 10s)
                withTimeoutOrNull(10000L) {
                    pageFinishedDeferred.await()
                }

                // Inject token from TokenManager
                tokenManager.getStoredToken()?.let { token ->
                    Log.d("BleedAI-WebView", "Injecting auth token into WebView localStorage...")
                    eval(TransportScripts.injectAuthJs(token))
                }

                // Verify ready probe
                val ready = eval(TransportScripts.READY_PROBE_JS)
                isBootstrapped = (ready == "true" || ready.contains("true"))
                Log.i("BleedAI-WebView", "Ready probe result: $ready (isBootstrapped=$isBootstrapped)")
                true
            } catch (e: Exception) {
                Log.e("BleedAI-WebView", "ensureReady failed: ${e.message}", e)
                false
            }
        }
    }

    suspend fun eval(js: String): String = withContext(Dispatchers.Main) {
        val view = webView ?: return@withContext "false"
        suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(js) { result ->
                if (continuation.isActive) {
                    continuation.resume(result ?: "")
                }
            }
        }
    }

    fun abort() {
        Log.w("BleedAI-WebView", "Aborting current in-flight WebView fetch execution")
        webView?.post {
            webView?.evaluateJavascript(TransportScripts.ABORT_JS, null)
        }
    }

    suspend fun reloadEngine() = withContext(Dispatchers.Main) {
        initMutex.withLock {
            isBootstrapped = false
            webView?.loadUrl(ZaiConfig.BASE_URL)
            tokenManager.getStoredToken()?.let { token ->
                eval(TransportScripts.injectAuthJs(token))
            }
        }
    }
}
