package com.zai.chat.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zai.chat.data.model.Message
import com.zai.chat.data.model.MessageRole
import com.zai.chat.ui.components.MessageBubble
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onOpenGallery: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
                    Text(
                        text = state.chatTitle,
                        style = MaterialTheme.typography.titleMedium
                            .copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Rounded.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    if (onOpenGallery != null) {
                        IconButton(onClick = onOpenGallery) {
                            Icon(Icons.Rounded.Code, contentDescription = "Component gallery")
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
                onRemoveAttachment = { viewModel.onEvent(ChatUiEvent.RemoveAttachment(it)) }
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
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Warning, null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.onEvent(ChatUiEvent.DismissError) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close, "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(14.dp)
                            )
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
                            isStreaming = state.isStreaming   // handoff phase: no cursor
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
                    .padding(16.dp)
            ) {
                BadgedBox(
                    badge = {
                        if (state.unreadWhileScrolledUp > 0) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("+${state.unreadWhileScrolledUp}")
                            }
                        }
                    }
                ) {
                    FloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Scroll to latest")
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit and resend") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("Resend") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
