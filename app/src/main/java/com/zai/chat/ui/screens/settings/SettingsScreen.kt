package com.zai.chat.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.zai.chat.config.ZaiConfig
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onManageSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()
    val enterIsSend by viewModel.enterIsSend.collectAsState()
    val hasSession by viewModel.hasSession.collectAsState()

    var showClearSessionDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    var sliderValue by remember { mutableFloatStateOf(fontScale) }
    LaunchedEffect(fontScale) {
        sliderValue = fontScale
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Appearance ───────────────────────────────────────────────
            SettingsSection(title = "Appearance") {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                val themes = listOf(
                    "OLED" to "OLED (Pure Black)",
                    "DARK" to "Dark (Obsidian)",
                    "LIGHT" to "Light",
                    "SYSTEM" to "System default"
                )

                themes.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setTheme(mode) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = { viewModel.setTheme(mode) }
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Font Scale Slider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Font Scale",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f×", sliderValue),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { newScale ->
                        sliderValue = newScale
                        viewModel.setFontScale(newScale)
                    },
                    valueRange = 0.75f..1.50f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ── Behavior ─────────────────────────────────────────────────
            SettingsSection(title = "Behavior") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setEnterIsSend(!enterIsSend) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Enter is Send",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "Pressing Enter sends the message immediately; Shift+Enter inserts a new line.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enterIsSend,
                        onCheckedChange = viewModel::setEnterIsSend
                    )
                }
            }

            // ── Session ──────────────────────────────────────────────────
            SettingsSection(title = "Session") {
                val context = LocalContext.current
                val clipboardManager = LocalClipboardManager.current

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Session Status",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        if (hasSession) {
                            Text(
                                text = "Long press badge to copy token",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        color = if (hasSession) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = {
                                val token = viewModel.getStoredToken()
                                if (token != null) {
                                    clipboardManager.setText(AnnotatedString(token))
                                    Toast.makeText(context, "Token copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    ) {
                        Text(
                            text = if (hasSession) "Active" else "Missing",
                            color = if (hasSession) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onManageSession,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Manage Session")
                    }

                    OutlinedButton(
                        onClick = { showClearSessionDialog = true },
                        enabled = hasSession,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Session")
                    }
                }

                if (hasSession) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = {
                                val token = viewModel.getStoredToken()
                                if (token != null) {
                                    clipboardManager.setText(AnnotatedString(token))
                                    Toast.makeText(context, "Token copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copy Token", style = MaterialTheme.typography.labelMedium)
                        }

                        TextButton(
                            onClick = {
                                val cookies = CookieManager.getInstance().getCookie(ZaiConfig.BASE_URL)
                                if (!cookies.isNullOrEmpty()) {
                                    clipboardManager.setText(AnnotatedString(cookies))
                                    Toast.makeText(context, "Cookies copied to clipboard", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No cookies found", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copy Cookies", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // ── Storage ──────────────────────────────────────────────────
            SettingsSection(title = "Storage") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Local Cache",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Wipes cached local chats and messages from device storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showClearCacheDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear Local Cache")
                    }
                }
            }

            // ── About ────────────────────────────────────────────────────
            SettingsSection(title = "About") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Z.AI Native Client",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "1.0.0 · personal build",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────
    if (showClearSessionDialog) {
        AlertDialog(
            onDismissRequest = { showClearSessionDialog = false },
            title = { Text("Clear Session") },
            text = {
                Text("Are you sure you want to clear your stored session token? You will need to reconnect to send messages.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearSessionDialog = false
                        viewModel.clearSession()
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSessionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Local Cache") },
            text = {
                Text("Local cached chats and messages will be removed from this device. Server data is untouched and chats will re-sync when online.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearLocalCache()
                    }
                ) {
                    Text("Clear Cache", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}
