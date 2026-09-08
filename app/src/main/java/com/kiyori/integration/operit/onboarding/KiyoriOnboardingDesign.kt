package com.kiyori.integration.operit.onboarding

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal data class KiyoriOnboardingColors(
    val background: Color,
    val surface: Color,
    val info: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryWeak: Color,
    val success: Color,
    val warning: Color,
    val tones: List<KiyoriOnboardingToneColors>,
)

@Immutable
internal data class KiyoriOnboardingToneColors(
    val foreground: Color,
    val background: Color,
)

internal object KiyoriOnboardingMetrics {
    const val PagePadding = 20
    const val TopBarHeight = 48
    const val ProgressHeight = 4
    const val ProgressGap = 6
    const val MapHeight = 104
    const val MapRadius = 18
    const val CardRadius = 16
    const val CardMinHeight = 122
    const val CardPadding = 12
    const val GridGap = 10
    const val IconBox = 36
    const val IconSize = 20
    const val PrimaryButtonHeight = 50
    const val PrimaryButtonRadius = 15
    const val SecondaryButtonHeight = 38
    const val BottomScrimHeight = 18
}

@Composable
internal fun currentKiyoriOnboardingColors(): KiyoriOnboardingColors =
    if (isSystemInDarkTheme()) {
        KiyoriOnboardingColors(
            background = Color(0xFF0E1116),
            surface = Color(0xFF171B22),
            info = Color(0xFF152033),
            onSurface = Color(0xFFE8EDF4),
            onSurfaceVariant = Color(0xFF98A2B3),
            outline = Color(0xFF252B35),
            primary = Color(0xFF5E8DFF),
            onPrimary = Color(0xFF0B1220),
            primaryWeak = Color(0xFF1B2740),
            success = Color(0xFF43C08C),
            warning = Color(0xFFE0A44A),
            tones =
                listOf(
                    KiyoriOnboardingToneColors(Color(0xFF7BA4FF), Color(0xFF17233A)),
                    KiyoriOnboardingToneColors(Color(0xFFA995FF), Color(0xFF221E3A)),
                    KiyoriOnboardingToneColors(Color(0xFF4FC392), Color(0xFF122A21)),
                    KiyoriOnboardingToneColors(Color(0xFFE0A44A), Color(0xFF2C2314)),
                ),
        )
    } else {
        KiyoriOnboardingColors(
            background = Color(0xFFF3F5F8),
            surface = Color(0xFFFFFFFF),
            info = Color(0xFFE9F0FE),
            onSurface = Color(0xFF0F172A),
            onSurfaceVariant = Color(0xFF667085),
            outline = Color(0xFFE5E9F0),
            primary = Color(0xFF2F6BFF),
            onPrimary = Color(0xFFFFFFFF),
            primaryWeak = Color(0xFFE8F0FF),
            success = Color(0xFF12855A),
            warning = Color(0xFFB4700A),
            tones =
                listOf(
                    KiyoriOnboardingToneColors(Color(0xFF2F6BFF), Color(0xFFE8F0FF)),
                    KiyoriOnboardingToneColors(Color(0xFF6B4FE0), Color(0xFFEDE9FE)),
                    KiyoriOnboardingToneColors(Color(0xFF12855A), Color(0xFFE2F4EC)),
                    KiyoriOnboardingToneColors(Color(0xFFB4700A), Color(0xFFFBEFD8)),
                ),
        )
    }

@Composable
internal fun KiyoriOnboardingTheme(content: @Composable () -> Unit) {
    val colors = currentKiyoriOnboardingColors()
    MaterialTheme(
        colorScheme =
            MaterialTheme.colorScheme.copy(
                primary = colors.primary,
                onPrimary = colors.onPrimary,
                primaryContainer = colors.primaryWeak,
                onPrimaryContainer = colors.primary,
                background = colors.background,
                onBackground = colors.onSurface,
                surface = colors.surface,
                onSurface = colors.onSurface,
                surfaceVariant = colors.outline,
                onSurfaceVariant = colors.onSurfaceVariant,
                outline = colors.outline,
                outlineVariant = colors.outline,
                surfaceContainerLowest = colors.surface,
                surfaceContainerLow = colors.surface,
                surfaceContainer = colors.surface,
                surfaceContainerHigh = colors.info,
                surfaceContainerHighest = colors.outline,
            ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
