package com.zai.chat.ui.screens.session

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.data.local.preferences.JwtClaims
import com.zai.chat.network.auth.CustomTabAuthHelper
import com.zai.chat.ui.theme.ClaudePeach
import com.zai.chat.ui.theme.KimiCyan
import com.zai.chat.ui.theme.ObsidianBase
import com.zai.chat.ui.theme.SurfaceContainerDark
import com.zai.chat.ui.theme.SurfaceContainerHighDark
import com.zai.chat.ui.theme.TrueBlack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSessionScreen(
    viewModel: SessionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Embedded Protected WebView Viewport
    if (state.isWebViewActive) {
        SafeWebViewContainer(
            mode = state.mode,
            onTokenExtracted = { token ->
                viewModel.handleTokenDiscoveredFromWeb(token)
            },
            onDismiss = { viewModel.onEvent(SessionUiEvent.DismissWebView) },
            onRendererCrashed = { viewModel.handleRendererCrash() },
            onMissingWebView = { viewModel.handleMissingWebView() }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session & Authorization", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TrueBlack)
            )
        },
        containerColor = TrueBlack,
        modifier = modifier.fillMaxSize().imePadding()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Notice Banner
            state.userFacingNotice?.let { notice ->
                Surface(
                    color = SurfaceContainerHighDark,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.onEvent(SessionUiEvent.DismissNotice) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss notice")
                        }
                    }
                }
            }

            // Crash Notification Recovery
            if (state.webViewCrashOccurred) {
                Surface(
                    color = ClaudePeach.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ClaudePeach),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = ClaudePeach
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Chromium Renderer Crash Caught",
                                fontWeight = FontWeight.Bold,
                                color = ClaudePeach
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "The isolated web process terminated. Host app preserved successfully without an OS crash. Use Chrome Custom Tabs or manual token entry below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.onEvent(SessionUiEvent.AcknowledgeRendererCrash) },
                            colors = ButtonDefaults.buttonColors(containerColor = ClaudePeach)
                        ) {
                            Text("Acknowledge", color = Color.Black)
                        }
                    }
                }
            }

            // SECTION 1: Active Token Inspector
            Text(
                "ACTIVE JWT STATUS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            ActiveClaimsCard(
                claims = state.currentClaims,
                onCopyToken = {
                    state.currentClaims?.rawToken?.let { token ->
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Zai Token", token))
                        Toast.makeText(context, "Token copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 2: Connection Testing
            Text(
                "CONNECTIVITY VERIFICATION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.onEvent(SessionUiEvent.VerifyCurrentSession) },
                    enabled = state.currentClaims != null && state.verificationState !is SessionVerificationState.Testing,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerDark),
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.verificationState is SessionVerificationState.Testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = ClaudePeach,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying...")
                    } else {
                        Icon(
                            Icons.Rounded.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test API Ping")
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.onEvent(SessionUiEvent.PurgeSession) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Token")
                }
            }

            when (val v = state.verificationState) {
                is SessionVerificationState.Valid -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = v.message, color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodyMedium)
                }
                is SessionVerificationState.Invalid -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = v.error, color = ClaudePeach, style = MaterialTheme.typography.bodyMedium)
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 3: Sign In Actions (Web / Chrome Custom Tab)
            Text(
                "RE-AUTHENTICATION PROTOCOLS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = SurfaceContainerDark,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Option A: Embedded Sandboxed Web",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Spawns an in-app WebView. Safe from OS termination. Intercepts tokens automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.onEvent(SessionUiEvent.SetMode(SessionMode.ActiveManagement))
                            viewModel.onEvent(SessionUiEvent.LaunchWebView)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClaudePeach),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Login,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Sandboxed WebView", color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Option B: External Browser (Chrome Custom Tabs)",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Recommended if Google OAuth 403 or Cloudflare Turnstile blocks the WebView.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { CustomTabAuthHelper.launch(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Launch Chrome Custom Tab")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 4: Direct Manual Token Injection
            Text(
                "MANUAL JWT INJECTION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.manualTokenInput,
                onValueChange = { viewModel.onEvent(SessionUiEvent.UpdateManualToken(it)) },
                label = { Text("Paste Bearer Token or JWT") },
                placeholder = { Text("eyJhbGciOiJIUzI1NiIsIn...") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClaudePeach,
                    unfocusedBorderColor = SurfaceContainerHighDark,
                    focusedContainerColor = ObsidianBase,
                    unfocusedContainerColor = ObsidianBase
                ),
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { viewModel.onEvent(SessionUiEvent.ApplyManualToken) },
                enabled = state.manualTokenInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerDark),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Validate & Save Token")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ActiveClaimsCard(
    claims: JwtClaims?,
    onCopyToken: () -> Unit
) {
    Surface(
        color = SurfaceContainerDark,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (claims == null) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = ClaudePeach
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "No Active Session Stored",
                        fontWeight = FontWeight.Medium,
                        color = Color.LightGray
                    )
                }
            }
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (claims.isExpired) Color.Red else Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (claims.isExpired) "EXPIRED SESSION" else "ACTIVE SESSION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (claims.isExpired) Color.Red else Color(0xFF4CAF50)
                            )
                        )
                    }

                    IconButton(onClick = onCopyToken, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Copy Token",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                SelectionContainer {
                    Column {
                        Text(
                            text = "Subject: ${claims.subject ?: "Unknown"}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Role: ${claims.role ?: "Standard User"}",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = claims.humanReadableExpiry,
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray)
                        )
                    }
                }
            }
        }
    }
}
