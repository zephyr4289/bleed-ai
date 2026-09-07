package com.zai.chat.network.sse

import com.zai.chat.network.model.CitationItem
import com.zai.chat.network.model.UsageInfo

/**
 * Unified event stream for one completion. Terminal states:
 * exactly one of [Done] / [Error] is emitted, then the flow closes.
 * [Error.partialContent]/[partialReasoning] preserve everything received
 * before the failure — the UI renders it inline with a "retry" affordance.
 */
sealed interface StreamEvent {
    data class ReasoningDelta(val text: String) : StreamEvent
    data class ContentDelta(val text: String) : StreamEvent
    data class Citations(val citations: List<CitationItem>) : StreamEvent
    data class Usage(val usage: UsageInfo) : StreamEvent
    data object Done : StreamEvent
    data class Error(
        val throwable: Throwable,
        val partialContent: String,
        val partialReasoning: String
    ) : StreamEvent
}
