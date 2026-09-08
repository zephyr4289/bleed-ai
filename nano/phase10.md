# 📎 Phase 10 — File Attachments

**Goal:** The attach button lives: system picker → cache copy → upload with progress → chips row in composer → `file_ids` on the wire → attachment names visible in chat history. **Gate:** CI green → install → attach an image, watch progress, send, see the attachment chip persist in the message (the model actually *seeing* it waits for P12's token, but the entire pipeline is provable without it).

Format noted — contracts and state shapes pinned exactly, everything else is reasoning for the agent to implement.

## The four decisions

**#1 — Copy to cache before upload. Don't stream from `content://`.**
OkHttp wants a `File` (or a hand-rolled InputStream body). The moment we copy `contentResolver.openInputStream(uri)` → `File(cacheDir/uploads/, timestamp_name)`, three problems vanish: no need to persist URI read-permissions (we consume it immediately in the picker callback), no weird provider lifetimes, and retriable uploads. **Delete the cache file as soon as its upload succeeds** — the returned `file_id` is all we ever need afterward. `cacheDir` cleanup by the OS is the backstop, not the plan.

**#2 — Attachments become named records, not bare id strings.**
P3/P4 stored `List<String>` (ids). Ids alone make history useless — scrolling back you'd see "📎" with no idea what it was. Introduce:

```kotlin
// data/model — the persistent record
data class FileAttachment(
    val id: String,          // server file_id, goes on the wire
    val name: String,        // DISPLAY_NAME from the content resolver
    val mimeType: String?,   // contentResolver.getType, null-safe
    val sizeBytes: Long? = null
)
```

`attachmentsJson` (a schemaless String column — **no Room migration needed**) now holds `List<FileAttachment>`. The wire body doesn't change: `streamCompletion` maps `.map { it.id }` into the existing `ChatCompletionRequest.fileIds`. `persistUserMessage` signature changes from `fileIds: List<String>` to `attachments: List<FileAttachment>`.

**#3 — VM owns pending attachments, composer stays dumb.**
Like the reasoning in P7 but stronger here: uploads are async jobs with progress. Composable-local state would die on rotation mid-upload. VM-side:

```kotlin
// ui/screens/chat — the transient, VM-lifecycle record (NOT persisted)
data class PendingAttachment(
    val localId: String,           // UUID — chip key, job-tracking key
    val uri: Uri,                  // local content uri → Coil thumbnail in composer
    val name: String,
    val isImage: Boolean,
    val status: Status
) {
    sealed interface Status {
        data object Copying : Status            // cache copy in flight (indeterminate)
        data class Uploading(val percent: Int) : Status
        data class Done(val attachment: FileAttachment) : Status
        data class Failed(val reason: String) : Status
    }
}
```

**#4 — Send semantics: block while uploading, exclude failures.**
- Any chip in `Copying`/`Uploading` → send disabled. No queueing, no auto-wait — predictable beats clever, and the progress ring tells the user why.
- `Failed` chips are silently **excluded** from send (they carry no id). If all attachments failed, the message goes text-only. No retry button — remove + re-pick is the retry; one less state machine.
- Send clears **all** pending chips (and their cache files) regardless of status.
- File-only messages are **not supported** — blank text still blocks send, matching the web UI's behavior. Documented, not a bug.

## File-by-file agent brief

**1. NEW `network/ProgressRequestBody.kt`** (~25 lines)
Wraps a delegate `RequestBody`: override `contentType()` to pass through, override `contentLength()`, and in `writeTo(sink)` wrap the sink in a counting `ForwardingSink` that invokes `onProgress(bytesWritten, contentLength)` per buffer write. Reasoning for the agent: this is the only mechanism OkHttp gives for upload progress; it must not buffer the whole body (that's what defeats progress). Callback fires per ~8KB write — **throttle in the caller** (skip state updates unless ≥100ms since last or percent changed by ≥5), because 1000 recompositions per file is waste, not fidelity.

**2. EDIT `ZaiApiService.uploadFile`**
Signature → `uploadFile(file: File, mimeType: String): FileUploadResponse`. The multipart part's `asRequestBody(mimeType.toMediaType())` replaces the hardcoded octet-stream — real MIME is what Open WebUI uses to decide image vs document handling server-side. Everything else unchanged.

**3. EDIT `data/mapper/EntityMappers.kt`**
`toDomain`: decode `attachmentsJson` as `List<FileAttachment>` (same null-safe `runCatching` pattern as citations — rename the helper to be generic over the type or add a second one). `toEntity`: encode `message.attachments` the same way. Update the P3 entity's stale `List<String>` comment.

**4. EDIT `ChatRepository` + Impl**
- `persistUserMessage(chatId, content, attachments: List<FileAttachment> = emptyList())` — encodes the full records.
- `streamCompletion(..., attachments: List<FileAttachment> = emptyList())` — sends `attachments.map { it.id }.ifEmpty { null }` as `fileIds`. 
Reasoning: the repository stays the single place that knows the wire format (ids) vs the storage format (records). ViewModels never see `fileIds` as a concept.

**5. EDIT `ChatUiState` + VM**
- State gains `pendingAttachments: List<PendingAttachment> = emptyList()`.
- VM gains a `private val uploadJobs = mutableMapOf<String, Job>()`.
- `onEvent(AddAttachments(uris: List<Uri>))` — new event, fed by the picker. For each uri, on `Dispatchers.IO`: query `DISPLAY_NAME` + `getType` (null → `"application/octet-stream"`; isImage = mime starts with `"image/"`), append a `Copying` chip, copy to cache, then start the upload job: call the API, map progress callbacks → throttled `Uploading(percent)` updates → `Done(FileAttachment(...))` → **delete the cache file**. Catch everything non-cancellation → `Failed(reason)` (cache file also deleted). Register every job in `uploadJobs[localId]`.
- `onEvent(RemoveAttachment(localId))` — cancel its job, delete its cache file, drop the chip.
- `launchSend` gains the attachments parameter: filter `status is Done → attachment`, pass to `persistUserMessage` and `streamCompletion`, then clear `pendingAttachments` (and `uploadJobs` entries). Cache files for Done/Failed chips are already gone per #1.
- The send-blocking check lives in the composer (button disabled), but the VM's `handleSend` should *also* guard (`pendingAttachments.any { it.status !is Done && it.status !is Failed } → return`) — UI guards are for UX, VM guards are for correctness.
- Process-death honesty: jobs are VM-scoped; a killed process mid-upload drops the chip silently. Accept and document; do not build upload persistence.

**6. EDIT `ChatComposer`**
- `ActivityResultContracts.OpenMultipleDocuments()` launcher with input `listOf("*/*")` → `onEvent(AddAttachments(uris))`. (OpenMultipleDocuments over GetContent: multi-select, and the picker callback hands us the uris while permissions are fresh — which is exactly why decision #1 works.)
- **Chips row** above the input (below the model/search/thinking row): horizontal scroll; per chip — Coil `AsyncImage` on the local `content://` uri when `isImage` (works with zero auth because it's local — this is the *only* image we can cheaply render; in-history rendering is deferred), else an `AttachFile` icon; name ellipsized to ~1 line; status overlay: indeterminate spinner while Copying, `CircularProgressIndicator(progress)` ring while Uploading, checkmark tint when Done, error-tinted border when Failed; trailing X button → `RemoveAttachment`.
- Send button's enabled state: `textInput.isNotBlank() && !pendingAttachments.any { it.status is Copying || it.status is Uploading }` — visually dim, click no-ops (VM guard is the real gate).

**7. EDIT `MessageBubble` (assistant **and** user branch)**
Above the text, if `message.attachments.isNotEmpty()`: a wrap-free single Row of small chips — `AttachFile` icon + `name` (ellipsized), surfaceVariant background. Reasoning for deferring remote image rendering: bubbles hold only server ids; rendering those images means an authenticated Coil ImageLoader (OkHttp client + interceptor injection) — real machinery for a personal nicety. The named chip answers "what did I attach here?"; pixels can come later as a hardening-phase option.

**8. `MainActivity`** — untouched. No new routes, no new dependencies, `libs.versions.toml` untouched (Coil was already in from P0).

## Deviations vs. PDF

| # | PDF | Ours | Why |
|---|---|---|---|
| 1 | `file_ids: List<String>` through the whole stack | `FileAttachment` records end-to-end, ids only at the wire | History without filenames is useless; wire format unchanged |
| 2 | Upload progress unspecified ("progress ring" in spec, no mechanism) | `ProgressRequestBody` + throttled percent state | Only real feedback for big files; ~25 lines |
| 3 | Single implicit attachment flow | Multi-select picker, parallel uploads, per-chip cancel | Compose UX parity with the web app |
| 4 | No send-time semantics | Block-while-uploading, exclude-failed, clear-all | The un-specified edge cases are where bugs live |
| 5 | Bubbles render nothing for attachments | Named chips in bubbles; remote-image rendering deferred | "What did I send?" answered cheaply; authenticated Coil documented as future option |

## ✅ Device gate

- Attach → image chip with a real thumbnail (Coil, local uri); PDF gets a generic icon.
- Upload a larger file → ring fills (progress visible), then checkmark.
- X mid-upload → chip vanishes, upload stops (no crash, no orphan state).
- Airplane mode → attach → chip turns Failed → add text → send → **message sends text-only**, failed chip gone, banner error for the request itself is expected without a token.
- Send with a Done attachment → bubble appears with the named attachment chip → kill app → reopen → **chip still there** (attachmentsJson round-trip through Room proves the persistence change).

## 🚀 Run

```bash
git add -A && git commit -m "P10: attachments — picker, cached copies, ProgressRequestBody, named records in history, composer chips, send semantics" && git push
gh run watch
```

**Red? Ranked suspects:** (1) `ForwardingSink` plumbing — delegate writes must actually go through the counting sink, (2) `OpenMultipleDocuments` callback signature is `(List<Uri>)` not `(Uri)`, (3) mapper generics — the decode helper must be typed per-list now, (4) the send-guard duplicated in VM and composer drifting apart — they read the same state, keep the predicate identical, (5) forgotten `Uri` import in the ui layer.

**Next: Phase 11 — hardening + release.** Proguard keep rules (the release build has been one bad R8 pass from crashes since P0), the airplane-mode/mid-stream/large-history edge matrix, the 1000-message recomposition audit, and a release workflow job with a signed artifact — after which the app is *done* pending P12 recon, and everything from there is live-fire tuning against the real server.
