package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionBrowserMenuColorPolicyTest {
    @Test
    fun `four menu rows contain eighteen stable action identities`() {
        val tones = WebSessionBrowserMenuTone.entries
        val tonesByRow = tones.groupBy(WebSessionBrowserMenuTone::rowIndex)

        assertEquals(18, tones.size)
        assertEquals(listOf(5, 5, 5, 3), (0..3).map { row -> tonesByRow.getValue(row).size })
        tonesByRow.forEach { (_, rowTones) ->
            assertEquals(
                rowTones.size,
                rowTones.map(WebSessionBrowserMenuTone::columnIndex).toSet().size,
            )
        }
    }

    @Test
    fun `every menu action has a unique light and dark color pair`() {
        val tones = WebSessionBrowserMenuTone.entries
        val lightColors =
            tones.map { tone -> resolveWebSessionBrowserMenuColors(tone, isDark = false) }
        val darkColors =
            tones.map { tone -> resolveWebSessionBrowserMenuColors(tone, isDark = true) }

        assertEquals(tones.size, lightColors.toSet().size)
        assertEquals(tones.size, darkColors.toSet().size)
        assertEquals(tones.size, lightColors.map { colors -> colors.icon }.toSet().size)
        assertEquals(tones.size, lightColors.map { colors -> colors.container }.toSet().size)
        assertEquals(tones.size, darkColors.map { colors -> colors.icon }.toSet().size)
        assertEquals(tones.size, darkColors.map { colors -> colors.container }.toSet().size)
        assertTrue(lightColors.zip(darkColors).all { (light, dark) -> light != dark })
    }

    @Test
    fun `all menu icons retain non text contrast in both themes`() {
        WebSessionBrowserMenuTone.entries.forEach { tone ->
            listOf(false, true).forEach { isDark ->
                val colors = resolveWebSessionBrowserMenuColors(tone, isDark)
                assertTrue(
                    "$tone contrast was ${contrastRatio(colors.icon, colors.container)}",
                    contrastRatio(colors.icon, colors.container) >= 3.0,
                )
            }
        }
    }

    @Test
    fun `detected media badge reuses the floating sniffer identity`() {
        assertEquals(
            WebSessionBrowserMenuTone.FLOATING_SNIFFER,
            WebSessionDetectedMediaBadgeTone,
        )
        listOf(false, true).forEach { isDark ->
            assertEquals(
                resolveWebSessionBrowserMenuColors(
                    WebSessionBrowserMenuTone.FLOATING_SNIFFER,
                    isDark,
                ),
                resolveWebSessionBrowserMenuColors(WebSessionDetectedMediaBadgeTone, isDark),
            )
        }
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
