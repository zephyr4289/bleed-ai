package com.zai.chat.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize

/**
 * Bleed-AI Continuous Spring Motion Specifications.
 * Zero default linear/cubic tweens; every interaction is driven by continuous spring physics.
 */
object BleedMotion {
    val PressScaleSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = 600f
    )

    val FluidNavigationSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = 300f
    )

    val PanelExpandSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.88f,
        stiffness = 220f
    )

    val PanelExpandIntSizeSpring: SpringSpec<IntSize> = spring(
        dampingRatio = 0.88f,
        stiffness = 220f
    )

    val CheckmarkPopSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 500f
    )

    val ColorMorphSpring: SpringSpec<Color> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

object ZaiMotion {
    val FluidSpring: SpringSpec<Float> = BleedMotion.FluidNavigationSpring
    val MorphSpring: SpringSpec<Float> = BleedMotion.PressScaleSpring
    val ColorMorphSpring: SpringSpec<Color> = BleedMotion.ColorMorphSpring
    val PanelExpandSpring: SpringSpec<IntSize> = BleedMotion.PanelExpandIntSizeSpring
}

