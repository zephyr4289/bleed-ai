package com.zai.chat.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Custom Moonshot/Kimi Spring Motion Specifications.
 * Zero default linear/cubic tweens; everything is spring physics.
 */
object ZaiMotion {
    // Flowy, bouncy spring for drawer and modal interactions
    val FluidSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    // Snappy spring for button morphs and state switches
    val MorphSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // Gentle spring for thinking expansion panel
    val PanelExpandSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = 250f
    )
}
