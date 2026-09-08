package com.zai.chat.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zai.chat.ui.theme.BleedMotion
import com.zai.chat.ui.theme.SpecularGradientBrush

/**
 * Zero-dependency Compose modifier providing tactile scale-down press physics and directional
 * specular top borders.
 */
fun Modifier.tactilePress(
    targetScale: Float = 0.965f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1.0f,
        animationSpec = BleedMotion.PressScaleSpring,
        label = "tactile_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    val upOrCancel = waitForUpOrCancellation()
                    isPressed = false
                    if (upOrCancel != null) {
                        onClick?.invoke()
                    }
                }
            }
        }
}

fun Modifier.specularBorder(
    shape: Shape = RoundedCornerShape(16.dp),
    strokeWidth: Dp = 1.dp
): Modifier = this.border(
    width = strokeWidth,
    brush = SpecularGradientBrush,
    shape = shape
)
