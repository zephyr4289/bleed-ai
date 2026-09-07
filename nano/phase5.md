# 🔐 Phase 5 — Auth Gate & Token Bootstrap

**Goal:** The WebView-based session gate + first-launch bootstrap + manual paste fallback. Written now so the skeleton is complete; *live-verified* in P12. **Gate:** CI green + (bonus, testable today) the WebView actually loads chat.z.ai in the APK.

**The gate's state machine (contract):**

```
┌─────────────┐  token==null at launch      ┌──────────────────┐
│ Placeholder │ ──────────────────────────▶ │  AuthGate screen │
│   (home)    │                             │  (WebView login  │
└─────▲───────┘ ◀────────────────────────── ┘   or manual paste)│
      │        token saved (auto-dismiss)                       │
      │ 401 event (any API call) ──────▶ re-open gate           │
      │ user dismisses w/o token ──▶ stay home, remember choice │
```

**One real gap this fixes vs. the PDF:** the PDF's dialog only opened on a `TokenExpired` event — but a *fresh install has no token*, so **nothing would ever trigger a 401** until P7 existed. The app would sit there brained-dead on first launch. Ours shows the gate at launch when `token == null`. Bootstrap solved.

## Setup

```bash
cd ~/zai
mkdir -p app/src/main/java/com/zai/chat/ui/auth
```

---

## File 1: `ui/auth/TokenReconnectScreen.kt`

```kotlin
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            // ── Header bar ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isFirstLogin) "Connect session" else "Session expired",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
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
                            settings.userAgentString = WEBVIEW_UA

                            CookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                setAcceptThirdPartyCookies(this@apply, true) // Google OAuth
                            }

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
```

## File 2: `ui/MainActivity.kt` — full rewrite (replaces P0 placeholder)

```kotlin
package com.zai.chat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zai.chat.data.local.preferences.TokenManager
import com.zai.chat.network.auth.AuthEventManager
import com.zai.chat.ui.auth.TokenReconnectScreen
import com.zai.chat.ui.theme.ZaiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var authEventManager: AuthEventManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ZaiTheme(themeMode = "OLED") {
                var showAuth by remember { mutableStateOf(tokenManager.getStoredToken() == null) }
                var dismissedWithoutToken by remember { mutableStateOf(false) }

                // Reactive auth state: token appearing (any path) closes the gate;
                // token vanishing re-opens it — unless the user explicitly backed out.
                LaunchedEffect(Unit) {
                    tokenManager.tokenFlow.collect { token ->
                        when {
                            token != null -> {
                                showAuth = false
                                dismissedWithoutToken = false
                            }
                            !dismissedWithoutToken -> showAuth = true
                        }
                    }
                }

                // Any 401 anywhere in the app forces the gate (P2 interceptor bus).
                LaunchedEffect(Unit) {
                    authEventManager.events.collect { showAuth = true }
                }

                if (showAuth) {
                    TokenReconnectScreen(
                        isFirstLogin = tokenManager.getStoredToken() == null,
                        onTokenExtracted = { token ->
                            // TokenManager trims quotes/whitespace and updates
                            // tokenFlow → the collector above closes the gate.
                            tokenManager.saveToken(token)
                        },
                        onDismiss = {
                            dismissedWithoutToken = true
                            showAuth = false
                        }
                    )
                } else {
                    PlaceholderHome(
                        hasSession = tokenManager.getStoredToken() != null,
                        onManageSession = {
                            dismissedWithoutToken = false
                            showAuth = true
                        }
                    )
                }
            }
        }
    }
}

/** Temporary home — replaced by the real chat screen in P7. */
@Composable
private fun PlaceholderHome(
    hasSession: Boolean,
    onManageSession: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Z.AI", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Phase 5 — auth gate online",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            AssistChip(
                onClick = onManageSession,
                label = { Text(if (hasSession) "Session: ACTIVE" else "Session: NONE") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (hasSession)
                        MaterialTheme.colorScheme.surfaceVariant
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onManageSession) { Text("Manage session") }
        }
    }
}
```

---

## 🔧 Deliberate upgrades vs. the PDF's version

| # | PDF | Ours | Why |
|---|---|---|---|
| 1 | Cramped `AlertDialog` + WebView | Full-screen gate | Google OAuth popups + Cloudflare interstitials need space; dialogs clip and trap focus |
| 2 | Default WebView UA | Mobile Chrome UA | Cloudflare challenges bare WebViews; stock mobile Chrome UA passes far more often |
| 3 | Token checked **once** on `onPageFinished` | Polled at 0 / 800 / 2500 / 5000 ms | SPA writes `localStorage` asynchronously post-render — single check loses that race constantly |
| 4 | localStorage only | + cookie fallback (`token=` cookie scoped to BASE_URL) | Open WebUI builds vary on where the JWT lives |
| 5 | No manual path | Key-icon toggle → paste field | **Your P12 escape hatch**: if the WebView gets Cloudflare-blocked, you paste the recon token and you're done in 10 seconds |
| 6 | Gate only on 401 event | + first-launch bootstrap (`token==null` → gate at startup) | PDF's app was inert on fresh install — no request = no 401 = no dialog, ever |
| 7 | Dismiss = dialog closes | `dismissedWithoutToken` flag | Without it, dismissing with no token re-triggers instantly (infinite gate) |

## ✅ What you can verify **today** (no live API needed)

After CI green + APK install:

1. **Fresh install → gate auto-opens** (no token stored). WebView loads chat.z.ai.
2. **Log in inside the WebView** (if Cloudflare/OAuth cooperate): when the page lands back logged-in, token auto-extracts within ~5s → gate closes → placeholder shows **Session: ACTIVE**. If that works — congrats, auth is *done early* and P12 shrinks to just endpoint verification.
3. **If WebView gets blocked** (spinner/challenge loop): key icon → paste any string → gate closes (proves the manual path + trimming).
4. **Dismiss without token** → home shows **Session: NONE**, stays closed (no infinite gate).
5. **Manage session** → reopens gate.

## 🚀 Run it

```bash
git add -A && git commit -m "P5: full-screen auth gate — WebView token extraction + poll, cookie fallback, manual paste, first-launch bootstrap" && git push
gh run watch
```

- **Red?** Usual suspects: (a) `removeSurrounding("\"")` escaping mangled by nano — check the quote count, (b) local `fun tryExtract` inside `apply {}` needs Kotlin ≥1.4 (we're on 2.0, fine), (c) missing `imePadding` import. `gh run view --log-failed | tail -60` and paste if stuck.

**Next: Phase 6 — the component library** (`StreamingCursor`, `ThinkingBlock` with the LaunchedEffect fix, `CodeBlockView`, `WebCitationStrip`, the Markdown decision, `MessageBubble` with markdown wired in). That's the last phase before the chat screen goes live — the visual soul of the app.
