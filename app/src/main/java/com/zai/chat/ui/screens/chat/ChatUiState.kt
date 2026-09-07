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
