package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import kotlin.math.abs

internal sealed interface BrowserAdMarkingTouchAction {
    data object None : BrowserAdMarkingTouchAction

    data class ScrollBy(
        val deltaYPx: Float,
    ) : BrowserAdMarkingTouchAction

    data class Select(
        val xPx: Float,
        val yPx: Float,
    ) : BrowserAdMarkingTouchAction

    data object EndScroll : BrowserAdMarkingTouchAction

    data object EndGesture : BrowserAdMarkingTouchAction
}

/**
 * 标记模式必须独占网页触摸，不能把滚动手势重新交给 DOM；否则广告可以在 pointer/touch
 * 阶段先于 click 发起跳转。这里仅区分轻点和纵向拖动，真正滚动仍由唯一 WebView 执行。
 */
internal class BrowserAdMarkingTouchTracker(
    private val touchSlopPx: Float,
) {
    private var active = false
    private var movedBeyondSlop = false
    private var verticalScrolling = false
    private var downXPx = 0f
    private var downYPx = 0f
    private var lastYPx = 0f

    init {
        require(touchSlopPx.isFinite() && touchSlopPx >= 0f) {
            "Browser ad-marking touch slop must be finite and non-negative."
        }
    }

    fun onDown(
        xPx: Float,
        yPx: Float,
    ): BrowserAdMarkingTouchAction {
        active = true
        movedBeyondSlop = false
        verticalScrolling = false
        downXPx = xPx
        downYPx = yPx
        lastYPx = yPx
        return BrowserAdMarkingTouchAction.None
    }

    fun onMove(
        xPx: Float,
        yPx: Float,
    ): BrowserAdMarkingTouchAction {
        if (!active) {
            return BrowserAdMarkingTouchAction.None
        }
        val totalXPx = abs(xPx - downXPx)
        val totalYPx = abs(yPx - downYPx)
        if (!movedBeyondSlop && (totalXPx > touchSlopPx || totalYPx > touchSlopPx)) {
            movedBeyondSlop = true
        }
        if (
            !verticalScrolling &&
                totalYPx > touchSlopPx &&
                totalYPx >= totalXPx
        ) {
            verticalScrolling = true
        }
        val deltaYPx = lastYPx - yPx
        lastYPx = yPx
        return if (verticalScrolling && deltaYPx != 0f) {
            BrowserAdMarkingTouchAction.ScrollBy(deltaYPx)
        } else {
            BrowserAdMarkingTouchAction.None
        }
    }

    fun onUp(
        xPx: Float,
        yPx: Float,
    ): BrowserAdMarkingTouchAction {
        if (!active) {
            return BrowserAdMarkingTouchAction.None
        }
        val result =
            when {
                verticalScrolling -> BrowserAdMarkingTouchAction.EndScroll
                movedBeyondSlop -> BrowserAdMarkingTouchAction.EndGesture
                else -> BrowserAdMarkingTouchAction.Select(xPx = xPx, yPx = yPx)
            }
        reset()
        return result
    }

    fun cancel() {
        reset()
    }

    private fun reset() {
        active = false
        movedBeyondSlop = false
        verticalScrolling = false
        downXPx = 0f
        downYPx = 0f
        lastYPx = 0f
    }
}
