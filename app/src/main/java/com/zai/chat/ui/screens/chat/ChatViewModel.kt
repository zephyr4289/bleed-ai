package com.zai.chat.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zai.chat.config.ZaiConfig
import com.zai.chat.data.local.preferences.SettingsDataStore
import com.zai.chat.data.model.Message
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
        // P8: navigation passes chatId via the route; SavedStateHandle delivers it.
        savedStateHandle.get<String>("chatId")?.let { id ->
            _uiState.update { it.copy(chatId = id) }
            observeChat(id)
            viewModelScope.launch { repository.syncChatMessages(id) }
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
        val target = state.editingMessage ?: return
        val chatId = state.chatId ?: return
        if (newText.isBlank() || state.isStreaming) return
        _uiState.update { it.copy(editingMessage = null) }
        viewModelScope.launch {
            repository.deleteFromMessage(chatId, target.id)   // target + everything after
            launchSend(chatId = chatId, prompt = newText, createIfMissing = false)
        }
    }

    private fun handleRegenerate(message: Message) {
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
    private fun launchSend(chatId: String?, prompt: String?, createIfMissing: Boolean) {
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
            if (effectiveChatId == null) return@launch

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
