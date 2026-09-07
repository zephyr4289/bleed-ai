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
