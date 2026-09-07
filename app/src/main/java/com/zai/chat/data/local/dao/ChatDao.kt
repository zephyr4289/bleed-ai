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
