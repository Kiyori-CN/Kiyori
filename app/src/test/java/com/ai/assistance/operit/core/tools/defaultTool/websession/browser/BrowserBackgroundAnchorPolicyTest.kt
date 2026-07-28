package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserBackgroundAnchorPolicyTest {
    @Test
    fun `background anchor cannot accept focus or touch`() {
        val flags = BrowserBackgroundAnchorPolicy.anchorFlags()

        assertTrue(flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertFalse(flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
    }

    @Test
    fun `app shell presentation has priority over the background anchor`() {
        assertEquals(
            BrowserPresentationTarget.APP_SHELL,
            BrowserBackgroundAnchorPolicy.resolvePresentationTarget(
                appPresentationActive = true,
                backgroundAnchorAttached = true,
            ),
        )
        assertEquals(
            BrowserPresentationTarget.BACKGROUND_ANCHOR,
            BrowserBackgroundAnchorPolicy.resolvePresentationTarget(
                appPresentationActive = false,
                backgroundAnchorAttached = true,
            ),
        )
        assertEquals(
            BrowserPresentationTarget.DETACHED,
            BrowserBackgroundAnchorPolicy.resolvePresentationTarget(
                appPresentationActive = false,
                backgroundAnchorAttached = false,
            ),
        )
    }

    @Test
    fun `repeated request for the current owner is a no-op`() {
        assertEquals(
            BrowserPresentationTransferPlan.NO_OP,
            BrowserBackgroundAnchorPolicy.resolveTransferPlan(
                currentTarget = BrowserPresentationTarget.BACKGROUND_ANCHOR,
                requestedTarget = BrowserPresentationTarget.BACKGROUND_ANCHOR,
                requestedTargetAlreadyOwnsActiveWebView = true,
            ),
        )
    }

    @Test
    fun `cross ViewRoot transfer waits for a render frame`() {
        assertEquals(
            BrowserPresentationTransferPlan.ATTACH_AFTER_FRAME,
            BrowserBackgroundAnchorPolicy.resolveTransferPlan(
                currentTarget = BrowserPresentationTarget.APP_SHELL,
                requestedTarget = BrowserPresentationTarget.BACKGROUND_ANCHOR,
                requestedTargetAlreadyOwnsActiveWebView = false,
            ),
        )
        assertEquals(
            BrowserPresentationTransferPlan.ATTACH_AFTER_FRAME,
            BrowserBackgroundAnchorPolicy.resolveTransferPlan(
                currentTarget = BrowserPresentationTarget.BACKGROUND_ANCHOR,
                requestedTarget = BrowserPresentationTarget.APP_SHELL,
                requestedTargetAlreadyOwnsActiveWebView = false,
            ),
        )
    }

    @Test
    fun `detached WebView can attach without an extra frame`() {
        assertEquals(
            BrowserPresentationTransferPlan.ATTACH_NOW,
            BrowserBackgroundAnchorPolicy.resolveTransferPlan(
                currentTarget = BrowserPresentationTarget.DETACHED,
                requestedTarget = BrowserPresentationTarget.APP_SHELL,
                requestedTargetAlreadyOwnsActiveWebView = false,
            ),
        )
    }

    @Test
    fun `detached target only removes the current owner`() {
        assertEquals(
            BrowserPresentationTransferPlan.DETACH_ONLY,
            BrowserBackgroundAnchorPolicy.resolveTransferPlan(
                currentTarget = BrowserPresentationTarget.APP_SHELL,
                requestedTarget = BrowserPresentationTarget.DETACHED,
                requestedTargetAlreadyOwnsActiveWebView = false,
            ),
        )
    }
}
