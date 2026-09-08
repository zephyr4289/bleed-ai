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

    // ── Cache management ─────────────────────────────────────────────
    /** Clears all local chats from Room and re-syncs if a token is present. Never calls remote delete. */
    suspend fun clearLocalCache()
}
