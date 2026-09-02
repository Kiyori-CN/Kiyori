package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.kiyori.platform.network.KiyoriNetworkProxyReadiness

class BrowserNavigationPolicyTest {
    @Test
    fun `startup proxy barrier only covers remote documents while not ready`() {
        assertTrue(
            shouldAwaitStartupProxyBeforeBrowserNavigation(
                targetUrl = "https://example.com",
                readiness = KiyoriNetworkProxyReadiness.RECONCILING,
            ),
        )
        assertFalse(
            shouldAwaitStartupProxyBeforeBrowserNavigation(
                targetUrl = "about:blank",
                readiness = KiyoriNetworkProxyReadiness.RECONCILING,
            ),
        )
        assertFalse(
            shouldAwaitStartupProxyBeforeBrowserNavigation(
                targetUrl = "file:///android_asset/home.html",
                readiness = KiyoriNetworkProxyReadiness.FAILED,
            ),
        )
        assertFalse(
            shouldAwaitStartupProxyBeforeBrowserNavigation(
                targetUrl = "https://example.com",
                readiness = KiyoriNetworkProxyReadiness.READY,
            ),
        )
        assertTrue(
            shouldAwaitStartupProxyBeforeBrowserNavigation(
                targetUrl = "https://example.com",
                readiness = KiyoriNetworkProxyReadiness.NOT_STARTED,
            ),
        )
    }
    @Test
    fun `site identity uses registrable domain and exact local hosts`() {
        assertEquals(
            browserSiteIdentity("https://www.example.co.uk/path"),
            browserSiteIdentity("https://cdn.example.co.uk/asset"),
        )
        assertEquals(
            browserSiteIdentity("http://127.0.0.1:8000"),
            browserSiteIdentity("https://127.0.0.1:9443"),
        )
        assertEquals(
            browserSiteIdentity("http://localhost:3000"),
            browserSiteIdentity("https://localhost"),
        )
        assertFalse(
            areBrowserUrlsSameSite(
                "https://example.com",
                "https://example.net",
            ),
        )
        assertNotNull(browserSiteIdentity("https://example.com"))
        assertNull(browserSiteIdentity("about:blank"))
    }

    @Test
    fun `search recovery remains attached only while the loaded result stays current`() {
        assertFalse(
            shouldClearBrowserSearchRecoveryOnNavigation(
                pageLoaded = false,
                searchRecoveryPending = true,
                resolvedResultUrl = "https://example.com/result",
                targetUrl = "https://example.net/redirect",
            ),
        )
        assertFalse(
            shouldClearBrowserSearchRecoveryOnNavigation(
                pageLoaded = true,
                searchRecoveryPending = false,
                resolvedResultUrl = "https://example.com/result",
                targetUrl = "https://example.com:443/result",
            ),
        )
        assertTrue(
            shouldClearBrowserSearchRecoveryOnNavigation(
                pageLoaded = true,
                searchRecoveryPending = false,
                resolvedResultUrl = "https://example.com/result",
                targetUrl = "https://example.net/article",
            ),
        )
    }

    @Test
    fun `search-created sessions skip their synthetic about blank history entry`() {
        assertFalse(
            shouldUseBrowserHistoryBack(
                creationReason = BrowserWindowCreationReason.SOFTWARE_HOME_SEARCH,
                canGoBack = true,
                backTargetUrl = "about:blank",
                backTargetIsInitialSyntheticEntry = true,
            ),
        )
        assertFalse(
            shouldUseBrowserHistoryBack(
                creationReason = BrowserWindowCreationReason.PROFILE_BOUNDARY_SEARCH,
                canGoBack = true,
                backTargetUrl = "ABOUT:BLANK",
                backTargetIsInitialSyntheticEntry = true,
            ),
        )
        assertTrue(
            shouldUseBrowserHistoryBack(
                creationReason = BrowserWindowCreationReason.SOFTWARE_HOME_SEARCH,
                canGoBack = true,
                backTargetUrl = "https://example.com/home",
                backTargetIsInitialSyntheticEntry = true,
            ),
        )
        assertTrue(
            shouldUseBrowserHistoryBack(
                creationReason = BrowserWindowCreationReason.MANUAL_NEW_WINDOW,
                canGoBack = true,
                backTargetUrl = "about:blank",
                backTargetIsInitialSyntheticEntry = true,
            ),
        )
        assertFalse(
            shouldUseBrowserHistoryBack(
                creationReason = BrowserWindowCreationReason.SOFTWARE_HOME_SEARCH,
                canGoBack = false,
                backTargetUrl = "https://example.com/home",
                backTargetIsInitialSyntheticEntry = true,
            ),
        )
        assertTrue(
            shouldUseBrowserHistoryBack(
                creationReason = BrowserWindowCreationReason.SOFTWARE_HOME_SEARCH,
                canGoBack = true,
                backTargetUrl = "about:blank",
                backTargetIsInitialSyntheticEntry = false,
            ),
        )
    }

