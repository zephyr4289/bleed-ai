package com.zai.chat.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.ui.theme.BleedMotion
import com.zai.chat.ui.theme.RadiantAmber
import com.zai.chat.ui.theme.SurfaceBase
import com.zai.chat.ui.theme.SurfaceRaised
import com.zai.chat.ui.theme.TextPrimary
import com.zai.chat.ui.theme.TextSecondary
import com.zai.chat.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * Dynamic neural thought inspector with continuous beacon pulse, stopwatch tracker, and zero emoji glyphs.
 */
@Composable
fun ThinkingBlock(
    reasoningText: String,
    isStreaming: Boolean = false,
    isStreamingReasoning: Boolean = isStreaming,
    durationSeconds: Float = 0f,
    modifier: Modifier = Modifier
) {
    val activeStreaming = isStreaming || isStreamingReasoning
    var isExpanded by remember { mutableStateOf(activeStreaming) }
    val view = LocalView.current
    var elapsedTimeSeconds by remember { mutableFloatStateOf(durationSeconds) }

    LaunchedEffect(activeStreaming) {
        if (activeStreaming) {
            isExpanded = true
            val startTime = System.currentTimeMillis() - (durationSeconds * 1000).toLong()
            while (activeStreaming) {
                elapsedTimeSeconds = (System.currentTimeMillis() - startTime) / 1000f
                delay(100)
            }
        }
    }

    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceBase)
            .specularBorder(shape)
            .animateContentSize(animationSpec = BleedMotion.PanelExpandIntSizeSpring)
    ) {
        // Quantum Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    isExpanded = !isExpanded
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Status Beacon
            if (activeStreaming) {
                PulsingCognitiveBeacon()
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(RadiantAmber)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.Rounded.Memory,
                contentDescription = null,
                tint = RadiantAmber,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (activeStreaming) "COGNITIVE SYNTHESIS IN PROGRESS" else "REASONING ARCHITECTURE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = if (activeStreaming) RadiantAmber else TextSecondary
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            // Duration Indicator
            if (elapsedTimeSeconds > 0.0f) {
                Text(
                    text = "${"%.1f".format(elapsedTimeSeconds)}s",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Icon(
                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = TextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }

        // Expandable Monospace Thought Stream
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .background(SurfaceRaised.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                val scrollState = rememberScrollState()
                Text(
                    text = reasoningText.ifBlank { "Initializing neural weights and synthesis tree…" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = TextSecondary
                    ),
                    modifier = Modifier.verticalScroll(scrollState)
                )
            }
        }
    }
}

@Composable
fun PulsingCognitiveBeacon() {
    val transition = rememberInfiniteTransition(label = "beacon_pulse")
    val scale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(10.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp * scale)
                .clip(CircleShape)
                .background(RadiantAmber.copy(alpha = alpha * 0.3f))
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(RadiantAmber)
        )
    }
}
