package com.zai.chat.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WrapText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.ui.theme.EmeraldPulse
import com.zai.chat.ui.theme.SurfaceRaised
import com.zai.chat.ui.theme.SyntaxBackground
import com.zai.chat.ui.theme.TextSecondary
import com.zai.chat.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Terminal-grade specular code frame with line wrapping, active line numbering, and vector animated
 * copy validation.
 */
@Composable
fun CodeBlockView(
    code: String,
    language: String = "kotlin",
    onInsertToInput: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    var wrapLines by remember { mutableStateOf(false) }
    var showLineNumbers by remember { mutableStateOf(true) }

    val highlightedText = remember(code, language) {
        SyntaxHighlighter.highlight(code, language)
    }
    val lines = remember(code) { code.lines() }
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SyntaxBackground)
            .specularBorder(shape)
    ) {
        // Specular Top Console Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceRaised)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = language.ifBlank { "TEXT" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Toggle Line Wrap
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        wrapLines = !wrapLines
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WrapText,
                        contentDescription = "Toggle wrap",
                        tint = if (wrapLines) EmeraldPulse else TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Toggle Line Numbers
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        showLineNumbers = !showLineNumbers
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FormatLineSpacing,
                        contentDescription = "Line numbers",
                        tint = if (showLineNumbers) EmeraldPulse else TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Copy Action Button
                IconButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Source Code", code))
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        isCopied = true
                        scope.launch {
                            delay(2200)
                            isCopied = false
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    AnimatedContent(
                        targetState = isCopied,
                        label = "copy_icon_transition"
                    ) { copied ->
                        if (copied) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Copied",
                                tint = EmeraldPulse,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy code",
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Code Editor Body
        val horizontalScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            if (showLineNumbers) {
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    lines.indices.forEach { index ->
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextTertiary.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (wrapLines) Modifier.padding(end = 12.dp)
                        else Modifier.horizontalScroll(horizontalScrollState).padding(end = 12.dp)
                    )
            ) {
                Text(
                    text = highlightedText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}
