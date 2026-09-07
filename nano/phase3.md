# 💾 Phase 3 — Room Persistence + FTS

**Goal:** The app's local brain — entities, FTS4 full-text index, DAOs, database, DI, domain models, and mappers. After this phase the app can *store and search* everything; P4 then becomes pure orchestration (network ↔ Room). **Gate:** CI green, still no UI changes.

**Phase contract (what P4/P7 will rely on):**

```
DAOs            → suspend for one-shot ops, Flow for observation
Chat list flow  → ordered pinned DESC, updated_at DESC (sidebar-ready)
Message flow    → created_at ASC (chronological, screen renders reversed)
Search flow     → FTS MATCH on content + reasoning, newest first, capped 200
Rollback        → two tools: by-timestamp AND by-id-list (see gotcha #4)
```

**Independence note:** like P2, this phase imports nothing from `SettingsDataStore` — so even if that P1 file is still pending your PDF-based rewrite, CI results here are clean-signal.

## Setup

```bash
cd ~/zai
mkdir -p app/src/main/java/com/zai/chat/data/{local/{dao,entity},model,mapper}
```

⚠️ Note the new directory: `data/mapper/` isn't in the PDF tree (PDF buried mappers as private functions inside `ChatRepositoryImpl`). We co-locate them with the data layer instead — P4 gets to be pure logic. First deliberate tree addition.

---

## File 1: `data/local/entity/ChatEntity.kt`

```kotlin
package com.zai.chat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    val pinned: Boolean = false,
    @ColumnInfo(name = "folder_id")
    val folderId: String? = null,
    /**
     * True once we've fetched the full message history from the server at
     * least once. Lets syncChatMessages() skip the network on re-opens —
     * the offline-first "instant sidebar" behavior depends on this flag.
     */
    @ColumnInfo(name = "fully_cached")
    val fullyCached: Boolean = false
)
```

## File 2: `data/local/entity/MessageEntity.kt`

```kotlin
package com.zai.chat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE   // delete chat → messages (and FTS) vanish atomically
        )
    ],
    // GOTCHA FIX #2: single composite index instead of the PDF's two separate
    // ones. Every hot query is WHERE chat_id = ? ORDER BY created_at — one
    // composite index serves both the filter and the sort.
    indices = [Index(value = ["chat_id", "created_at"])]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "chat_id")
    val chatId: String,
    /** Wire format: "user" | "assistant" | "system" (lowercase, mirrors server). */
    val role: String,
    val content: String,
    val reasoning: String? = null,
    /** JSON-encoded List<String> (file ids) */
    @ColumnInfo(name = "attachments_json")
    val attachmentsJson: String? = null,
    /** JSON-encoded List<SearchCitation> */
    @ColumnInfo(name = "search_results_json")
    val searchResultsJson: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "token_count")
    val tokenCount: Int? = null,
    /** True when persisted mid-stream on network loss → UI shows inline retry. */
    @ColumnInfo(name = "is_partial")
    val isPartial: Boolean = false
)
```

## File 3: `data/local/entity/MessageFtsEntity.kt`

```kotlin
package com.zai.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * External-content FTS4 index over [MessageEntity].
 *
 * GOTCHA FIX #1 — this file differs from the PDF, deliberately:
 * the PDF declared `@PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long`
 * in here. Room's contentEntity-FTS validation requires the FTS entity to
 * declare ONLY text fields that exist in the content entity — `rowId` is
 * neither (fails KSP with a field-mismatch error). Room maintains the FTS
 * rowid↔content-rowid mapping and sync triggers automatically; we declare
 * nothing extra.
 *
 * Nullable reasoning mirrors the content entity's declared type.
 */
@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFtsEntity(
    val content: String,
    val reasoning: String?
)
```

## File 4: `data/local/dao/ChatDao.kt`

```kotlin
package com.zai.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zai.chat.data.local.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    /** Sidebar source of truth. Emits on every change — UI never manually refreshes. */
    @Query("SELECT * FROM chats ORDER BY pinned DESC, updated_at DESC")
    fun getAllChatsFlow(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: String): ChatEntity?

    /**
     * GOTCHA FIX #5: @Upsert instead of @Insert(REPLACE). REPLACE is
     * DELETE+INSERT under the hood — it churns rowids and fires extra FTS
     * sync triggers on every sidebar refresh. Upsert does a true in-place
     * update on conflict.
     */
    @Upsert
    suspend fun upsertChats(chats: List<ChatEntity>)

    @Upsert
    suspend fun upsertChat(chat: ChatEntity)

    @Query("UPDATE chats SET pinned = :pinned WHERE id = :chatId")
    suspend fun setPinned(chatId: String, pinned: Boolean)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: String)
}
```

