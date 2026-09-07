package com.zai.chat.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [RECON] Inbound SSE chunk. GLM streams content and reasoning as two
 *  independent delta channels inside the same chunk format. */
@Serializable
data class ChatCompletionChunk(
    @SerialName("id") val id: String? = null,
    @SerialName("choices") val choices: List<ChunkChoice> = emptyList(),
    @SerialName("usage") val usage: UsageInfo? = null,
    @SerialName("citations") val citations: List<CitationItem>? = null
)

@Serializable
data class ChunkChoice(
    @SerialName("index") val index: Int = 0,
    @SerialName("delta") val delta: ChunkDelta = ChunkDelta(),
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChunkDelta(
    @SerialName("role") val role: String? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null
)

@Serializable
data class UsageInfo(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

/** [RECON] Web-search citation shape — verified against a live capture in P12. */
@Serializable
data class CitationItem(
    @SerialName("index") val index: Int,
    @SerialName("url") val url: String,
    @SerialName("title") val title: String,
    @SerialName("snippet") val snippet: String? = null
)
