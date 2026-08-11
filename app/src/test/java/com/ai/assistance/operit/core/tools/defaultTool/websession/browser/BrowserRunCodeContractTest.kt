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
    fun `keyboard subset exposes press with effectful keys only`() {
        assertEquals(setOf("press"), BrowserRunCodeContract.supportedKeyboardMembers)
        assertEquals(
            setOf("Enter", "Backspace", "Delete"),
            BrowserRunCodeContract.supportedKeyboardNamedKeys,
        )
        assertTrue(BrowserRunCodeContract.supportsKeyboardPress("a"))
        assertTrue(BrowserRunCodeContract.supportsKeyboardPress("Enter"))
        assertTrue(!BrowserRunCodeContract.supportsKeyboardPress("Tab"))
        assertTrue(!BrowserRunCodeContract.supportsKeyboardPress("End"))
    }

    @Test
    fun `run code accepts function sources and rejects statement bodies`() {
        assertTrue(
            BrowserRunCodeContract.supportsFunctionSource(
                "async (page) => { return await page.title(); }"
            )
        )
        assertTrue(
            BrowserRunCodeContract.supportsFunctionSource(
                "function(page) { return page.url(); }"
            )
        )
        assertTrue(!BrowserRunCodeContract.supportsFunctionSource("return await page.title();"))
    }

    @Test
    fun `shared async wrapper injects the expression without dynamic compilation`() {
        val script =
            buildDirectAsyncJavascriptSource(
                quotedCallId = "\"call-1\"",
                expression = "(async function() { return 7; })();",
            )

        assertTrue(script.contains("const operitValue = ((async function() { return 7; })())"))
        assertTrue(!script.contains("eval("))
        assertTrue(!script.contains("AsyncFunction"))
        assertTrue(!script.contains("new Function"))
    }

    @Test
    fun `tab state change and page observation use separate response sections`() {
        val response =
            buildBrowserResponse(
                stateChange = "Closed tab 1. Remaining tabs: 1.",
                pageObservation = "Captured the active page.",
            )

        assertTrue(response.contains("### State change\nClosed tab 1. Remaining tabs: 1."))
        assertTrue(response.contains("### Page observation\nCaptured the active page."))
    }

    @Test
    fun `unsupported Page APIs use a stable structured error prefix`() {
        assertEquals(
            "Unsupported Playwright API: page.screenshot",
            BrowserRunCodeContract.unsupportedApi("screenshot"),
        )
    }
}
