package com.ai.assistance.operit.ui.features.websession.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserHomeMode

class WebSessionBrowserWindowPolicyTest {
    @Test
    fun `clearing windows opens a home tab for custom mode`() {
        assertFalse(shouldOpenConfiguredHomeAfterClearingWindows(BrowserHomeMode.BLANK))
        assertTrue(shouldOpenConfiguredHomeAfterClearingWindows(BrowserHomeMode.CUSTOM_URL))
    }
}
