package com.kiyori.design.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriDesignThemeTest {
    @Test
    fun `all typography roles use neutral tracking`() {
        val styles =
            listOf(
                KiyoriTypography.displayLarge,
                KiyoriTypography.displayMedium,
                KiyoriTypography.displaySmall,
                KiyoriTypography.headlineLarge,
                KiyoriTypography.headlineMedium,
                KiyoriTypography.headlineSmall,
                KiyoriTypography.titleLarge,
                KiyoriTypography.titleMedium,
                KiyoriTypography.titleSmall,
                KiyoriTypography.bodyLarge,
                KiyoriTypography.bodyMedium,
                KiyoriTypography.bodySmall,
                KiyoriTypography.labelLarge,
                KiyoriTypography.labelMedium,
                KiyoriTypography.labelSmall,
            )

        styles.forEach { style -> assertEquals(0.sp, style.letterSpacing) }
    }

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
    fun `theme resolver exposes only the fixed Kiyori light and dark palettes`() {
        assertEquals(KiyoriLightColorScheme, resolveKiyoriColorScheme(darkTheme = false))
        assertEquals(KiyoriDarkColorScheme, resolveKiyoriColorScheme(darkTheme = true))
    }

    @Test
    fun `settings palette keeps a neutral hierarchy in both application themes`() {
        with(resolveKiyoriSettingsColors(isDark = false)) {
            assertEquals(Color(0xFFF6F7F9), pageBackground)
            assertEquals(Color.White, cardBackground)
            assertEquals(Color(0xFF20242A), primaryText)
            assertEquals(Color(0xFF6E747C), secondaryText)
            assertEquals(Color(0xFFE9EDF2), divider)
            assertEquals(Color(0xFF1E88E5), accent)
        }
        with(resolveKiyoriSettingsColors(isDark = true)) {
            assertEquals(Color(0xFF101215), pageBackground)
            assertEquals(Color(0xFF1B1F24), cardBackground)
            assertEquals(Color(0xFFF2F5F8), primaryText)
            assertEquals(Color(0xFFAAB2BC), secondaryText)
            assertEquals(Color(0xFF2A3037), divider)
            assertEquals(Color(0xFF90CAF9), accent)
        }
    }

    @Test
    fun `application semantic icon tones stay colorful and theme aware`() {
        val lightPairs =
            KiyoriSemanticTone.entries.map { tone ->
                resolveKiyoriSemanticColors(tone, isDark = false)
            }
        val darkPairs =
            KiyoriSemanticTone.entries.map { tone ->
                resolveKiyoriSemanticColors(tone, isDark = true)
            }

        assertEquals(KiyoriSemanticTone.entries.size, lightPairs.map { it.icon }.toSet().size)
        assertEquals(KiyoriSemanticTone.entries.size, darkPairs.map { it.icon }.toSet().size)
        assertTrue(lightPairs.zip(darkPairs).all { (light, dark) -> light != dark })
        assertTrue(
            (lightPairs + darkPairs).all { colors ->
                contrastRatio(colors.icon, colors.container) >= 3.0
            },
        )
    }

    @Test
    fun `settings home keeps all twelve icon palettes distinct and readable`() {
        val lightPairs =
            KiyoriSettingsHomeIconPalette.entries.map { palette ->
                resolveKiyoriSettingsHomeIconColors(palette, isDark = false)
            }
        val darkPairs =
            KiyoriSettingsHomeIconPalette.entries.map { palette ->
                resolveKiyoriSettingsHomeIconColors(palette, isDark = true)
            }

        assertEquals(12, KiyoriSettingsHomeIconPalette.entries.size)
        assertEquals(12, lightPairs.map { colors -> colors.icon }.toSet().size)
        assertEquals(12, lightPairs.map { colors -> colors.container }.toSet().size)
        assertEquals(12, darkPairs.map { colors -> colors.icon }.toSet().size)
        assertEquals(12, darkPairs.map { colors -> colors.container }.toSet().size)
        assertTrue(lightPairs.zip(darkPairs).all { (light, dark) -> light != dark })
        assertTrue(
            (lightPairs + darkPairs).all { colors ->
                contrastRatio(colors.icon, colors.container) >= 3.0
            },
        )
    }

    @Test
    fun `settings detail icon palette stays balanced and readable in both themes`() {
        val lightPairs =
            KiyoriSemanticTone.entries.map { tone ->
                resolveKiyoriSettingsIconColors(tone, isDark = false)
            }
        val darkPairs =
            KiyoriSemanticTone.entries.map { tone ->
                resolveKiyoriSettingsIconColors(tone, isDark = true)
            }

        assertEquals(KiyoriSemanticTone.entries.size, lightPairs.map { it.icon }.toSet().size)
        assertEquals(KiyoriSemanticTone.entries.size, lightPairs.map { it.container }.toSet().size)
        assertEquals(KiyoriSemanticTone.entries.size, darkPairs.map { it.icon }.toSet().size)
        assertEquals(KiyoriSemanticTone.entries.size, darkPairs.map { it.container }.toSet().size)
        assertTrue(lightPairs.zip(darkPairs).all { (light, dark) -> light != dark })
        assertTrue(
            (lightPairs + darkPairs).all { colors ->
                contrastRatio(colors.icon, colors.container) >= 3.0
            },
        )
        assertNotEquals(
            resolveKiyoriSettingsThemeShortcutIconColor(isDark = false),
            resolveKiyoriSettingsThemeShortcutIconColor(isDark = true),
        )
    }

    @Test
    fun `bottom navigation yellow is exact while weather sun remains independent`() {
        val lightWeatherSun = resolveKiyoriWeatherSunColor(isDark = false)
        val darkWeatherSun = resolveKiyoriWeatherSunColor(isDark = true)

        assertEquals(Color(0xFFFFC153), KiyoriBottomNavigationSelectedFillColor)
        assertEquals(Color(0xFFC57C00), lightWeatherSun)
        assertEquals(Color(0xFFFFD166), darkWeatherSun)
        assertNotEquals(KiyoriBottomNavigationSelectedFillColor, lightWeatherSun)
        assertNotEquals(KiyoriBottomNavigationSelectedFillColor, darkWeatherSun)
        assertTrue(contrastRatio(lightWeatherSun, KiyoriLightColorScheme.background) >= 3.0)
        assertTrue(contrastRatio(darkWeatherSun, KiyoriDarkColorScheme.background) >= 3.0)
    }

    @Test
    fun `stable entry ids keep deterministic semantic tones`() {
        val entryIds =
            listOf(
                "toolbox.tool_tester",
                "toolbox.text_to_speech",
                "toolbox.speech_to_text",
                "toolbox.app_permissions",
                "toolbox.terminal",
                "toolbox.ui_debugger",
                "toolbox.ffmpeg_toolbox",
                "toolbox.shell_executor",
                "toolbox.logcat",
                "toolbox.sql_viewer",
                "toolbox.token_config",
            )
        val firstPass = entryIds.map(::kiyoriSemanticToneForStableId)
        val secondPass = entryIds.map(::kiyoriSemanticToneForStableId)

        assertEquals(firstPass, secondPass)
        assertTrue(firstPass.toSet().size >= 5)
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
