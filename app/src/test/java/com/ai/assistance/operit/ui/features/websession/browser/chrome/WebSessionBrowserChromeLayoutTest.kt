package com.ai.assistance.operit.ui.features.websession.browser.chrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionBrowserChromeLayoutTest {
    @Test
    fun `tab grid follows compact medium and expanded width classes`() {
        assertEquals(2, resolveWebSessionBrowserChromeLayout(599f, 900f).tabColumnCount)
        assertEquals(3, resolveWebSessionBrowserChromeLayout(600f, 900f).tabColumnCount)
        assertEquals(3, resolveWebSessionBrowserChromeLayout(839f, 900f).tabColumnCount)
        assertEquals(4, resolveWebSessionBrowserChromeLayout(840f, 900f).tabColumnCount)
    }

    @Test
    fun `wide layouts cap and center drawer width contract`() {
        assertEquals(600f, resolveWebSessionBrowserChromeLayout(700f, 900f).drawerMaxWidthDp, 0f)
        assertEquals(680f, resolveWebSessionBrowserChromeLayout(1200f, 900f).drawerMaxWidthDp, 0f)
    }

    @Test
    fun `compact landscape exposes more drawer content than portrait`() {
        val portrait = resolveWebSessionBrowserChromeLayout(400f, 800f)
        val landscape = resolveWebSessionBrowserChromeLayout(800f / 2f, 300f)

        assertTrue(landscape.drawerPartialFraction > portrait.drawerPartialFraction)
    }
}
