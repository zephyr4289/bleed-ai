package com.zai.chat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zai.chat.data.local.preferences.TokenManager
import com.zai.chat.network.auth.AuthEventManager
import com.zai.chat.ui.auth.TokenReconnectScreen
import com.zai.chat.ui.theme.ZaiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var authEventManager: AuthEventManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ZaiTheme(themeMode = "OLED") {
                var showAuth by remember { mutableStateOf(tokenManager.getStoredToken() == null) }
                var dismissedWithoutToken by remember { mutableStateOf(false) }

                // Reactive auth state: token appearing (any path) closes the gate;
                // token vanishing re-opens it — unless the user explicitly backed out.
                LaunchedEffect(Unit) {
                    tokenManager.tokenFlow.collect { token ->
                        when {
                            token != null -> {
                                showAuth = false
                                dismissedWithoutToken = false
                            }
                            !dismissedWithoutToken -> showAuth = true
                        }
                    }
                }

                // Any 401 anywhere in the app forces the gate (P2 interceptor bus).
                LaunchedEffect(Unit) {
                    authEventManager.events.collect { showAuth = true }
                }

                if (showAuth) {
                    TokenReconnectScreen(
                        isFirstLogin = tokenManager.getStoredToken() == null,
                        onTokenExtracted = { token ->
                            // TokenManager trims quotes/whitespace and updates
                            // tokenFlow → the collector above closes the gate.
                            tokenManager.saveToken(token)
                        },
                        onDismiss = {
                            dismissedWithoutToken = true
                            showAuth = false
                        }
                    )
                } else {
                    PlaceholderHome(
                        hasSession = tokenManager.getStoredToken() != null,
                        onManageSession = {
                            dismissedWithoutToken = false
                            showAuth = true
                        }
                    )
                }
            }
        }
    }
}

/** Temporary home — replaced by the real chat screen in P7. */
@Composable
private fun PlaceholderHome(
    hasSession: Boolean,
    onManageSession: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Z.AI", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Phase 5 — auth gate online",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            AssistChip(
                onClick = onManageSession,
                label = { Text(if (hasSession) "Session: ACTIVE" else "Session: NONE") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (hasSession)
                        MaterialTheme.colorScheme.surfaceVariant
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onManageSession) { Text("Manage session") }
        }
    }
}
