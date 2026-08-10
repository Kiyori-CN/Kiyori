package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserViewportPolicyTest {
    @Test
    fun `requested CSS viewport remains exact without dp minimums`() {
        assertEquals(
            BrowserViewportSize(width = 900, height = 600),
            BrowserViewportPolicy.requestedSize(width = 900, height = 600),
        )
    }

    @Test
    fun `host uses display size only when no explicit viewport exists`() {
        assertEquals(
            BrowserViewportSize(width = 1080, height = 2400),
            BrowserViewportPolicy.hostSize(
                requestedWidth = null,
                requestedHeight = null,
                defaultWidth = 1080,
                defaultHeight = 2400,
            ),
        )
    }

    @Test
    fun `requested CSS viewport converts to physical host pixels by density`() {
        assertEquals(
            BrowserViewportSize(width = 3150, height = 2100),
            BrowserViewportPolicy.hostLayoutSize(
                requested = BrowserViewportSize(width = 900, height = 600),
                density = 3.5f,
            ),
        )
    }

    @Test
    fun `viewport metrics accept only positive dimensions close to the request`() {
        val requested = BrowserViewportSize(width = 900, height = 600)

        assertTrue(
            BrowserViewportPolicy.matches(
                metrics =
                    BrowserViewportMetrics(
                        innerWidth = 900,
                        innerHeight = 600,
                        clientWidth = 899,
                        clientHeight = 601,
                    ),
                requested = requested,
            ),
        )
        assertFalse(
            BrowserViewportPolicy.matches(
                metrics =
                    BrowserViewportMetrics(
                        innerWidth = 1,
                        innerHeight = 1,
                        clientWidth = 0,
                        clientHeight = 0,
                    ),
                requested = requested,
            ),
        )
        assertFalse(
            BrowserViewportPolicy.matches(
                metrics =
                    BrowserViewportMetrics(
                        innerWidth = 900,
                        innerHeight = 1120,
                        clientWidth = 900,
                        clientHeight = 1120,
                    ),
                requested = requested,
            ),
        )
    }

    @Test
    fun `css coordinates map proportionally into the real WebView`() {
        assertEquals(
            450,
            BrowserViewportPolicy.mapCssCoordinate(
                cssCoordinate = 225.0,
                cssExtent = 450.0,
                viewExtent = 900,
            ),
        )
        assertEquals(
            600,
            BrowserViewportPolicy.mapCssCoordinate(
                cssCoordinate = 700.0,
                cssExtent = 600.0,
                viewExtent = 600,
            ),
        )
    }
}
