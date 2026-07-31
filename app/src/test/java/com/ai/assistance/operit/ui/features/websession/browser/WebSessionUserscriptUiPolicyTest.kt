package com.ai.assistance.operit.ui.features.websession.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionUserscriptUiPolicyTest {
    @Test
    fun `userscript install url accepts only absolute http and https urls`() {
        assertTrue(isSupportedUserscriptInstallUrl("https://example.com/script.user.js"))
        assertTrue(isSupportedUserscriptInstallUrl(" http://localhost:8080/test.user.js "))
        assertFalse(isSupportedUserscriptInstallUrl(""))
        assertFalse(isSupportedUserscriptInstallUrl("example.com/script.user.js"))
        assertFalse(isSupportedUserscriptInstallUrl("file:///sdcard/script.user.js"))
        assertFalse(isSupportedUserscriptInstallUrl("javascript:alert(1)"))
    }
}
