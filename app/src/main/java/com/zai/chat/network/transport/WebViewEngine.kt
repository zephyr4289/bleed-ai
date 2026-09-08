package com.zai.chat.network.transport

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
            eventChannel.trySend(payload)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun ensureReady(): Boolean = withContext(Dispatchers.Main) {
        initMutex.withLock {
            if (isBootstrapped && webView != null) return@withContext true

            try {
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

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
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
                    eval(TransportScripts.injectAuthJs(token))
                }

                // Verify ready probe
                val ready = eval(TransportScripts.READY_PROBE_JS)
                isBootstrapped = (ready == "true" || ready.contains("true"))
                true
            } catch (_: Exception) {
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
