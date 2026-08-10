package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRunCodeContractTest {
    @Test
    fun `documented WebView page subset includes content and dialog registration`() {
        assertTrue("setContent" in BrowserRunCodeContract.supportedPageMembers)
        assertTrue("on" in BrowserRunCodeContract.supportedPageMembers)
        assertTrue("once" in BrowserRunCodeContract.supportedPageMembers)
        assertTrue("off" in BrowserRunCodeContract.supportedPageMembers)
        assertTrue("removeListener" in BrowserRunCodeContract.supportedPageMembers)
    }

    @Test
    fun `dialog subset covers all JavaScript dialog kinds`() {
        assertEquals(
            setOf("alert", "confirm", "prompt"),
            BrowserRunCodeContract.supportedDialogTypes,
        )
    }

    @Test
    fun `locator subset is explicit`() {
        assertEquals(
            setOf("click", "hover", "fill", "selectOption", "textContent"),
            BrowserRunCodeContract.supportedLocatorMembers,
        )
    }

    @Test
    fun `unsupported Page APIs use a stable structured error prefix`() {
        assertEquals(
            "Unsupported Playwright API: page.screenshot",
            BrowserRunCodeContract.unsupportedApi("screenshot"),
        )
    }
}
