package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

internal fun handleChatEnterKey(
    event: KeyEvent,
    enabled: Boolean,
    isComposing: Boolean,
    onSubmit: () -> Unit,
): Boolean = when (
    resolveChatEnterKeyAction(
        enabled = enabled,
        isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter,
        isShiftPressed = event.isShiftPressed,
        isComposing = isComposing,
        isKeyDown = event.type == KeyEventType.KeyDown,
        isKeyUp = event.type == KeyEventType.KeyUp,
        repeatCount = event.nativeKeyEvent.repeatCount,
    )
) {
    ChatEnterKeyAction.PASS_THROUGH -> false
    ChatEnterKeyAction.CONSUME -> true
    ChatEnterKeyAction.SUBMIT -> { onSubmit(); true }
}
