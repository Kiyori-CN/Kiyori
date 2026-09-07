package com.ai.assistance.operit.core.tools.defaultTool.websession.extension

import org.junit.Assert.*
import org.junit.Test

class BrowserExtensionMessagePolicyTest {
    @Test fun bridgeRequiresCurrentTopPageAndExactOriginIncludingPort() {
        val page = "https://example.com/private"
        assertTrue(BrowserExtensionMessagePolicy.acceptsPage(page, "https://example.com:443", page, true))
        assertFalse(BrowserExtensionMessagePolicy.acceptsPage(page, "https://example.com:444", page, true))
        assertFalse(BrowserExtensionMessagePolicy.acceptsPage(page, "https://evil.test", page, true))
        assertFalse(BrowserExtensionMessagePolicy.acceptsPage(page, "https://example.com", page, false))
        assertFalse(BrowserExtensionMessagePolicy.acceptsPage(page, "https://example.com", "$page/new", true))
        assertFalse(BrowserExtensionMessagePolicy.acceptsPage("file:///secret", "file://", "file:///secret", true))
        val repeatedFragment = "https://www.gov.cn/example#1#1#1"
        assertTrue(BrowserExtensionMessagePolicy.acceptsPage(repeatedFragment, "https://www.gov.cn", repeatedFragment, true))
    }

    @Test fun onlyOneHandshakeCanOwnDocumentAndLateOldEventsAreRejectedAfterNavigation() {
        assertTrue(BrowserExtensionMessagePolicy.acceptsDocument("", "first", "READY"))
        assertFalse(BrowserExtensionMessagePolicy.acceptsDocument("first", "first", "READY"))
        assertTrue(BrowserExtensionMessagePolicy.acceptsDocument("first", "first", "SUCCESS"))
        assertFalse(BrowserExtensionMessagePolicy.acceptsDocument("second", "first", "SUCCESS"))
        assertFalse(BrowserExtensionMessagePolicy.acceptsDocument("", "first", "SUCCESS"))
        assertFalse(BrowserExtensionMessagePolicy.acceptsDocument("", "", "READY"))
    }
}
