package com.ai.assistance.operit.ui.common.gestures

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

/**
 * 汇总 AI 内容内部正在进行的横向交互。
 *
 * 表格、代码、公式和内嵌预览可能同时存在；使用 owner token 而不是单个 Boolean，避免一个组件
 * 结束手势时过早释放另一个仍在活动的组件。该状态只属于当前 AI 组合，不持久化。
 */
@Stable
internal class AiContentHorizontalGestureOwnership(
    private val onOwnershipChanged: (Boolean) -> Unit = {},
) {
    private val activeOwners = mutableStateMapOf<Any, Unit>()

    val isOwned: Boolean
        get() = activeOwners.isNotEmpty()

    internal fun claim(owner: Any) {
        val wasOwned = isOwned
        activeOwners[owner] = Unit
        if (!wasOwned && isOwned) {
            onOwnershipChanged(true)
        }
    }

    internal fun release(owner: Any) {
        val wasOwned = isOwned
        activeOwners.remove(owner)
        if (wasOwned && !isOwned) {
            onOwnershipChanged(false)
        }
    }
}

internal val LocalAiContentHorizontalGestureOwnership =
    staticCompositionLocalOf<AiContentHorizontalGestureOwnership?> { null }

@Stable
internal class AiContentHorizontalGestureOwner internal constructor(
    private val ownership: AiContentHorizontalGestureOwnership?,
    private val token: Any,
) {
    val isAvailable: Boolean
        get() = ownership != null

    fun claim() {
        ownership?.claim(token)
    }

    fun release() {
        ownership?.release(token)
    }

    fun updateFromMotionEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> claim()
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> release()
        }
    }
}

@Composable
internal fun rememberAiContentHorizontalGestureOwner(): AiContentHorizontalGestureOwner {
    val ownership = LocalAiContentHorizontalGestureOwnership.current
    val token = remember { Any() }
    val owner = remember(ownership, token) { AiContentHorizontalGestureOwner(ownership, token) }
    DisposableEffect(owner) {
        onDispose { owner.release() }
    }
    return owner
}

/**
 * 从 DOWN 到 UP/CANCEL 暂停 AI 首页 Pager，但不消费事件，真实内容组件继续处理点击、拖动和缩放。
 */
internal fun Modifier.ownAiContentHorizontalGestureOnTouch(
    owner: AiContentHorizontalGestureOwner,
    enabled: Boolean,
): Modifier {
    if (!enabled || !owner.isAvailable) {
        return this
    }
    return pointerInput(owner, enabled) {
        awaitEachGesture {
            awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            owner.claim()
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.changes.none { change -> change.pressed }) {
                        break
                    }
                }
            } finally {
                owner.release()
            }
        }
    }
}
