package com.zai.chat.ui.screens.settings

import android.view.HapticFeedbackConstants
import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.config.ZaiConfig
import com.zai.chat.ui.components.specularBorder
import com.zai.chat.ui.theme.BorderAmbient
import com.zai.chat.ui.theme.CanvasPureBlack
import com.zai.chat.ui.theme.CrimsonFlare
import com.zai.chat.ui.theme.CrimsonFlareGlow
import com.zai.chat.ui.theme.EmeraldPulse
import com.zai.chat.ui.theme.EmeraldPulseGlow
import com.zai.chat.ui.theme.QuantumCyan
import com.zai.chat.ui.theme.RadiantAmber
import com.zai.chat.ui.theme.SurfaceActive
import com.zai.chat.ui.theme.SurfaceBase
import com.zai.chat.ui.theme.SurfaceRaised
import com.zai.chat.ui.theme.TextPrimary
import com.zai.chat.ui.theme.TextSecondary
import com.zai.chat.ui.theme.TextTertiary
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
    val view = LocalView.current

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
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Appearance ───────────────────────────────────────────────
            SettingsSection(title = "APPEARANCE") {
                Text(
                    text = "Color Palette",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                val themes = listOf(
                    "OLED" to "OLED (Pure Black #000000)",
                    "DARK" to "Obsidian Midnight (#08080C)",
                    "LIGHT" to "Light",
                    "SYSTEM" to "System default"
                )

                themes.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.setTheme(mode)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.setTheme(mode)
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = QuantumCyan,
                                unselectedColor = TextTertiary
                            )
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (themeMode == mode) TextPrimary else TextSecondary,
                                fontWeight = if (themeMode == mode) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

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
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f×", sliderValue),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = QuantumCyan,
                            fontWeight = FontWeight.Bold
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
                    colors = SliderDefaults.colors(
                        thumbColor = QuantumCyan,
                        activeTrackColor = QuantumCyan,
                        inactiveTrackColor = SurfaceActive
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ── Behavior ─────────────────────────────────────────────────
            SettingsSection(title = "BEHAVIOR") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.setEnterIsSend(!enterIsSend)
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Enter is Send",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Pressing Enter sends the message immediately; Shift+Enter inserts a new line.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                    Switch(
                        checked = enterIsSend,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.setEnterIsSend(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CanvasPureBlack,
                            checkedTrackColor = QuantumCyan,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = SurfaceActive
                        )
                    )
                }
            }

            // ── Session ──────────────────────────────────────────────────
            SettingsSection(title = "SESSION & CREDENTIALS") {
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
                            text = "Authentication Status",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        if (hasSession) {
                            Text(
                                text = "Long press badge to copy bearer token",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary)
                            )
                        }
                    }

                    val badgeShape = RoundedCornerShape(12.dp)
                    Surface(
                        color = if (hasSession) EmeraldPulseGlow else CrimsonFlareGlow,
                        shape = badgeShape,
                        modifier = Modifier
                            .specularBorder(badgeShape)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    val token = viewModel.getStoredToken()
                                    if (token != null) {
                                        clipboardManager.setText(AnnotatedString(token))
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        Toast.makeText(context, "Token copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (hasSession) EmeraldPulse else CrimsonFlare)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (hasSession) "ACTIVE" else "MISSING",
                                color = if (hasSession) EmeraldPulse else CrimsonFlare,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onManageSession()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = QuantumCyan),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Manage Session", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showClearSessionDialog = true
                        },
                        enabled = hasSession,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonFlare),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Session", style = MaterialTheme.typography.labelMedium)
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
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    Toast.makeText(context, "Token copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copy Token", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }

                        TextButton(
                            onClick = {
                                val cookies = CookieManager.getInstance().getCookie(ZaiConfig.BASE_URL)
                                if (!cookies.isNullOrEmpty()) {
                                    clipboardManager.setText(AnnotatedString(cookies))
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    Toast.makeText(context, "Cookies copied to clipboard", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No cookies found", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copy Cookies", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Storage ──────────────────────────────────────────────────
            SettingsSection(title = "STORAGE & CACHE") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Local Cache",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Wipes cached local chats and messages from device Room database storage.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showClearCacheDialog = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonFlare)
                    ) {
                        Text("Clear Local Cache", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // ── About ────────────────────────────────────────────────────
            SettingsSection(title = "ABOUT") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Bleed-AI Flagship Client",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Version 1.0.0 • Quantum Architecture Build",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────
    if (showClearSessionDialog) {
        val dialogShape = RoundedCornerShape(18.dp)
        AlertDialog(
            onDismissRequest = { showClearSessionDialog = false },
            containerColor = SurfaceRaised,
            shape = dialogShape,
            modifier = Modifier.specularBorder(dialogShape),
            title = {
                Text(
                    "Clear Session",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            },
            text = {
                Text(
                    "Are you sure you want to clear your stored session token? You will need to reconnect to send messages.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        showClearSessionDialog = false
                        viewModel.clearSession()
                    }
                ) {
                    Text("Clear", color = CrimsonFlare)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSessionDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showClearCacheDialog) {
        val dialogShape = RoundedCornerShape(18.dp)
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            containerColor = SurfaceRaised,
            shape = dialogShape,
            modifier = Modifier.specularBorder(dialogShape),
            title = {
                Text(
                    "Clear Local Cache",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            },
            text = {
                Text(
                    "Local cached chats and messages will be removed from this device. Server data is untouched and chats will re-sync when online.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        showClearCacheDialog = false
                        viewModel.clearLocalCache()
                    }
                ) {
                    Text("Clear Cache", color = CrimsonFlare)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel", color = TextSecondary)
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
    val sectionShape = RoundedCornerShape(14.dp)
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                color = QuantumCyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            color = SurfaceBase,
            shape = sectionShape,
            modifier = Modifier
                .fillMaxWidth()
                .specularBorder(sectionShape)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}
