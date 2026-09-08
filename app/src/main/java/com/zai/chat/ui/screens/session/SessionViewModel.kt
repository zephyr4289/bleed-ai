package com.zai.chat.ui.screens.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zai.chat.data.local.preferences.JwtParser
import com.zai.chat.data.local.preferences.TokenManager
import com.zai.chat.network.ZaiApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val apiService: ZaiApiService
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        loadActiveToken()
    }

    fun loadActiveToken() {
        val stored = tokenManager.getStoredToken()
        val claims = JwtParser.parse(stored)
        _uiState.update {
            it.copy(
                currentClaims = claims,
                manualTokenInput = stored.orEmpty(),
                verificationState = SessionVerificationState.Idle
            )
        }
    }

    fun onEvent(event: SessionUiEvent) {
        when (event) {
            is SessionUiEvent.SetMode -> _uiState.update { it.copy(mode = event.mode) }
            is SessionUiEvent.LaunchWebView -> _uiState.update {
                it.copy(isWebViewActive = true, webViewCrashOccurred = false)
            }
            is SessionUiEvent.DismissWebView -> _uiState.update { it.copy(isWebViewActive = false) }
            is SessionUiEvent.ClearWebCookies -> clearCookiesInternal()
            is SessionUiEvent.LaunchChromeCustomTab -> _uiState.update { it.copy(isChromeCustomTabTriggered = true) }
            is SessionUiEvent.UpdateManualToken -> _uiState.update { it.copy(manualTokenInput = event.token) }
            is SessionUiEvent.ApplyManualToken -> applyInputToken()
            is SessionUiEvent.VerifyCurrentSession -> verifyTokenConnectivity()
            is SessionUiEvent.PurgeSession -> purgeSessionInternal()
            is SessionUiEvent.DismissNotice -> _uiState.update { it.copy(userFacingNotice = null) }
            is SessionUiEvent.AcknowledgeRendererCrash -> _uiState.update {
                it.copy(webViewCrashOccurred = false, isWebViewActive = false)
            }
        }
    }

    fun handleTokenDiscoveredFromWeb(rawToken: String) {
        val claims = JwtParser.parse(rawToken)
        if (claims == null) {
            _uiState.update { it.copy(userFacingNotice = "Captured payload was not a valid JWT") }
            return
        }
        // Save new verified token
        tokenManager.saveToken(claims.rawToken)
        _uiState.update {
            it.copy(
                currentClaims = claims,
                isWebViewActive = false,
                manualTokenInput = claims.rawToken,
                userFacingNotice = "Session renewed successfully: ${claims.subject ?: "Authenticated"}"
            )
        }
    }

    fun handleRendererCrash() {
        _uiState.update {
            it.copy(
                isWebViewActive = false,
                webViewCrashOccurred = true,
                userFacingNotice = "Chromium rendering process exhausted memory or was terminated by OS."
            )
        }
    }

    fun handleMissingWebView() {
        _uiState.update {
            it.copy(
                isWebViewActive = false,
                webViewMissingError = true,
                userFacingNotice = "System WebView package is missing or disabled on this device."
            )
        }
    }

    private fun applyInputToken() {
        val clean = _uiState.value.manualTokenInput.removePrefix("Bearer ").trim()
        val claims = JwtParser.parse(clean)
        if (claims == null) {
            _uiState.update { it.copy(verificationState = SessionVerificationState.Invalid("Invalid JWT structure")) }
            return
        }
        tokenManager.saveToken(clean)
        _uiState.update {
            it.copy(
                currentClaims = claims,
                verificationState = SessionVerificationState.Valid("JWT applied. Testing connectivity...")
            )
        }
        verifyTokenConnectivity()
    }

    private fun verifyTokenConnectivity() {
        viewModelScope.launch {
            _uiState.update { it.copy(verificationState = SessionVerificationState.Testing) }
            try {
                val models = withContext(Dispatchers.IO) { apiService.pingAvailableModels() }
                if (models.isNotEmpty()) {
                    _uiState.update {
                        it.copy(verificationState = SessionVerificationState.Valid("Connected! ${models.size} models reachable (${models.take(3).joinToString()})"))
                    }
                } else {
                    _uiState.update {
                        it.copy(verificationState = SessionVerificationState.Invalid("Endpoint responded but 0 models returned"))
                    }
                }
            } catch (e: Exception) {
                val rawMsg = e.message ?: "Connection failure"
                val diagnostic = when {
                    rawMsg.contains("401") || rawMsg.contains("403") -> "Authentication rejected (HTTP 401/403) — invalid or expired token."
                    rawMsg.contains("404") -> "Endpoint not found (HTTP 404) — check API base path."
                    rawMsg.contains("Unable to resolve host") || rawMsg.contains("timeout") -> "Network error: unreachable ($rawMsg)"
                    else -> rawMsg
                }
                _uiState.update {
                    it.copy(verificationState = SessionVerificationState.Invalid(diagnostic))
                }
            }
        }
    }

    private fun clearCookiesInternal() {
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies {
                android.webkit.CookieManager.getInstance().flush()
                _uiState.update { it.copy(userFacingNotice = "Browser cookies completely cleared.") }
            }
        } catch (_: Exception) {}
    }

    private fun purgeSessionInternal() {
        tokenManager.clearToken()
        clearCookiesInternal()
        _uiState.update {
            it.copy(
                currentClaims = null,
                manualTokenInput = "",
                verificationState = SessionVerificationState.Idle,
                userFacingNotice = "Active session discarded."
            )
        }
    }
}
