package com.zai.chat.ui.screens.chat

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.R
import com.zai.chat.data.model.Message
import com.zai.chat.data.model.MessageRole
import com.zai.chat.ui.components.MessageBubble
import com.zai.chat.ui.components.specularBorder
import com.zai.chat.ui.components.tactilePress
import com.zai.chat.ui.theme.BorderAmbient
import com.zai.chat.ui.theme.CanvasPureBlack
import com.zai.chat.ui.theme.CrimsonFlare
import com.zai.chat.ui.theme.EmeraldPulse
import com.zai.chat.ui.theme.GlassIslandBackground
import com.zai.chat.ui.theme.MidnightObsidian
import com.zai.chat.ui.theme.QuantumCyan
import com.zai.chat.ui.theme.RadiantAmber
import com.zai.chat.ui.theme.SurfaceActive
import com.zai.chat.ui.theme.SurfaceBase
import com.zai.chat.ui.theme.SurfaceRaised
import com.zai.chat.ui.theme.TextPrimary
import com.zai.chat.ui.theme.TextSecondary
import com.zai.chat.ui.theme.TextTertiary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onOpenTerminal: () -> Unit = {},
    onOpenGallery: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val isInteractive by viewModel.signer.isInteractive.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val isScrolledUp by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    LaunchedEffect(isScrolledUp) {
        viewModel.onEvent(ChatUiEvent.ScrolledStateChange(isScrolledUp))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (state.isStreaming) RadiantAmber else EmeraldPulse)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = state.chatTitle.ifBlank { "Bleed-AI" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onOpenDrawer()
                    }) {
                        Icon(Icons.Rounded.Menu, contentDescription = "Open drawer", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onOpenTerminal()
                    }) {
                        Icon(Icons.Rounded.Terminal, contentDescription = "Live Logcat Terminal", tint = EmeraldPulse)
                    }
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onNewChat()
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = "New chat", tint = TextPrimary)
                    }
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onOpenSettings()
                    }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                    if (onOpenGallery != null) {
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onOpenGallery()
                        }) {
                            Icon(Icons.Rounded.Code, contentDescription = "Component gallery", tint = TextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            ChatComposer(
                isStreaming = state.isStreaming,
                selectedModel = state.selectedModel,
                availableModels = state.availableModels,
                webSearch = state.webSearch,
                deepThinking = state.deepThinking,
                enterIsSend = state.enterIsSend,
                pendingAttachments = state.pendingAttachments,
                onSendMessage = { viewModel.onEvent(ChatUiEvent.SendMessage(it)) },
                onStopStreaming = { viewModel.onEvent(ChatUiEvent.StopStreaming) },
                onToggleWebSearch = { viewModel.onEvent(ChatUiEvent.ToggleWebSearch(it)) },
                onToggleDeepThinking = { viewModel.onEvent(ChatUiEvent.ToggleDeepThinking(it)) },
                onSelectModel = { viewModel.onEvent(ChatUiEvent.SelectModel(it)) },
                onAddAttachments = { viewModel.onEvent(ChatUiEvent.AddAttachments(it)) },
                onRemoveAttachment = { viewModel.onEvent(ChatUiEvent.RemoveAttachment(it)) },
                onUserComposing = { viewModel.onEvent(ChatUiEvent.UserComposing) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Error banner
            state.bannerError?.let { err ->
                val errorShape = RoundedCornerShape(12.dp)
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = errorShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .specularBorder(errorShape)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Warning, null,
                            tint = CrimsonFlare,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.onEvent(ChatUiEvent.DismissError)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close, "Dismiss",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Empty State
            if (state.messages.isEmpty() && !state.isStreaming && !state.awaitingHandoff) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val logoShape = RoundedCornerShape(20.dp)
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(logoShape)
                            .background(SurfaceRaised)
                            .specularBorder(logoShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_bleed_ai_logo),
                            contentDescription = "Bleed-AI Logo",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "How can I help you today?",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Powered by GLM-5.3 • Ultra-fast reasoning & code",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )

                    Spacer(Modifier.height(28.dp))

                    val starterPrompts = listOf(
                        Triple(Icons.Rounded.Lightbulb, QuantumCyan, "Explain quantum computing and qubit superposition"),
                        Triple(Icons.Rounded.Terminal, EmeraldPulse, "Write a Kotlin StateFlow vs SharedFlow guide"),
                        Triple(Icons.Rounded.DataObject, RadiantAmber, "Debug an OkHttp SSE streaming protocol issue"),
                        Triple(Icons.Rounded.Psychology, QuantumCyan, "Draft a high-concurrency microservice design")
                    )

                    starterPrompts.forEach { (icon, tintColor, promptText) ->
                        val promptShape = RoundedCornerShape(14.dp)
                        Surface(
                            shape = promptShape,
                            color = SurfaceBase,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .specularBorder(promptShape)
                                .tactilePress {
                                    viewModel.onEvent(ChatUiEvent.SendMessage(promptText))
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tintColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = promptText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Message list (index 0 pinned to bottom = zero jump) ──
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.isStreaming || state.awaitingHandoff) {
                    item(key = "transient") {
                        MessageBubble(
                            message = Message(
                                id = "transient",
                                chatId = state.chatId ?: "",
                                role = MessageRole.ASSISTANT,
                                content = state.liveContent,
                                reasoning = state.liveReasoning.takeIf { it.isNotEmpty() }
                            ),
                            isStreaming = state.isStreaming
                        )
                    }
                }
                items(items = state.messages.asReversed(), key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        isStreaming = false,
                        onEditAndResend = { m ->
                            viewModel.onEvent(ChatUiEvent.StartEdit(m))
                        },
                        onRegenerate = {
                            viewModel.onEvent(ChatUiEvent.RegenerateFrom(msg))
                        },
                        onRetryFromHere = { m ->
                            viewModel.onEvent(ChatUiEvent.RegenerateFrom(m))
                        }
                    )
                }
            }

            // Scroll-to-bottom FAB with live-token badge
            AnimatedVisibility(
                visible = isScrolledUp,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp)
            ) {
                BadgedBox(
                    badge = {
                        if (state.unreadWhileScrolledUp > 0) {
                            Badge(containerColor = RadiantAmber) {
                                Text("+${state.unreadWhileScrolledUp}", color = CanvasPureBlack)
                            }
                        }
                    }
                ) {
                    val fabShape = CircleShape
                    Surface(
                        shape = fabShape,
                        color = GlassIslandBackground,
                        modifier = Modifier
                            .size(44.dp)
                            .specularBorder(fabShape)
                            .tactilePress {
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Scroll to latest",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Edit & resend dialog
            state.editingMessage?.let { target ->
                EditMessageDialog(
                    initial = target.content,
                    onConfirm = { newText ->
                        viewModel.onEvent(ChatUiEvent.ConfirmEdit(newText))
                    },
                    onDismiss = { viewModel.onEvent(ChatUiEvent.DismissEdit) }
                )
            }

            // Permanent 1x1 hardware attachment. Guarantees V8 runtime prioritization.
            if (!isInteractive) {
                AndroidView(
                    factory = { viewModel.signer.webView },
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0.01f)
                )
            }
        }
    }

    // Dynamic promotion to foreground when puzzle/slider challenge is requested
    if (isInteractive) {
        ModalBottomSheet(
            onDismissRequest = { /* Require user to finish puzzle or cancel */ },
            sheetState = sheetState,
            containerColor = SurfaceRaised,
            modifier = Modifier.fillMaxHeight(0.65f)
        ) {
            AndroidView(
                factory = { viewModel.signer.webView },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp) // Exceeds Aliyun 320px bounding box constraint
            )
        }
    }
}

@Composable
private fun EditMessageDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val dialogShape = RoundedCornerShape(18.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaised,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = dialogShape,
        modifier = Modifier.specularBorder(dialogShape),
        title = {
            Text(
                "Edit and resend",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 3,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = QuantumCyan,
                    unfocusedBorderColor = BorderAmbient,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceBase,
                    unfocusedContainerColor = SurfaceBase
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Resend", color = if (text.isNotBlank()) QuantumCyan else TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
