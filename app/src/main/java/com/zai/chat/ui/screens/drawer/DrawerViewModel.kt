package com.zai.chat.ui.screens.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zai.chat.data.local.preferences.TokenManager
import com.zai.chat.data.model.Chat
import com.zai.chat.data.model.Message
import com.zai.chat.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DrawerUiState(
    val chats: List<Chat> = emptyList(),
    val query: String = "",
    val searchResults: List<Message> = emptyList()
) {
    val searchQuery: String get() = query
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrawerUiState())
    val uiState: StateFlow<DrawerUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            repository.getChatsStream().collectLatest { chats ->
                _uiState.update { it.copy(chats = chats) }
            }
        }
        viewModelScope.launch {
            tokenManager.tokenFlow.filterNotNull().collectLatest {
                repository.refreshChats()
            }
        }
        viewModelScope.launch {
            queryFlow
                .debounce(250)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    if (q.isBlank()) flowOf(emptyList()) else repository.searchMessages(q)
                }
                .collectLatest { results ->
                    _uiState.update { it.copy(searchResults = results) }
                }
        }
        refresh()
    }

    /** Called every time the drawer opens — picks up web-created chats. */
    fun refresh() {
        viewModelScope.launch { repository.refreshChats() }
    }

    fun onQueryChanged(q: String) {
        _uiState.update { it.copy(query = q) }
        queryFlow.value = q
    }

    fun onSearchQueryChanged(q: String) = onQueryChanged(q)

    fun clearQuery() = onQueryChanged("")

    fun togglePin(chatId: String, currentPinned: Boolean) {
        viewModelScope.launch { repository.togglePinChat(chatId, !currentPinned) }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch { repository.deleteChat(chatId) }
    }
}
