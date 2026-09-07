package com.zai.chat.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zai.chat.data.model.Message
import com.zai.chat.data.model.MessageRole
import com.zai.chat.data.model.SearchCitation
import com.zai.chat.ui.components.CodeBlockView
import com.zai.chat.ui.components.MarkdownText
import com.zai.chat.ui.components.MessageBubble
import com.zai.chat.ui.components.ThinkingBlock
import com.zai.chat.ui.components.WebCitationStrip

private val SectionShape = RoundedCornerShape(10.dp)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), SectionShape)
            .background(MaterialTheme.colorScheme.surface, SectionShape)
            .padding(12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** Visual regression target for the component library — view on-device. */
@Composable
fun ComponentGalleryScreen(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text("Component Gallery", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Section("ThinkingBlock — done") {
            ThinkingBlock(reasoningText = "User wants X. Consider Y… then Z.", isStreamingReasoning = false, durationSeconds = 4.2f)
        }
        Section("ThinkingBlock — streaming") {
            ThinkingBlock(reasoningText = "Parsing the request… checking edge…", isStreamingReasoning = true)
        }
        Section("CodeBlockView") {
            CodeBlockView(
                language = "kotlin",
                code = "fun fib(n: Int): Long =\n    if (n < 2) n.toLong() else fib(n - 1) + fib(n - 2)"
            )
        }
        Section("WebCitationStrip") {
            WebCitationStrip(
                listOf(
                    SearchCitation(1, "Kotlin coroutines guide", "https://kotlinlang.org/docs/coroutines-guide.html"),
                    SearchCitation(2, "Room persistence", "https://developer.android.com/training/data-storage/room")
                )
            )
        }
        Section("MarkdownText — full syntax") {
            MarkdownText(
                markdown = """
                    ## Heading 2
                    **Bold**, *italic*, `inline code`, and a [link](https://kotlinlang.org).
                    Bare URL too: https://developer.android.com

                    - Top level item
                      - Nested item
                        - Deeper still
                    1. First
                    2. Second

                    > A quote that wraps across
                    > multiple source lines.

                    ---
                    | a | b |
                    |---|---|
                    | 1 | 2 |

                    Final paragraph after a rule.
                """.trimIndent()
            )
        }
        Section("MarkdownText — streaming") {
            MarkdownText(
                markdown = "Writing a **streaming** response with an unterminated fence:\n\n```python\ndef hello():",
                isStreaming = true
            )
        }
        Section("MessageBubble — user") {
            MessageBubble(
                message = Message("u1", "c1", MessageRole.USER, "Explain Room FTS vs FTS4 tradeoffs?"),
                isStreaming = false
            )
        }
        Section("MessageBubble — assistant complete") {
            MessageBubble(
                message = Message(
                    "a1", "c1", MessageRole.ASSISTANT,
                    content = "FTS4 with a **content entity** avoids duplicating text — the index mirrors `messages` via triggers.\n\nKey win: one write path.",
                    reasoning = "Comparing FTS3/4/5… contentEntity avoids double storage…",
                    citations = listOf(
                        SearchCitation(1, "Room FTS docs", "https://developer.android.com")
                    )
                ),
                isStreaming = false
            )
        }
        Section("MessageBubble — partial (retry)") {
            MessageBubble(
                message = Message(
                    "a2", "c1", MessageRole.ASSISTANT,
                    content = "The stream dropped mid-sent",
                    isPartial = true
                ),
                isStreaming = false,
                onRetryFromHere = { }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
