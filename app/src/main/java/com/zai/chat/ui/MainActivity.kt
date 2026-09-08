package com.zai.chat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zai.chat.data.local.preferences.SettingsDataStore
import com.zai.chat.data.local.preferences.TokenManager
import com.zai.chat.network.auth.AuthEventManager
import com.zai.chat.ui.auth.TokenReconnectScreen
import com.zai.chat.ui.debug.ComponentGalleryScreen
import com.zai.chat.ui.screens.chat.ChatScreen
import com.zai.chat.ui.screens.chat.ChatViewModel
import com.zai.chat.ui.screens.drawer.DrawerChatList
import com.zai.chat.ui.screens.drawer.DrawerViewModel
import com.zai.chat.ui.screens.settings.SettingsScreen
import com.zai.chat.ui.screens.settings.SettingsViewModel
import com.zai.chat.ui.theme.ZaiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var authEventManager: AuthEventManager
    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by settingsDataStore.themeMode.collectAsState(initial = "OLED")
            val fontScale by settingsDataStore.fontScale.collectAsState(initial = 1f)

            ZaiTheme(themeMode = themeMode, fontScale = fontScale) {
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

                val navController = rememberNavController()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val drawerViewModel: DrawerViewModel = hiltViewModel()

                LaunchedEffect(drawerState.currentValue) {
                    if (drawerState.currentValue == DrawerValue.Open) {
                        drawerViewModel.refresh()
                    }
                }

                when {
                    showGallery -> {
                        Box(modifier = Modifier.fillMaxSize()) {
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
                    else -> ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            DrawerChatList(
                                viewModel = drawerViewModel,
                                onSelectChat = { chatId ->
                                    scope.launch { drawerState.close() }
                                    navController.navigate("chat?chatId=$chatId")
                                },
                                onNewChat = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate("chat")
                                },
                                onOpenSettings = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate("settings")
                                }
                            )
                        }
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = "chat"
                        ) {
                            composable(
                                route = "chat?chatId={chatId}",
                                arguments = listOf(
                                    navArgument("chatId") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) { backStackEntry ->
                                val chatViewModel: ChatViewModel = hiltViewModel(backStackEntry)
                                ChatScreen(
                                    viewModel = chatViewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onOpenGallery = { showGallery = true }
                                )
                            }
                            composable("settings") {
                                val settingsViewModel: SettingsViewModel = hiltViewModel()
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { navController.popBackStack() },
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
        }
    }
}
