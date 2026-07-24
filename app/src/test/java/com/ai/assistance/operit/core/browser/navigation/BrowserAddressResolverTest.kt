package com.ai.assistance.operit.core.browser.navigation

import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserAddressResolverTest {
    @Test
    fun `selected search engine identity is stable`() {
        assertEquals(
            "bing",
            WebSessionSearchEngine.BING.id,
        )
    }

    @Test
    fun `explicit address bypasses search engine`() {
        assertEquals(
            "https://example.com/path",
            BrowserAddressResolver.resolve("https://example.com/path", WebSessionSearchEngine.GOOGLE),
        )
    }
}