## File 5: `data/local/dao/MessageDao.kt`

```kotlin
package com.zai.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.zai.chat.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** Chronological ascending — the chat screen renders with reverseLayout. */
    @Query("SELECT * FROM messages WHERE chat_id = :chatId ORDER BY created_at ASC")
    fun getMessagesForChatFlow(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chat_id = :chatId ORDER BY created_at ASC")
    suspend fun getMessagesForChat(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Upsert
    suspend fun upsertMessage(message: MessageEntity)

    @Upsert
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    /** Time-based rollback (edit-and-resend). Deletes target AND everything after. */
    @Query("DELETE FROM messages WHERE chat_id = :chatId AND created_at >= :sinceTimestamp")
    suspend fun deleteMessagesSince(chatId: String, sinceTimestamp: Long)

    /**
     * GOTCHA FIX #4: id-based rollback. Two messages created in the same
     * millisecond (fast regenerate taps) make timestamp rollback delete the
     * wrong row — P7 will prefer this precise variant.
     */
    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessagesWithIds(ids: List<String>)

    /**
     * GOTCHA FIX #7 (contract): :searchQuery MUST arrive pre-sanitized FTS
     * syntax — the DAO does no escaping. Sanitizer lands in the repository
     * (P4): per-token quoted-prefix form, e.g. `"kotlin"* AND "room"*`.
     * Raw user text with spaces or special chars throws SQLiteException.
     */
    @Transaction
    @Query(
        """
        SELECT m.* FROM messages m
        JOIN messages_fts fts ON m.rowid = fts.rowid
        WHERE messages_fts MATCH :searchQuery
        ORDER BY m.created_at DESC
        LIMIT 200
        """
    )
    fun searchMessages(searchQuery: String): Flow<List<MessageEntity>>
}
```

## File 6: `data/local/ZaiDatabase.kt`

```kotlin
package com.zai.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zai.chat.data.local.dao.ChatDao
import com.zai.chat.data.local.dao.MessageDao
import com.zai.chat.data.local.entity.ChatEntity
import com.zai.chat.data.local.entity.MessageEntity
import com.zai.chat.data.local.entity.MessageFtsEntity

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageFtsEntity::class
    ],
    version = 1,
    exportSchema = false   // personal app: no migration history files
)
abstract class ZaiDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
}
```

## File 7: `di/DatabaseModule.kt`

```kotlin
package com.zai.chat.di

import android.content.Context
import androidx.room.Room
import com.zai.chat.data.local.ZaiDatabase
import com.zai.chat.data.local.dao.ChatDao
import com.zai.chat.data.local.dao.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * fallbackToDestructiveMigration: a future schema bump wipes the local
     * cache instead of migrating. Acceptable by design — the server is the
     * source of truth and Room is a cache; worst case is a re-fetch.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ZaiDatabase =
        Room.databaseBuilder(
            context,
            ZaiDatabase::class.java,
            "zai_local_chat.db"
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideChatDao(db: ZaiDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMessageDao(db: ZaiDatabase): MessageDao = db.messageDao()
}
```

## File 8: `data/model/MessageRole.kt`

```kotlin
package com.zai.chat.data.model

enum class MessageRole {
    USER, ASSISTANT, SYSTEM;

    /** DB/server wire format is the lowercase name. */
    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(raw: String): MessageRole = when (raw.lowercase()) {
            "user" -> USER
            "assistant" -> ASSISTANT
            else -> SYSTEM
        }
    }
}
```

## File 9: `data/model/Chat.kt`

```kotlin
package com.zai.chat.data.model

data class Chat(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val pinned: Boolean,
    val folderId: String?,
    val fullyCached: Boolean
)
```

## File 10: `data/model/Message.kt`

```kotlin
package com.zai.chat.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchCitation(
    val index: Int,
    val title: String,
    val url: String,
    val snippet: String? = null
)

data class Message(
    val id: String,
    val chatId: String,
    val role: MessageRole,
    val content: String,
    val reasoning: String? = null,
    val attachments: List<String> = emptyList(),
    val citations: List<SearchCitation> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val tokenCount: Int? = null,
    val isPartial: Boolean = false
)
```

## File 11: `data/mapper/EntityMappers.kt`

