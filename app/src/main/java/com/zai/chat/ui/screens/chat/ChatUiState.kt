package com.zai.chat.ui.screens.chat

import android.net.Uri
import com.zai.chat.config.ZaiConfig
import com.zai.chat.data.model.FileAttachment
import com.zai.chat.data.model.Message

data class PendingAttachment(
    val localId: String,
    val uri: Uri,
    val name: String,
    val isImage: Boolean,
    val status: Status
) {
    sealed interface Status {
        data object Copying : Status
        data class Uploading(val percent: Int) : Status
        data class Done(val attachment: FileAttachment) : Status
        data class Failed(val reason: String) : Status
    }
}

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
    val editingMessage: Message? = null,
    val pendingAttachments: List<PendingAttachment> = emptyList()
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
    data class AddAttachments(val uris: List<Uri>) : ChatUiEvent
    data class RemoveAttachment(val localId: String) : ChatUiEvent
}
