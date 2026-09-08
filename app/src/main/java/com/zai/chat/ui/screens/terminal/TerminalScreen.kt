package com.zai.chat.ui.screens.terminal

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.terminal.LogEntry
import com.zai.chat.terminal.LogLevel
import com.zai.chat.ui.components.specularBorder
import com.zai.chat.ui.components.tactilePress
import com.zai.chat.ui.theme.BorderAmbient
import com.zai.chat.ui.theme.CanvasPureBlack
import com.zai.chat.ui.theme.CrimsonFlare
import com.zai.chat.ui.theme.EmeraldPulse
import com.zai.chat.ui.theme.EmeraldPulseGlow
import com.zai.chat.ui.theme.GlassIslandBackground
import com.zai.chat.ui.theme.MidnightObsidian
import com.zai.chat.ui.theme.QuantumCyan
import com.zai.chat.ui.theme.RadiantAmber
import com.zai.chat.ui.theme.SurfaceActive
import com.zai.chat.ui.theme.SurfaceBase
import com.zai.chat.ui.theme.SurfaceRaised
import com.zai.chat.ui.theme.TextPrimary
import com.zai.chat.ui.theme.TextSecondary
import com.zai.chat.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLogging by viewModel.isLogging.collectAsState()
    val filteredLogs by viewModel.filteredLogs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedLevel by viewModel.selectedLevel.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val autoScroll by viewModel.autoScroll.collectAsState()

    val context = LocalContext.current
    val view = LocalView.current
    val listState = rememberLazyListState()

    // Handle toast messages (e.g. copied or saved to downloads)
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Auto-scroll to bottom on new logs
    LaunchedEffect(filteredLogs.size, autoScroll) {
        if (autoScroll && filteredLogs.isNotEmpty()) {
            listState.scrollToItem(filteredLogs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isLogging) EmeraldPulse else TextTertiary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "SYSTEM LOGCAT TERMINAL",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = TextPrimary
                                )
                            )
                        }
                        Text(
                            text = if (isLogging) "Live capture active • ${filteredLogs.size} lines" else "Standby (0% CPU/RAM overhead)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isLogging) EmeraldPulse else TextTertiary,
                                fontSize = 10.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (isLogging) "REC" else "OFF",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isLogging) CrimsonFlare else TextTertiary
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Switch(
                            checked = isLogging,
                            onCheckedChange = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                viewModel.toggleLogging(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CanvasPureBlack,
                                checkedTrackColor = EmeraldPulse,
                                uncheckedThumbColor = TextTertiary,
                                uncheckedTrackColor = SurfaceActive
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MidnightObsidian
                )
            )
        },
        containerColor = CanvasPureBlack,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Controls & Actions Toolbar ──────────────────────────────
            Surface(
                color = SurfaceBase,
                modifier = Modifier
                    .fillMaxWidth()
                    .specularBorder(RoundedCornerShape(0.dp))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Search Filter
                    val searchShape = RoundedCornerShape(8.dp)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = {
                            Text(
                                "Filter logcat (regex, tag, message)...",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = TextTertiary
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = searchShape,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CanvasPureBlack,
                            unfocusedContainerColor = CanvasPureBlack,
                            focusedBorderColor = QuantumCyan,
                            unfocusedBorderColor = BorderAmbient
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Log Levels & Quick Tags Horizontal Scroll
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Level Filter Chips
                        LevelFilterChip("ALL", selected = selectedLevel == null) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.setSelectedLevel(null)
                        }
                        LevelFilterChip("V", selected = selectedLevel == LogLevel.VERBOSE, color = TextTertiary) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.setSelectedLevel(LogLevel.VERBOSE)
                        }
                        LevelFilterChip("D", selected = selectedLevel == LogLevel.DEBUG, color = QuantumCyan) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.setSelectedLevel(LogLevel.DEBUG)
                        }
                        LevelFilterChip("I", selected = selectedLevel == LogLevel.INFO, color = EmeraldPulse) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.setSelectedLevel(LogLevel.INFO)
                        }
                        LevelFilterChip("W", selected = selectedLevel == LogLevel.WARN, color = RadiantAmber) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.setSelectedLevel(LogLevel.WARN)
                        }
                        LevelFilterChip("E", selected = selectedLevel == LogLevel.ERROR, color = CrimsonFlare) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.setSelectedLevel(LogLevel.ERROR)
                        }

                        Spacer(Modifier.width(6.dp))

                        // Quick Tag Chips
                        TagChip("Transport", tag = "BleedAI-Transport", selected = selectedTag == "BleedAI-Transport") {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.setSelectedTag(if (selectedTag == "BleedAI-Transport") null else "BleedAI-Transport")
                        }
                        TagChip("WebView JS", tag = "BleedAI-WebView", selected = selectedTag == "BleedAI-WebView") {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.setSelectedTag(if (selectedTag == "BleedAI-WebView") null else "BleedAI-WebView")
                        }
                        TagChip("Chat VM", tag = "BleedAI-Chat", selected = selectedTag == "BleedAI-Chat") {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.setSelectedTag(if (selectedTag == "BleedAI-Chat") null else "BleedAI-Chat")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Action Buttons Row: Copy, Download, Clear, AutoScroll
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Copy Button
                        ActionButton(
                            icon = Icons.Rounded.ContentCopy,
                            label = "Copy",
                            color = QuantumCyan,
                            modifier = Modifier.weight(1f)
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.copyLogs()
                        }

                        // Download Button -> Direct to phone Downloads folder
                        ActionButton(
                            icon = Icons.Rounded.Download,
                            label = "Download",
                            color = EmeraldPulse,
                            modifier = Modifier.weight(1.3f)
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            viewModel.exportLogs()
                        }

                        // Clear Button
                        ActionButton(
                            icon = Icons.Rounded.DeleteOutline,
                            label = "Clear",
                            color = CrimsonFlare,
                            modifier = Modifier.weight(1f)
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.clearLogs()
                        }

                        // Auto-scroll toggle
                        val autoScrollShape = RoundedCornerShape(8.dp)
                        Surface(
                            shape = autoScrollShape,
                            color = if (autoScroll) QuantumCyan.copy(alpha = 0.2f) else CanvasPureBlack,
                            modifier = Modifier
                                .height(34.dp)
                                .specularBorder(autoScrollShape)
                                .tactilePress {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.toggleAutoScroll()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Auto scroll",
                                    tint = if (autoScroll) QuantumCyan else TextTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Auto",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (autoScroll) QuantumCyan else TextTertiary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── Live Terminal Console Output ────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CanvasPureBlack)
            ) {
                if (!isLogging && filteredLogs.isEmpty()) {
                    // Zero overhead standby splash
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = EmeraldPulse,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "0% OVERHEAD LOGCAT STANDBY",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Zero CPU & RAM consumed in background. Toggle recording in top-right or tap Start to read live adb logcat output.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextTertiary,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                viewModel.toggleLogging(true)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPulse),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.specularBorder(RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start Live Logcat", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Awaiting logcat messages matching active filters...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = TextTertiary
                            )
                        )
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(filteredLogs, key = { it.id }) { entry ->
                                LogcatLineItem(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogcatLineItem(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.VERBOSE -> TextTertiary
        LogLevel.DEBUG -> QuantumCyan
        LogLevel.INFO -> EmeraldPulse
        LogLevel.WARN -> RadiantAmber
        LogLevel.ERROR, LogLevel.ASSERT -> CrimsonFlare
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (entry.timestamp.isNotEmpty()) {
            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextTertiary
                ),
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        Text(
            text = "[${entry.level.shortName}]",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = levelColor
            ),
            modifier = Modifier.padding(end = 4.dp)
        )

        if (entry.tag.isNotEmpty() && entry.tag != "System") {
            Text(
                text = "${entry.tag}:",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = if (entry.tag.startsWith("BleedAI")) QuantumCyan else TextSecondary
                ),
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = if (entry.level == LogLevel.ERROR) CrimsonFlare else TextPrimary
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LevelFilterChip(
    label: String,
    selected: Boolean,
    color: Color = QuantumCyan,
    onClick: () -> Unit
) {
    val chipShape = RoundedCornerShape(6.dp)
    Surface(
        shape = chipShape,
        color = if (selected) color.copy(alpha = 0.22f) else CanvasPureBlack,
        modifier = Modifier
            .specularBorder(chipShape)
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) color else TextTertiary
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TagChip(
    label: String,
    tag: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val chipShape = RoundedCornerShape(6.dp)
    Surface(
        shape = chipShape,
        color = if (selected) EmeraldPulseGlow else CanvasPureBlack,
        modifier = Modifier
            .specularBorder(chipShape)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = EmeraldPulse, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) EmeraldPulse else TextSecondary
                )
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = color.copy(alpha = 0.12f),
        modifier = modifier
            .height(34.dp)
            .specularBorder(shape)
            .tactilePress { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
