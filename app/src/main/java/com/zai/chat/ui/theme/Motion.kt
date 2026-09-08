package com.zai.chat.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize

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

    val ColorMorphSpring: SpringSpec<Color> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // Gentle spring for thinking expansion panel
    val PanelExpandSpring: SpringSpec<IntSize> = spring(
        dampingRatio = 0.85f,
        stiffness = 250f
    )
}
