package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserExternalNavigationPolicyTest {
    @Test
    fun `external app launch requires main frame and explicit user gesture`() {
        assertTrue(
            shouldLaunchBrowserExternalNavigation(
                isMainFrame = true,
                hasUserGesture = true,
            ),
        )
        assertFalse(
            shouldLaunchBrowserExternalNavigation(
                isMainFrame = true,
                hasUserGesture = false,
            ),
        )
        assertFalse(
            shouldLaunchBrowserExternalNavigation(
                isMainFrame = false,
                hasUserGesture = true,
            ),
        )
        assertFalse(
            shouldLaunchBrowserExternalNavigation(
                isMainFrame = false,
                hasUserGesture = false,
            ),
        )
    }
}
