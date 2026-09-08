package com.zai.chat.ui.screens.chat

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zai.chat.ui.theme.ClaudePeach
import com.zai.chat.ui.theme.KimiCyan
import com.zai.chat.ui.theme.ShapeChip
import com.zai.chat.ui.theme.ZaiMotion

@Composable
fun ChatComposer(
    isStreaming: Boolean,
    selectedModel: String,
    availableModels: List<String>,
    webSearch: Boolean,
    deepThinking: Boolean,
    enterIsSend: Boolean,
    pendingAttachments: List<PendingAttachment> = emptyList(),
    onSendMessage: (String) -> Unit,
    onStopStreaming: () -> Unit,
    onToggleWebSearch: (Boolean) -> Unit,
    onToggleDeepThinking: (Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    onAddAttachments: (List<Uri>) -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    var modelMenuOpen by remember { mutableStateOf(false) }
    val view = LocalView.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddAttachments(uris)
        }
    }

    val isUploading = pendingAttachments.any {
        it.status is PendingAttachment.Status.Copying || it.status is PendingAttachment.Status.Uploading
    }
    val canSend = textInput.isNotBlank() && !isUploading

    fun send() {
        if (isStreaming || !canSend) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onSendMessage(textInput)
        textInput = ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            .navigationBarsPadding()
            .imePadding()
            .padding(top = 8.dp, bottom = 10.dp, start = 12.dp, end = 12.dp)
    ) {
        // ── Chips row ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    shape = ShapeChip,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.clickable { modelMenuOpen = true }
                ) {
                    Text(
                        text = selectedModel,
                        style = MaterialTheme.typography.labelSmall
                            .copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                DropdownMenu(
                    expanded = modelMenuOpen,
                    onDismissRequest = { modelMenuOpen = false }
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                onSelectModel(model)
                                modelMenuOpen = false
                            }
                        )
                    }
                }
            }
            ComposerChip(
                icon = { tint ->
                    Icon(
                        Icons.Rounded.Language, null,
                        tint = tint, modifier = Modifier.size(14.dp)
                    )
                },
                label = "Search",
                active = webSearch,
                activeColor = KimiCyan,
                onClick = { onToggleWebSearch(!webSearch) }
            )
            ComposerChip(
                icon = { tint ->
                    Icon(
                        Icons.Rounded.Psychology, null,
                        tint = tint, modifier = Modifier.size(14.dp)
                    )
                },
                label = "Thinking",
                active = deepThinking,
                activeColor = ClaudePeach,
                onClick = { onToggleDeepThinking(!deepThinking) }
            )
        }

        // ── Attachments row (P10) ────────────────────────────────────
        if (pendingAttachments.isNotEmpty()) {
            Spacer(Modifier.padding(top = 6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pendingAttachments.forEach { pending ->
                    val isFailed = pending.status is PendingAttachment.Status.Failed
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isFailed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (pending.isImage) {
                                AsyncImage(
                                    model = pending.uri,
                                    contentDescription = pending.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = pending.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                            Spacer(Modifier.width(6.dp))

                            when (val status = pending.status) {
                                is PendingAttachment.Status.Copying -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                is PendingAttachment.Status.Uploading -> {
                                    CircularProgressIndicator(
                                        progress = { status.percent / 100f },
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                is PendingAttachment.Status.Done -> {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = "Uploaded",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                is PendingAttachment.Status.Failed -> {
                                    Icon(
                                        Icons.Rounded.Error,
                                        contentDescription = "Failed",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onRemoveAttachment(pending.localId) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.padding(top = 4.dp))

        // ── Input row ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = { launcher.launch(arrayOf("*/*")) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = "Attach files",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (textInput.isEmpty()) {
                    Text(
                        text = "Ask anything…",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                }
                BasicTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(
                        MaterialTheme.colorScheme.primary
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (enterIsSend) ImeAction.Send else ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { if (enterIsSend) send() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                )
            }
            Spacer(Modifier.width(8.dp))

            // Morphing send/stop
            val sendBg by animateColorAsState(
                targetValue = when {
                    isStreaming -> ClaudePeach
                    canSend -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surface
                },
                animationSpec = ZaiMotion.ColorMorphSpring,
                label = "send_bg"
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(sendBg)
                    .clickable {
                        if (isStreaming) onStopStreaming() else send()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Rounded.Stop
                    else Icons.Rounded.ArrowUpward,
                    contentDescription = if (isStreaming) "Stop" else "Send",
                    tint = if (isStreaming || canSend) Color.Black
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ComposerChip(
    icon: @Composable (Color) -> Unit,
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = ShapeChip,
        color = if (active) activeColor.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon(if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) activeColor
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
