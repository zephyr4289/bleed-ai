package com.zai.chat.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [RECON] Outgoing completion body. Field names mirror the browser's POST.
 * P12 replaces guesses with the captured ground truth — single-file change.
 */
@Serializable
data class ChatCompletionRequest(
    @SerialName("model") val model: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("messages") val messages: List<RequestMessage>,
    @SerialName("stream") val stream: Boolean = true,
    @SerialName("web_search") val webSearch: Boolean = false,
    @SerialName("reasoning") val reasoning: Boolean = true,
    @SerialName("file_ids") val fileIds: List<String>? = null
)

@Serializable
data class RequestMessage(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String
)
