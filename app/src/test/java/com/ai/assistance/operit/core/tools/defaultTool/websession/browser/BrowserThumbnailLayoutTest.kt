package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserThumbnailLayoutTest {
    @Test
    fun `portrait viewport fits completely with horizontal centering`() {
        val transform =
            requireNotNull(
                resolveBrowserThumbnailTransform(
                    sourceWidth = 1080,
                    sourceHeight = 2160,
                ),
            )

        assertEquals(512f / 2160f, transform.scale, 0.0001f)
        assertEquals(32f, transform.offsetX, 0.001f)
        assertEquals(0f, transform.offsetY, 0.001f)
    }

    @Test
    fun `landscape viewport fits completely with vertical centering`() {
        val transform =
            requireNotNull(
                resolveBrowserThumbnailTransform(
                    sourceWidth = 2160,
                    sourceHeight = 1080,
                ),
            )

        assertEquals(320f / 2160f, transform.scale, 0.0001f)
        assertEquals(0f, transform.offsetX, 0.001f)
        assertEquals(176f, transform.offsetY, 0.001f)
    }

    @Test
    fun `thumbnail contract is five by eight and rejects empty sources`() {
        assertEquals(5f / 8f, BROWSER_TAB_THUMBNAIL_ASPECT_RATIO, 0f)
        assertEquals(320, BROWSER_TAB_THUMBNAIL_WIDTH_PX)
        assertEquals(512, BROWSER_TAB_THUMBNAIL_HEIGHT_PX)
        assertNull(resolveBrowserThumbnailTransform(sourceWidth = 0, sourceHeight = 1080))
    }
}
