package com.zai.chat.ui.screens.drawer

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.R
import com.zai.chat.data.model.Chat
import com.zai.chat.ui.components.specularBorder
import com.zai.chat.ui.components.tactilePress
import com.zai.chat.ui.theme.BorderAmbient
import com.zai.chat.ui.theme.CrimsonFlare
import com.zai.chat.ui.theme.EmeraldPulse
import com.zai.chat.ui.theme.MidnightObsidian
import com.zai.chat.ui.theme.QuantumCyan
import com.zai.chat.ui.theme.RadiantAmber
import com.zai.chat.ui.theme.SurfaceActive
import com.zai.chat.ui.theme.SurfaceBase
import com.zai.chat.ui.theme.SurfaceRaised
import com.zai.chat.ui.theme.TextPrimary
import com.zai.chat.ui.theme.TextSecondary
import com.zai.chat.ui.theme.TextTertiary
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
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(MidnightObsidian)
            .padding(16.dp)
    ) {
        // ── Brand Header ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(EmeraldPulse)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "BLEED-AI",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = TextPrimary
                    )
                )
            }

            Row {
                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onNewChat()
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "New Session",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onOpenSettings()
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Specular Search Field ─────────────────────────────────────
        val searchShape = RoundedCornerShape(12.dp)
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholder = {
                Text(
                    "Search conversations...",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextTertiary)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = searchShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceBase,
                unfocusedContainerColor = SurfaceBase,
                focusedBorderColor = BorderAmbient,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .specularBorder(searchShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Chronological List Stream ─────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Group Pinned Chats
            val pinned = state.chats.filter { it.pinned }
            if (pinned.isNotEmpty()) {
                item {
                    Text(
                        text = "PINNED THREADS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = RadiantAmber
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(pinned, key = { "pinned_${it.id}" }) { chat ->
                    TimelineChatCard(
                        chat = chat,
                        onClick = { onSelectChat(chat.id) },
                        onTogglePin = { viewModel.togglePin(chat.id, chat.pinned) }
                    )
                }
            }

            // Group Recent Chats
            val recent = state.chats.filter { !it.pinned }
            if (recent.isNotEmpty()) {
                item {
                    Text(
                        text = "ACTIVE RECENT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = TextTertiary
                        ),
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                items(recent, key = { it.id }) { chat ->
                    val dismissState = rememberDismissState(
                        confirmValueChange = { value ->
                            if (value == DismissValue.DismissedToStart) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                viewModel.deleteChat(chat.id)
                                true
                            } else false
                        }
                    )
                    SwipeToDismiss(
                        state = dismissState,
                        directions = setOf(DismissDirection.EndToStart),
                        background = {
                            val dismissShape = RoundedCornerShape(10.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .clip(dismissShape)
                                    .background(CrimsonFlare.copy(alpha = 0.85f))
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete conversation",
                                    tint = TextPrimary
                                )
                            }
                        },
                        dismissContent = {
                            TimelineChatCard(
                                chat = chat,
                                onClick = { onSelectChat(chat.id) },
                                onTogglePin = { viewModel.togglePin(chat.id, chat.pinned) }
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Live Session Telemetry Footer ─────────────────────────────
        val footerShape = RoundedCornerShape(12.dp)
        Surface(
            shape = footerShape,
            color = SurfaceBase,
            modifier = Modifier
                .fillMaxWidth()
                .specularBorder(footerShape)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(EmeraldPulse)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Z.AI HANDSHAKE ACTIVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = EmeraldPulse
                        )
                    )
                    Text(
                        text = "Latency: 42ms • TLS 1.3",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextTertiary,
                            fontSize = 10.sp
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = EmeraldPulse,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TimelineChatCard(
    chat: Chat,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(10.dp)
    val formattedDate = remember(chat.updatedAt) {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        sdf.format(Date(chat.updatedAt))
    }

    Surface(
        shape = shape,
        color = SurfaceBase,
        modifier = Modifier
            .fillMaxWidth()
            .specularBorder(shape)
            .tactilePress {
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title.ifBlank { "Untitled thread" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                )
            }

            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onTogglePin()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = if (chat.pinned) "Unpin" else "Pin",
                    tint = if (chat.pinned) RadiantAmber else TextTertiary.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
