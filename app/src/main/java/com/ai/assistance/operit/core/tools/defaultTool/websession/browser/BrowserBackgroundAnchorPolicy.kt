package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.view.WindowManager

internal enum class BrowserPresentationTarget {
    DETACHED,
    APP_SHELL,
    BACKGROUND_ANCHOR,
}

internal enum class BrowserPresentationTransferPlan {
    NO_OP,
    DETACH_ONLY,
    ATTACH_NOW,
    ATTACH_AFTER_FRAME,
}

internal object BrowserBackgroundAnchorPolicy {
    fun anchorFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    fun resolvePresentationTarget(
        appPresentationActive: Boolean,
        backgroundAnchorAttached: Boolean,
    ): BrowserPresentationTarget =
        when {
            appPresentationActive -> BrowserPresentationTarget.APP_SHELL
            backgroundAnchorAttached -> BrowserPresentationTarget.BACKGROUND_ANCHOR
            else -> BrowserPresentationTarget.DETACHED
        }

    fun resolveTransferPlan(
        currentTarget: BrowserPresentationTarget,
        requestedTarget: BrowserPresentationTarget,
        requestedTargetAlreadyOwnsActiveWebView: Boolean,
    ): BrowserPresentationTransferPlan =
        when {
            currentTarget == requestedTarget && requestedTargetAlreadyOwnsActiveWebView ->
                BrowserPresentationTransferPlan.NO_OP
            requestedTarget == BrowserPresentationTarget.DETACHED ->
                BrowserPresentationTransferPlan.DETACH_ONLY
            currentTarget == BrowserPresentationTarget.DETACHED ||
                currentTarget == requestedTarget ->
                BrowserPresentationTransferPlan.ATTACH_NOW
            else ->
                BrowserPresentationTransferPlan.ATTACH_AFTER_FRAME
        }
}
