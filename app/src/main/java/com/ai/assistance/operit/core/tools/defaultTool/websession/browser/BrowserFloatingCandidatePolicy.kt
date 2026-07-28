package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

internal fun selectAutomaticFloatingMediaCandidate(
    candidates: List<WebSessionBrowserMediaCandidate>,
): WebSessionBrowserMediaCandidate? =
    candidates
        .asSequence()
        .filter(WebSessionBrowserMediaCandidate::directPlaybackReady)
        .filterNot(WebSessionBrowserMediaCandidate::isBlob)
        .maxWithOrNull(
            compareBy<WebSessionBrowserMediaCandidate> { automaticFloatingCandidateScore(it) }
                .thenBy(WebSessionBrowserMediaCandidate::lastDiscoveredAt),
        )

internal fun automaticFloatingCandidateScore(candidate: WebSessionBrowserMediaCandidate): Int {
    var score =
        when (candidate.urlEvidence) {
            BrowserMediaCandidateUrlEvidence.HLS_MANIFEST -> 420
            BrowserMediaCandidateUrlEvidence.DASH_MANIFEST -> 410
            BrowserMediaCandidateUrlEvidence.VIDEO_FILE -> 360
            BrowserMediaCandidateUrlEvidence.NONE -> 0
        }
    if (BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT in candidate.discoverySources) score += 700
    if (BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC in candidate.discoverySources) score += 600
    if (BrowserMediaCandidateDiscoverySource.DOM_SRC in candidate.discoverySources) score += 420
    if (BrowserMediaCandidateDiscoverySource.DOM_SOURCE in candidate.discoverySources) score += 400
    if (BrowserMediaCandidateDiscoverySource.INTERCEPTED_RESPONSE in candidate.discoverySources) score += 80
    return score
}