    @Test
    fun `ordinary same-site and cross-site navigation stays in the current session`() {
        listOf(
            "https://example.com/next",
            "https://other.example/path",
        ).forEach { target ->
            assertEquals(
                BrowserWindowNavigationDecision.CURRENT_SESSION,
                resolveBrowserWindowNavigationDecision(
                    BrowserWindowNavigationRequest(
                        sourceUrl = "https://example.com/page",
                        targetUrl = target,
                        isMainFrame = true,
                        hasUserGesture = true,
                        isPopup = false,
                        sourceAtConfiguredHome = false,
                    ),
                ),
            )
        }
    }

    @Test
    fun `configured home creates a child only for a user cross-site navigation`() {
        assertEquals(
            BrowserWindowNavigationDecision.CURRENT_SESSION,
            resolveBrowserWindowNavigationDecision(
                BrowserWindowNavigationRequest(
                    sourceUrl = "https://home.example.com",
                    targetUrl = "https://news.example.com",
                    isMainFrame = true,
                    hasUserGesture = true,
                    isPopup = false,
                    sourceAtConfiguredHome = true,
                ),
            ),
        )
        assertEquals(
            BrowserWindowNavigationDecision.CREATE_CHILD_SESSION,
            resolveBrowserWindowNavigationDecision(
                BrowserWindowNavigationRequest(
                    sourceUrl = "https://home.example.com",
                    targetUrl = "https://example.org",
                    isMainFrame = true,
                    hasUserGesture = true,
                    isPopup = false,
                    sourceAtConfiguredHome = true,
                ),
            ),
        )
        assertEquals(
            BrowserWindowNavigationDecision.CURRENT_SESSION,
            resolveBrowserWindowNavigationDecision(
                BrowserWindowNavigationRequest(
                    sourceUrl = "https://home.example.com",
                    targetUrl = "https://example.org",
                    isMainFrame = true,
                    hasUserGesture = false,
                    isPopup = false,
                    sourceAtConfiguredHome = true,
                ),
            ),
        )
    }

    @Test
    fun `popup requires a user gesture and a stable HTTP target`() {
        assertEquals(
            BrowserWindowNavigationDecision.REJECT,
            resolveBrowserWindowNavigationDecision(
                BrowserWindowNavigationRequest(
                    sourceUrl = "https://example.com",
                    targetUrl = "https://example.org",
                    isMainFrame = true,
                    hasUserGesture = false,
                    isPopup = true,
                    sourceAtConfiguredHome = false,
                ),
            ),
        )
        assertEquals(
            BrowserWindowNavigationDecision.REJECT,
            resolveBrowserWindowNavigationDecision(
                BrowserWindowNavigationRequest(
                    sourceUrl = "https://example.com",
                    targetUrl = "about:blank",
                    isMainFrame = true,
                    hasUserGesture = true,
                    isPopup = true,
                    sourceAtConfiguredHome = false,
                ),
            ),
        )
        assertEquals(
            BrowserWindowNavigationDecision.CURRENT_SESSION,
            resolveBrowserWindowNavigationDecision(
                BrowserWindowNavigationRequest(
                    sourceUrl = "https://example.com/page",
                    targetUrl = "https://example.org",
                    isMainFrame = true,
                    hasUserGesture = true,
                    isPopup = true,
                    sourceAtConfiguredHome = false,
                ),
            ),
        )
    }

    @Test
    fun `home opener back closes only a valid automatic child`() {
        assertEquals(
            BrowserSessionRootBackAction.CLOSE_AND_ACTIVATE_OPENER_HOME,
            resolveBrowserSessionRootBackAction(
                creationReason =
                    BrowserWindowCreationReason.HOME_CROSS_SITE_USER_NAVIGATION,
                openerHomeSessionExists = true,
                openerProfileMatches = true,
                openerStillAtConfiguredHome = true,
            ),
        )
        assertEquals(
            BrowserSessionRootBackAction.NAVIGATE_TO_CONFIGURED_HOME,
            resolveBrowserSessionRootBackAction(
                creationReason = BrowserWindowCreationReason.MANUAL_NEW_WINDOW,
                openerHomeSessionExists = true,
                openerProfileMatches = true,
                openerStillAtConfiguredHome = true,
            ),
        )
        assertTrue(
            BrowserWindowCreationReason.entries.contains(
                BrowserWindowCreationReason.RESTORED_NORMAL_WINDOW,
            ),
        )
    }
}
