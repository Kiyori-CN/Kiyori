package com.ai.assistance.operit.core.browser.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPresentationReleaseGateTest {
    @Test
    fun `only the first terminal release action runs`() {
        val gate = BrowserPresentationReleaseGate()
        var releaseCount = 0

        assertTrue(gate.runOnce { releaseCount += 1 })
        assertFalse(gate.runOnce { releaseCount += 1 })
        assertEquals(1, releaseCount)
    }
}
