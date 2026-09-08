package com.zai.chat.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zai.chat.data.model.FileAttachment
import com.zai.chat.data.model.Message
import com.zai.chat.data.model.MessageRole
import com.zai.chat.ui.theme.ShapeBubbleUser
import com.zai.chat.ui.theme.UserBubbleFillDark

/**
 * One message. User = right pill (plain text — input is literal).
 * Assistant = edge-to-edge document flow: Thinking → Markdown → Citations
 * → partial-retry, with tap/long-press action reveal.
 *
 * Contracts with P7:
 *  - onRegenerate = "regenerate THIS assistant message" (delete onward + restream)
 *  - onEditAndResend = message content goes into P7's edit dialog first
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isStreaming: Boolean,
    onEditAndResend: ((Message) -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onRetryFromHere: ((Message) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }
    val isUser = message.role == MessageRole.USER

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                if (message.attachments.isNotEmpty()) {
                    AttachmentChipsRow(attachments = message.attachments)
                    Spacer(Modifier.height(4.dp))
                }
                Surface(
                    shape = ShapeBubbleUser,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { showActions = !showActions },
                            onLongClick = { showActions = true }
                        )
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { showActions = !showActions },
                        onLongClick = { showActions = true }
                    )
            ) {
                if (message.attachments.isNotEmpty()) {
                    AttachmentChipsRow(attachments = message.attachments)
                    Spacer(Modifier.height(6.dp))
                }
                // 1. Thinking (also when content hasn't started yet)
                if (!message.reasoning.isNullOrEmpty()) {
                    ThinkingBlock(
                        reasoningText = message.reasoning,
                        isStreamingReasoning = isStreaming && message.content.isEmpty(),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                // 2. Document body (selectable) — empty while only thinking
                if (message.content.isNotEmpty()) {
                    SelectionContainer {
                        MarkdownText(
                            markdown = message.content,
                            isStreaming = isStreaming,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                } else if (!isStreaming && message.reasoning.isNullOrEmpty()) {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 3. Citations
                if (message.citations.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    WebCitationStrip(citations = message.citations)
                }
                // 4. Inline retry on interrupted stream
                if (message.isPartial && !isStreaming) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { onRetryFromHere?.invoke(message) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Stream interrupted — tap to resume")
                    }
                }
            }
        }

        // Action reveal
        AnimatedVisibility(visible = showActions, enter = fadeIn() + scaleIn()) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Message", message.content)
                        )
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        showActions = false
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message.content)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                        showActions = false
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (isUser && onEditAndResend != null) {
                    IconButton(
                        onClick = { onEditAndResend(message); showActions = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit and resend",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (!isUser && !isStreaming && onRegenerate != null) {
                    IconButton(
                        onClick = { onRegenerate(); showActions = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Regenerate",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChipsRow(
    attachments: List<FileAttachment>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        attachments.forEach { attachment ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = attachment.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
