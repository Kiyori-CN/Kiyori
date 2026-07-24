package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabDiscoveryFormatterTest {
    @Test
    fun `tab list exposes stable session ids and shared runtime ownership`() {
        val rendered =
            BrowserTabDiscoveryFormatter.render(
                listOf(
                    BrowserTabDiscoveryEntry(
                        index = 0,
                        sessionId = "manual-session-id",
                        title = "Page opened by the user",
                        url = "https://example.com/manual",
                        isActive = true,
                        profile = WebSessionProfile.NORMAL,
                    ),
                    BrowserTabDiscoveryEntry(
                        index = 1,
                        sessionId = "ai-session-id",
                        title = "AI page",
                        url = "https://example.com/ai",
                        isActive = false,
                        profile = WebSessionProfile.INCOGNITO,
                    ),
                )
            )

        assertTrue(rendered.contains("same tabs used by Kiyori UI and AI browser tools"))
        assertTrue(rendered.contains("session_id: manual-session-id"))
        assertTrue(rendered.contains("session_id: ai-session-id"))
        assertTrue(rendered.contains("profile: normal"))
        assertTrue(rendered.contains("profile: incognito"))
        assertTrue(rendered.contains("[0] Page opened by the user [active]"))
    }

    @Test
    fun `tab fields remain one line for machine readable discovery`() {
        val rendered =
            BrowserTabDiscoveryFormatter.render(
                listOf(
                    BrowserTabDiscoveryEntry(
                        index = 0,
                        sessionId = "session-id",
                        title = "Line one\nLine two",
                        url = "https://example.com/\npath",
                        isActive = false,
                        profile = WebSessionProfile.NORMAL,
                    )
                )
            )

        assertTrue(rendered.contains("Line one Line two"))
        assertTrue(rendered.contains("https://example.com/ path"))
        assertFalse(rendered.contains("Line one\nLine two"))
    }
}
