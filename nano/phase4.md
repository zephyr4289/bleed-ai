# 📦 Phase 4 — Repository Layer

**Goal:** The orchestration brain — `ChatRepository` interface, implementation, FTS sanitizer, Hilt binding. This is where the P2 wire engine and the P3 database finally meet: persist → stream → persist. **Gate:** CI green. Still zero UI changes.

**Phase contract (P7's ViewModel will code against this — read it):**

```
persistUserMessage()   → separate from streaming (fixes regenerate-duplication,
                         see Fix #1). Returns the message id.
streamCompletion()     → expects history (incl. user msg) ALREADY in Room.
                         Reads it, calls server, persists assistant reply on
                         Done, persists partial on Error-with-content.
                         Never throws except CancellationException.
deleteFromMessage()    → precise id-based history surgery: deletes the target
                         message AND everything after it. Used by edit&resend,
                         regenerate, and retry-from-partial.
createChat()           → server-first; offline fallback returns temp id.
searchMessages()       → takes RAW user text; sanitization happens here.
```

## Setup

```bash
cd ~/zai
mkdir -p app/src/main/java/com/zai/chat/data/repository
```

---

## File 1: `data/repository/FtsSanitizer.kt`

```kotlin
package com.zai.chat.data.repository

/**
 * Converts raw user search text into safe FTS4 MATCH syntax.
 *
 * "what is kotlin coroutines?"  →  "\"what\"* AND \"is\"* AND \"kotlin\"* AND \"coroutines\"*"
 *
 * Strategy: strip every char that isn't a letter/digit/underscore (kills
 * quotes, hyphens, operators like AND/OR/NEAR that would otherwise be parsed
 * as FTS syntax → SQLiteException), quote each token, prefix-match, AND-join.
 * Empty/whitespace input returns null → caller emits empty results.
 *
 * Known limitation (accepted in P3): FTS4's default tokenizer doesn't
 * segment CJK, so CJK prefix search won't behave. Latin scripts are fine.
 */
internal fun sanitizeFtsQuery(raw: String): String? {
    val tokens = raw.trim()
        .split(Regex("\\s+"))
        .map { it.replace(Regex("[^\\p{L}\\p{N}_]"), "") }
        .filter { it.isNotEmpty() }
        .map { "\"$it\"*" }
    return if (tokens.isEmpty()) null else tokens.joinToString(" AND ")
}
```

## File 2: `data/repository/ChatRepository.kt`

```kotlin
package com.zai.chat.data.repository

import com.zai.chat.data.model.Chat
import com.zai.chat.data.model.Message
import com.zai.chat.network.sse.StreamEvent
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    // ── Observation (Room-backed, instant, offline-safe) ────────────
    fun getChatsStream(): Flow<List<Chat>>
    fun getMessagesStream(chatId: String): Flow<List<Message>>

    // ── Server → Room sync ──────────────────────────────────────────
    suspend fun refreshChats(page: Int = 0)
    suspend fun syncChatMessages(chatId: String)

    // ── Mutations ───────────────────────────────────────────────────
    /** Server-first. Returns the chatId to use (real id, or temp id if offline). */
    suspend fun createChat(firstPrompt: String): String

    /** Optimistic local delete + server delete; restores from cache on failure. */
    suspend fun deleteChat(chatId: String)

    /** Local-only for now — pin state does not sync to the web UI. */
    suspend fun togglePinChat(chatId: String, pinned: Boolean)

    // ── Search ──────────────────────────────────────────────────────
    /** Takes RAW user text — sanitization is this layer's job. */
    fun searchMessages(rawQuery: String): Flow<List<Message>>

    // ── History surgery ─────────────────────────────────────────────
    /** Deletes [messageId] and every message after it (edit&resend / regenerate / retry). */
    suspend fun deleteFromMessage(chatId: String, messageId: String)

    // ── Streaming pipeline ──────────────────────────────────────────
    /**
     * Persists the user message immediately (Room = source of truth for reads).
     * Returns the persisted message id.
     */
    suspend fun persistUserMessage(
        chatId: String,
        content: String,
        fileIds: List<String> = emptyList()
    ): String

    /**
     * Streams a completion for the CURRENT Room history (must already include
     * the latest user message). Persists the assistant reply on Done, or a
     * partial (isPartial=true) on mid-stream Error with content.
     * Emits every StreamEvent to the caller. Terminal: exactly one Done|Error.
     */
    fun streamCompletion(
        chatId: String,
        model: String,
        webSearch: Boolean,
        deepThinking: Boolean,
        fileIds: List<String> = emptyList()
    ): Flow<StreamEvent>
}
```

## File 3: `data/repository/ChatRepositoryImpl.kt`

```kotlin
package com.zai.chat.data.repository

import com.zai.chat.data.local.dao.ChatDao
import com.zai.chat.data.local.dao.MessageDao
import com.zai.chat.data.local.entity.ChatEntity
import com.zai.chat.data.mapper.toDomain
import com.zai.chat.data.mapper.toEntity
import com.zai.chat.data.model.Chat
import com.zai.chat.data.model.Message
import com.zai.chat.data.model.MessageRole
import com.zai.chat.data.model.SearchCitation
import com.zai.chat.network.ZaiApiService
import com.zai.chat.network.model.ChatCompletionRequest
import com.zai.chat.network.model.RequestMessage
import com.zai.chat.network.sse.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val apiService: ZaiApiService,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val json: Json
) : ChatRepository {

    // ── Observation ──────────────────────────────────────────────────

    override fun getChatsStream(): Flow<List<Chat>> =
        chatDao.getAllChatsFlow().map { list -> list.map { it.toDomain() } }

    override fun getMessagesStream(chatId: String): Flow<List<Message>> =
        messageDao.getMessagesForChatFlow(chatId).map { list -> list.map { it.toDomain(json) } }

    // ── Sync ─────────────────────────────────────────────────────────

    override suspend fun refreshChats(page: Int): Unit = withContext(Dispatchers.IO) {
        try {
            val remote = apiService.getChats(page)
            val entities = remote.map { r ->
                val cached = chatDao.getChatById(r.id)
                ChatEntity(
                    id = r.id,
                    title = r.title,
                    updatedAt = r.updatedAt,          // [RECON] P12: verify ms vs seconds
                    pinned = r.pinned,
                    folderId = r.folderId,
                    fullyCached = cached?.fullyCached ?: false   // never regress the flag
                )
            }
            chatDao.upsertChats(entities)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline → Room keeps serving the cache. Silent by design.
        }
    }

    override suspend fun syncChatMessages(chatId: String): Unit = withContext(Dispatchers.IO) {
        val chat = chatDao.getChatById(chatId)
        if (chat?.fullyCached == true) return@withContext
        try {
            val detail = apiService.getChatDetail(chatId)
            val entities = detail.chat.messages.map { m ->
                MessageEntity(
                    id = m.id,
                    chatId = chatId,
                    role = MessageRole.fromWire(m.role).wire,
                    content = m.content,
                    reasoning = m.reasoning?.takeIf { it.isNotBlank() },
                    createdAt = m.timestamp               // [RECON] P12: verify units
                )
            }
            messageDao.upsertMessages(entities)
            chat?.let { chatDao.upsertChat(it.copy(fullyCached = true)) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Leave fullyCached=false → retried on next open.
        }
    }

    // ── Mutations ────────────────────────────────────────────────────

    override suspend fun createChat(firstPrompt: String): String = withContext(Dispatchers.IO) {
        val firstLine = firstPrompt.lineSequence().firstOrNull().orEmpty()
        val title = if (firstLine.length > 28) firstLine.take(28) + "…" else firstLine.ifBlank { "New Chat" }
        try {
            // Server-first: the returned id is what completions carry, so
            // server-side history binding works in the happy path.
            val created = apiService.createChat(title)
            chatDao.upsertChat(
                ChatEntity(
                    id = created.id,
                    title = created.title.ifBlank { title },
                    updatedAt = created.updatedAt,
                    pinned = created.pinned,
                    folderId = created.folderId,
                    fullyCached = true
                )
            )
            created.id
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline fallback: local-only chat. [RECON] P12 must confirm the
            // server tolerates an unknown chat_id in completions (Open WebUI
            // typically does) — otherwise offline first-messages won't persist
            // server-side history until a real chat is created later.
            val tempId = UUID.randomUUID().toString()
            chatDao.upsertChat(
                ChatEntity(id = tempId, title = title, updatedAt = System.currentTimeMillis(), fullyCached = true)
            )
            tempId
        }
    }

    override suspend fun deleteChat(chatId: String): Unit = withContext(Dispatchers.IO) {
        val backup = chatDao.getChatById(chatId)
        // Optimistic local delete (cascade wipes messages too).
        chatDao.deleteChatById(chatId)
        try {
            if (!apiService.deleteChat(chatId) && backup != null) {
                restoreChatForResync(backup)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            backup?.let { restoreChatForResync(it) }
        }
    }

    /** Restores the chat row with fullyCached=false so messages re-fetch on next open. */
    private suspend fun restoreChatForResync(backup: ChatEntity) {
        chatDao.upsertChat(backup.copy(fullyCached = false))
        syncChatMessages(backup.id)
    }

    override suspend fun togglePinChat(chatId: String, pinned: Boolean) {
        chatDao.setPinned(chatId, pinned)
        // Note: local-only. Open WebUI stores pinning per-user too; wiring the
        // update-chat endpoint is a P12 option, not required for personal use.
    }

    // ── Search ───────────────────────────────────────────────────────

    override fun searchMessages(rawQuery: String): Flow<List<Message>> {
        val ftsQuery = sanitizeFtsQuery(rawQuery) ?: return flowOf(emptyList())
        return messageDao.searchMessages(ftsQuery).map { list -> list.map { it.toDomain(json) } }
    }

    // ── History surgery ──────────────────────────────────────────────

    override suspend fun deleteFromMessage(chatId: String, messageId: String) {
        val idsToDelete = messageDao.getMessagesForChat(chatId)
            .dropWhile { it.id != messageId }   // target (inclusive) → end of thread
            .map { it.id }
        if (idsToDelete.isNotEmpty()) {
            messageDao.deleteMessagesWithIds(idsToDelete)
        }
    }

    // ── Streaming pipeline ───────────────────────────────────────────

    override suspend fun persistUserMessage(
        chatId: String,
        content: String,
        fileIds: List<String>
    ): String {
        val id = UUID.randomUUID().toString()
        val msg = Message(
            id = id,
            chatId = chatId,
            role = MessageRole.USER,
            content = content,
            attachments = fileIds
        )
        messageDao.upsertMessage(msg.toEntity(id, json))
        bumpChatTimestamp(chatId)
        return id
    }

    override fun streamCompletion(
        chatId: String,
        model: String,
        webSearch: Boolean,
        deepThinking: Boolean,
        fileIds: List<String>
    ): Flow<StreamEvent> = flow {
        // 1. History as-is (already contains the fresh user message).
        val history = messageDao.getMessagesForChat(chatId).map {
            RequestMessage(role = it.role, content = it.content)
        }

        val request = ChatCompletionRequest(
            model = model,
            chatId = chatId,
            messages = history,
            stream = true,
            webSearch = webSearch,
            reasoning = deepThinking,
            fileIds = fileIds.ifEmpty { null }
        )

        val assistantId = UUID.randomUUID().toString()
        val content = StringBuilder()
        val reasoning = StringBuilder()
        var citations: List<SearchCitation> = emptyList()
        var tokens = 0

        apiService.streamChatCompletion(request).collect { event ->
            when (event) {
                is StreamEvent.ReasoningDelta -> {
                    reasoning.append(event.text)
                    emit(event)
                }
                is StreamEvent.ContentDelta -> {
                    content.append(event.text)
                    emit(event)
                }
                is StreamEvent.Citations -> {
                    citations = event.citations.map {
                        SearchCitation(index = it.index, title = it.title, url = it.url, snippet = it.snippet)
                    }
                    emit(event)
                }
                is StreamEvent.Usage -> {
                    tokens = event.usage.totalTokens
                    emit(event)
                }
                is StreamEvent.Done -> {
                    // Persist the final reply. Guard: skip empty/blank outputs
                    // (some failures arrive as clean Done with nothing).
                    if (content.isNotBlank() || reasoning.isNotBlank()) {
                        messageDao.upsertMessage(
                            Message(
                                id = assistantId,
                                chatId = chatId,
                                role = MessageRole.ASSISTANT,
                                content = content.toString(),
                                reasoning = reasoning.toString().takeIf { it.isNotBlank() },
                                citations = citations,
                                tokenCount = tokens.takeIf { it > 0 },
                                createdAt = System.currentTimeMillis()
                            ).toEntity(assistantId, json)
                        )
                        bumpChatTimestamp(chatId)
                    }
                    emit(event)
                }
                is StreamEvent.Error -> {
                    // Mid-stream loss with partial text → keep it, mark partial,
                    // UI renders inline retry. Connect failures (empty partials)
                    // persist nothing — the user message stays, banner shows.
                    if (event.partialContent.isNotEmpty() || event.partialReasoning.isNotEmpty()) {
                        messageDao.upsertMessage(
                            Message(
                                id = assistantId,
                                chatId = chatId,
                                role = MessageRole.ASSISTANT,
                                content = event.partialContent,
                                reasoning = event.partialReasoning.takeIf { it.isNotBlank() },
                                createdAt = System.currentTimeMillis(),
                                isPartial = true
                            ).toEntity(assistantId, json)
                        )
                        bumpChatTimestamp(chatId)
                    }
                    emit(event)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun bumpChatTimestamp(chatId: String) {
        chatDao.getChatById(chatId)?.let {
            chatDao.upsertChat(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }
}
```

## File 4: `di/AppModule.kt`

```kotlin
package com.zai.chat.di

import com.zai.chat.data.repository.ChatRepository
import com.zai.chat.data.repository.ChatRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
}
```

---

## 🔧 Deliberate design decisions vs. the PDF (all shape P7)

| # | PDF version | Ours | Why |
|---|---|---|---|
| 1 | `streamCompletion` took `userPrompt` and inserted it internally → `handleRegenerate` re-sent the prompt and **inserted a duplicate user message** | Split: `persistUserMessage()` + `streamCompletion()` (history-only) | Send = persist + stream. Regenerate/retry = stream only, **no duplication possible** by construction |
| 2 | Optimistic `createChat` returned temp id in the happy path → completions carried a fake `chat_id`, server history unbound | **Server-first**, temp id only on failure | One RTT before sidebar updates is invisible; a dangling chat_id is a real bug |
| 3 | `deleteChat` backed up only the chat row; cascade had already killed messages; restore left an empty chat | Local delete → server call → on failure restore + `fullyCached=false` + auto-resync | Correct recovery without message-backup complexity |
| 4 | Timestamp rollback (`>=` cutoff) | `deleteFromMessage` — explicit id list from target onward | Same-millisecond rows can't mis-select; edit&resend/regenerate/retry all share one precise primitive |
| 5 | Search escaped only quotes → any multi-word or special-char query crashed `MATCH` | `FtsSanitizer` → quoted-prefix AND-joined tokens + empty-input guard | Robust against arbitrary user input by construction |
| 6 | Regenerate created stacked assistant replies implicitly | ViewModel will call `deleteFromMessage(oldAssistantId)` then `streamCompletion` | Repository stays primitive; orchestration lives in P7 where it belongs |

**Also tagged `[RECON]` for P12:** timestamp units in chat list/detail, `chat_id` tolerance for offline temp ids, pin-sync endpoint, `deleteChat` response shape.

## 🚀 Run it

```bash
git add -A && git commit -m "P4: repository layer — persist/stream pipeline, FTS sanitizer, history surgery, DI binding" && git push
gh run watch
```

- **Red?** Top suspects: (a) missing `flowOf` import, (b) `encodeToString(fileIds)` needs `kotlinx.serialization.encodeToString` import — it's there, (c) `dropWhile` semantics — verify the paste, (d) nano artifact → `gh run view --log-failed | tail -60`, paste here.
- **Green?** Data layer is **100% complete**. Everything from here is UI.

**Next: Phase 5 — the auth code** (`SilentTokenReconnectDialog` + MainActivity wiring). Still untestable against the live server (that's P12), but written now so the P7 chat screen can compile against a complete app skeleton. After that, the fun begins: component library, then the chat screen goes live.
