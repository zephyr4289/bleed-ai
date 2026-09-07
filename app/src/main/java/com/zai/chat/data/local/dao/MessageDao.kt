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
