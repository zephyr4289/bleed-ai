# 🛡️ Phase 11 — Hardening & Release

**Goal:** Make the APK *trustworthy*: a signing key that never changes (your chat history depends on it), R8 rules that can't break serialization, the edge matrix verified, and a release artifact flowing from CI. **Gate:** CI green → `app-release.apk` installs *over* the existing debug install or cleanly → smoke protocol passes.

## The one decision that matters most: signing stability

Here's the trap the current setup is heading toward: release is debug-signed, and the debug keystore on GitHub runners is *not contractually stable* — runner image updates can regenerate it. The day that happens, the new APK refuses to install over the old one ("signature mismatch"), and the fix is **uninstall → wipe Room → lose every local chat**. Room is your source of truth for reads. So: **we mint our own keystore once, today, and it signs every release forever.** This is a data-preservation decision disguised as a build decision.

### A. Mint + stash the key (Termux, once)

```bash
pkg install -y openjdk-17
keytool -genkeypair -v -keystore ~/zai-release.jks -alias zai \
  -keyalg RSA -keysize 2048 -validity 10000   # ~27 years; you'll never redo this

# back it up somewhere real — losing it = forced uninstall on next update
cp ~/zai-release.jks /sdcard/Download/  # and ideally a cloud copy

base64 -w0 ~/zai-release.jks > /tmp/ks.b64
gh secret set SIGNING_KEYSTORE < /tmp/ks.b64
gh secret set SIGNING_KEYSTORE_PASSWORD --body "<your store password>"
gh secret set SIGNING_KEY_ALIAS        --body "zai"
gh secret set SIGNING_KEY_PASSWORD     --body "<your key password>"
gh secret list                          # verify all 4 exist
```

### B. `app/build.gradle.kts` — signing block + release wiring

```kotlin
signingConfigs {
    create("release") {
        val ksPath = System.getenv("SIGNING_KEYSTORE_PATH")
        if (ksPath != null) {                       // CI
            storeFile = file(ksPath)
            storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
        // env absent (local builds) → fall through to debug signing below
    }
}
// in buildTypes { release { ... } }:
signingConfig = if (System.getenv("SIGNING_KEYSTORE_PATH") != null)
    signingConfigs.getByName("release") else signingConfigs.getByName("debug")
```

### C. `.github/workflows/android.yml` — decode + release artifact

Add before the gradle step: decode the secret → `$GITHUB_WORKSPACE/keystore.jks`, and set the four `SIGNING_*` envs on the build step. Add a second assemble: `./gradlew assembleDebug assembleRelease` and a second artifact upload for `app/build/outputs/apk/release/app-release.apk`. Nothing else changes.

## R8: keep rules, not prayers

`kotlinx.serialization` 1.7.x ships consumer rules, and our decode paths use reified generated serializers — but the P10 `FileAttachment` round-trip through Room is exactly the kind of thing that works in debug and `ClassCastException`s in release, and CI is your only compiler. Explicit keeps are five lines of insurance:

