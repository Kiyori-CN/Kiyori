package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserWindowCountStateTest {
    @After
    fun resetWindowCount() {
        BrowserWindowCountState.publish(0)
    }

    @Test
    fun `window count projection does not require Browser Runtime`() {
        BrowserWindowCountState.publish(3)

        assertEquals(3, BrowserWindowCountState.windowCount.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `window count projection rejects negative registry size`() {
        BrowserWindowCountState.publish(-1)
    }
}
