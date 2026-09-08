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
    primary = ClaudePeach,
    onPrimary = TrueBlack,
    surface = ObsidianBase,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderSubtleDark,
    background = TrueBlack,
    onBackground = TextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = ClaudePeach,
    onPrimary = PureWhite,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderSubtleLight,
    background = PureWhite,
    onBackground = TextPrimaryLight
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
        val bg = if (themeMode == "OLED") TrueBlack else ObsidianBase
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
            typography = ZaiTypography,
            content = content
        )
    }
}