**`app/proguard-rules.pro`** (full content — it's config):

```
# kotlinx.serialization: DTOs + enums + generated serializers
-keepattributes *Annotation*, InnerClasses, Signature
-keep class com.zai.chat.network.model.** { *; }
-keep class com.zai.chat.data.model.** { *; }
-keep,includedescriptorclasses class com.zai.chat.**$$serializer { *; }
-keepclassmembers class com.zai.chat.** { *** Companion; }
-keepclassmembers enum com.zai.chat.** { values(); valueOf(); }
```

Note `data.model` is included deliberately — that's `FileAttachment`/`SearchCitation` living in JSON columns. Hilt, Room, OkHttp, Coil all ship their own consumer rules; don't add anything for them. And log the escape hatch: if release smoke-testing ever fails *mysteriously* while debug passes, `isMinifyEnabled = false` is a legitimate personal-app answer — we keep R8 on by default, but we don't worship it.

## The edge matrix — mostly *verify*, three tiny fixes

The P2–P4 architecture already handles most of this by construction. Audit, don't rewrite:

| Scenario | Expected behavior | Where it lives | Action |
|---|---|---|---|
| Send with no token | User msg in Room, "Connection failed" banner, gate does NOT auto-open | P4 catches IOException; P5 gate only opens on token==null / 401-event | Verify |
| Send with expired token | **Gate auto-opens** even though `refreshChats` swallows the exception | AuthInterceptor emits the 401 event *below* the repository's catch — this is exactly why the event bus exists (P2) | Verify — this one proves the design |
| Airplane mode mid-stream | Partial text persisted, `isPartial=true`, retry chip renders | P2 reader partials → P4 persist → P6 bubble | Verify |
| Manual Stop mid-word | Buffers discarded, nothing persisted, no stub bubble | P7 documented semantics | Verify |
| Rotation during streaming | Stream continues; live text intact | Nav-scoped VM survives config change; Job untouched | Verify |
| Back-press during streaming | Socket cancelled instantly (P2's `invokeOnCompletion`), no zombie | P2/P7 | Verify |
| Process death mid-stream | User msg survives, reply lost; **edit&resend is the recovery path** | Accepted + documented — don't build resurrection | Document only |
| FTS input like `"; DROP TABLE` | Sanitizer neutralizes to quoted tokens, empty result at worst | P4 sanitizer | Verify in drawer |
| Kill app mid-upload | Chip gone on relaunch, no orphan job | P10 documented | Document only |
| 1000-message scroll | Visible-item composition only; keys diff; `remember(markdown)` caches parses; `asReversed()` is a view not a copy | P6/P7 already correct | Scroll test, not code |

**The three real code edits, all one-liners:**
1. `TokenReconnectScreen` WebView hardening: `settings.allowFileAccess = false` and `settings.allowContentAccess = false` — the auth WebView needs JS + DOM storage, nothing else; least-privilege costs nothing.
2. Rename the badge label concept: `unreadWhileScrolledUp` counts *chunks*, not tokens — leave the field (churn), but the badge renders `+N` which is honest enough. **No change; documenting so nobody "fixes" it later.**
3. Confirm nothing else references `streamingEnabled` (dormant P1 key) — grep, expect zero hits, move on.

## Release smoke protocol (10 minutes, after install)

Run these *in release*, because R8 differences only exist there:
1. **Gallery** — every section renders (R8 didn't strip components).
2. **Serialization round-trip** — send a message with an attachment → chip appears → kill app → reopen → chip still there. This exercises `FileAttachment` decode in release — the #1 risk candidate.
3. **FTS search** in the drawer — proves Room queries survived shrinking.
4. **Settings persistence** — flip theme, kill, relaunch (DataStore intact).
5. **Auth gate** — clear session → gate opens → WebView loads (the hardened WebView still works).
6. Streaming itself stays gray until P12 — banner-error path is the proxy.

## 🚀 Run

```bash
git add -A && git commit -m "P11: stable release signing via CI secrets, R8 keep rules, WebView hardening, edge matrix audit" && git push
gh run watch
gh run download -n release-apk -D ~/apk
termux-open ~/apk/app-release.apk
```

**Red? Ranked suspects:** (1) secrets env not visible to Gradle — envs must be declared on the *gradle step*, not job level defaults mixed up, (2) `keyPassword` vs `storePassword` swapped (same value is fine — just consistent), (3) base64 secret got a trailing newline — use `-w0`, (4) duplicate `signingConfig` lines in `buildTypes.release` after editing.

---

**State of the world after P11:** the app is feature-complete, hardened, signed with a key you control, and producing both artifacts from every push. Everything that remains is P12: live recon (the deferred cookie/endpoint work — the Termux CDP script and curl suite from the old Phase 0 plan is exactly what we execute there), swapping the six `[RECON]` placeholders in `ZaiConfig`, and the first real streamed message in your own UI. That's the payoff phase — say the word when you're ready to go live.