```kotlin
package com.zai.chat.data.mapper

import com.zai.chat.data.local.entity.ChatEntity
import com.zai.chat.data.local.entity.MessageEntity
import com.zai.chat.data.model.Chat
import com.zai.chat.data.model.Message
import com.zai.chat.data.model.MessageRole
import com.zai.chat.data.model.SearchCitation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

// ── Chat ────────────────────────────────────────────────────────────────

fun ChatEntity.toDomain(): Chat = Chat(
    id = id,
    title = title,
    updatedAt = updatedAt,
    pinned = pinned,
    folderId = folderId,
    fullyCached = fullyCached
)

fun Chat.toEntity(): ChatEntity = ChatEntity(
    id = id,
    title = title,
    updatedAt = updatedAt,
    pinned = pinned,
    folderId = folderId,
    fullyCached = fullyCached
)

// ── Message ─────────────────────────────────────────────────────────────

/**
 * Null-safe decode: a corrupted/legacy JSON blob degrades to empty list
 * instead of crashing the whole chat screen.
 */
fun MessageEntity.toDomain(json: Json): Message = Message(
    id = id,
    chatId = chatId,
    role = MessageRole.fromWire(role),
    content = content,
    reasoning = reasoning,
    attachments = attachmentsJson.decodeListOrEmpty(json),
    citations = searchResultsJson.decodeCitationsOrEmpty(json),
    createdAt = createdAt,
    tokenCount = tokenCount,
    isPartial = isPartial
)

/**
 * [newId] is supplied by the repository — streaming persistence and
 * optimistic inserts each generate their own UUIDs, so the mapper never
 * hides id semantics.
 */
fun Message.toEntity(newId: String, json: Json): MessageEntity = MessageEntity(
    id = newId,
    chatId = chatId,
    role = role.wire,
    content = content,
    reasoning = reasoning?.takeIf { it.isNotBlank() },
    attachmentsJson = json.encodeToString(attachments),
    searchResultsJson = json.encodeToString(citations),
    createdAt = createdAt,
    tokenCount = tokenCount,
    isPartial = isPartial
)

// ── private decode helpers ──────────────────────────────────────────────

private fun String?.decodeListOrEmpty(json: Json): List<String> =
    this?.let { raw ->
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    } ?: emptyList()

private fun String?.decodeCitationsOrEmpty(json: Json): List<SearchCitation> =
    this?.let { raw ->
        runCatching { json.decodeFromString<List<SearchCitation>>(raw) }.getOrDefault(emptyList())
    } ?: emptyList()
```

*(UUID import is unused for now — P4 uses it. If your linter flags it, drop the line; re-added in P4.)*

---

## 🔧 Gotchas fixed vs. the PDF (recap)

| # | PDF version | Ours | Why |
|---|---|---|---|
| 1 | FTS entity had `@PrimaryKey rowId` | text-only fields | Room's contentEntity FTS **forbids** non-text/foreign fields — PDF version fails at KSP |
| 2 | Two single indices on messages | one composite `(chat_id, created_at)` | every hot query filters + sorts by exactly that pair |
| 3 | unbounded FTS search | `LIMIT 200` | years of history shouldn't OOM the sidebar search |
| 4 | timestamp-only rollback | + `deleteMessagesWithIds` | same-millisecond messages make `>=` delete the wrong row; P7 uses ids |
| 5 | `@Insert(REPLACE)` | `@Upsert` | REPLACE = delete+insert → rowid churn + redundant FTS trigger fires |
| 6 | mappers private in Impl | dedicated `data/mapper/` | P4 stays orchestration-only; mappers testable in isolation |

Also logged for P4: the **FTS sanitizer** (`"tok"* AND "tok2"*` form) must live in the repository — the PDF's escaping was quote-escaping only and would crash `MATCH` on any multi-word query. And one known limitation to accept: FTS4's default tokenizer won't segment CJK text; fine for your use, noted for honesty.

## 🚀 Run it

```bash
git add -A && git commit -m "P3: Room entities, FTS4 index, DAOs, database, DI, domain models + mappers" && git push
gh run watch
```

- **Red?** Most likely suspects in order: (a) FTS schema complaint → check `MessageFtsEntity` matches exactly (no rowid), (b) `@Upsert` import missing (`androidx.room.Upsert`), (c) a nano paste artifact — `gh run view --log-failed | tail -60` and paste here.
- **Green?** Phase gate met: entities + FTS + DAOs + DI all compile, app unchanged on the surface.

**Next: Phase 4 — the Repository layer**, where the P2 streaming engine and the P3 database finally meet: persist-user-message → build history → collect SSE → persist assistant message, plus optimistic chat creation, rollback-safe deletes, and the FTS query sanitizer. That's the last pure-data phase before anything visible.
