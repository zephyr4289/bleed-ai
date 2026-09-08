# ✅ Zone 2 — Dual-Transport: Approved, With One Critical Pre-Move

First, the milestone check: **Batch A is live.** Sidebar, full history from the tree structure, seconds→ms, honest ping — that means the app *right now* syncs and displays your real chat.z.ai history natively. Half the product is done and didn't need the captcha at all. The GET-half of the app is finished forever (until they gate GETs, which would be hostile even by their standards).

Now the completions half. Your agent's question is the right one, and the probe results lock the decision table:

| Probe | Result | Consequence |
|---|---|---|
| P1 risk-gating | **Unconditional** | Zone 1 (pure native) is dead. No header ladder will save it. |
| P2 replay | **Single-use ticket** | No harvest-and-replay economy. Every send needs a fresh ticket minted in a browser context. |
| P3 sign spec | **Fully extracted** | *Not wasted* — this powers injected-fetch mode below. |

## The critical pre-move: ground-truth capture before writing any transport code

The sign spec has exactly the kind of ambiguities that burn days: is `window` stringified or numeric? Is `prompt` the last message or the whole conversation? Does `base64` wrap UTF-8 bytes? We *can* verify all of it empirically in one 20-minute experiment, because HMAC is deterministic — **capture one real signed request from the page, then assert our Kotlin implementation reproduces its `X-Signature` bit-for-bit from the captured inputs.** No guessing, no blind probing.

