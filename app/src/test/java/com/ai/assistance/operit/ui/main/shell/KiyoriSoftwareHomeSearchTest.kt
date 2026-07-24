package com.ai.assistance.operit.ui.main.shell

import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KiyoriSoftwareHomeSearchTest {
    @Test
    fun `home layout changes at tablet and expanded width gates`() {
        assertEquals(
            KiyoriSoftwareHomeLayout.COMPACT,
            resolveKiyoriSoftwareHomeLayout(599f),
        )
        assertEquals(
            KiyoriSoftwareHomeLayout.MEDIUM,
            resolveKiyoriSoftwareHomeLayout(600f),
        )
        assertEquals(
            KiyoriSoftwareHomeLayout.MEDIUM,
            resolveKiyoriSoftwareHomeLayout(839f),
        )
        assertEquals(
            KiyoriSoftwareHomeLayout.EXPANDED,
            resolveKiyoriSoftwareHomeLayout(840f),
        )
    }

    @Test
    fun `blank home search does not create a browser request`() {
        assertNull(resolveKiyoriWebSearchRequest("   ", WebSessionSearchEngine.BING))
    }

    @Test
    fun `home search trims the input and resolves a host for a new session`() {
        assertEquals(
            KiyoriWebSearchRequest(
                query = "example.com/docs",
                targetUrl = "https://example.com/docs",
            ),
            resolveKiyoriWebSearchRequest(
                rawQuery = "  example.com/docs  ",
                searchEngine = WebSessionSearchEngine.BING,
            ),
        )
    }
}
