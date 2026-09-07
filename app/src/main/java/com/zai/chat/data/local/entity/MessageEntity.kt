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
            onDelete = ForeignKey.CASCADE
        )
    ],
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
