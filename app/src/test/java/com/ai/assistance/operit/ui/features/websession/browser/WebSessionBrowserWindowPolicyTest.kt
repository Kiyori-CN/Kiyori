package com.ai.assistance.operit.ui.features.websession.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionBrowserWindowPolicyTest {
    @Test
    fun `clearing windows opens only a configured custom home`() {
        assertFalse(shouldOpenConfiguredHomeAfterClearingWindows("about:blank"))
        assertFalse(shouldOpenConfiguredHomeAfterClearingWindows("ABOUT:BLANK"))
        assertTrue(
            shouldOpenConfiguredHomeAfterClearingWindows(
                "https://example.com/custom-home",
            ),
        )
    }
}
