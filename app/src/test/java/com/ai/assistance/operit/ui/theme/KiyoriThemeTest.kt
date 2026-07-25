package com.ai.assistance.operit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriThemeTest {
    @Test
    fun `light palette uses the accessible Kiyori accent contract`() {
        with(KiyoriLightColorScheme) {
            assertEquals(Color(0xFF1E88E5), primary)
            assertEquals(Color(0xFF0A1929), onPrimary)
            assertEquals(Color(0xFFD8ECFF), primaryContainer)
            assertEquals(Color(0xFF12324A), onPrimaryContainer)
            assertEquals(Color(0xFF536D79), secondary)
            assertEquals(Color(0xFFE4EDF1), secondaryContainer)
            assertEquals(Color(0xFF20343D), onSecondaryContainer)
            assertEquals(Color(0xFFFFFFFF), background)
            assertEquals(Color(0xFF202124), onBackground)
            assertEquals(Color(0xFF5F6368), onSurfaceVariant)
            assertEquals(Color(0xFF9AA0A6), outline)
            assertEquals(Color(0xFFDADCE0), outlineVariant)
            assertEquals(Color(0xFFFAFAFA), surfaceContainerLow)
            assertEquals(Color(0xFFF7F7F7), surfaceContainer)
            assertEquals(Color(0xFFF1F3F4), surfaceContainerHigh)
            assertEquals(Color(0xFFE8EAED), surfaceContainerHighest)
            assertEquals(Color.Transparent, surfaceTint)
        }
    }

    @Test
    fun `dark palette keeps blue actions on layered neutral surfaces`() {
        with(KiyoriDarkColorScheme) {
            assertEquals(Color(0xFF90CAF9), primary)
            assertEquals(Color(0xFF0A2638), onPrimary)
            assertEquals(Color(0xFF0B5F94), primaryContainer)
            assertEquals(Color(0xFFD8ECFF), onPrimaryContainer)
            assertEquals(Color(0xFFB9CBD4), secondary)
            assertEquals(Color(0xFF374D57), secondaryContainer)
            assertEquals(Color(0xFF121212), background)
            assertEquals(Color(0xFFE8EAED), onBackground)
            assertEquals(Color(0xFF1A1A1A), surfaceContainerLow)
            assertEquals(Color(0xFF202124), surfaceContainer)
            assertEquals(Color(0xFF282A2D), surfaceContainerHigh)
            assertEquals(Color(0xFF303134), surfaceContainerHighest)
            assertEquals(Color(0xFFBDC1C6), onSurfaceVariant)
            assertEquals(Color.Transparent, surfaceTint)
        }
    }

    @Test
    fun `browser palettes retain the neutral chrome contract`() {
        with(KiyoriBrowserLightColorScheme) {
            assertEquals(Color(0xFF202124), primary)
            assertEquals(Color(0xFF5F6368), secondary)
            assertEquals(Color(0xFFFFFFFF), background)
            assertEquals(Color(0xFFE8EAED), primaryContainer)
        }
        with(KiyoriBrowserDarkColorScheme) {
            assertEquals(Color(0xFFF1F3F4), primary)
            assertEquals(Color(0xFFBDC1C6), secondary)
            assertEquals(Color(0xFF121212), background)
            assertEquals(Color(0xFF303134), primaryContainer)
        }
    }

    @Test
    fun `explicit custom colors do not tint neutral page surfaces`() {
        val customScheme =
            resolveThemeColorScheme(
                darkTheme = false,
                useCustomColors = true,
                customPrimaryColor = 0xFF146C43.toInt(),
                customSecondaryColor = 0xFF4B5F83.toInt(),
                onColorMode = UserPreferencesManager.ON_COLOR_MODE_AUTO,
            )

        assertEquals(Color(0xFF146C43), customScheme.primary)
        assertEquals(Color(0xFF4B5F83), customScheme.secondary)
        assertEquals(Color(0xFFFFFFFF), customScheme.background)
        assertEquals(Color(0xFFF7F7F7), customScheme.surfaceContainer)
        assertEquals(Color.Transparent, customScheme.surfaceTint)
    }

    @Test
    fun `default text pairs exceed WCAG normal text contrast`() {
        val pairs =
            listOf(
                KiyoriLightColorScheme.onBackground to KiyoriLightColorScheme.background,
                KiyoriLightColorScheme.onSurfaceVariant to KiyoriLightColorScheme.background,
                KiyoriLightColorScheme.onPrimary to KiyoriLightColorScheme.primary,
                KiyoriLightColorScheme.onPrimaryContainer to KiyoriLightColorScheme.primaryContainer,
                KiyoriDarkColorScheme.onBackground to KiyoriDarkColorScheme.background,
                KiyoriDarkColorScheme.onSurfaceVariant to KiyoriDarkColorScheme.background,
                KiyoriDarkColorScheme.onPrimary to KiyoriDarkColorScheme.primary,
                KiyoriDarkColorScheme.onPrimaryContainer to KiyoriDarkColorScheme.primaryContainer,
                KiyoriLightColorScheme.onSecondary to KiyoriLightColorScheme.secondary,
                KiyoriLightColorScheme.onSecondaryContainer to KiyoriLightColorScheme.secondaryContainer,
                KiyoriDarkColorScheme.onSecondary to KiyoriDarkColorScheme.secondary,
                KiyoriDarkColorScheme.onSecondaryContainer to KiyoriDarkColorScheme.secondaryContainer,
                KiyoriBrowserLightColorScheme.onPrimary to KiyoriBrowserLightColorScheme.primary,
                KiyoriBrowserDarkColorScheme.onPrimary to KiyoriBrowserDarkColorScheme.primary,
            )

        pairs.forEach { (foreground, background) ->
            assertTrue(
                "Contrast was ${contrastRatio(foreground, background)} for $foreground on $background",
                contrastRatio(foreground, background) >= 4.5,
            )
        }
    }

    @Test
    fun `light primary remains a visible non text accent on white`() {
        assertTrue(
            contrastRatio(KiyoriLightColorScheme.primary, KiyoriLightColorScheme.background) >= 3.0,
        )
    }

    @Test
    fun `all typography roles use neutral tracking`() {
        val styles =
            listOf(
                Typography.displayLarge,
                Typography.displayMedium,
                Typography.displaySmall,
                Typography.headlineLarge,
                Typography.headlineMedium,
                Typography.headlineSmall,
                Typography.titleLarge,
                Typography.titleMedium,
                Typography.titleSmall,
                Typography.bodyLarge,
                Typography.bodyMedium,
                Typography.bodySmall,
                Typography.labelLarge,
                Typography.labelMedium,
                Typography.labelSmall,
            )

        styles.forEach { style -> assertEquals(0.sp, style.letterSpacing) }
    }

    private fun contrastRatio(
        foreground: Color,
        background: Color,
    ): Double {
        val foregroundLuminance = relativeLuminance(foreground)
        val backgroundLuminance = relativeLuminance(background)
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red.toDouble()) +
            0.7152 * linearize(color.green.toDouble()) +
            0.0722 * linearize(color.blue.toDouble())

    private fun linearize(channel: Double): Double =
        if (channel <= 0.04045) {
            channel / 12.92
        } else {
            Math.pow((channel + 0.055) / 1.055, 2.4)
        }
}
