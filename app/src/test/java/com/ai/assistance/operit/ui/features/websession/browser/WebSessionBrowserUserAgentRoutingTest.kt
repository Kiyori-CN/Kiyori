package com.ai.assistance.operit.ui.features.websession.browser

import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSheetRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionBrowserUserAgentRoutingTest {
    @Test
    fun `user agent bypasses browser drawers`() {
        assertFalse(WebSessionBrowserSheetRoute.USER_AGENT.isWebSessionBrowserDrawerRoute())
        assertTrue(WebSessionBrowserSheetRoute.MENU.isWebSessionBrowserDrawerRoute())
        assertTrue(WebSessionBrowserSheetRoute.HISTORY.isWebSessionBrowserDrawerRoute())
        assertTrue(WebSessionBrowserSheetRoute.PLUGINS.isWebSessionBrowserDrawerRoute())
        assertTrue(WebSessionBrowserSheetRoute.NETWORK_LOG.isWebSessionBrowserDrawerRoute())
        assertTrue(WebSessionBrowserSheetRoute.MEDIA_CANDIDATES.isWebSessionBrowserDrawerRoute())
    }
}
