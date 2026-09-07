Solid spec — the PDF covers ~90% of the system. Before the master list, here's what the spec **misses** so we fold those into the plan (otherwise the agent will hit walls):

## ⚠️ Gaps in the spec (tasks added below to cover them)

1. **`MarkdownRenderer.kt` is in the tree but has zero implementation** — and `MessageBubble` currently renders raw text. Decision needed: pull in `com.mikepenz:multiplatform-markdown-renderer` (recommended) or build a minimal GFM parser.
2. **`ZaiApplication.kt` never written** — needs `@HiltAndroidApp`, and the manifest must reference it.
3. **`AndroidManifest.xml` content missing** — needs `INTERNET` permission + application name.
4. **`proguard-rules.pro` missing** — release build has `isMinifyEnabled = true`, so kotlinx.serialization will crash without keep rules.
5. **Dead settings**: `fontScale` is stored but never applied to typography; `enterIsSend` and `streamingEnabled` are never read by the composer.
6. **File upload UI**: API method exists (`uploadFile`) but composer has `/* File picker integration */` placeholder.
7. **`MessageActionToolbar.kt`** is in the tree but its logic is inlined in `MessageBubble` — drop the file or extract it.
8. **`ThinkingBlock` bug**: mutates `expanded` during composition (`if (isStreamingReasoning && !expanded) expanded = true`) — should be `LaunchedEffect`.
9. **Transient→Room handoff flicker**: on `Done`, the transient message disappears and the Room-inserted one appears — verify no visible gap (may need to keep transient visible until Room emission arrives).

---

# 📋 Master Implementation Board

Ordered by dependency. Each phase ends **compilable and manually testable** — never let the agent proceed on a red build.

| # | Phase | Delivers | Verified by |
|---|---|---|---|
| 0 | 🔍 Recon | 6 real values from DevTools, confirmed via curl | curl returns 200 + SSE chunks |
| 1 | 🏗️ Skeleton + Design System | Installable app, full theme | Build+run, themed empty screen |
| 2 | 🔐 Config & Secure Storage | `ZaiConfig`, `TokenManager`, `SettingsDataStore` | Token save/read roundtrip |
| 3 | 🌐 Network + SSE Engine | DTOs, interceptors, `ManualSseReader`, `ZaiApiService` | Fetch chat list in a smoke test |
| 4 | 💾 Room Persistence | Entities, FTS4, DAOs, DB, mappers | DAO insert/query smoke test |
| 5 | 📦 Repository Layer | `ChatRepository` + Impl + Hilt binding | `refreshChats()` populates Room |
| 6 | 🔄 Auth Flow | WebView reconnect dialog, 401 handling | 401 → dialog → token saved |
| 7 | 🧩 Component Library | Cursor, ThinkingBlock, CodeBlock, Citations, Markdown, MessageBubble | `@Preview` renders all |
| 8 | 💬 Chat Screen (E2E) | UiState/Event, ViewModel, Composer, Screen | **Send → live stream → persists** |
| 9 | 🗂️ Drawer, History, Search | Drawer list, FTS live search, pin/swipe-delete | Open old chat from sidebar |
| 10 | ⚙️ Settings | Settings screen + **wire fontScale/enterIsSend** | Change theme/font live |
| 11 | 📎 File Upload | Picker, upload progress, `file_ids` in request | Attach image → model sees it |
| 12 | 🛡️ Hardening & Release | Edge cases, perf pass, proguard, release APK | 1000-msg scroll, release install |

---

## Phase 0 — Recon (manual, ~15 min, do NOT skip)

