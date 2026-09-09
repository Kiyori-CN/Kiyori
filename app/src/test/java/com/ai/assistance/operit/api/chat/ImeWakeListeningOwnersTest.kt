package com.ai.assistance.operit.api.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeWakeListeningOwnersTest {
    @Test fun `closing one window cannot resume listening while another has keyboard focus`() {
        val owners = ImeWakeListeningOwners()
        val main = Any()
        val floating = Any()
        assertTrue(owners.setVisible(main, true))
        assertTrue(owners.setVisible(floating, true))
        assertTrue(owners.setVisible(floating, false))
        assertFalse(owners.setVisible(main, false))
    }

    @Test fun `late disposal of old floating input cannot clear replacement input`() {
        val owners = ImeWakeListeningOwners()
        val oldInput = Any()
        val newInput = Any()
        owners.setVisible(oldInput, true)
        owners.setVisible(newInput, true)
        assertTrue(owners.setVisible(oldInput, false))
        assertTrue(owners.setVisible(oldInput, false))
        assertFalse(owners.setVisible(newInput, false))
        assertFalse(owners.isVisible)
    }
}
