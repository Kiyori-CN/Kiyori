package com.kiyori.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Cross-surface UI tokens derived from the active Material theme.
 *
 * Settings already defines the product's visual hierarchy. These tokens expose that hierarchy to
 * AI, browser and media surfaces without introducing another color source or changing navigation
 * and state ownership.
 */
@Immutable
data class KiyoriUiColors(
    val pageBackground: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val subtleSurface: Color,
    val divider: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccent: Color,
    val error: Color,
    val errorContainer: Color,
    val onError: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
)

@Immutable
data class KiyoriUiTokens(
    val colors: KiyoriUiColors,
    val touchTarget: Dp = 40.dp,
    val iconSize: Dp = 20.dp,
    val compactIconSize: Dp = 18.dp,
)

/** Product-wide shapes. Keep controls compact and reserve larger radii for genuinely framed content. */
object KiyoriUiShapes {
    val card = RoundedCornerShape(16.dp)
    val control = RoundedCornerShape(12.dp)
    val field = RoundedCornerShape(14.dp)
    val dialog = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}

/** Material component shape defaults shared by every root theme, including legacy utility screens. */
val KiyoriMaterialShapes =
    Shapes(
        extraSmall = KiyoriUiShapes.control,
        small = KiyoriUiShapes.control,
        medium = KiyoriUiShapes.field,
        large = KiyoriUiShapes.card,
        extraLarge = KiyoriUiShapes.dialog,
    )

@Composable
fun rememberKiyoriUiTokens(): KiyoriUiTokens {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val success = if (isDark) Color(0xFF8CCDB3) else Color(0xFF2C7A63)
    val successContainer = if (isDark) Color(0xFF213A32) else Color(0xFFE6F2EE)
    val warning = if (isDark) Color(0xFFFFC27A) else Color(0xFFAA642A)
    val warningContainer = if (isDark) Color(0xFF433425) else Color(0xFFF8EEE4)
    return KiyoriUiTokens(
        colors =
            KiyoriUiColors(
                pageBackground = scheme.background,
                surface = scheme.surface,
                elevatedSurface = scheme.surfaceContainer,
                subtleSurface = scheme.surfaceContainerLow,
                divider = scheme.outlineVariant,
                primaryText = scheme.onSurface,
                secondaryText = scheme.onSurfaceVariant,
                mutedText = scheme.onSurfaceVariant.copy(alpha = 0.72f),
                accent = scheme.primary,
                accentContainer = scheme.primaryContainer,
                onAccent = scheme.onPrimary,
                error = scheme.error,
                errorContainer = scheme.errorContainer,
                onError = scheme.onError,
                success = success,
                successContainer = successContainer,
                warning = warning,
                warningContainer = warningContainer,
            ),
    )
}
