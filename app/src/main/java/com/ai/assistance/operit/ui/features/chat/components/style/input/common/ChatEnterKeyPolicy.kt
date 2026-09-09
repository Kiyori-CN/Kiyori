package com.ai.assistance.operit.ui.features.chat.components.style.input.common

internal enum class ChatEnterKeyAction { PASS_THROUGH, CONSUME, SUBMIT }

/** 中文候选确认交给 IME；长按的重复事件和抬起不能再次发送或插入多余换行。 */
internal fun resolveChatEnterKeyAction(
    enabled: Boolean,
    isEnter: Boolean,
    isShiftPressed: Boolean,
    isComposing: Boolean,
    isKeyDown: Boolean,
    isKeyUp: Boolean,
    repeatCount: Int,
): ChatEnterKeyAction = when {
    !enabled || !isEnter || isShiftPressed || isComposing -> ChatEnterKeyAction.PASS_THROUGH
    isKeyDown && repeatCount == 0 -> ChatEnterKeyAction.SUBMIT
    isKeyDown || isKeyUp -> ChatEnterKeyAction.CONSUME
    else -> ChatEnterKeyAction.PASS_THROUGH
}
