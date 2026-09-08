package com.zai.chat.ui.screens.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zai.chat.terminal.LogEntry
import com.zai.chat.terminal.LogLevel
import com.zai.chat.terminal.LogcatManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val logcatManager: LogcatManager
) : ViewModel() {

    val isLogging: StateFlow<Boolean> = logcatManager.isLogging
    private val allLogs: StateFlow<List<LogEntry>> = logcatManager.logs

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedLevel = MutableStateFlow<LogLevel?>(null)
    val selectedLevel: StateFlow<LogLevel?> = _selectedLevel.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _autoScroll = MutableStateFlow(true)
    val autoScroll: StateFlow<Boolean> = _autoScroll.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    val filteredLogs: StateFlow<List<LogEntry>> = combine(
        allLogs,
        _searchQuery,
        _selectedLevel,
        _selectedTag
    ) { logs, query, level, tag ->
        logs.filter { entry ->
            val matchQuery = query.isBlank() ||
                entry.message.contains(query, ignoreCase = true) ||
                entry.tag.contains(query, ignoreCase = true) ||
                entry.raw.contains(query, ignoreCase = true)

            val matchLevel = level == null || entry.level == level ||
                (level == LogLevel.VERBOSE) ||
                (level == LogLevel.DEBUG && entry.level != LogLevel.VERBOSE) ||
                (level == LogLevel.INFO && (entry.level == LogLevel.INFO || entry.level == LogLevel.WARN || entry.level == LogLevel.ERROR || entry.level == LogLevel.ASSERT)) ||
                (level == LogLevel.WARN && (entry.level == LogLevel.WARN || entry.level == LogLevel.ERROR || entry.level == LogLevel.ASSERT)) ||
                (level == LogLevel.ERROR && (entry.level == LogLevel.ERROR || entry.level == LogLevel.ASSERT))

            val matchTag = tag.isNullOrBlank() || entry.tag.contains(tag, ignoreCase = true)

            matchQuery && matchLevel && matchTag
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun toggleLogging(enabled: Boolean) {
        if (enabled) {
            logcatManager.startLogging()
        } else {
            logcatManager.stopLogging()
        }
    }

    fun clearLogs() {
        logcatManager.clearLogs()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedLevel(level: LogLevel?) {
        _selectedLevel.value = level
    }

    fun setSelectedTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun toggleAutoScroll() {
        _autoScroll.value = !_autoScroll.value
    }

    fun copyLogs() {
        viewModelScope.launch {
            val count = logcatManager.copyLogsToClipboard(filteredLogs.value)
            _toastEvent.emit("Copied $count log lines to clipboard")
        }
    }

    fun exportLogs() {
        viewModelScope.launch {
            val entries = filteredLogs.value
            if (entries.isEmpty()) {
                _toastEvent.emit("No log lines to export")
                return@launch
            }
            logcatManager.exportLogsToDownloads(entries)
                .onSuccess { path ->
                    _toastEvent.emit("Saved to $path")
                }
                .onFailure { error ->
                    _toastEvent.emit("Failed to save logs: ${error.message}")
                }
        }
    }
}
