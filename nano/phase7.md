# 💬 Phase 7 — The Chat Screen Goes Live

**Goal:** UiState/Events → ViewModel (full persist→stream→persist loop against P4's contracts) → Composer (finally consuming P1's `enterIsSend`) → Screen with reverseLayout list, transient streaming bubble, edit dialog, error banner, scroll-FAB — all wired into MainActivity. **Gate:** CI green → install → *the app is the app* (streaming itself needs P12 token, but every state path is exercise-able now via the gallery's habits: send → banner error (no token) is already correct behavior).

**Key contract decisions (all follow from P4's design):**

```
SendMessage      → createChat? → persistUserMessage → stream (no duplication possible)
Regenerate(msg)  → deleteFromMessage(msg.id) → stream (history ends at user prompt)
RetryPartial(msg)→ same primitive — one code path
Stop             → cancel job, DISCARD live buffers (partial only survives on
                   network Error, which the repository persists — manual stop doesn't)
Edit&Resend      → dialog (prefilled) → deleteFromMessage → persistUserMessage → stream
Transient→Room   → "awaitingHandoff" flag: live buffers stay visible until Room
                   emits the persisted assistant message → zero flicker (P-gap #9 closed)
```

## Setup

```bash
cd ~/zai
mkdir -p app/src/main/java/com/zai/chat/ui/screens/chat
```

---

## File 1: `ui/screens/chat/ChatUiState.kt`

```kotlin
package com.zai.chat.ui.screens.chat

import com.zai.chat.config.ZaiConfig
import com.zai.chat.data.model.Message

data class ChatUiState(
    val chatId: String? = null,
    val chatTitle: String = "New Chat",
    val messages: List<Message> = emptyList(),
    val isStreaming: Boolean = false,
    /** Kept visible until Room emits the persisted assistant message (handoff). */
    val liveReasoning: String = "",
    val liveContent: String = "",
    val awaitingHandoff: Boolean = false,
    val selectedModel: String = ZaiConfig.MODEL_DEFAULT,
    val availableModels: List<String> = listOf(ZaiConfig.MODEL_DEFAULT),
    val webSearch: Boolean = false,
    val deepThinking: Boolean = true,
    val enterIsSend: Boolean = false,
    val unreadWhileScrolledUp: Int = 0,
    val bannerError: String? = null,
    /** Non-null → edit dialog is open for this message. */
    val editingMessage: Message? = null
)

sealed interface ChatUiEvent {
    data class SendMessage(val text: String) : ChatUiEvent
    data object StopStreaming : ChatUiEvent
    data class ToggleWebSearch(val enabled: Boolean) : ChatUiEvent
    data class ToggleDeepThinking(val enabled: Boolean) : ChatUiEvent
    data class SelectModel(val model: String) : ChatUiEvent
    data class StartEdit(val message: Message) : ChatUiEvent
    data class ConfirmEdit(val newText: String) : ChatUiEvent
    data object DismissEdit : ChatUiEvent
    /** Delete [message] onward, restream from the preceding user prompt. */
    data class RegenerateFrom(val message: Message) : ChatUiEvent
    data object DismissError : ChatUiEvent
    data class ScrolledStateChange(val isScrolledUp: Boolean) : ChatUiEvent
}
```

## File 2: `ui/screens/chat/ChatViewModel.kt`

```kotlin
package com.zai.chat.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zai.chat.config.ZaiConfig
import com.zai.chat.data.local.preferences.SettingsDataStore
import com.zai.chat.data.model.MessageRole
import com.zai.chat.data.repository.ChatRepository
import com.zai.chat.network.ZaiApiService
import com.zai.chat.network.sse.StreamEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val apiService: ZaiApiService,
    settingsDataStore: SettingsDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var messagesJob: Job? = null
    private var metaJob: Job? = null
    private var isScrolledUp = false

    init {
        // Forward-compat: P8 navigation will pass chatId as a route arg.
        savedStateHandle.get<String>("chatId")?.let { id ->
            _uiState.update { it.copy(chatId = id) }
            observeChat(id)
        }

        viewModelScope.launch {
            apiService.getAvailableModels().let { models ->
                _uiState.update { it.copy(availableModels = models) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.enterIsSend.collectLatest { enabled ->
                _uiState.update { it.copy(enterIsSend = enabled) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.defaultModel.collectLatest { model ->
                if (model.isNotBlank()) {
                    _uiState.update { it.copy(selectedModel = model) }
                }
            }
        }
    }

    /** Called by P8 drawer navigation to open an existing chat. */
    fun loadChat(chatId: String) {
        _uiState.update { it.copy(chatId = chatId, chatTitle = "…", messages = emptyList()) }
        observeChat(chatId)
        viewModelScope.launch { repository.syncChatMessages(chatId) }
    }

    private fun observeChat(chatId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getMessagesStream(chatId).collectLatest { msgs ->
                _uiState.update { st ->
                    if (st.awaitingHandoff && msgs.lastOrNull()?.role == MessageRole.ASSISTANT) {
                        // Persisted reply arrived in Room → swap out the transient
                        // bubble atomically with buffers clearing. No flicker.
                        st.copy(
                            messages = msgs,
                            isStreaming = false,
                            liveContent = "",
                            liveReasoning = "",
                            awaitingHandoff = false
                        )
                    } else {
                        st.copy(messages = msgs)
                    }
                }
            }
        }
        metaJob?.cancel()
        metaJob = viewModelScope.launch {
            repository.getChatsStream().collectLatest { chats ->
                chats.firstOrNull { it.id == chatId }?.let { chat ->
                    _uiState.update { it.copy(chatTitle = chat.title) }
                }
            }
        }
    }

    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.SendMessage -> handleSend(event.text)
            is ChatUiEvent.StopStreaming -> stopStreaming()
            is ChatUiEvent.ToggleWebSearch ->
                _uiState.update { it.copy(webSearch = event.enabled) }
            is ChatUiEvent.ToggleDeepThinking ->
                _uiState.update { it.copy(deepThinking = event.enabled) }
            is ChatUiEvent.SelectModel ->
                _uiState.update { it.copy(selectedModel = event.model) }
            is ChatUiEvent.StartEdit ->
                _uiState.update { it.copy(editingMessage = event.message) }
            is ChatUiEvent.ConfirmEdit -> handleConfirmEdit(event.newText)
            is ChatUiEvent.DismissEdit ->
                _uiState.update { it.copy(editingMessage = null) }
            is ChatUiEvent.RegenerateFrom -> handleRegenerate(event.message)
            is ChatUiEvent.DismissError ->
                _uiState.update { it.copy(bannerError = null) }
            is ChatUiEvent.ScrolledStateChange -> {
                isScrolledUp = event.isScrolledUp
                if (!event.isScrolledUp) {
                    _uiState.update { it.copy(unreadWhileScrolledUp = 0) }
                }
            }
        }
    }

    private fun handleSend(text: String) {
        val current = _uiState.value
        if (text.isBlank() || current.isStreaming) return
        launchSend(chatId = current.chatId, prompt = text, createIfMissing = true)
    }

    private fun handleConfirmEdit(newText: String) {
        val state = _uiState.value
        val target = state.editingMessage
        val chatId = state.chatId ?: return
        if (newText.isBlank() || state.isStreaming) return
        _uiState.update { it.copy(editingMessage = null) }
        viewModelScope.launch {
            repository.deleteFromMessage(chatId, target.id)   // target + everything after
            launchSend(chatId = chatId, prompt = newText, createIfMissing = false)
        }
    }

    private fun handleRegenerate(message: com.zai.chat.data.model.Message) {
        val state = _uiState.value
        val chatId = state.chatId ?: return
        if (state.isStreaming) return
        viewModelScope.launch {
            repository.deleteFromMessage(chatId, message.id)  // partial/reply + everything after
            launchSend(chatId = chatId, prompt = null, createIfMissing = false)
        }
    }

    /**
     * Single pipeline for send / edit-resend / regenerate / retry.
     * [prompt] == null → history already ends at a user message (regenerate/retry path).
     */
    private fun launchSend(chatId: String, prompt: String?, createIfMissing: Boolean) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            var effectiveChatId = chatId
            if (effectiveChatId == null && createIfMissing) {
                val promptText = prompt ?: return@launch
                effectiveChatId = repository.createChat(promptText)
                _uiState.update {
                    it.copy(
                        chatId = effectiveChatId,
                        chatTitle = promptText.lineSequence().firstOrNull()
                            ?.take(28).orEmpty().ifBlank { "New Chat" }
                    )
                }
                observeChat(effectiveChatId)
            }
            prompt?.let {
                repository.persistUserMessage(chatId = effectiveChatId, content = it)
            }
            _uiState.update {
                it.copy(
                    isStreaming = true,
                    liveContent = "",
                    liveReasoning = "",
                    awaitingHandoff = false,
                    unreadWhileScrolledUp = 0,
                    bannerError = null
                )
            }
            startStream(effectiveChatId)
        }
    }

    private suspend fun startStream(chatId: String) {
        val state = _uiState.value
        repository.streamCompletion(
            chatId = chatId,
            model = state.selectedModel,
            webSearch = state.webSearch,
            deepThinking = state.deepThinking
        ).collect { event ->
            when (event) {
                is StreamEvent.ReasoningDelta ->
                    _uiState.update { it.copy(liveReasoning = it.liveReasoning + event.text) }
                is StreamEvent.ContentDelta ->
                    _uiState.update {
                        it.copy(
                            liveContent = it.liveContent + event.text,
                            unreadWhileScrolledUp =
                                if (isScrolledUp) it.unreadWhileScrolledUp + 1 else 0
                        )
                    }
                // Citations/Usage are persisted by the repository on Done;
                // the transient bubble intentionally skips them.
                is StreamEvent.Citations, is StreamEvent.Usage -> Unit
                is StreamEvent.Done -> {
                    val hasText = _uiState.value.let {
                        it.liveContent.isNotBlank() || it.liveReasoning.isNotBlank()
                    }
                    _uiState.update {
                        if (hasText) {
                            it.copy(awaitingHandoff = true)   // keep visible → Room swap
                        } else {
                            it.copy(
                                isStreaming = false,
                                bannerError = "Empty response from model"
                            )
                        }
                    }
                }
                is StreamEvent.Error -> {
                    val hasPartial = event.partialContent.isNotEmpty() ||
                        event.partialReasoning.isNotEmpty()
                    _uiState.update {
                        if (hasPartial) {
                            it.copy(awaitingHandoff = true, bannerError = "Stream interrupted")
                        } else {
                            it.copy(
                                isStreaming = false,
                                liveContent = "",
                                liveReasoning = "",
                                bannerError = "Connection failed — is a session token set?"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        // Manual stop = discard. (Network-failure partials are persisted by the
        // repository; deliberate choice — a stop mid-word shouldn't save a stub.)
        _uiState.update {
            it.copy(
                isStreaming = false,
                liveContent = "",
                liveReasoning = "",
                awaitingHandoff = false
            )
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }
}
```

## File 3: `ui/screens/chat/ChatComposer.kt`

```kotlin
package com.zai.chat.ui.screens.chat

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.ui.theme.ClaudePeach
import com.zai.chat.ui.theme.KimiCyan
import com.zai.chat.ui.theme.ShapeChip
import com.zai.chat.ui.theme.ZaiMotion

/**
 * Composer consuming P1's enterIsSend: ImeAction.Send when enabled, newline
 * otherwise. Attachment button is a P10 placeholder.
 */
@Composable
fun ChatComposer(
    isStreaming: Boolean,
    selectedModel: String,
    availableModels: List<String>,
    webSearch: Boolean,
    deepThinking: Boolean,
    enterIsSend: Boolean,
    onSendMessage: (String) -> Unit,
    onStopStreaming: () -> Unit,
    onToggleWebSearch: (Boolean) -> Unit,
    onToggleDeepThinking: (Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    var modelMenuOpen by remember { mutableStateOf(false) }
    val view = LocalView.current

    fun send() {
        if (isStreaming || textInput.isBlank()) return
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
    
file³, package com.zai.chat.ui.screens.chat

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.ui.theme.ClaudePeach
import com.zai.chat.ui.theme.KimiCyan
import com.zai.chat.ui.theme.ShapeChip
import com.zai.chat.ui.theme.ZaiMotion

/**
 * Composer consuming P1's enterIsSend: ImeAction.Send when enabled, newline
 * otherwise. Attachment button is a P10 placeholder.
 */
@Composable
fun ChatComposer(
    isStreaming: Boolean,
    selectedModel: String,
    availableModels: List<String>,
    webSearch: Boolean,
    deepThinking: Boolean,
    enterIsSend: Boolean,
    onSendMessage: (String) -> Unit,
    onStopStreaming: () -> Unit,
    onToggleWebSearch: (Boolean) -> Unit,
    onToggleDeepThinking: (Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    var modelMenuOpen by remember { mutableStateOf(false) }
    val view = LocalView.current

    fun send() {
        if (isStreaming || textInput.isBlank()) return
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

        Spacer(Modifier.width(0.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))

        // ── Input row ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            // P10: file upload lands here
            IconButton(onClick = { /* P10: file picker */ }, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = "Attach",
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
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
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
                    textInput.isNotBlank() -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surface
                },
                animationSpec = ZaiMotion.MorphSpring,
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
                    tint = if (isStreaming || textInput.isNotBlank()) Color.Black
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


4.


package com.zai.chat.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
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
                onSendMessage = { viewModel.onEvent(ChatUiEvent.SendMessage(it)) },
                onStopStreaming = { viewModel.onEvent(ChatUiEvent.StopStreaming) },
                onToggleWebSearch = { viewModel.onEvent(ChatUiEvent.ToggleWebSearch(it)) },
                onToggleDeepThinking = { viewModel.onEvent(ChatUiEvent.ToggleDeepThinking(it)) },
                onSelectModel = { viewModel.onEvent(ChatUiEvent.SelectModel(it)) }
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
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Warning, null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
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




5.


package com.zai.chat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zai.chat.data.local.preferences.TokenManager
import com.zai.chat.network.auth.AuthEventManager
import com.zai.chat.ui.auth.TokenReconnectScreen
import com.zai.chat.ui.debug.ComponentGalleryScreen
import com.zai.chat.ui.screens.chat.ChatScreen
import com.zai.chat.ui.screens.chat.ChatViewModel
import com.zai.chat.ui.theme.ZaiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var authEventManager: AuthEventManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ZaiTheme(themeMode = "OLED") {
                var showAuth by remember {
                    mutableStateOf(tokenManager.getStoredToken() == null)
                }
                var dismissedWithoutToken by remember { mutableStateOf(false) }
                var showGallery by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    tokenManager.tokenFlow.collect { token ->
                        when {
                            token != null -> { showAuth = false; dismissedWithoutToken = false }
                            !dismissedWithoutToken -> showAuth = true
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    authEventManager.events.collect { showAuth = true }
                }

                when {
                    showGallery -> {
                        ComponentGalleryScreen(Modifier.fillMaxSize())
                        TextButton(
                            onClick = { showGallery = false },
                            modifier = Modifier.padding(16.dp)
                        ) { Text("← Back") }
                    }
                    showAuth -> TokenReconnectScreen(
                        isFirstLogin = tokenManager.getStoredToken() == null,
                        onTokenExtracted = { tokenManager.saveToken(it) },
                        onDismiss = { dismissedWithoutToken = true; showAuth = false }
                    )
                    else -> ChatScreen(
                        viewModel = hiltViewModel<ChatViewModel>(),
                        onOpenDrawer = { /* P8: drawer */ },
                        onOpenGallery = { showGallery = true }
                    )
                }
            }
        }
    }
}




Notable calls vs. the PDF
#
PDF
Ours
Why
1	Regenerate re-sent prompt text → duplicate user messages	All four actions (send/edit/regenerate/retry) route through one launchSend(chatId, prompt?, …) primitive	P4's split API makes duplication impossible by construction
2	Cleared live buffers on Done → one-frame gap before Room emission	awaitingHandoff flag; buffers clear only when Room emits the assistant message	Transient→persisted swap is seamless
3	Empty response still persisted + flickered	Empty Done → no persist (P4), banner shown	no phantom "…" bubbles
4	Streaming composer ignored settings	enterIsSend → ImeAction.Send vs newline	P1's setting finally consumed
5	Manual stop kept nothing, silently	Documented: stop = discard; only network-error partials persist	predictable semantics
6	Title never updated after create	Title from chats stream observation + first-line fallback	sidebar and header 
