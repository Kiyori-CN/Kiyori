package com.ai.assistance.operit.ui.features.websession.browser.chrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionBrowserChromeLayoutTest {
    @Test
    fun `bottom bars use legacy Kiyori five-slot geometry`() {
        assertEquals(5, WEB_SESSION_BROWSER_CHROME_SLOT_COUNT)
        assertEquals(16, WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP)
        assertEquals(0, WEB_SESSION_BROWSER_BOTTOM_TOP_PADDING_DP)
        assertEquals(6, WEB_SESSION_BROWSER_BOTTOM_BOTTOM_PADDING_DP)
        assertEquals(44, WEB_SESSION_BROWSER_BOTTOM_ACTION_SIZE_DP)
        assertEquals(50, WEB_SESSION_BROWSER_BOTTOM_CONTENT_HEIGHT_DP)
        assertEquals(26, WEB_SESSION_BROWSER_BOTTOM_ICON_SIZE_DP)
        assertEquals(25, WEB_SESSION_BROWSER_BOTTOM_CENTER_ICON_SIZE_DP)
    }

    @Test
    fun `browser menu uses compact adaptive four-row geometry`() {
        assertEquals(14, WEB_SESSION_BROWSER_MENU_START_PADDING_DP)
        assertEquals(18, WEB_SESSION_BROWSER_MENU_TOP_PADDING_DP)
        assertEquals(14, WEB_SESSION_BROWSER_MENU_END_PADDING_DP)
        assertEquals(8, WEB_SESSION_BROWSER_MENU_BOTTOM_PADDING_DP)
        assertEquals(1, WEB_SESSION_BROWSER_MENU_ROW_SPACING_DP)
        assertEquals(2, WEB_SESSION_BROWSER_MENU_CELL_HORIZONTAL_PADDING_DP)
        assertEquals(8, WEB_SESSION_BROWSER_MENU_CELL_VERTICAL_PADDING_DP)
        assertEquals(32, WEB_SESSION_BROWSER_MENU_ICON_CONTAINER_SIZE_DP)
        assertEquals(21, WEB_SESSION_BROWSER_MENU_ICON_SIZE_DP)
        assertEquals(6, WEB_SESSION_BROWSER_MENU_ICON_LABEL_SPACING_DP)
        assertEquals(11, WEB_SESSION_BROWSER_MENU_LABEL_SIZE_SP)
        assertEquals(6, WEB_SESSION_BROWSER_MENU_BOTTOM_ROW_HORIZONTAL_PADDING_DP)
        assertEquals(8, WEB_SESSION_BROWSER_MENU_BOTTOM_ROW_TOP_PADDING_DP)
        assertEquals(46, WEB_SESSION_BROWSER_MENU_BOTTOM_ACTION_WIDTH_DP)
        assertEquals(36, WEB_SESSION_BROWSER_MENU_BOTTOM_ACTION_HEIGHT_DP)
        assertEquals(22, WEB_SESSION_BROWSER_MENU_BOTTOM_ICON_SIZE_DP)
        assertEquals(2, WEB_SESSION_BROWSER_MENU_BOTTOM_SIDE_SLOT_WEIGHT)
        assertEquals(1, WEB_SESSION_BROWSER_MENU_BOTTOM_CENTER_SLOT_WEIGHT)
    }

    @Test
    fun `top bar and full screen search share exact geometry`() {
        assertEquals(8, WEB_SESSION_BROWSER_TOP_HORIZONTAL_PADDING_DP)
        assertEquals(8, WEB_SESSION_BROWSER_TOP_VERTICAL_PADDING_DP)
        assertEquals(40, WEB_SESSION_BROWSER_TOP_ACTION_SIZE_DP)
        assertEquals(42, WEB_SESSION_BROWSER_TOP_SEARCH_HEIGHT_DP)
        assertEquals(6, WEB_SESSION_BROWSER_TOP_GAP_DP)
    }

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

    @Test
    fun `drawer content viewport follows the currently exposed height`() {
        assertEquals(
            972f,
            resolveWebSessionBrowserDrawerContentViewportHeight(
                drawerHeightDp = 1000f,
                offsetFraction = 0f,
            ),
            0f,
        )
        assertEquals(
            612f,
            resolveWebSessionBrowserDrawerContentViewportHeight(
                drawerHeightDp = 1000f,
                offsetFraction = 0.36f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveWebSessionBrowserDrawerContentViewportHeight(
                drawerHeightDp = 1000f,
                offsetFraction = 1f,
            ),
            0f,
        )
    }
}