**Capture procedure** (in the P5 gate WebView while it's alive, or a throwaway engine):
1. Inject a fetch/XHR wrapper that deep-copies `{url, headers, body}` into `window.__zaiLastRequest` before calling through (patch both — SPA might use either).
2. Send "hi" through the page's own UI.
3. Dump `__zaiLastRequest` → record: exact query params, full header set, body JSON — especially the **`captcha_verify_param` field name and structural shape** (value is dead-single-use, but we learn where it lives and what wraps it).

This one artifact verifies the signer, reveals captcha param anatomy, and doubles as the prototype for the DOM-automation tee. **Do this first; it converts Zone 2 from "probably works" to "verified before built."**

## Architecture — the contract that makes this clean

```
ChatRepository (P4 — UNCHANGED)
        │ streamCompletion() — same pipeline, same persist semantics
        ▼
CompletionTransport          ← NEW interface; the only seam
        ├── OkHttpCompletionTransport   (today's P2 code, preserved verbatim)
        └── WebViewCompletionTransport  (new; selected by ZaiConfig flag)
```

The payoff of the seam: P4's persist-user → stream → persist-assistant pipeline, the partial-on-error semantics, the handoff in P7's VM — **none of it changes.** The WebView is just a different pipe emitting identical `StreamEvent`s.

## File-by-file agent brief

**1. `ZaiConfig` additions** — `COMPLETIONS_TRANSPORT = "webview"` (values: `"webview" | "okhttp"` — the okhttp escape hatch stays in case Z.ai ever drops the captcha, and for A/B during bring-up). Reserved: DOM selector constants for Phase 14.

**2. `network/transport/CompletionTransport.kt`** — `fun stream(request: ChatCompletionRequest): Flow<StreamEvent>`. Move P2's streaming body into `OkHttpCompletionTransport` unchanged. `ChatRepositoryImpl` swaps `apiService.streamChatCompletion(...)` for the injected transport. Only one constructor injection changes in the whole data layer.

**3. `network/transport/WebViewEngine.kt`** — the singleton heart. Contract:
- One `WebView`, **application context, created on Main** (WebView is main-thread-only; the engine's public API is suspend/flow-safe internally).
- Settings: JS + DOM storage, P5's mobile Chrome UA, cookies accepted. NOT `allowFileAccess`/`allowContentAccess` (P11 hardening carries over).
- **Bootstrap sequence:** `loadUrl(BASE)` → on page finish, `evaluateJavascript("localStorage.setItem('token', …)")` from `TokenManager` → `loadUrl(BASE)` again → readiness probe (`!!document.body && localStorage.getItem('token') !== null`). The SPA reads the token from localStorage at boot — that's how we log the engine in without any user interaction.
- `suspend fun eval(js: String): String` via `suspendCancellableCoroutine` wrapping `evaluateJavascript`.
- Bridge: `addJavascriptInterface(bridge, "ZaiBridge")`, events landing in a `Channel<String>` consumed by the transport's flow.
- Single-slot: one in-flight request (P7's VM already enforces this upstream — document it as engine contract, don't build a queue).
- Resilience: if a request produces no bridge events within N seconds → engine reload + one retry; if Cloudflare challenge is detected in page content → surface a typed error suggesting a gate visit (don't build the visible-verify UI yet).

**4. `network/transport/RequestSigner.kt`** — P3's formula in Kotlin (`javax.crypto.Mac`, HmacSHA256, double HMAC). **Gated on the ground-truth assertion from the pre-move** — the agent's acceptance test is: recompute from the captured request's own `requestId/userId/prompt/ts` → byte-equal to the captured `X-Signature`. If it doesn't match, the captured request *is* the debugging oracle — vary one input at a time (window as int vs string, prompt = last vs full) until it does. `userId` comes from the JWT's `sub` claim (decode the payload segment; no library needed).

**5. `network/transport/WebViewCompletionTransport.kt`** — flow shape:
- Ensure engine bootstrapped → compute sign → `eval` the send script (below) → collect bridge events → map to `StreamEvent` **identically to `ManualSseReader`'s mapping** (reasoning channel, content channel, citations, usage, `[DONE]`, error-with-partials).
- Cancellation: `invokeOnCancellation` → `eval(ZAI_ABORT_JS)` → JS `AbortController.abort()` kills the socket instantly. **The Stop button keeps its P2 semantics.**
- 401 mapping: bridge error events with code 401 → `AuthEventManager.emitTokenExpired()` — **critical integration point**, because WebView requests bypass `AuthInterceptor`, and without this the P5 gate never opens on expiry.

**6. `network/transport/TransportScripts.kt`** — JS as Kotlin raw strings (one file, same one-file philosophy as ZaiConfig):
- `ZAI_SEND_JS`: create `window.__zaiAbort = new AbortController()` → `fetch(url + "?...&signature_timestamp=" + ts, { method:"POST", credentials:"include", signal, headers:{X-Signature, X-FE-Version, Content-Type}, body })` → `response.body.getReader()` loop → decode → split SSE lines → for each `data:` payload `ZaiBridge.onEvent(JSON.stringify({t:"delta", ch:"content"|"reasoning", s: text}))` → `[DONE]` → `{t:"done"}` → catch → `{t:"error", code, msg, partial}`.
- `ZAI_ABORT_JS`, `ZAI_READY_JS`, `ZAI_LAST_REQUEST_JS` (the capture dump).
- Bridge message protocol is a *versioned contract*: one JSON envelope field `v:1` today, so future protocol changes can't silently desync JS from Kotlin.

**7. Probes to run during bring-up** (cheap, decisive):
- **Fetch-patch check:** after boot, does the page patch `window.fetch` globally? If an injected fetch to completions *without* captcha param still 404s into `captcha_missing_param`, the patch isn't auto-attaching → we need plan B for the param (below).
- **Upload gate check:** native `POST /api/v1/files/` with cookie → 200? If ungated, **attachments survive Zone 2 unchanged** (native upload → `file_ids` in the injected fetch body — another win for injected-fetch over DOM mode).

## The captcha-param question, honestly

Injected-fetch gives us full body control (model, reasoning, attachments, chat_id binding) — but only works if one of these holds: **(a)** the page's fetch patch auto-attaches the captcha param to any same-origin completion fetch (test: the probe above), **(b)** the page's captcha instance is reachable from injected JS to mint a ticket on demand, or **(c)** — the likely truth per the "invisible captcha" pattern — the param is only minted inside the page's own send flow. If all three fail, injected-fetch is dead on arrival and the **DOM-automation fallback becomes primary** (Phase 14, already designed-in: selectors in ZaiConfig, patch-fetch-then-trigger-real-send tee, page does 100% of the captcha/sign dance, chat_id learned from the page URL). The ground-truth capture in the pre-move tells us which world we're in *before* a line of transport code is written — that's why it goes first.

## Device gate

1. Config → `webview` → send "ping" → shimmer → content streams → "Thought for Xs" → persisted.
2. Stop button mid-stream → instant halt, no stub bubble.
3. Kill app mid-stream → reopen → user message present, edit&resend recovers.
4. **Browser mirror check** → conversation appears in chat.z.ai web UI (chat_id binding through the WebView pipe).
5. Expire the token deliberately → next send → **gate auto-opens** (the 401 bridge mapping).
6. Sidebar/history/search — unchanged behavior (still native OkHttp).

## 🚀 Run

```bash
git add -A && git commit -m "Zone 2: CompletionTransport seam, WebViewEngine singleton, RequestSigner (ground-truth verified), injected-fetch SSE bridge" && git push
gh run watch
```

CI compiles everything (WebView code can't execute there — the device gate above is the real test).

## Risks on the record

| Risk | Mitigation |
|---|---|
| Sign formula subtly wrong | Ground-truth capture = deterministic oracle, not guesswork |
| Captcha param unreachable from injected JS | DOM-automation fallback is designed-in (P14), selectors reserved in config |
| Frontend update breaks selectors/SDK | Page-side code does the work; drift surface is small; recon folder is the toolkit |
| Engine WebView ~80MB memory | Accepted for personal use; single instance, reused across requests |
| Engine silent death | No-event watchdog → reload + single retry |

**Order of operations for the agent: (1) ground-truth capture → paste me the dump (headers + body structure, redact token), (2) I confirm the signer inputs against it, (3) transport implementation.** Don't let it skip to step 3 — that capture is the difference between an afternoon and a week.
