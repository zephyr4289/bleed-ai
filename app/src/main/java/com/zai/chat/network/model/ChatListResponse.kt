package com.zai.chat.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [RECON] GET /api/v1/chats/?page={n} → bare JSON array of these. */
@Serializable
data class ChatListItemResponse(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("updated_at") val updatedAt: Long,   // [RECON] may be ISO-8601 string on some builds
    @SerialName("pinned") val pinned: Boolean = false,
    @SerialName("folder_id") val folderId: String? = null
)

/** [RECON] GET /api/v1/chats/{id} → wrapper containing full message history. */
@Serializable
data class ChatDetailResponse(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("chat") val chat: ChatContentPayload
)

@Serializable
data class ChatContentPayload(
    @SerialName("messages") val messages: List<RemoteMessagePayload> = emptyList()
)

@Serializable
data class RemoteMessagePayload(
    @SerialName("id") val id: String,
    @SerialName("role") val role: String,
    @SerialName("content") val content: String,
    @SerialName("reasoning") val reasoning: String? = null,
    @SerialName("timestamp") val timestamp: Long = System.currentTimeMillis()
)
