package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionUserAgentPolicyTest {
    @Test
    fun `persisted modes are strict and stable`() {
        WebSessionUserAgentMode.entries.forEach { mode ->
            assertEquals(mode, WebSessionUserAgentMode.fromPersistedId(mode.persistedId))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebSessionUserAgentMode.fromPersistedId("desktop")
        }
    }

    @Test
    fun `preset modes resolve distinct platform identities`() {
        assertTrue(resolvePreset(WebSessionUserAgentMode.ANDROID).contains("Android"))
        assertTrue(resolvePreset(WebSessionUserAgentMode.PC_DESKTOP).contains("Windows NT"))
        assertTrue(resolvePreset(WebSessionUserAgentMode.IPHONE).contains("iPhone"))
        assertTrue(resolvePreset(WebSessionUserAgentMode.SYMBIAN_WAP).contains("SymbianOS"))
        assertThrows(IllegalArgumentException::class.java) {
            resolveWebSessionPresetUserAgent(WebSessionUserAgentMode.CUSTOM_GLOBAL, "  ")
        }
    }

    @Test
    fun `domain normalization accepts hosts urls ports and unicode`() {
        assertEquals("example.com", normalizeWebSessionUserAgentDomain("Example.COM"))
        assertEquals("m.example.com", normalizeWebSessionUserAgentDomain("https://M.Example.com/path"))
        assertEquals("example.com", normalizeWebSessionUserAgentDomain("example.com:8443"))
        assertEquals("xn--fsqu00a.xn--0zwm56d", normalizeWebSessionUserAgentDomain("例子.测试"))
        assertNull(normalizeWebSessionUserAgentDomain("not a host"))
        assertNull(normalizeWebSessionUserAgentDomain("%"))
    }

    @Test
    fun `site rules match their domain and subdomains with the most specific rule`() {
        val root = WebSessionSiteUserAgentRule("example.com", "root-ua")
        val mobile = WebSessionSiteUserAgentRule("m.example.com", "mobile-ua")
        val rules = listOf(root, mobile)

        assertEquals(root, resolveWebSessionSiteUserAgentRule(rules, "https://example.com/read"))
        assertEquals(mobile, resolveWebSessionSiteUserAgentRule(rules, "https://m.example.com/read"))
        assertEquals(mobile, resolveWebSessionSiteUserAgentRule(rules, "https://a.m.example.com/read"))
        assertNull(resolveWebSessionSiteUserAgentRule(rules, "https://notexample.com"))
        assertNull(resolveWebSessionSiteUserAgentRule(rules, "about:blank"))
    }

    @Test
    fun `session then site then global precedence is deterministic`() {
        val settings =
            WebSessionBrowserSettings(
                userAgentMode = WebSessionUserAgentMode.IPHONE,
                siteUserAgentRules =
                    listOf(WebSessionSiteUserAgentRule("example.com", "site-mobile-ua")),
            )

        assertEquals(
            "session-ua",
            resolveWebSessionUserAgent(settings, "https://example.com", "session-ua").userAgent,
        )
        assertEquals(
            "site-mobile-ua",
            resolveWebSessionUserAgent(settings, "https://example.com", null).userAgent,
        )
        assertEquals(
            WEB_SESSION_IPHONE_USER_AGENT,
            resolveWebSessionUserAgent(settings, "https://other.example", null).userAgent,
        )
    }

    @Test
    fun `desktop layout follows the resolved user agent`() {
        assertTrue(isWebSessionDesktopUserAgent(WEB_SESSION_PC_DESKTOP_USER_AGENT))
        assertFalse(isWebSessionDesktopUserAgent(WEB_SESSION_ANDROID_USER_AGENT))
        assertFalse(isWebSessionDesktopUserAgent(WEB_SESSION_IPHONE_USER_AGENT))
        assertFalse(isWebSessionDesktopUserAgent(WEB_SESSION_SYMBIAN_WAP_USER_AGENT))
    }

    private fun resolvePreset(mode: WebSessionUserAgentMode): String =
        resolveWebSessionPresetUserAgent(mode, "")
}
