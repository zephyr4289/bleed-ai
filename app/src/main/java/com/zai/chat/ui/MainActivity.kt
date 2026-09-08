package com.zai.chat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zai.chat.data.local.preferences.TokenManager
import com.zai.chat.network.auth.AuthEventManager
import com.zai.chat.ui.auth.TokenReconnectScreen
import com.zai.chat.ui.debug.ComponentGalleryScreen
import com.zai.chat.ui.screens.chat.ChatScreen
import com.zai.chat.ui.screens.chat.ChatViewModel
import com.zai.chat.ui.theme.ZaiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var authEventManager: AuthEventManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ZaiTheme(themeMode = "OLED") {
                var showAuth by remember {
                    mutableStateOf(tokenManager.getStoredToken() == null)
                }
                var dismissedWithoutToken by remember { mutableStateOf(false) }
                var showGallery by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    tokenManager.tokenFlow.collect { token ->
                        when {
                            token != null -> { showAuth = false; dismissedWithoutToken = false }
                            !dismissedWithoutToken -> showAuth = true
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    authEventManager.events.collect { showAuth = true }
                }

                when {
                    showGallery -> {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                            ComponentGalleryScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 32.dp)
                            )
                            TextButton(
                                onClick = { showGallery = false },
                                modifier = Modifier.padding(16.dp)
                            ) { Text("← Back") }
                        }
                    }
                    showAuth -> TokenReconnectScreen(
                        isFirstLogin = tokenManager.getStoredToken() == null,
                        onTokenExtracted = { tokenManager.saveToken(it) },
                        onDismiss = { dismissedWithoutToken = true; showAuth = false }
                    )
                    else -> ChatScreen(
                        viewModel = hiltViewModel<ChatViewModel>(),
                        onOpenDrawer = { /* P8: drawer */ },
                        onOpenGallery = { showGallery = true }
                    )
                }
            }
        }
    }
}
