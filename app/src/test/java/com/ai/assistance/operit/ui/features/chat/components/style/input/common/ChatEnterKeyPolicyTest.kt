package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatEnterKeyPolicyTest {
    private fun action(
        enabled: Boolean = true,
        enter: Boolean = true,
        shift: Boolean = false,
        composing: Boolean = false,
        down: Boolean = true,
        up: Boolean = false,
        repeat: Int = 0,
    ) = resolveChatEnterKeyAction(enabled, enter, shift, composing, down, up, repeat)

    @Test fun `one held enter produces exactly one submission`() {
        val actions = listOf(action(), action(repeat = 1), action(repeat = 2), action(down = false, up = true))
        assertEquals(1, actions.count { it == ChatEnterKeyAction.SUBMIT })
        assertEquals(0, actions.count { it == ChatEnterKeyAction.PASS_THROUGH })
    }

    @Test fun `IME candidate confirmation never submits`() {
        assertEquals(ChatEnterKeyAction.PASS_THROUGH, action(composing = true))
        assertEquals(ChatEnterKeyAction.PASS_THROUGH, action(composing = true, repeat = 1))
        assertEquals(ChatEnterKeyAction.CONSUME, action(repeat = 2))
    }

    @Test fun `newline and unrelated keyboard input remain with editor`() {
        assertEquals(ChatEnterKeyAction.PASS_THROUGH, action(shift = true))
        assertEquals(ChatEnterKeyAction.PASS_THROUGH, action(enabled = false))
        assertEquals(ChatEnterKeyAction.PASS_THROUGH, action(enter = false))
        assertEquals(ChatEnterKeyAction.PASS_THROUGH, action(down = false))
    }
}
