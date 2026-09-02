package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserCookieReaderTest {
    @Test
    fun `only http and https pages are supported`() {
        assertTrue(isSupportedBrowserCookieUrl("https://example.com/path"))
        assertTrue(isSupportedBrowserCookieUrl("http://example.com"))
        assertFalse(isSupportedBrowserCookieUrl("about:blank"))
        assertFalse(isSupportedBrowserCookieUrl("file:///tmp/page.html"))
        assertFalse(isSupportedBrowserCookieUrl("https:///missing-host"))
    }

    @Test
    fun `cookie header parser preserves equals in values and skips malformed segments`() {
        assertEquals(
            listOf(
                BrowserCookieItem("session", "abc123"),
                BrowserCookieItem("signed", "a=b=="),
                BrowserCookieItem("empty", ""),
            ),
            parseBrowserCookieHeader("session=abc123; broken; signed=a=b==; empty=; =ignored"),
        )
    }

    @Test
    fun `cookie provider is available as a built in plugin for a supported page`() {
        val snapshot =
            BrowserPluginCenterFacade.project(
                userscriptState = com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState(),
                currentPageMenuCommands = emptyList(),
                currentPageUrl = "https://example.com/account",
            )
        val cookiePlugin = snapshot.installedPlugins.single { plugin ->
            plugin.id == BUILT_IN_COOKIE_PLUGIN_ID
        }

        assertEquals(BrowserPluginKind.COOKIE_READER, cookiePlugin.kind)
        assertEquals(BrowserPluginAvailability.AVAILABLE, cookiePlugin.availability)
        assertEquals(1, cookiePlugin.currentPageItemCount)
        assertTrue(snapshot.currentPageProviders.any { provider ->
            provider.summary.id == BUILT_IN_COOKIE_PLUGIN_ID
        })
        assertTrue(BrowserPluginAction.SET_PLUGIN_PERMISSION in cookiePlugin.supportedActions)
    }

    @Test
    fun `disabled cookie reader remains installed but is not projected to the current page`() {
        val snapshot =
            BrowserPluginCenterFacade.project(
                userscriptState = com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState(),
                currentPageMenuCommands = emptyList(),
                currentPageUrl = "https://example.com/account",
                cookieReaderEnabled = false,
            )
        val cookiePlugin = snapshot.installedPlugins.single { plugin ->
            plugin.id == BUILT_IN_COOKIE_PLUGIN_ID
        }

        assertFalse(cookiePlugin.runtimeAllowed)
        assertTrue(BrowserPluginAction.SET_PLUGIN_PERMISSION in cookiePlugin.supportedActions)
        assertEquals(0, cookiePlugin.currentPageItemCount)
        assertFalse(snapshot.currentPageProviders.any { provider ->
            provider.summary.id == BUILT_IN_COOKIE_PLUGIN_ID
        })
    }
}
