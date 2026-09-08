# 🗂️ Phase 8 — Drawer, History, Search & Navigation

**Goal:** The sidebar — real chat history from Room, FTS live search, swipe-delete, pin — plus a proper `NavHost` so opening a chat is navigation, not state mutation. MainActivity grows a `ModalNavigationDrawer`. **Gate:** CI green → install → drawer works end-to-end against local data (server history arrives with P12's token, as always).

**Phase contract:**

```
Route "chat?chatId={id}" → SavedStateHandle delivers id → VM observes + syncs.
                            New chat = "chat" (no arg). Back between chats = history stack.
Drawer open              → refreshChats() fires (web-created chats appear)
Search                   → debounced 250ms, FTS, results tap → open that chat, query cleared
Swipe right-to-left      → delete (P4 auto-restores if the server call fails)
Pin                      → local reorder into a Pinned section
```

**Two edits to existing files + three new files.** One structural decision first:

**Navigation replaces `loadChat`.** P7's `loadChat()` was a placeholder for "P8 will call this." Instead, chatId arrives via the route → `SavedStateHandle` → `init`. One VM per backstack entry, back button returns to the previous chat, no imperative handoffs. `loadChat()` stays in the file (harmless, useful if you ever add deep links).

## Setup

```bash
cd ~/zai
mkdir -p app/src/main/java/com/zai/chat/ui/screens/drawer
```

---

## Edit 1: `ui/screens/chat/ChatViewModel.kt` — init block only

Replace the `init { ... }` block's first section with:

```kotlin
    init {
        // P8: navigation passes chatId via the route; SavedStateHandle delivers it.
        savedStateHandle.get<String>("chatId")?.let { id ->
            _uiState.update { it.copy(chatId = id) }
            observeChat(id)
            viewModelScope.launch { repository.syncChatMessages(id) }
        }
        // ... the three existing launches (models, enterIsSend, defaultModel) unchanged
```

(P7's init observed but never synced — that was `loadChat`'s job. Now the route path does both. `loadChat` itself can stay for future deep links.)

## File 1: `ui/screens/drawer/DrawerViewModel.kt`

```kotlin
package com.zai.chat.ui.screens.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DrawerUiState(
    val chats: List<Chat> = emptyList(),
    val query: String = "",
    val searchResults: List<Message> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val repository: ChatRepository
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

    fun clearQuery() = onQueryChanged("")

    fun togglePin(chatId: String, currentPinned: Boolean) {
        viewModelScope.launch { repository.togglePinChat(chatId, !currentPinned) }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch { repository.deleteChat(chatId) }
    }
}
```

## File 2: `ui/screens/drawer/DrawerChatList.kt`

```kotlin
package com.zai.chat.ui.screens.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.data.model.Chat
import com.zai.chat.data.model.Message
import com.zai.chat.data.model.MessageRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerChatList(
    viewModel: DrawerViewModel,
    onSelectChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.85f)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        // ── Header ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Chats",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Row {
                IconButton(onClick = onNewChat) {
                    Icon(Icons.Rounded.Add, contentDescription = "New chat")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // ── Search ───────────────────────────────────────────────────
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChanged,
            placeholder = {
                Text("Search messages…", style = MaterialTheme.typography.bodyMedium)
            },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Close, "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        // ── List / Results ───────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (state.query.isNotBlank()) {
                items(state.searchResults, key = { it.id }) { result ->
                    SearchResultRow(
                        result = result,
                        chatTitle = state.chats.firstOrNull { it.id == result.chatId }?.title,
                        onClick = {
                            viewModel.clearQuery()
                            onSelectChat(result.chatId)
                        }
                    )
                }
                if (state.searchResults.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "No matches in your local history",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        )
                    }
                }
            } else {
                val pinned = state.chats.filter { it.pinned }
                val recent = state.chats.filter { !it.pinned }

                if (pinned.isNotEmpty()) {
                    item(key = "header-pinned") { SectionLabel("Pinned") }
                    items(pinned, key = { "p-${it.id}" }) { chat ->
                        ChatRow(viewModel, chat, onSelectChat)
                    }
                    item(key = "header-recent") { SectionLabel("Recent") }
                }
                items(recent, key = { it.id }) { chat ->
                    ChatRow(viewModel, chat, onSelectChat)
                }
                if (state.chats.isEmpty()) {
                    item(key = "no-chats") {
                        Text(
                            "No chats yet — send your first message",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRow(
    viewModel: DrawerViewModel,
    chat: Chat,
    onSelectChat: (String) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                viewModel.deleteChat(chat.id)   // P4: auto-restores if server refuses
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onSelectChat(chat.id) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatChatTime(chat.updatedAt),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { viewModel.togglePin(chat.id, chat.pinned) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = "Pin",
                    tint = if (chat.pinned) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: Message,
    chatTitle: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = chatTitle ?: "Chat",
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = result.content.take(160),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = (if (result.role == MessageRole.USER) "You" else "Assistant") +
                   " · ${formatChatTime(result.createdAt)}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        ),
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

/** "now" / "5m" / "3h" / "Mar 3" */
private fun formatChatTime(updatedAt: Long): String {
    val diff = System.currentTimeMillis() - updatedAt
    return when {
        diff < 60_000L -> "now"
        diff < 3_600_000L -> "${diff / 60_000}m"
        diff < 86_400_000L -> "${diff / 3_600_000}h"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(updatedAt))
    }
}
```

## File 3 (full replacement): `ui/MainActivity.kt`

```kotlin
package com.zai.chat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zai.chat.data.local.preferences.TokenManager
import com.zai.chat.network.auth.AuthEventManager
import com.zai.chat.ui.auth.TokenReconnectScreen
import com.zai.chat.ui.debug.ComponentGalleryScreen
import com.zai.chat.ui.screens.chat.ChatScreen
import com.zai.chat.ui.screens.chat.ChatViewModel
import com.zai.chat.ui.screens.drawer.DrawerChatList
import com.zai.chat.ui.screens.drawer.DrawerViewModel
import com.zai.chat.ui.theme.ZaiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
            

file 3 , package com.zai.chat.ui import android.os.Bundle import androidx.activity.ComponentActivity import androidx.activity.compose.setContent import 
androidx.activity.enableEdgeToEdge import androidx.compose.foundation.layout.Box import androidx.compose.foundation.layout.fillMaxSize import 
androidx.compose.foundation.layout.padding import androidx.compose.material.icons.Icons import 
androidx.compose.material.icons.automirrored.rounded.ArrowBack import androidx.compose.material3.DrawerValue import 
androidx.compose.material3.ExperimentalMaterial3Api import androidx.compose.material3.Icon import androidx.compose.material3.IconButton import 
androidx.compose.material3.MaterialTheme import androidx.compose.material3.ModalNavigationDrawer import androidx.compose.material3.Scaffold import 
androidx.compose.material3.Text import androidx.compose.material3.TextButton import androidx.compose.material3.TopAppBar import 
androidx.compose.material3.TopAppBarDefaults import androidx.compose.material3.rememberDrawerState import androidx.compose.runtime.Composable import 
androidx.compose.runtime.LaunchedEffect import androidx.compose.runtime.getValue import androidx.compose.runtime.mutableStateOf import 
androidx.compose.runtime.remember import androidx.compose.runtime.rememberCoroutineScope import androidx.compose.runtime.setValue import 
androidx.compose.ui.Alignment import androidx.compose.ui.Modifier import androidx.compose.ui.unit.dp import 
androidx.hilt.navigation.compose.hiltViewModel import androidx.navigation.NavType import androidx.navigation.compose.NavHost import 
androidx.navigation.compose.composable import androidx.navigation.compose.rememberNavController import androidx.navigation.navArgument import 
com.zai.chat.data.local.preferences.TokenManager import com.zai.chat.network.auth.AuthEventManager import com.zai.chat.ui.auth.TokenReconnectScreen 
import com.zai.chat.ui.debug.ComponentGalleryScreen import com.zai.chat.ui.screens.chat.ChatScreen import com.zai.chat.ui.screens.chat.ChatViewModel 
import com.zai.chat.ui.screens.drawer.DrawerChatList import com.zai.chat.ui.screens.drawer.DrawerViewModel import com.zai.chat.ui.theme.ZaiTheme import 
dagger.hilt.android.AndroidEntryPoint import kotlinx.coroutines.launch import javax.inject.Inject @AndroidEntryPoint class MainActivity : 
ComponentActivity() {
    @Inject lateinit var tokenManager: TokenManager @Inject lateinit var authEventManager: AuthEventManager override fun onCreate(savedInstanceState: 
    Bundle?) {
        enableEdgeToEdge() super.onCreate(savedInstanceState) setContent { ZaiTheme(themeMode = "OLED") { var showAuth by remember { 
                    mutableStateOf(tokenManager.getStoredToken() == null)
                }
                var dismissedWithoutToken by remember { mutableStateOf(false) } var showGallery by remember { mutableStateOf(false) } 
                LaunchedEffect(Unit) {
                    tokenManager.tokenFlow.collect { token -> when { token != null -> { showAuth = false; dismissedWithoutToken = false }
                            !dismissedWithoutToken -> showAuth = true
                        }
                    }
                }
                LaunchedEffect(Unit) { authEventManager.events.collect { showAuth = true }
                }
                // Created before the `when` so nav state survives gallery/auth detours.
                val navController = rememberNavController() val drawerState = rememberDrawerState(DrawerValue.Closed) val scope = 
                rememberCoroutineScope() val drawerViewModel: DrawerViewModel = hiltViewModel() LaunchedEffect(drawerState.currentValue) {
                    if (drawerState.currentValue == DrawerValue.Open) drawerViewModel.refresh()
                }
                when { showGallery -> { ComponentGalleryScreen(Modifier.fillMaxSize()) TextButton( onClick = { showGallery = false }, modifier = 
                            Modifier.padding(16.dp)
                        ) { Text("← Back") }
                    }
                    showAuth -> TokenReconnectScreen( isFirstLogin = tokenManager.getStoredToken() == null, onTokenExtracted = { 
                        tokenManager.saveToken(it) }, onDismiss = { dismissedWithoutToken = true; showAuth = false }
                    ) else -> ModalNavigationDrawer( drawerState = drawerState, drawerContent = { DrawerChatList( viewModel = drawerViewModel, 
                                onSelectChat = { chatId ->
                                    scope.launch { drawerState.close() 
🔧 Deviations vs. the PDF
#
PDF
Ours
Why
1	Single chat screen + imperative loadChat	NavHost + chat?chatId={id} route → SavedStateHandle	Real back stack (back = previous chat), VM-per-entry, no state mutation from navigation
2	Deprecated SwipeToDismiss + DismissValue	SwipeToDismissBox + SwipeToDismissBoxValue (M3 1.3.0, in our BOM)	New API; old one is deprecated and will warn-fail later upgrades
3	Flat chat list	Pinned / Recent sections	The pin feature was useless without visual separation
4	Always "MMM d" timestamps	now / 5m / 3h / Mar 3	The relative-time spec from the original UI vision
5	Refresh chats in init only	+ refresh on every drawer open	Web-created chats appear without app restart
6	Search results kept after selection	Query cleared on select	No stale results on next open
7	—	Empty-search and no-chats states	Drawer never looks broken
8	—	Settings stub route	Drawer's button works today; P9 swaps the body

Expected behavior note: without a token (P12), refreshChats fails silently → the sidebar shows only locally-created chats (your P7 test sends). That's correct offline-first behavior, not a bug.

🚀 Run it
bash

git add -A && git commit -m "P8: drawer with history/FTS search/swipe-delete/pin, NavHost with chat routes, refresh-on-open" && git push
gh run watch
Red? Ranked suspects:

SwipeToDismissBox/rememberSwipeToDismissBoxState imports — they're androidx.compose.material3.*, names must match exactly (BOM 2024.09.00 → material3 1.3.0 has them)
Icons.AutoMirrored.Rounded.ArrowBack — needs the automirrored package path
navArgument/NavType from androidx.navigation.* (not navigation.compose)
TextAlign import forgotten for the empty-state rows
ChatViewModel init edit — make sure the three existing viewModelScope.launch blocks survived the paste
Green? Install and poke: edge-swipe opens the drawer → new chat → send a test message (persists locally, appears in sidebar with "now") → pin it (jumps to Pinned) → open another chat, back returns → search for a word you sent → tap result lands in that chat → swipe-delete a chat → settings stub opens/back.

Next: Phase 9 — real Settings. The theme-mode radio finally stops being decorative (wire into MainActivity's hardcoded "OLED"), the font slider scales ZaiTypography via a CompositionLocal multiplier, streamingEnabled finds a consumer, plus a session-status row (token present/absent + "Manage session" shortcut) and a clear-cache button. Small phase — the plumbing exists, P9 just connects wires.





Send a Message




