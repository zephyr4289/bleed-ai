package com.zai.chat.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/v1/chats/?page={n} → bare JSON array of these. */
@Serializable
data class ChatListItemResponse(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("updated_at") val updatedAt: Long = 0L,
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("pinned") val pinned: Boolean = false,
    @SerialName("folder_id") val folderId: String? = null
)

/** GET /api/v1/chats/{id} → wrapper containing full message history graph. */
@Serializable
data class ChatDetailResponse(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String = "",
    @SerialName("chat") val chat: ChatDetailBody = ChatDetailBody()
)

@Serializable
data class ChatDetailBody(
    @SerialName("history") val history: ChatHistoryPayload = ChatHistoryPayload()
)

@Serializable
data class ChatHistoryPayload(
    @SerialName("currentId") val currentId: String? = null,
    @SerialName("messages") val messages: Map<String, RemoteMessageNode> = emptyMap()
)

@Serializable
data class RemoteMessageNode(
    @SerialName("id") val id: String,
    @SerialName("role") val role: String = "user",
    @SerialName("content") val content: String? = null,
    @SerialName("reasoning") val reasoning: String? = null,
    @SerialName("parentId") val parentId: String? = null,
    @SerialName("childrenIds") val childrenIds: List<String> = emptyList(),
    @SerialName("timestamp") val timestamp: Long = 0L
)

/**
 * Linearizes the message history tree by following parent pointers backwards
 * from history.currentId, returning the active conversation thread in chronological order.
 * Falls back to timestamp sorting if currentId or parent links are broken.
 */
fun linearizeChatHistory(history: ChatHistoryPayload): List<RemoteMessageNode> {
    val nodes = history.messages
    if (nodes.isEmpty()) return emptyList()

    val chain = mutableListOf<RemoteMessageNode>()
    val visited = mutableSetOf<String>()
    var cursor = history.currentId

    while (cursor != null && visited.add(cursor)) {
        val node = nodes[cursor] ?: break
        chain.add(node)
        cursor = node.parentId
    }

    return if (chain.isNotEmpty()) {
        chain.reversed()
    } else {
        nodes.values.sortedBy { it.timestamp }
    }
}
