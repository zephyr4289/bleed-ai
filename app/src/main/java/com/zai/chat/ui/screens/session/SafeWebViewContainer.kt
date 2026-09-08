package com.zai.chat.ui.screens.session

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zai.chat.config.ZaiConfig
import com.zai.chat.ui.theme.TrueBlack

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SafeWebViewContainer(
    mode: SessionMode,
    onTokenExtracted: (String) -> Unit,
    onDismiss: () -> Unit,
    onRendererCrashed: () -> Unit,
    onMissingWebView: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.let { wv ->
                try {
                    val parent = wv.parent as? ViewGroup
                    parent?.removeView(wv)
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.clearHistory()
                    wv.removeAllViews()
                    wv.destroy()
                } catch (_: Throwable) {}
            }
            webViewInstance = null
        }
    }

    Surface(
        color = TrueBlack,
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Header Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Sign in to z.ai",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { webViewInstance?.reload() }) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reload",
                        tint = Color.White
                    )
                }
                IconButton(onClick = {
                    CookieManager.getInstance().removeAllCookies(null)
                    webViewInstance?.clearCache(true)
                    webViewInstance?.loadUrl(ZaiConfig.BASE_URL)
                }) {
                    Icon(
                        imageVector = Icons.Rounded.CleaningServices,
                        contentDescription = "Clear cookies",
                        tint = Color.White
                    )
                }
            }

            if (isPageLoading) {
                LinearProgressIndicator(
                    progress = { loadingProgress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        try {
                            createSafeWebView(
                                context = context,
                                mode = mode,
                                onProgressUpdate = { progress ->
                                    loadingProgress = progress / 100f
                                    isPageLoading = progress < 100
                                },
                                onTokenFound = onTokenExtracted,
                                onRendererFatal = onRendererCrashed
                            ).also { webViewInstance = it }
                        } catch (e: Exception) {
                            onMissingWebView()
                            WebView(context) // Degrade to empty view
                        }
                    }
                )
            }
        }
    }
}

private fun createSafeWebView(
    context: Context,
    mode: SessionMode,
    onProgressUpdate: (Int) -> Unit,
    onTokenFound: (String) -> Unit,
    onRendererFatal: () -> Unit
): WebView {
    val webView = WebView(context)
    with(webView.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        loadsImagesAutomatically = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportMultipleWindows(true)
        javaScriptCanOpenWindowsAutomatically = true

        // Clean Desktop User-Agent: Strip '; wv' and Android WebView identifiers
        userAgentString = ZaiConfig.USER_AGENT
    }

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    webView.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            onProgressUpdate(newProgress)
            if (newProgress > 60) {
                attemptTokenHarvest(view, mode, onTokenFound)
            }
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            // Handle OAuth Popups inside the same frame without throwing crashes
            val transport = resultMsg?.obj as? WebView.WebViewTransport
            transport?.webView = view
            resultMsg?.sendToTarget()
            return true
        }
    }

    webView.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            attemptTokenHarvest(view, mode, onTokenFound)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            attemptTokenHarvest(view, mode, onTokenFound)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val destination = request?.url?.toString().orEmpty()
            // Trap OAuth callback loops or App links
            if (destination.contains("token=") || destination.contains("access_token=")) {
                val extracted = extractTokenFromUrl(destination)
                if (!extracted.isNullOrBlank()) {
                    onTokenFound(extracted)
                    return true
                }
            }
            return false
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            // CRITICAL OS RESILIENCE:
            // Returning true tells Android: DO NOT terminate the host app process
            try {
                val parent = view?.parent as? ViewGroup
                parent?.removeView(view)
                view?.destroy()
            } catch (_: Throwable) {}
            onRendererFatal()
            return true
        }
    }

    webView.loadUrl(ZaiConfig.BASE_URL)
    return webView
}

private fun attemptTokenHarvest(
    view: WebView?,
    mode: SessionMode,
    onTokenFound: (String) -> Unit
) {
    if (view == null) return
    // 1. Check Web Storage (Open WebUI primary token container)
    view.evaluateJavascript(
        "(function() { try { return localStorage.getItem('token'); } catch(e) { return null; } })()"
    ) { rawStorageToken ->
        val cleaned = rawStorageToken?.trim('"', ' ', '\'')
        if (!cleaned.isNullOrBlank() && cleaned != "null" && cleaned != "undefined") {
            onTokenFound(cleaned)
            return@evaluateJavascript
        }

        // 2. Fallback: Parse Cookie Jar for session tokens
        val cookies = CookieManager.getInstance().getCookie(ZaiConfig.BASE_URL)
        if (!cookies.isNullOrEmpty()) {
            val tokenMatch = cookies.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("token=") || it.startsWith("jwt=") }
                ?.substringAfter("=")
            if (!tokenMatch.isNullOrBlank() && tokenMatch != "null") {
                // In Active Management mode, ignore stale cookies on initial render
                if (mode !is SessionMode.ActiveManagement) {
                    onTokenFound(tokenMatch)
                }
            }
        }
    }
}

private fun extractTokenFromUrl(url: String): String? {
    return try {
        val uri = android.net.Uri.parse(url)
        uri.getQueryParameter("token")
            ?: uri.getQueryParameter("access_token")
            ?: uri.fragment?.split("&")
                ?.map { it.trim() }
                ?.firstOrNull { it.startsWith("access_token=") }
                ?.substringAfter("access_token=")
    } catch (_: Exception) {
        null
    }
}
