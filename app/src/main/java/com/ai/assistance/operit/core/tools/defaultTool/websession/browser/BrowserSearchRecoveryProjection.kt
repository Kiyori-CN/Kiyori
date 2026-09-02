package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

internal fun WebSessionBrowserHostState.hydrateProjectedSearchRecovery(
    activeSessionId: String?,
    currentPageUrl: String,
    searchRecovery: BrowserSessionSearchRecovery?,
): WebSessionBrowserHostState {
    val projectionKey = searchRecovery?.toProjectionKey()
    val currentPageIsSearchResult =
        searchRecovery != null &&
            areBrowserHomeUrlsEquivalent(
                currentPageUrl,
                searchRecovery.resolvedResultUrl,
            )
    val sameRecoveryProjection =
        projectedSearchRecoverySessionId == activeSessionId &&
            projectedSearchRecoveryKey == projectionKey
    if (!sameRecoveryProjection) {
        return copy(
            lastSearchQuery = searchRecovery?.query?.takeIf { currentPageIsSearchResult }.orEmpty(),
            isSearchEngineQuickSwitchBarVisible = currentPageIsSearchResult,
            projectedSearchRecoverySessionId = activeSessionId,
            projectedSearchRecoveryKey = projectionKey,
            projectedSearchRecoveryPageIsResult = currentPageIsSearchResult,
        )
    }
    if (projectedSearchRecoveryPageIsResult == currentPageIsSearchResult) {
        return this
    }
    return copy(
        lastSearchQuery =
            if (currentPageIsSearchResult) {
                searchRecovery.query
            } else {
                ""
            },
        isSearchEngineQuickSwitchBarVisible = currentPageIsSearchResult,
        projectedSearchRecoveryPageIsResult = currentPageIsSearchResult,
    )
}

internal fun WebSessionBrowserHostState.prepareForFullScreenSearchNavigation():
    WebSessionBrowserHostState =
    copy(
        isSearchVisible = false,
        isSearchEnginePanelVisible = false,
        searchDraft = "",
        lastSearchQuery = "",
        isSearchEngineQuickSwitchBarVisible = false,
    )

private fun BrowserSessionSearchRecovery.toProjectionKey(): BrowserSearchRecoveryProjectionKey =
    BrowserSearchRecoveryProjectionKey(
        query = query,
        engineId = engineId,
        source = source,
        requestedUrl = requestedUrl,
        submittedAt = submittedAt,
    )
