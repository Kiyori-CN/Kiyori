package com.ai.assistance.operit.ui.features.browser.appshell

import com.ai.assistance.operit.core.browser.presentation.BrowserAppPresentationReleaseMode
import com.kiyori.capability.browser.presentation.KiyoriBrowserExitPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriBrowserHomeNavigationPolicyTest {
    @Test
    fun `top bar exits to the recorded entry page while system Back keeps browser navigation`() {
        assertEquals(
            KiyoriBrowserHomeBackAction.EXIT_TO_ENTRY_PAGE,
            resolveKiyoriBrowserHomeBackAction(KiyoriBrowserHomeBackSource.TOP_BAR),
        )
        assertEquals(
            KiyoriBrowserHomeBackAction.HANDLE_BROWSER_BACK_STACK,
            resolveKiyoriBrowserHomeBackAction(KiyoriBrowserHomeBackSource.SYSTEM_BACK),
        )
    }

    @Test
    fun `entry page return keeps the recorded presentation release mode`() {
        assertEquals(
            BrowserAppPresentationReleaseMode.DESTROY,
            resolveBrowserAppPresentationReleaseMode(KiyoriBrowserExitPresentation.CLOSE),
        )
        assertEquals(
            BrowserAppPresentationReleaseMode.MINIMIZE,
            resolveBrowserAppPresentationReleaseMode(
                KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
            ),
        )
    }

    @Test
    fun `queued foreground URL waits for the Browser Home presentation lease`() {
        assertFalse(
            shouldDispatchPendingForegroundBrowserUrl(
                hasPresentationLease = false,
                pendingForegroundUrl = "https://example.com",
            ),
        )
        assertFalse(
            shouldDispatchPendingForegroundBrowserUrl(
                hasPresentationLease = true,
                pendingForegroundUrl = null,
            ),
        )
        assertTrue(
            shouldDispatchPendingForegroundBrowserUrl(
                hasPresentationLease = true,
                pendingForegroundUrl = "https://example.com",
            ),
        )
    }
}
