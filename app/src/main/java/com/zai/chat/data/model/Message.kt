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
