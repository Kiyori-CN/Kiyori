package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import kotlin.math.abs

internal enum class BrowserGestureNavigationAction {
    NONE,
    BACK,
    FORWARD,
}

internal fun shouldCancelBrowserGestureCandidate(
    gestureCandidate: Boolean,
    canStart: Boolean,
    movedPastTouchSlop: Boolean,
    horizontalDominates: Boolean,
): Boolean =
    gestureCandidate &&
        (!canStart ||
            (movedPastTouchSlop && !horizontalDominates))

internal data class BrowserGestureNavigationConfiguration(
    val edgeWidthPx: Float,
    val commitDistancePx: Float,
    val horizontalToVerticalRatio: Float = 1.5f,
    val fastSwipeVelocityPxPerSecond: Float,
) {
    init {
        require(edgeWidthPx > 0f) { "Gesture edge width must be positive" }
        require(commitDistancePx > 0f) { "Gesture commit distance must be positive" }
        require(horizontalToVerticalRatio > 1f) {
            "Gesture horizontal-to-vertical ratio must be greater than one"
        }
        require(fastSwipeVelocityPxPerSecond > 0f) {
            "Gesture fast-swipe velocity must be positive"
        }
    }
}

internal class BrowserGestureNavigationTracker(
    private val configuration: BrowserGestureNavigationConfiguration,
) {
    private enum class Edge {
        LEFT,
        RIGHT,
    }

    private var edge: Edge? = null
    private var startX = 0f
    private var startY = 0f
    private var viewportWidthPx = 0f
    private var cancelled = false

    fun onDown(
        x: Float,
        y: Float,
        viewportWidthPx: Float,
        enabled: Boolean,
        pointerCount: Int,
    ) {
        require(viewportWidthPx > 0f) { "Gesture viewport width must be positive" }
        this.viewportWidthPx = viewportWidthPx
        edge =
            when {
                !enabled || pointerCount != 1 -> null
                x <= configuration.edgeWidthPx -> Edge.LEFT
                x >= viewportWidthPx - configuration.edgeWidthPx -> Edge.RIGHT
                else -> null
            }
        startX = x
        startY = y
        cancelled = !enabled || pointerCount != 1
    }

    fun onPointerCountChanged(pointerCount: Int) {
        if (pointerCount != 1) {
            cancelled = true
        }
    }

    fun cancel() {
        cancelled = true
        edge = null
    }

    fun onUp(
        x: Float,
        y: Float,
        velocityXPxPerSecond: Float,
    ): BrowserGestureNavigationAction {
        val activeEdge = edge
        edge = null
        if (cancelled) {
            cancelled = false
            return BrowserGestureNavigationAction.NONE
        }
        cancelled = false
        val deltaX = x - startX
        val deltaY = y - startY
        val reachedLeftEdge = x <= configuration.edgeWidthPx
        val reachedRightEdge = x >= viewportWidthPx - configuration.edgeWidthPx
        val direction =
            when {
                deltaX > 0f -> BrowserGestureNavigationAction.BACK
                deltaX < 0f -> BrowserGestureNavigationAction.FORWARD
                else -> BrowserGestureNavigationAction.NONE
            }
        val edgeBoundaryInvolved =
            when (direction) {
                // A rightward swipe is a Back gesture when it starts at the left edge or
                // finishes at the right edge. The inverse applies to Forward.
                BrowserGestureNavigationAction.BACK ->
                    activeEdge == Edge.LEFT || (activeEdge == null && reachedRightEdge)
                BrowserGestureNavigationAction.FORWARD ->
                    activeEdge == Edge.RIGHT || (activeEdge == null && reachedLeftEdge)
                BrowserGestureNavigationAction.NONE -> false
            }
        if (!edgeBoundaryInvolved) {
            return BrowserGestureNavigationAction.NONE
        }
        val horizontalDominates =
            abs(deltaX) >= abs(deltaY) * configuration.horizontalToVerticalRatio
        if (!horizontalDominates) {
            return BrowserGestureNavigationAction.NONE
        }
        val distanceCommitted = abs(deltaX) >= configuration.commitDistancePx
        val velocityCommitted =
            abs(velocityXPxPerSecond) >= configuration.fastSwipeVelocityPxPerSecond
        if (!distanceCommitted && !velocityCommitted) {
            return BrowserGestureNavigationAction.NONE
        }
        return direction
    }
}