- [ ] Log into chat.z.ai on desktop Chrome → DevTools → Network
- [ ] Capture: **①** Bearer JWT (`localStorage.getItem('token')`) **②** completions path **③** `X-FE-Version` header key+value **④** Origin/Referer **⑤** User-Agent **⑥** exact `model` string in POST payload
- [ ] Verify each endpoint with curl: `GET /api/v1/chats/?page=0` (200), `POST /api/chat/completions` (SSE chunks stream)
- [ ] Save one **full JSON capture** of: chat list response, one chat detail, 3 SSE lines → these become your unit-test fixtures
- [ ] Fill all 6 values into a scratch note (pasted into `ZaiConfig` in Phase 2)

## Phase 1 — Skeleton + Design System

- [ ] `gradle/libs.versions.toml` + root `build.gradle.kts` + `settings.gradle.kts`
- [ ] `app/build.gradle.kts` (as spec'd, incl. opt-ins)
- [ ] `AndroidManifest.xml` — **INTERNET permission**, `android:name=".ZaiApplication"`, edge-to-edge theme
- [ ] `ZaiApplication.kt` with `@HiltAndroidApp` ⚠️ *gap #2*
- [ ] Theme: `Color.kt`, `Type.kt`, `Spacing.kt`, `Shape.kt`, `Motion.kt`, `Theme.kt` (all 6 files)
- [ ] `MainActivity` with empty themed Scaffold (title "Z.AI")
- ✅ **Done when:** `assembleDebug` passes, app installs, dark OLED screen renders

## Phase 2 — Config & Secure Storage

- [ ] `ZaiConfig.kt` — fill with **real recon values** (esp. `MODEL_DEFAULT`, `FE_VERSION_HEADER_VALUE`)
- [ ] `TokenManager.kt` (EncryptedSharedPreferences + StateFlow)
- [ ] `SettingsDataStore.kt` (all 5 keys)
- ✅ **Done when:** builds; temporarily log `tokenFlow.value` from a debug LaunchedEffect to confirm encryption works

## Phase 3 — Network + SSE Engine

- [ ] All DTOs: `ChatCompletionRequest/Chunk`, `ChunkChoice/Delta`, `UsageInfo`, `CitationItem`, `ChatList/Detail` responses, `FileUploadResponse`, `ModelsListResponse`
- [ ] `AuthEvent.kt`, `AuthEventManager`, `AuthInterceptor`
- [ ] `StreamEvent.kt`, `ManualSseReader.kt` (line-by-line, partial-text-on-error)
- [ ] `ZaiApiService.kt` (all 7 methods)
- [ ] `NetworkModule.kt` (Json + OkHttp with timeouts)
- ✅ **Done when:** temp debug button calls `getChats(0)` and logs real titles; a test `streamCompletion` call logs 5 SSE events

## Phase 4 — Room Persistence

- [ ] `ChatEntity`, `MessageEntity`, `MessageFtsEntity` (+ FTS triggers)
- [ ] `ChatDao`, `MessageDao` (incl. FTS MATCH query)
- [ ] `ZaiDatabase`, `DatabaseModule`
- [ ] Domain models: `Chat`, `Message`, `MessageRole`, `SearchCitation` + entity↔domain mappers
- ✅ **Done when:** DAO smoke test — insert chat+messages, query flow emits, FTS search returns hit

## Phase 5 — Repository Layer

- [ ] `ChatRepository` interface (all 10 methods)
- [ ] `ChatRepositoryImpl` — incl. `streamCompletion` (persist user msg → history → SSE → persist assistant msg, partial-save on error), optimistic `createChat` with temp-ID swap, rollback-safe `deleteChat`
- [ ] `AppModule.kt` binding
- ✅ **Done when:** `refreshChats()` fills Room from live server; fake prompt through `streamCompletion` persists a message row

## Phase 6 — Auth Flow

- [ ] `SilentTokenReconnectDialog` (WebView → `localStorage.getItem('token')` → auto-dismiss)
- [ ] Collect `authEventManager.events` in MainActivity → show dialog on `TokenExpired`
- [ ] Dev path: manual token paste into `TokenManager` (debug-only screen or toast-action)
- ✅ **Done when:** expire token manually → API call → dialog opens → reconnect → API works again

## Phase 7 — Component Library

- [ ] `StreamingCursor.kt`
- [ ] `ThinkingBlock.kt` + shimmer — **fix gap #8 (LaunchedEffect)**
- [ ] `CodeBlockView.kt` (copy/wrap/lang chip)
- [ ] `WebCitationStrip.kt`
- [ ] **`MarkdownRenderer.kt`** ⚠️ *gap #1* — add mikepenz renderer dependency, wire `CodeBlockView` into its code-block slot
- [ ] `MessageBubble.kt` — swap raw `Text` for MarkdownRenderer; keep user pills as plain text
- [ ] Delete or extract `MessageActionToolbar.kt` ⚠️ *gap #7*
- [ ] `@Preview` for each component (dark + light)
- ✅ **Done when:** all previews render; build green

## Phase 8 — Chat Screen (the E2E milestone 🎯)

- [ ] `ChatUiState.kt`, `ChatUiEvent.kt`
- [ ] `ChatViewModel.kt` — streamingJob lifecycle, edit&resend, regenerate, retry-from-partial, unread-token badge counter
- [ ] `ChatComposer.kt` — chips row, morphing send/stop, haptics, **respect `enterIsSend` setting** ⚠️ *gap #5*
- [ ] `ChatScreen.kt` — reverseLayout LazyColumn, transient streaming item, error banner, scroll-FAB with badge
- [ ] Wire transient→Room handoff; verify no flicker ⚠️ *gap #9*
- ✅ **Done when:** **full loop**: type → send → thinking streams → content streams → Done persists → kill app → reopen → message still there (from Room)

## Phase 9 — Drawer, History, Search

- [ ] `DrawerViewModel.kt` (debounced FTS query)
- [ ] `DrawerChatList.kt` (swipe-delete, pin, search results view)
- [ ] Wire `ModalNavigationDrawer` + NavHost in `MainActivity` (routes: `chat?chatId={id}`, `settings`)
- ✅ **Done when:** sidebar shows real history; search "kotlin" finds old messages; open one → full messages render

## Phase 10 — Settings

- [ ] `SettingsViewModel.kt`, `SettingsScreen.kt` (theme radio, font slider, enter-to-send)
- [ ] **Apply `fontScale` to `ZaiTypography`** via CompositionLocal ⚠️ *gap #5*
- ✅ **Done when:** change theme → instant; font slider → live reflow

## Phase 11 — File Upload

- [ ] `rememberLauncherForActivityResult` (GetContent / OpenDocument) in composer
- [ ] Upload → progress ring → thumbnail chip → `file_ids` injected into `ChatCompletionRequest`
- ✅ **Done when:** attach an image, ask "what's in this image", model answers

## Phase 12 — Hardening & Release

- [ ] **Proguard rules** for kotlinx.serialization + Hilt ⚠️ *gap #4*
- [ ] Edge cases: airplane-mode mid-stream (partial saved + retry chip), malformed SSE line injection, oversized chat
- [ ] Perf: 1000-message LazyColumn scroll test (check `items(key=)` working, no full recompose per chunk)
- [ ] Release build: R8 passes, signed APK installs, streaming works in release
- [ ] Optional: export-chats-as-markdown, app icon

---

## 🤖 Working rules for the Antigravity agent (use in every phase prompt)

```
- Implement ONLY the files listed in this phase. Do not touch other files.
- Every file must be complete and compilable — no TODOs, no placeholders, no "...".
- At the end: run ./gradlew assembleDebug and fix all errors before finishing.
- Do not rename packages or classes defined in the spec.
- State which files you created/modified and paste the final build result.
```

---

The board is ready. Say **"start Phase 0"** (or whichever phase) and I'll generate the exact full prompt for the agent — including the recon curl commands, or the complete file list + constraints for that phase — so you can paste it in and let it run.

