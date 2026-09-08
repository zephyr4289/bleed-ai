# 🔴 Phase 12 — Live Recon & First Message

**Goal:** Turn six `[RECON]` placeholders into verified values, confirm every DTO against real payloads, and send the first streamed message through *your* UI. **Gate:** a thinking GLM reply renders in your app, persists, survives restart, and appears mirrored in chat.z.ai's own web UI.

**The strategic insight for this phase:** we never needed the browser's DevTools. The request contract can be fully reconstructed from three sources we *can* reach: (1) the token, (2) **the frontend's own JS bundles** — public files we can `curl` and grep, which contain every endpoint path, header name, version string, model id, and possibly the sign salt in plaintext, and (3) trial-and-error probing with curl until we find the minimal header set that returns 200. DevTools is a convenience, not a requirement — the bundle *is* the documentation.

## Setup + one security rule

```bash
pkg install -y curl jq
mkdir -p ~/recon/fixtures && cd ~/recon
echo "recon/" >> ~/zai/.gitignore    # BEFORE anything else
```

**The rule: the token is a live session credential. It lives in `~/recon/recon.env` (gitignored) and in the app's encrypted storage — nowhere else. Never in a commit, never in a chat.**

## Step 1 — Token acquisition (four paths, ordered by capability)

Key nuance that orders them: if probing reveals we need cookies (Cloudflare's `cf_clearance` is **httpOnly**), JavaScript in a page *cannot read it* — bookmarklets are blind to it. But our P5 gate's `CookieManager.getCookie()` *can* read httpOnly cookies. So the in-app path is the most capable, not just the most convenient.

**Path A (primary) — the app itself, with a 5-line debug affordance:**
In `SettingsScreen`'s session row, make **long-press on the status chip** copy the token to clipboard, and add a small "Copy cookies" text action that copies `CookieManager.getInstance().getCookie(ZaiConfig.BASE_URL)`. Then in Termux (`pkg install termux-api` + the Termux:API app from F-Droid — versions must match your Termux source):

```bash
termux-clipboard-get > token.txt          # after long-press copy in app
termux-clipboard-get > cookies.txt        # after cookie copy
```

The auth gate auto-extraction (P5) may already have your token stored from P11 testing — check "Session: ACTIVE" first.

**Path B (no code changes) — bookmarklet:** Firefox Android → add a bookmark, edit its URL to:

```
javascript:(function(){prompt('token', localStorage.getItem('token')||'NOT FOUND')})()
```

Open chat.z.ai logged in → open that bookmark → long-press-copy from the prompt. (Chrome Android strips `javascript:` — Firefox or Via Browser work. If this gets the token but Step 3 says cookies required → jump to Path A.)

**Path C (full capture) — CDP:** `pkg install android-tools`, pair wireless debugging to localhost, forward `tcp:9222 → chrome_devtools_remote`, and reuse `zai_recon.py` from our earlier planning session — it captures the real request headers + body verbatim.

**Path D — mitmproxy:** last resort, cert dance.

## Step 2 — Static recon: the bundle *is* the docs

```bash
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
curl -s https://chat.z.ai/ -H "User-Agent: $UA" > page.html
grep -oE 'src="[^"]+\.js[^"]*"' page.html | sed 's/src="//;s/"$//' | sort -u > bundles.txt
# download each (absolute-ize relative paths), save as bundles/NN.js
```

Then run each grep and **write what it answers into `findings.txt`**:

```bash
grep -ohE 'X-[A-Za-z-]*[Vv]ersion[^,;)]{0,40}' bundles/*.js | sort -u   # FE header name+value
grep -ohE '"prod-[A-Za-z0-9._-]+"' bundles/*.js | sort -u               # version value candidates
grep -ohE '/api/[a-z0-9/_${}.-]+' bundles/*.js | sort -u | head -60     # endpoint inventory
grep -ohE '(glm|0727|GLM)[A-Za-z0-9._-]*' bundles/*.js | sort -u | head -40   # model ids
grep -c 'reasoning_content' bundles/*.js                                 # reasoning channel confirmed?
grep -ohE "localStorage\.getItem\('[^']+'" bundles/*.js | sort -u        # token key name
grep -inE 'hmac|signature|sign\(|secret|salt' bundles/*.js | head -20    # request signing?
```

The model-id grep is how we settle `glm-5.3` vs `0727-360B-API` vs whatever's live. If the sign grep hits real code (look for `HmacSHA256`/`crypto` near it), capture the surrounding ~200 chars — that's the payload template for the interceptor.

## Step 3 — Dynamic probes: find the minimal working contract

```bash
cat > recon.env <<'EOF'
export B="https://chat.z.ai"
export T="<token>"
export UA="<same desktop UA>"
export FEV="<value from Step 2, or blank>"
EOF
source recon.env
probe() { curl -s -o /tmp/b -w "%{http_code}" "$@"; echo "  |  $(head -c 100 /tmp/b)"; }
```

**The header ladder** — run against `GET $B/api/models` with the token, stop at the first 200, **record the rung**:

1. `Authorization: Bearer` + `Content-Type` only
2. + `User-Agent`, `Origin`, `Referer`, `Accept-Language`, `X-FE-Version: $FEV`
3. + client hints (`sec-ch-ua`, `sec-fetch-site: same-origin`, etc.)
4. Cookie mode: drop `Authorization`, use `Cookie: token=$T` (plus `cf_clearance` from Path A if you have it)

**Then the endpoint suite** at the winning rung, each saving a fixture:

- `GET $B/api/v1/chats/?page=0` → `fixtures/chats_page0.json`
- `GET $B/api/v1/chats/<id-from-above>` → `fixtures/chat_detail.json`
- `POST $B/api/chat/completions` with `stream:true`, one user message, `chat_id:""` → `curl -N --max-time 60 ... | head -c 3000 | tee fixtures/sse_sample.txt`

**Classification questions to answer in findings.txt:**
- `jq -r '.[0].updatedAt' chats_page0.json` → `1.7e12`-magnitude = **ms** · `1.7e9` = **seconds** · a string = **ISO-8601**. Same check on the detail's message timestamps.
- Re-post completions with a **fake UUID** `chat_id` → 200 (offline temp-ids valid) or 400 (createChat-first is mandatory)?
- In the SSE sample: does `reasoning_content` actually arrive? What are the citation objects' real field names? Is there a `sign`-echo or usage object?
- Same completions call **without** `reasoning:true` → does the thinking channel disappear (confirms our flag)?

## Step 4 — Findings → code (contract level)

| Finding | Where it lands |
|---|---|
| FE version / model string / endpoint paths | `ZaiConfig` — the 6-value swap |
| ISO timestamps | DTO fields `updatedAt`/`timestamp` → `String`; parse with `java.time` (minSdk 26 = fine) at the mapper boundary |
| Second-based timestamps | ×1000 at the mapper |
| Header rung > 1 | Add the winning rung's headers to `AuthInterceptor` (additive, one block) |
| Cookie auth required | Swap the Authorization block for the Cookie header — the P2-reserved slot |
| Sign header found | Interceptor computes HMAC-SHA256 (javax.crypto) over the bundle's payload template using `SIGN_SALT` |
| Citation shape differs | Adjust `CitationItem` field names — nothing downstream changes |
| Fake `chat_id` → 400 | No code change (we're server-first already); note for offline behavior |

## Step 5 — Swap + commit

Edit `ZaiConfig` (+ whatever Step 4 table demands), `git add` — **verify `git status` shows no `recon/`** — commit as `P12: live recon values + DTO corrections`. Push, watch CI green, install.

## Step 6 — Live-fire protocol (in order, checking each)

1. Settings → paste token via gate manual entry (or gate auto if WebView path works)
2. Send `ping` → shimmer → content streams → Done → **persisted bubble with "Thought for Xs"**
3. Kill app → reopen → message still there (Room)
4. Open drawer → **old web chats appear** (this proves `refreshChats` + sync end-to-end)
5. Open a web-created chat → full history renders
6. Search a word from an old web chat (FTS over server data)
7. Toggle 🌐 → ask something current → citation strip renders
8. Send a message → open chat.z.ai in the browser → **the conversation is there** (mirror = `chat_id` binding works)
9. Edit&resend → verify in browser that history was truncated, not duplicated

## Troubleshooting

| Symptom | Meaning → action |
|---|---|
| Banner shows `HTTP 401` | Token dead → re-grab (it *should* also auto-open the gate) |
| Banner `HTTP 403` + HTML body | Cloudflare rung → escalate ladder; if all fail, the TLS fingerprint issue is real → we design the WebView-transport fallback (not today) |
| `HTTP 404` | Path wrong → back to Step 2's endpoint grep |
| 200 but zero chunks | Model string wrong or `reasoning`/`stream` flags rejected → check sample body vs bundle |
| App crash on history open | DTO mismatch → compare fixture JSON field-by-field against the DTO; that's why we saved fixtures |

## After the gate passes

The app is *done*. What remains is the maintenance contract you accepted on day one: when Z.ai rotates their frontend, the symptom is sudden 403/404s, the fix is re-running Step 2's greps and a one-line `ZaiConfig` diff — your `~/recon/` folder is the permanent toolkit for that.

**Now go run Steps 1–3.** Paste me back: `findings.txt`, the first ~10 lines of `sse_sample.txt`, and the timestamp `jq` outputs — and I'll write the exact `ZaiConfig` + DTO diffs against your real data. That last step is the only one where guessing beats reading, so let's read.
