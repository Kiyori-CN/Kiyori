package com.ai.assistance.operit.core.browser.navigation

import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `quick switch bar eligibility only accepts text searches`() {
        assertTrue(BrowserAddressResolver.isSearchQuery("我的 世界"))
        assertTrue(BrowserAddressResolver.isSearchQuery("我的"))
        assertFalse(BrowserAddressResolver.isSearchQuery(""))
        assertFalse(BrowserAddressResolver.isSearchQuery("https://example.com/path"))
        assertFalse(BrowserAddressResolver.isSearchQuery("example.com/path"))
        assertFalse(BrowserAddressResolver.isSearchQuery("localhost:8080"))
    }
}
