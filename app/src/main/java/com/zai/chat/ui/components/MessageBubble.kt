package com.zai.chat.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ForkRight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.data.model.FileAttachment
import com.zai.chat.data.model.Message
import com.zai.chat.data.model.MessageRole
import com.zai.chat.ui.theme.BorderAmbient
import com.zai.chat.ui.theme.GlassIslandBackground
import com.zai.chat.ui.theme.QuantumCyan
import com.zai.chat.ui.theme.SurfaceActive
import com.zai.chat.ui.theme.SurfaceRaised
import com.zai.chat.ui.theme.TextPrimary
import com.zai.chat.ui.theme.TextSecondary
import com.zai.chat.ui.theme.TextTertiary

/**
 * Editorial document layout for model responses paired with organic asymmetric user bubbles and a
 * glassmorphic floating action island.
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
    val view = LocalView.current
    var showActionIsland by remember { mutableStateOf(false) }
    val isUser = message.role == MessageRole.USER
    val userShape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

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
                    shape = userShape,
                    color = SurfaceRaised,
                    modifier = Modifier
                        .specularBorder(userShape)
                        .combinedClickable(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showActionIsland = !showActionIsland
                            },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                showActionIsland = true
                            }
                        )
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showActionIsland = !showActionIsland
                        },
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            showActionIsland = true
                        }
                    )
            ) {
                if (message.attachments.isNotEmpty()) {
                    AttachmentChipsRow(attachments = message.attachments)
                    Spacer(Modifier.height(6.dp))
                }

                // Identity Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(QuantumCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INTELLIGENCE CORE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = QuantumCyan
                        )
                    )
                }

                // 1. Thinking Accordion
                if (!message.reasoning.isNullOrBlank()) {
                    ThinkingBlock(
                        reasoningText = message.reasoning,
                        isStreaming = isStreaming && message.content.isEmpty()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 2. Document Body Content
                if (message.content.isNotEmpty()) {
                    SelectionContainer {
                        MarkdownText(
                            markdown = message.content,
                            isStreaming = isStreaming,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = TextPrimary
                            )
                        )
                    }
                } else if (!isStreaming && message.reasoning.isNullOrEmpty()) {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }

                // 3. Web Citations Strip
                if (message.citations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
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

        // Floating Specular Action Island
        AnimatedVisibility(
            visible = showActionIsland,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 0.94f)
        ) {
            val islandShape = RoundedCornerShape(20.dp)
            Surface(
                shape = islandShape,
                color = GlassIslandBackground,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .specularBorder(islandShape)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Message Content", message.content))
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            showActionIsland = false
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Copy",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, message.content)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share"))
                            showActionIsland = false
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    if (isUser) {
                        IconButton(
                            onClick = {
                                onEditAndResend?.invoke(message)
                                showActionIsland = false
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Edit and resend",
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    } else if (!isStreaming) {
                        IconButton(
                            onClick = {
                                onRegenerate?.invoke()
                                showActionIsland = false
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Regenerate",
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
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
            val shape = RoundedCornerShape(8.dp)
            Surface(
                shape = shape,
                color = SurfaceRaised,
                modifier = Modifier.specularBorder(shape)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = QuantumCyan
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = attachment.name,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
