# ⚙️ Phase 9 — Settings (the Wiring Phase)

Agreed on format — spec-level briefs from here on: we reason about the logic, the agent writes the code, CI verifies. Phase 9 is almost zero *new* logic; the engineering is in **three decisions** and **seven file touches**. **Gate:** CI green → install → theme/font/enter-to-send all change live on-device.

**Contract:**

```
themeMode      → stops being decorative: MainActivity collects it, ZaiTheme consumes it
fontScale      → stops being decorative: scales ALL text app-wide, live while dragging
enterIsSend    → already consumed by P7 composer; P9 just exposes its switch
streamingEnabled → PRUNED (decision #3)
+ session status row, clear-session, clear-local-cache (P12 debugging essentials)
```

## The three decisions

**#1 — Font scale via `LocalDensity` override, not Typography surgery.**
The PDF says "apply fontScale to ZaiTypography" — that means copying a 15-style Typography with every `fontSize`/`lineHeight` multiplied. Instead: inside `ZaiTheme`, override density:

```
LocalDensity provides Density(current.density, current.fontScale * fontScale)
```

Why this wins: ~3 lines, scales every `sp` value uniformly (body, titles, labels, code) — which is exactly the semantics of a *reading scale* — while `dp` values (icon sizes, padding, bubbles) stay fixed. If we later want labels to *not* scale, that's the moment to switch to Typography-copy, not before.

**#2 — OLED vs DARK actually become different.**
In the P0 theme both map to dark — the radio would be a placebo. One-line semantic: `DARK` → `background = ObsidianBase` (near-black, softer), `OLED` → `background = TrueBlack` (battery + contrast). Same for light? No — LIGHT stays LIGHT. Edit lives in `Theme.kt`'s dark scheme construction only.

