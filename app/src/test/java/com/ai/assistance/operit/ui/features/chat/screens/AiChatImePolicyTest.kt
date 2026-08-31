package com.ai.assistance.operit.ui.features.chat.screens

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatImePolicyTest {
    @Test
    fun embeddedTerminalKeepsWindowFixedAndOwnsViewportInsets() {
        val policy = resolveAiChatImePolicy(
            shouldUseChatLocalImeHandling = false,
            showAiComputer = true,
        )

        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, policy.requestedSoftInputMode)
        assertFalse(policy.useGlobalImePadding)
    }

    @Test
    fun agentChatKeepsItsExistingLocalImePolicy() {
        val policy = resolveAiChatImePolicy(
            shouldUseChatLocalImeHandling = true,
            showAiComputer = false,
        )

        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, policy.requestedSoftInputMode)
        assertFalse(policy.useGlobalImePadding)
    }

    @Test
    fun regularScreensRetainGlobalImePaddingWithoutForcingWindowMode() {
        val policy = resolveAiChatImePolicy(
            shouldUseChatLocalImeHandling = false,
            showAiComputer = false,
        )

        assertNull(policy.requestedSoftInputMode)
        assertTrue(policy.useGlobalImePadding)
    }

    @Test
    fun terminalPanelUsesShellPagerWhileSuppressingStaleChatGestureOwnership() {
        assertFalse(
            resolveAiChatExternalGestureBlocked(
                chatScreenGestureConsumed = false,
                showAiComputer = true,
            )
        )
        assertFalse(
            resolveAiChatExternalGestureBlocked(
                chatScreenGestureConsumed = true,
                showAiComputer = true,
            )
        )
        assertTrue(
            resolveAiChatExternalGestureBlocked(
                chatScreenGestureConsumed = true,
                showAiComputer = false,
            )
        )
        assertFalse(
            resolveAiChatExternalGestureBlocked(
                chatScreenGestureConsumed = false,
                showAiComputer = false,
            )
        )
    }
}
