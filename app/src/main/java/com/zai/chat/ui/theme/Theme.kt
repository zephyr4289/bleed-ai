package com.zai.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        "LIGHT" -> false
        "DARK", "OLED" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (dark) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalSpacing provides ZaiSpacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZaiTypography,
            content = content
        )
    }
}
