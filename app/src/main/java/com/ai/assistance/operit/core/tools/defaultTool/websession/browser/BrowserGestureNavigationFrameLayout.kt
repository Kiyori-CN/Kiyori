package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import androidx.core.content.getSystemService
import kotlin.math.abs

internal class BrowserGestureNavigationFrameLayout(
    context: Context,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val viewConfiguration = ViewConfiguration.get(context)
    private val tracker =
        BrowserGestureNavigationTracker(
            BrowserGestureNavigationConfiguration(
                edgeWidthPx = 48f * density,
                commitDistancePx = 72f * density,
                fastSwipeVelocityPxPerSecond = 800f * density,
            ),
        )
    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var intercepting = false
    private var gestureCandidate = false
    private var childDisallowIntercept = false

    var gestureNavigationEnabled: Boolean = false
    var gestureNavigationBlocked: Boolean = false
    var canNavigateBack: Boolean = false
    var canNavigateForward: Boolean = false
    var onNavigateBack: (() -> Unit)? = null
    var onNavigateForward: (() -> Unit)? = null

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                intercepting = false
                downX = event.x
                downY = event.y
                gestureCandidate = canStartGesture()
                childDisallowIntercept = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                tracker.onDown(
                    x = event.x,
                    y = event.y,
                    viewportWidthPx = width.coerceAtLeast(1).toFloat(),
                    enabled = gestureCandidate,
                    pointerCount = event.pointerCount,
                )
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                tracker.onPointerCountChanged(event.pointerCount)
                if (event.pointerCount != 1) {
                    gestureCandidate = false
                    releaseChildDisallowIntercept()
                }
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (!intercepting && event.pointerCount == 1) {
                    val deltaX = event.x - downX
                    val deltaY = event.y - downY
                    val edgeWidthPx = 48f * density
                    val movingRight = deltaX > 0f
                    val movingLeft = deltaX < 0f
                    val reachesLeftEdge = event.x <= edgeWidthPx
                    val reachesRightEdge = event.x >= width - edgeWidthPx
                    val reachesNavigationBoundary =
                        (movingRight &&
                            (downX <= edgeWidthPx || reachesRightEdge)) ||
                            (movingLeft &&
                                (downX >= width - edgeWidthPx || reachesLeftEdge))
                    val movedPastTouchSlop = abs(deltaX) > viewConfiguration.scaledTouchSlop
                    val horizontalDominates = abs(deltaX) >= abs(deltaY) * 1.5f
                    val canStart = canStartGesture()
                    intercepting =
                        gestureCandidate &&
                            canStart &&
                            reachesNavigationBoundary &&
                            movedPastTouchSlop &&
                            horizontalDominates
                    if (
                        shouldCancelBrowserGestureCandidate(
                            gestureCandidate = gestureCandidate,
                            canStart = canStart,
                            movedPastTouchSlop = movedPastTouchSlop,
                            horizontalDominates = horizontalDominates,
                        )
                    ) {
                        // A vertical-dominant gesture is page-owned. A horizontal gesture that
                        // has not reached an edge must remain a candidate: users may begin in the
                        // page body and continue to the matching edge before we can promote it.
                        gestureCandidate = false
                        releaseChildDisallowIntercept()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!intercepting) {
                    finishGesture(event)
                }
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return intercepting
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker?.addMovement(event)
        val wasIntercepting = intercepting
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN ->
                tracker.onPointerCountChanged(event.pointerCount)
            MotionEvent.ACTION_UP -> {
                finishGesture(event)
                if (!wasIntercepting) {
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return wasIntercepting || intercepting || super.onTouchEvent(event)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && gestureCandidate && !intercepting) {
            // WebView requests this while it starts scrolling. Keep observing the current
            // single-finger candidate so an edge-reaching swipe can still be promoted to the
            // parent before the child owns the remainder of the gesture.
            childDisallowIntercept = true
            return
        }
        childDisallowIntercept = false
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun performClick(): Boolean {
        // A completed edge navigation swipe never reaches this path; retaining the standard
        // click entry point keeps non-intercepted container clicks accessible.
        return super.performClick()
    }

    private fun canStartGesture(): Boolean {
        if (!gestureNavigationEnabled || gestureNavigationBlocked) {
            return false
        }
        val accessibilityManager = context.getSystemService<AccessibilityManager>()
        return accessibilityManager?.isTouchExplorationEnabled != true
    }

    private fun finishGesture(event: MotionEvent) {
        val currentVelocityTracker = velocityTracker
        currentVelocityTracker?.computeCurrentVelocity(
            1_000,
            viewConfiguration.scaledMaximumFlingVelocity.toFloat(),
        )
        val velocityX =
            currentVelocityTracker?.getXVelocity(event.getPointerId(event.actionIndex)) ?: 0f
        when (tracker.onUp(event.x, event.y, velocityX)) {
            BrowserGestureNavigationAction.BACK ->
                if (canNavigateBack) {
                    onNavigateBack?.invoke()
                }
            BrowserGestureNavigationAction.FORWARD ->
                if (canNavigateForward) {
                    onNavigateForward?.invoke()
                }
            BrowserGestureNavigationAction.NONE -> Unit
        }
        velocityTracker?.recycle()
        velocityTracker = null
        intercepting = false
        gestureCandidate = false
        childDisallowIntercept = false
        super.requestDisallowInterceptTouchEvent(false)
    }

    private fun cancelGesture() {
        tracker.cancel()
        velocityTracker?.recycle()
        velocityTracker = null
        intercepting = false
        gestureCandidate = false
        childDisallowIntercept = false
        super.requestDisallowInterceptTouchEvent(false)
    }

    private fun releaseChildDisallowIntercept() {
        if (!childDisallowIntercept) {
            return
        }
        childDisallowIntercept = false
        super.requestDisallowInterceptTouchEvent(true)
    }
}
