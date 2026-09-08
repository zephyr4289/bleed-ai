package com.zai.chat.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zai.chat.data.local.preferences.SettingsDataStore
import com.zai.chat.data.local.preferences.TokenManager
import com.zai.chat.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val tokenManager: TokenManager,
    private val repository: ChatRepository
) : ViewModel() {

    val themeMode: StateFlow<String> = settingsDataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "OLED")

    val fontScale: StateFlow<Float> = settingsDataStore.fontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)

    val enterIsSend: StateFlow<Boolean> = settingsDataStore.enterIsSend
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasSession: StateFlow<Boolean> = tokenManager.tokenFlow
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), tokenManager.getStoredToken() != null)

    fun setTheme(mode: String) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { settingsDataStore.setFontScale(scale) }
    }

    fun setEnterIsSend(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setEnterIsSend(enabled) }
    }

    fun clearSession() {
        tokenManager.clearToken()
    }

    fun clearLocalCache() {
        viewModelScope.launch { repository.clearLocalCache() }
    }
}
