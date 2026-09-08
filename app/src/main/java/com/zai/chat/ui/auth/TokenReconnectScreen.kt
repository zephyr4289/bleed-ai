package com.zai.chat.ui.auth

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zai.chat.config.ZaiConfig

/**
 * Mobile Chrome UA for the login WebView only (NOT for native API calls —
 * those use ZaiConfig.USER_AGENT). Rationale: Cloudflare fingerprints
 * WebViews by default and challenges them; a stock mobile Chrome UA passes
 * far more often, and the site renders properly for a phone screen.
 */
private const val WEBVIEW_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

/** Poll delays after each page load — SPAs write localStorage asynchronously. */
private val POLL_DELAYS_MS = longArrayOf(800L, 2500L, 5000L)

/**
 * Full-screen session gate (deliberately NOT the PDF's cramped AlertDialog —
 * Google OAuth popups and Cloudflare interstitials need real estate).
 *
 * Token extraction, in priority order:
 *   1. localStorage.getItem('token') on any chat.z.ai page finish,
 *      polled at 0/800/2500/5000ms to win the race against SPA hydration.
 *   2. Cookie fallback: 'token' cookie scoped to BASE_URL.
 *   3. Manual paste (key icon) — the P12 escape hatch if Cloudflare or
 *      OAuth refuses the WebView: paste the token captured in recon.
 *
 * Origin safety: localStorage is per-origin (returning null elsewhere by
 * construction); the cookie read is explicitly scoped to BASE_URL.
 */
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.zai.chat.R

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TokenReconnectScreen(
    isFirstLogin: Boolean,
    onTokenExtracted: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showManualEntry by remember { mutableStateOf(false) }
    var manualToken by remember { mutableStateOf("") }
    val handler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(Unit) {
        val cookies = CookieManager.getInstance().getCookie(ZaiConfig.BASE_URL)
        if (!cookies.isNullOrEmpty()) {
            val tokenFromCookie = cookies.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("token=") }
                ?.removePrefix("token=")
            if (!tokenFromCookie.isNullOrBlank()) {
                onTokenExtracted(tokenFromCookie)
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            // ── Header bar ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_bleed_ai_logo),
                    contentDescription = "Bleed-AI",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bleed-AI",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (isFirstLogin) "Sign in to connect account" else "Session expired • Re-authenticate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showManualEntry = !showManualEntry }) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = "Manual token entry",
                        tint = if (showManualEntry) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            if (showManualEntry) {
                // ── Manual paste path (P12 escape hatch) ─────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Paste the JWT captured from your browser " +
                               "(DevTools → localStorage.getItem('token')). " +
                               "Quotes and whitespace are stripped automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = manualToken,
                        onValueChange = { manualToken = it },
                        placeholder = { Text("eyJhbGciOi…") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onTokenExtracted(manualToken) },
                        enabled = manualToken.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save session")
                    }
                }
            } else {
                // ── WebView login path ───────────────────────────────
                AndroidView(
                    factory = { ctx ->
                        var extracted = false
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true          // localStorage
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.userAgentString = WEBVIEW_UA

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true) // Google OAuth

                            fun tryExtract(view: WebView) {
                                if (extracted) return
                                view.evaluateJavascript(
                                    "(function(){ return localStorage.getItem('token'); })()"
                                ) { raw ->
                                    val token = raw?.trim()?.removeSurrounding("\"")
                                    if (!token.isNullOrBlank() && token != "null") {
                                        extracted = true
                                        onTokenExtracted(token)
                                        return@evaluateJavascript
                                    }
                                    // Cookie fallback, scoped to our origin only
                                    val cookieToken = CookieManager.getInstance()
                                        .getCookie(ZaiConfig.BASE_URL)
                                        ?.split(";")
                                        ?.mapNotNull {
                                            val p = it.trim().split("=", limit = 2)
                                            if (p.size == 2 && p[0] == "token") p[1] else null
                                        }
                                        ?.firstOrNull()
                                    if (!cookieToken.isNullOrBlank()) {
                                        extracted = true
                                        onTokenExtracted(cookieToken)
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val v = view ?: return
                                    if (!url.orEmpty().startsWith(ZaiConfig.BASE_URL)) return
                                    tryExtract(v)
                                    POLL_DELAYS_MS.forEach { delay ->
                                        handler.postDelayed({ tryExtract(v) }, delay)
                                    }
                                }
                            }

                            loadUrl(ZaiConfig.BASE_URL)
                        }
                    },
                    onRelease = { webView ->
                        handler.removeCallbacksAndMessages(null)
                        CookieManager.getInstance().flush()   // persist session cookies
                        webView.destroy()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Text(
                    text = "Log in inside this page — the app grabs the session " +
                           "automatically when it lands back on chat.z.ai.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
