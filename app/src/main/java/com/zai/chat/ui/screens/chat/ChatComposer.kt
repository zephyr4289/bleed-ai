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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
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
import com.zai.chat.ui.components.specularBorder
import com.zai.chat.ui.theme.BleedMotion
import com.zai.chat.ui.theme.CanvasPureBlack
import com.zai.chat.ui.theme.CrimsonFlare
import com.zai.chat.ui.theme.GlassIslandBackground
import com.zai.chat.ui.theme.QuantumCyan
import com.zai.chat.ui.theme.QuantumCyanGlow
import com.zai.chat.ui.theme.RadiantAmber
import com.zai.chat.ui.theme.RadiantAmberGlow
import com.zai.chat.ui.theme.SurfaceActive
import com.zai.chat.ui.theme.SurfaceRaised
import com.zai.chat.ui.theme.TextPrimary
import com.zai.chat.ui.theme.TextSecondary
import com.zai.chat.ui.theme.TextTertiary

/**
 * Detached squircle composer floating 12dp above the gesture bar with glowing auras and spring-animated morphing controls.
 */
@Composable
fun ChatComposer(
    isStreaming: Boolean,
    selectedModel: String,
    availableModels: List<String>,
    webSearch: Boolean,
    deepThinking: Boolean,
    enterIsSend: Boolean = false,
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
    val view = LocalView.current
    var modelMenuOpen by remember { mutableStateOf(false) }

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

    val islandShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Surface(
            shape = islandShape,
            color = GlassIslandBackground,
            modifier = Modifier
                .fillMaxWidth()
                .specularBorder(islandShape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // ── Micro-Feature Dynamic Chips ─────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Model Selector Pill
                    Box {
                        val modelShape = RoundedCornerShape(14.dp)
                        Surface(
                            shape = modelShape,
                            color = SurfaceActive,
                            modifier = Modifier
                                .specularBorder(modelShape)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    modelMenuOpen = true
                                }
                        ) {
                            Text(
                                text = selectedModel.ifBlank { "MODEL" }.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = modelMenuOpen,
                            onDismissRequest = { modelMenuOpen = false },
                            modifier = Modifier
                                .background(GlassIslandBackground)
                                .specularBorder(RoundedCornerShape(14.dp))
                        ) {
                            availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            model,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (model == selectedModel) QuantumCyan else TextPrimary,
                                                fontWeight = if (model == selectedModel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                    },
                                    onClick = {
                                        onSelectModel(model)
                                        modelMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    // Quantum Web Search Toggle
                    val chipShape = RoundedCornerShape(14.dp)
                    Surface(
                        shape = chipShape,
                        color = if (webSearch) SurfaceActive else SurfaceRaised,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (webSearch) QuantumCyan else Color.Transparent
                        ),
                        modifier = Modifier
                            .specularBorder(chipShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onToggleWebSearch(!webSearch)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = if (webSearch) QuantumCyan else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WEB SEARCH",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (webSearch) QuantumCyan else TextSecondary
                                )
                            )
                        }
                    }

                    // Radiant Thinking Toggle
                    Surface(
                        shape = chipShape,
                        color = if (deepThinking) SurfaceActive else SurfaceRaised,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (deepThinking) RadiantAmber else Color.Transparent
                        ),
                        modifier = Modifier
                            .specularBorder(chipShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onToggleDeepThinking(!deepThinking)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = null,
                                tint = if (deepThinking) RadiantAmber else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REASONING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (deepThinking) RadiantAmber else TextSecondary
                                )
                            )
                        }
                    }
                }

                // ── Attachments row ──────────────────────────────────────────
                if (pendingAttachments.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        pendingAttachments.forEach { pending ->
                            val isFailed = pending.status is PendingAttachment.Status.Failed
                            val attShape = RoundedCornerShape(10.dp)
                            Surface(
                                shape = attShape,
                                color = SurfaceActive,
                                modifier = Modifier.specularBorder(attShape)
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
                                            modifier = Modifier.size(18.dp),
                                            tint = QuantumCyan
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = pending.name,
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary),
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
                                                color = QuantumCyan
                                            )
                                        }
                                        is PendingAttachment.Status.Uploading -> {
                                            CircularProgressIndicator(
                                                progress = { status.percent / 100f },
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = QuantumCyan
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
                                                tint = CrimsonFlare,
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
                                            tint = TextTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Input Box with Morphing Action Button ──────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            launcher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AttachFile,
                            contentDescription = "Attach files",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        if (textInput.isEmpty()) {
                            Text(
                                text = "Query model or execute command...",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = TextTertiary,
                                    fontSize = 15.sp
                                )
                            )
                        }
                        BasicTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                            cursorBrush = SolidColor(QuantumCyan),
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

                    // Morphing Send / Cancel Action Pill
                    val actionButtonBg by animateColorAsState(
                        targetValue = when {
                            isStreaming -> CrimsonFlare
                            canSend -> TextPrimary
                            else -> SurfaceActive
                        },
                        animationSpec = BleedMotion.ColorMorphSpring,
                        label = "action_btn_bg"
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(actionButtonBg)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (isStreaming) {
                                    onStopStreaming()
                                } else if (canSend) {
                                    send()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isStreaming) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                            contentDescription = if (isStreaming) "Stop" else "Send",
                            tint = if (isStreaming || canSend) CanvasPureBlack else TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
