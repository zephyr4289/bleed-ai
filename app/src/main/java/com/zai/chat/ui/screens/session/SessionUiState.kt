package com.zai.chat.ui.screens.session

import com.zai.chat.data.local.preferences.JwtClaims

sealed interface SessionMode {
    /** Passive reconnect triggered by an unexpected 401 HTTP response */
    data object BackgroundExpired : SessionMode

    /** Explicit user-driven session configuration from Settings */
    data object ActiveManagement : SessionMode
}

sealed interface SessionVerificationState {
    data object Idle : SessionVerificationState
    data object Testing : SessionVerificationState
    data class Valid(val message: String) : SessionVerificationState
    data class Invalid(val error: String) : SessionVerificationState
}

data class SessionUiState(
    val mode: SessionMode = SessionMode.ActiveManagement,
    val currentClaims: JwtClaims? = null,
    val isWebViewActive: Boolean = false,
    val isChromeCustomTabTriggered: Boolean = false,
    val manualTokenInput: String = "",
    val verificationState: SessionVerificationState = SessionVerificationState.Idle,
    val webViewCrashOccurred: Boolean = false,
    val webViewMissingError: Boolean = false,
    val userFacingNotice: String? = null
)

sealed interface SessionUiEvent {
    data class SetMode(val mode: SessionMode) : SessionUiEvent
    data object LaunchWebView : SessionUiEvent
    data object DismissWebView : SessionUiEvent
    data object ClearWebCookies : SessionUiEvent
    data object LaunchChromeCustomTab : SessionUiEvent
    data class UpdateManualToken(val token: String) : SessionUiEvent
    data object ApplyManualToken : SessionUiEvent
    data object VerifyCurrentSession : SessionUiEvent
    data object PurgeSession : SessionUiEvent
    data object DismissNotice : SessionUiEvent
    data object AcknowledgeRendererCrash : SessionUiEvent
}
