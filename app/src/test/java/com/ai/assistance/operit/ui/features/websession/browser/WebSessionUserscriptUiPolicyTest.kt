package com.ai.assistance.operit.ui.features.websession.browser

import org.junit.Assert.assertEquals
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

    @Test
    fun `userscript export file name preserves identity and removes unsafe path characters`() {
        assertEquals(
            "轻小说文库_增强版_20260803_123456_789.user.js",
            buildUserscriptExportFileName(
                scriptName = " 轻小说文库/增强版 ",
                timestamp = "20260803_123456_789",
            ),
        )
        assertEquals(
            "_____20260803_123456_789.user.js",
            buildUserscriptExportFileName(
                scriptName = "...?",
                timestamp = "20260803_123456_789",
            ),
        )
    }
}
