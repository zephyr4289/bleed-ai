package com.zai.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkColorScheme = darkColorScheme(
    primary = RadiantAmber,
    onPrimary = CanvasPureBlack,
    primaryContainer = RadiantAmberContainer,
    onPrimaryContainer = ClaudePeachOnContainerDark,
    secondary = QuantumCyan,
    onSecondary = CanvasPureBlack,
    secondaryContainer = QuantumCyanContainer,
    surface = SurfaceBase,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    outline = BorderAmbient,
    background = MidnightObsidian,
    onBackground = TextPrimary,
    error = CrimsonFlare,
    errorContainer = CrimsonFlareGlow,
    onError = TextPrimary,
    onErrorContainer = CrimsonFlare
)

private val LightColorScheme = lightColorScheme(
    primary = ClaudePeachDark,
    onPrimary = PureWhite,
    primaryContainer = ClaudePeachContainerLight,
    onPrimaryContainer = ClaudePeachOnContainerLight,
    secondary = QuantumCyan,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderSubtleLight,
    background = PureWhite,
    onBackground = TextPrimaryLight,
    error = CrimsonFlare
)

@Composable
fun ZaiTheme(
    themeMode: String = "OLED",
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "LIGHT" -> false
        "DARK", "OLED" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) {
        val bg = if (themeMode == "OLED") CanvasPureBlack else MidnightObsidian
        DarkColorScheme.copy(background = bg)
    } else {
        LightColorScheme
    }

    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalSpacing provides ZaiSpacing(),
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * fontScale
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BleedAiTypography,
            content = content
        )
    }
}

