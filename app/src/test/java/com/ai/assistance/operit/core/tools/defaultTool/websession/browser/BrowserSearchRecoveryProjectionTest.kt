package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import com.kiyori.capability.browser.presentation.KiyoriBrowserSearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSearchRecoveryProjectionTest {
    @Test
    fun `new recovery hydrates first search after session was already projected`() {
        val recovery = searchRecovery()
        val sessionBeforeRecovery =
            WebSessionBrowserHostState()
                .hydrateProjectedSearchRecovery(
                    activeSessionId = "search-session",
                    currentPageUrl = "https://example.com/search?q=first+query",
                    searchRecovery = null,
                )

        val hydrated =
            sessionBeforeRecovery.hydrateProjectedSearchRecovery(
                activeSessionId = "search-session",
                currentPageUrl = recovery.resolvedResultUrl,
                searchRecovery = recovery,
            )

        assertEquals("first query", hydrated.lastSearchQuery)
        assertTrue(hydrated.isSearchEngineQuickSwitchBarVisible)
    }

    @Test
    fun `same recovery preserves manual dismissal within session`() {
        val recovery = searchRecovery()
        val dismissed =
            WebSessionBrowserHostState()
                .hydrateProjectedSearchRecovery(
                    activeSessionId = "search-session",
                    currentPageUrl = recovery.resolvedResultUrl,
                    searchRecovery = recovery,
                )
                .copy(isSearchEngineQuickSwitchBarVisible = false)

        val projectedAgain =
            dismissed.hydrateProjectedSearchRecovery(
                activeSessionId = "search-session",
                currentPageUrl = recovery.resolvedResultUrl,
                searchRecovery = recovery,
            )

        assertFalse(projectedAgain.isSearchEngineQuickSwitchBarVisible)
        assertEquals("first query", projectedAgain.lastSearchQuery)
    }

    @Test
    fun `custom home hides stale recovery chrome`() {
        val recovery = searchRecovery()
        val projected =
            WebSessionBrowserHostState()
                .hydrateProjectedSearchRecovery(
                    activeSessionId = "search-session",
                    currentPageUrl = "https://home.example/",
                    searchRecovery = recovery,
                )

        assertEquals("", projected.lastSearchQuery)
        assertFalse(projected.isSearchEngineQuickSwitchBarVisible)
    }

    @Test
    fun `same site child page hides recovery chrome`() {
        val recovery = searchRecovery()
        val resultPage =
            WebSessionBrowserHostState()
                .hydrateProjectedSearchRecovery(
                    activeSessionId = "search-session",
                    currentPageUrl = recovery.resolvedResultUrl,
                    searchRecovery = recovery,
                )
        val childPage =
            resultPage.hydrateProjectedSearchRecovery(
                activeSessionId = "search-session",
                currentPageUrl = "https://example.com/article/child",
                searchRecovery = recovery,
            )

        assertFalse(childPage.isSearchEngineQuickSwitchBarVisible)
        assertEquals("", childPage.lastSearchQuery)
    }

    @Test
    fun `returning to custom home clears query when recovery identity is unchanged`() {
        val recovery = searchRecovery()
        val resultPage =
            WebSessionBrowserHostState()
                .hydrateProjectedSearchRecovery(
                    activeSessionId = "search-session",
                    currentPageUrl = recovery.resolvedResultUrl,
                    searchRecovery = recovery,
                )

        val homePage =
            resultPage.hydrateProjectedSearchRecovery(
                activeSessionId = "search-session",
                currentPageUrl = "https://home.example/",
                searchRecovery = recovery,
            )

        assertEquals("", homePage.lastSearchQuery)
        assertFalse(homePage.isSearchEngineQuickSwitchBarVisible)
    }

    @Test
    fun `resolved redirect displays chrome only after exact result url is projected`() {
        val pendingRecovery = searchRecovery()
        val redirectedRecovery =
            pendingRecovery.copy(
                resolvedResultUrl = "https://search.example/result?q=first+query",
            )
        val redirecting =
            WebSessionBrowserHostState()
                .hydrateProjectedSearchRecovery(
                    activeSessionId = "search-session",
                    currentPageUrl = redirectedRecovery.resolvedResultUrl,
                    searchRecovery = pendingRecovery,
                )
        val resolved =
            redirecting.hydrateProjectedSearchRecovery(
                activeSessionId = "search-session",
                currentPageUrl = redirectedRecovery.resolvedResultUrl,
                searchRecovery = redirectedRecovery,
            )

        assertFalse(redirecting.isSearchEngineQuickSwitchBarVisible)
        assertEquals("first query", resolved.lastSearchQuery)
        assertTrue(resolved.isSearchEngineQuickSwitchBarVisible)
    }

    @Test
    fun `full screen submission hides chrome until result page projection arrives`() {
        val recovery = searchRecovery()
        val searchScreen =
            WebSessionBrowserHostState(
                isSearchVisible = true,
                isSearchEnginePanelVisible = true,
                searchDraft = "next query",
                lastSearchQuery = "previous query",
                isSearchEngineQuickSwitchBarVisible = true,
            )
        val navigating = searchScreen.prepareForFullScreenSearchNavigation()
        val resultPage =
            navigating.hydrateProjectedSearchRecovery(
                activeSessionId = "search-session",
                currentPageUrl = recovery.resolvedResultUrl,
                searchRecovery = recovery,
            )

        assertFalse(navigating.isSearchVisible)
        assertFalse(navigating.isSearchEnginePanelVisible)
        assertEquals("", navigating.searchDraft)
        assertEquals("", navigating.lastSearchQuery)
        assertFalse(navigating.isSearchEngineQuickSwitchBarVisible)
        assertEquals("first query", resultPage.lastSearchQuery)
        assertTrue(resultPage.isSearchEngineQuickSwitchBarVisible)
    }

    @Test
    fun `result projection after pre navigation reset is not mistaken for manual dismissal`() {
        val recovery = searchRecovery()
        val preNavigation =
            WebSessionBrowserHostState(
                lastSearchQuery = "previous query",
                isSearchEngineQuickSwitchBarVisible = true,
            ).prepareForFullScreenSearchNavigation()

        val projected =
            preNavigation.hydrateProjectedSearchRecovery(
                activeSessionId = "search-session",
                currentPageUrl = recovery.resolvedResultUrl,
                searchRecovery = recovery,
            )

        assertEquals("first query", projected.lastSearchQuery)
        assertTrue(projected.isSearchEngineQuickSwitchBarVisible)
        assertTrue(projected.projectedSearchRecoveryPageIsResult)
    }

    @Test
    fun `switching sessions hides and restores search recovery chrome`() {
        val recovery = searchRecovery()
        val searchSession =
            WebSessionBrowserHostState()
                .hydrateProjectedSearchRecovery(
                    activeSessionId = "search-session",
                    currentPageUrl = recovery.resolvedResultUrl,
                    searchRecovery = recovery,
                )
        val normalSession =
            searchSession.hydrateProjectedSearchRecovery(
                activeSessionId = "normal-session",
                currentPageUrl = "https://ordinary.example/",
                searchRecovery = null,
            )
        val restoredSearchSession =
            normalSession.hydrateProjectedSearchRecovery(
                activeSessionId = "search-session",
                currentPageUrl = recovery.resolvedResultUrl,
                searchRecovery = recovery,
            )

        assertEquals("", normalSession.lastSearchQuery)
        assertFalse(normalSession.isSearchEngineQuickSwitchBarVisible)
        assertEquals("first query", restoredSearchSession.lastSearchQuery)
        assertTrue(restoredSearchSession.isSearchEngineQuickSwitchBarVisible)
    }

    private fun searchRecovery(): BrowserSessionSearchRecovery =
        BrowserSessionSearchRecovery(
            query = "first query",
            engineId = "bing",
            source = KiyoriBrowserSearchSource.SOFTWARE_HOME,
            requestedUrl = "https://example.com/search?q=first+query",
            resolvedResultUrl = "https://example.com/search?q=first+query",
            submittedAt = 123L,
        )
}