**#3 — `streamingEnabled` is pruned, honestly.**
Building its consumer means a non-streaming response path (JSON body parser instead of SSE) — a second pipeline for a toggle with no use case in a streaming-first app. Decision: **no UI, no consumer; the DataStore key stays dormant** (removing it would churn P1's file for nothing). If we ever want a "low-bandwidth mode" that coalesces chunk emission, that's a P12-hardening idea, and the dormant key is already named for it.

## File-by-file agent brief

**1. `ChatDao` — one method.**
`@Query("DELETE FROM chats") suspend fun deleteAllChats()`. Reasoning for the agent: cascade FK wipes messages; FTS stays consistent automatically because contentEntity sync is trigger-based — no manual FTS cleanup.

**2. `ChatRepository` + Impl — one method.**
`clearLocalCache()` → `chatDao.deleteAllChats()`. Explicitly **no server call ever** — Room is a cache, the server is truth; this can never destroy a real chat. Also have Impl optionally call `refreshChats()` afterward so titles re-sync when a token exists.

**3. `ui/theme/Theme.kt` — signature + density.**
- `ZaiTheme(themeMode: String = "OLED", fontScale: Float = 1f, content)`
- Dark scheme background chosen by `themeMode` per decision #2.
- Wrap all content in the `LocalDensity` override from decision #1. Imports: `androidx.compose.ui.unit.Density` (the factory), `androidx.compose.ui.platform.LocalDensity`.

**4. `SettingsViewModel` (new).**
- Injects: `SettingsDataStore`, `TokenManager`, `ChatRepository`.
- Exposes four `StateFlow`s via `stateIn(scope, WhileSubscribed(5_000), default)`: `themeMode` (default "OLED"), `fontScale` (1f), `enterIsSend` (false), and `hasSession = tokenManager.tokenFlow.map { it != null }` (needs `kotlinx.coroutines.flow.map`).
- Actions: `setTheme(mode)`, `setFontScale(v)` (write-through — see slider note), `setEnterIsSend(b)`, `clearSession()` → `tokenManager.clearToken()`, `clearLocalCache()` → repository.clear + refresh.
- Slider write-through note for the agent: persist **on every tick**, because the live reflow *is* the feature (theme reads the DataStore flow; debouncing would kill the drag-preview). DataStore handles rapid async writes; churn is acceptable at personal scale. If jank ever shows up, the swap to a debounced writer is one function — don't pre-optimize.

**5. `SettingsScreen` (new).**
Structure, top to bottom:
- Scaffold + TopAppBar ("Settings", back arrow) — same pattern as the P8 stub it replaces.
- **Appearance:** four `RadioButton` rows (OLED / DARK / LIGHT / SYSTEM — note Theme.kt's `else` branch already handles SYSTEM via `isSystemInDarkTheme()`, no theme edit needed for it); slider row with `"%.2f".format(x) + "×"` label — **slider drag state is composable-local** (`remember { mutableFloatStateOf(collectedValue) }`), calls `vm.setFontScale` on every change; the VM flow re-seeds it after persistence. Dialog-confirm state stays composable-local too.
- **Behavior:** one Switch row, Enter-to-send.
- **Session:** status row (chip: "Active" in primary container / "Missing" in error container from `hasSession`), "Manage session" → `onManageSession()` callback, "Clear session" → confirm dialog → `vm.clearSession()`.
- **Storage:** "Clear local cache" → confirm dialog (text must reassure: *server data untouched; chats re-sync*) → `vm.clearLocalCache()`.
- **About:** static "1.0.0 · personal build" text — no BuildConfig, not worth enabling it.

**6. `MainActivity` — four edits.**
1. `@Inject lateinit var settingsDataStore: SettingsDataStore`
2. Collect at top of `setContent`: `themeMode` (`initial = "OLED"`) and `fontScale` (`initial = 1f`) — **initial values must match the DataStore defaults**, otherwise first frame flashes the wrong theme.
3. `ZaiTheme(themeMode = themeMode, fontScale = fontScale)` replacing the hardcoded `"OLED"`.
4. `composable("settings")` body: real `SettingsScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() }, onManageSession = { dismissedWithoutToken = false; showAuth = true })` — **replace** the stub, don't add a second composable.

**Deliberate interaction to leave alone:** "Clear session" nulls the token → P5's collector sees `token == null` with `dismissedWithoutToken == false` → **the auth gate opens immediately**. That's correct UX (you asked to end the session; the gate is the way back in). The agent should not "fix" this.

## Deviations vs. PDF

| # | PDF | Ours | Why |
|---|---|---|---|
| 1 | Scale Typography's 15 styles | `LocalDensity` override | 3 lines vs a copy-mechanical Typography; uniform sp scaling is the correct semantic |
| 2 | OLED ≡ DARK | Differentiated backgrounds | Radio must mean something |
| 3 | streaming toggle exists | Pruned, key dormant | Streaming is the product; no second parser for zero value |
| 4 | Slider per-tick writes (implied) | Per-tick writes, embraced | Live drag-preview is the payoff; churn acceptable, debounced swap documented as escape hatch |
| 5 | No session/cache tools | Added | P12 requires reset-to-fresh-install without reinstalling |

## ✅ Device gate

- Radio: OLED/DARK/LIGHT switch live; SYSTEM follows the device's dark-mode toggle.
- Slider: **entire app reflows while dragging** (this is the money shot); kill app → relaunch → scale persists.
- Enter-to-send: composer's keyboard action flips between Send-arrow and newline.
- Session row truthful; clear session → gate auto-opens; manage session → gate; active token → "Active".
- Clear cache → sidebar empties → drawer open re-syncs titles (needs token; without one it stays empty — correct until P12).

## 🚀 Run

```bash
git add -A && git commit -m "P9: settings live — theme modes wired, global font scale via Density, enter-to-send, session status/clear, local cache clear" && git push
gh run watch
```

**Red? Ranked suspects:** (1) `Density` (from `unit`) vs `LocalDensity` (from `platform`) import mix-up, (2) missing `flow.map` in the VM, (3) two `composable("settings")` registrations after a sloppy edit, (4) slider seeding recomposing every frame — it must be `remember`ed, keyed on the collected value only.

**Next: Phase 10 — file upload.** Attach button comes alive: `ActivityResult` picker, P2's `uploadFile` gains progress plumbing, thumbnails row in the composer, `file_ids` flowing through `persistUserMessage` into the request body. Small surface, one new state concept (pending attachments), and after it the app is feature-complete pending recon.
