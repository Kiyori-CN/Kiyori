package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.view.WindowManager

internal const val BROWSER_MINIMIZED_INDICATOR_CLOSE_TIMEOUT_MILLIS = 3_000L
internal const val BROWSER_MINIMIZED_INDICATOR_CLOSE_ACTION_SIZE_DP = 28
internal const val BROWSER_MINIMIZED_INDICATOR_CLOSE_ACTION_OVERLAP_DP = 10

internal enum class BrowserMinimizedIndicatorCloseEvent {
    LONG_PRESS_RECOGNIZED,
    GESTURE_FINISHED,
    HIDE_TIMEOUT,
    RESET,
}

internal data class BrowserMinimizedIndicatorCloseState(
    val isCloseActionVisible: Boolean = false,
    val isLongPressGestureActive: Boolean = false,
)

internal data class BrowserMinimizedIndicatorCloseTransition(
    val state: BrowserMinimizedIndicatorCloseState,
    val cancelPendingHide: Boolean = false,
    val scheduleHideAfterMillis: Long? = null,
)

internal object BrowserMinimizedIndicatorClosePolicy {
    fun reduce(
        state: BrowserMinimizedIndicatorCloseState,
        event: BrowserMinimizedIndicatorCloseEvent,
    ): BrowserMinimizedIndicatorCloseTransition =
        when (event) {
            BrowserMinimizedIndicatorCloseEvent.LONG_PRESS_RECOGNIZED ->
                BrowserMinimizedIndicatorCloseTransition(
                    state =
                        state.copy(
                            isCloseActionVisible = true,
                            isLongPressGestureActive = true,
                        ),
                    cancelPendingHide = true,
                )

            BrowserMinimizedIndicatorCloseEvent.GESTURE_FINISHED ->
                if (state.isLongPressGestureActive) {
                    BrowserMinimizedIndicatorCloseTransition(
                        state = state.copy(isLongPressGestureActive = false),
                        cancelPendingHide = true,
                        scheduleHideAfterMillis =
                            BROWSER_MINIMIZED_INDICATOR_CLOSE_TIMEOUT_MILLIS,
                    )
                } else {
                    BrowserMinimizedIndicatorCloseTransition(state = state)
                }

            BrowserMinimizedIndicatorCloseEvent.HIDE_TIMEOUT,
            BrowserMinimizedIndicatorCloseEvent.RESET ->
                BrowserMinimizedIndicatorCloseTransition(
                    state = BrowserMinimizedIndicatorCloseState(),
                    cancelPendingHide = true,
                )
        }
}

internal data class BrowserMinimizedIndicatorCloseOverlayPosition(
    val x: Int,
    val y: Int,
)

internal enum class BrowserMinimizedIndicatorCloseOverlayUpdate {
    NONE,
    SHOW,
    HIDE,
    UPDATE_TOUCHABILITY,
}

internal object BrowserMinimizedIndicatorCloseOverlayPolicy {
    fun resolveUpdate(
        previousState: BrowserMinimizedIndicatorCloseState,
        currentState: BrowserMinimizedIndicatorCloseState,
    ): BrowserMinimizedIndicatorCloseOverlayUpdate =
        when {
            !previousState.isCloseActionVisible && currentState.isCloseActionVisible ->
                BrowserMinimizedIndicatorCloseOverlayUpdate.SHOW

            previousState.isCloseActionVisible && !currentState.isCloseActionVisible ->
                BrowserMinimizedIndicatorCloseOverlayUpdate.HIDE

            previousState.isCloseActionVisible &&
                currentState.isCloseActionVisible &&
                previousState.isLongPressGestureActive !=
                    currentState.isLongPressGestureActive ->
                BrowserMinimizedIndicatorCloseOverlayUpdate.UPDATE_TOUCHABILITY

            else -> BrowserMinimizedIndicatorCloseOverlayUpdate.NONE
        }

    fun windowFlags(isLongPressGestureActive: Boolean): Int {
        val baseFlags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        // 叉号在按住期间只负责显示，不能成为新的触摸目标并中断 indicator 的当前手势。
        return if (isLongPressGestureActive) {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseFlags
        }
    }

    fun resolvePosition(
        indicatorX: Int,
        indicatorY: Int,
        indicatorWidth: Int,
        closeActionSize: Int,
        overlap: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): BrowserMinimizedIndicatorCloseOverlayPosition {
        val maxX = (screenWidth - closeActionSize).coerceAtLeast(0)
        val maxY = (screenHeight - closeActionSize).coerceAtLeast(0)
        return BrowserMinimizedIndicatorCloseOverlayPosition(
            x = (indicatorX + indicatorWidth - overlap).coerceIn(0, maxX),
            y = (indicatorY - closeActionSize + overlap).coerceIn(0, maxY),
        )
    }
}
