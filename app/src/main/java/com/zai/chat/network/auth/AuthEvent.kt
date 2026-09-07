package com.zai.chat.network.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthEvent {
    data object TokenExpired : AuthEvent
}

/**
 * App-wide auth signal bus. The interceptor detects 401s deep inside the
 * network stack and publishes here; P5's reconnect dialog subscribes in
 * MainActivity. Decoupled by design — the network layer never knows about UI.
 */
@Singleton
class AuthEventManager @Inject constructor() {
    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun emitTokenExpired() {
        _events.tryEmit(AuthEvent.TokenExpired)
    }
}
