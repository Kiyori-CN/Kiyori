package com.kiyori.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Immutable
data class KiyoriSettingsColors(
    val pageBackground: Color,
    val cardBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedIcon: Color,
    val divider: Color,
    val accent: Color,
    val accentContent: Color,
    val disabledTrack: Color,
    val scrim: Color,
)

val LocalKiyoriSettingsColors =
    staticCompositionLocalOf<KiyoriSettingsColors> {
        error("KiyoriSettingsTheme is not available")
    }

private val LightSettingsColors =
    KiyoriSettingsColors(
        pageBackground = Color(0xFFF6F7F9),
        cardBackground = Color(0xFFFFFFFF),
        primaryText = Color(0xFF20242A),
        secondaryText = Color(0xFF6E747C),
        mutedIcon = Color(0xFF9AA2AC),
        divider = Color(0xFFE9EDF2),
        accent = Color(0xFF1E88E5),
        accentContent = Color(0xFFFFFFFF),
        disabledTrack = Color(0xFFD5DAE1),
        scrim = Color(0x73000000),
    )

private val DarkSettingsColors =
    KiyoriSettingsColors(
        pageBackground = Color(0xFF101215),
        cardBackground = Color(0xFF1B1F24),
        primaryText = Color(0xFFF2F5F8),
        secondaryText = Color(0xFFAAB2BC),
        mutedIcon = Color(0xFF7F8995),
        divider = Color(0xFF2A3037),
        accent = Color(0xFF90CAF9),
        accentContent = Color(0xFF0A2638),
        disabledTrack = Color(0xFF444B54),
        scrim = Color(0x99000000),
    )

@Composable
fun KiyoriSettingsTheme(content: @Composable () -> Unit) {
    val parentColorScheme = MaterialTheme.colorScheme
    val parentTypography = MaterialTheme.typography
    val parentShapes = MaterialTheme.shapes
    val isDark = parentColorScheme.background.luminance() < 0.5f
    val settingsColors = resolveKiyoriSettingsColors(isDark)
    val colorScheme =
        (if (isDark) KiyoriDarkColorScheme else KiyoriLightColorScheme).copy(
            background = settingsColors.pageBackground,
            onBackground = settingsColors.primaryText,
            surface = settingsColors.cardBackground,
            onSurface = settingsColors.primaryText,
            surfaceVariant =
                if (isDark) {
                    Color(0xFF242A31)
                } else {
                    Color(0xFFEDF1F5)
                },
            onSurfaceVariant = settingsColors.secondaryText,
            outline = settingsColors.mutedIcon,
            outlineVariant = settingsColors.divider,
            surfaceContainerLowest = settingsColors.cardBackground,
            surfaceContainerLow = settingsColors.cardBackground,
            surfaceContainer = settingsColors.cardBackground,
            surfaceContainerHigh =
                if (isDark) {
                    Color(0xFF22272D)
                } else {
                    Color(0xFFF2F4F7)
                },
            surfaceContainerHighest =
                if (isDark) {
                    Color(0xFF2A3037)
                } else {
                    Color(0xFFE9EDF2)
                },
        )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = parentTypography,
        shapes = parentShapes,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalKiyoriSettingsColors provides settingsColors,
            content = content,
        )
    }
}

internal fun resolveKiyoriSettingsColors(isDark: Boolean): KiyoriSettingsColors =
    if (isDark) DarkSettingsColors else LightSettingsColors
