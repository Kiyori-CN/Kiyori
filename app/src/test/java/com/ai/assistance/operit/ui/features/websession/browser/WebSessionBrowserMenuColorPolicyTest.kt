package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionBrowserMenuColorPolicyTest {
    @Test
    fun `primary menu retains eighteen identities and AI moves into toolbox`() {
        val tones = WebSessionBrowserMenuTone.entries
        val tonesByRow = tones.groupBy(WebSessionBrowserMenuTone::rowIndex)
        val expectedRows =
            listOf(
                listOf(
                    WebSessionBrowserMenuTone.ADD_BOOKMARK,
                    WebSessionBrowserMenuTone.BOOKMARKS,
                    WebSessionBrowserMenuTone.HISTORY,
                    WebSessionBrowserMenuTone.DOWNLOADS,
                    WebSessionBrowserMenuTone.PLUGINS,
                ),
                listOf(
                    WebSessionBrowserMenuTone.USER_AGENT,
                    WebSessionBrowserMenuTone.FLOATING_SNIFFER,
                    WebSessionBrowserMenuTone.NETWORK_LOG,
                    WebSessionBrowserMenuTone.DIAGNOSTICS,
                    WebSessionBrowserMenuTone.TOOLBOX,
                ),
                listOf(
                    WebSessionBrowserMenuTone.INCOGNITO,
                    WebSessionBrowserMenuTone.READER_MODE,
                    WebSessionBrowserMenuTone.PAGE_SOURCE,
                    WebSessionBrowserMenuTone.AD_MARKING,
                    WebSessionBrowserMenuTone.SITE_CONFIG,
                ),
                listOf(
                    WebSessionBrowserMenuTone.EXIT_BROWSER,
                    WebSessionBrowserMenuTone.COLLAPSE,
                    WebSessionBrowserMenuTone.SETTINGS,
                ),
            )

        assertEquals(19, tones.size)
        assertEquals(listOf(5, 5, 5, 3), (0..3).map { row -> tonesByRow.getValue(row).size })
        assertEquals(
            expectedRows,
            (0..3).map { row ->
                tonesByRow.getValue(row).sortedBy(WebSessionBrowserMenuTone::columnIndex)
            },
        )
        assertEquals(listOf(WebSessionBrowserMenuTone.AI_DIALOGUE), tonesByRow.getValue(-1))
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
    fun `plugin identity uses the approved muted deep plum palette`() {
        assertEquals(
            WebSessionBrowserMenuColors(
                icon = Color(0xFF5E3A8A),
                container = Color(0xFFEEE8F4),
            ),
            resolveWebSessionBrowserMenuColors(
                WebSessionBrowserMenuTone.PLUGINS,
                isDark = false,
            ),
        )
        assertEquals(
            WebSessionBrowserMenuColors(
                icon = Color(0xFFCBB8E2),
                container = Color(0xFF2D2238),
            ),
            resolveWebSessionBrowserMenuColors(
                WebSessionBrowserMenuTone.PLUGINS,
                isDark = true,
            ),
        )
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
