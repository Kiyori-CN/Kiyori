package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.webkit.WebSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDisplaySettingsPolicyTest {
    @Test
    fun `web text zoom accepts only the fixed five percent scale`() {
        assertTrue(isSupportedWebTextZoomPercent(50))
        assertTrue(isSupportedWebTextZoomPercent(100))
        assertTrue(isSupportedWebTextZoomPercent(135))
        assertTrue(isSupportedWebTextZoomPercent(200))
        assertFalse(isSupportedWebTextZoomPercent(45))
        assertFalse(isSupportedWebTextZoomPercent(103))
        assertFalse(isSupportedWebTextZoomPercent(205))
    }

    @Test
    fun `return without reload changes only the selected Back cache mode`() {
        assertEquals(
            WebSettings.LOAD_CACHE_ELSE_NETWORK,
            resolveBrowserBackCacheMode(
                returnWithoutReloadEnabled = true,
                currentCacheMode = WebSettings.LOAD_DEFAULT,
            ),
        )
        assertEquals(
            WebSettings.LOAD_NO_CACHE,
            resolveBrowserBackCacheMode(
                returnWithoutReloadEnabled = false,
                currentCacheMode = WebSettings.LOAD_NO_CACHE,
            ),
        )
    }

    @Test
    fun `force page zoom script owns and restores viewport mutations`() {
        val enabled = browserForcePageZoomScript(enabled = true)
        val disabled = browserForcePageZoomScript(enabled = false)

        assertTrue(enabled.contains("user-scalable=yes"))
        assertTrue(enabled.contains("maximum-scale=10.0"))
        assertTrue(enabled.contains("MutationObserver"))
        assertTrue(enabled.contains("observer.disconnect()"))
        assertTrue(enabled.contains("originals.set(record.target"))
        assertTrue(enabled.contains("originals.forEach"))
        assertTrue(enabled.contains("created.forEach"))
        assertTrue(disabled.contains("if (!false)"))
        assertTrue(disabled.contains("previous.dispose()"))
        assertFalse(enabled.contains("location.reload"))
    }
}
