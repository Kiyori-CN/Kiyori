package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserMediaCandidatePolicyTest {
    @Test
    fun fileAndManifestUrlsAreDirectPlaybackCandidatesWithoutRewriting() {
        val videoUrl = "https://media.example/video/episode.MP4?token=raw#fragment"
        val hlsUrl = "https://media.example/master.m3u8?authorization=exact"

        assertEquals(BrowserMediaCandidateUrlEvidence.VIDEO_FILE, classifyBrowserMediaCandidateUrl(videoUrl))
        assertEquals(BrowserMediaCandidateUrlEvidence.HLS_MANIFEST, classifyBrowserMediaCandidateUrl(hlsUrl))
        assertEquals(videoUrl, candidate(observation(url = videoUrl)).url)
        assertTrue(candidate(observation(url = videoUrl)).directPlaybackReady)
        assertTrue(candidate(observation(url = hlsUrl)).directPlaybackReady)
    }

    @Test
    fun responseMimeDoesNotTurnAnApiUrlIntoADirectVideoFile() {
        val candidate =
            candidate(
                observation(
                    url = "https://api.example/resource?id=42",
                    source = BrowserMediaCandidateDiscoverySource.INTERCEPTED_RESPONSE,
                    responseMimeType = "video/mp4",
                ),
            )

        assertEquals("video/mp4", candidate.responseMimeType)
        assertEquals(BrowserMediaCandidateUrlEvidence.NONE, candidate.urlEvidence)
        assertFalse(candidate.directPlaybackReady)
        assertFalse(candidate.downloadReady)
    }

    @Test
    fun domCurrentSourceIsDirectEvidenceButBlobRemainsOnlyAClue() {
        val opaqueHttp =
            candidate(
                observation(
                    url = "https://cdn.example/opaque/asset",
                    source = BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC,
                ),
            )
        val blob =
            candidate(
                observation(
                    url = "blob:https://page.example/01234567",
                    source = BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT,
                ),
            )

        assertTrue(opaqueHttp.directPlaybackReady)
        assertFalse(blob.directPlaybackReady)
        assertFalse(blob.downloadReady)
    }

    @Test
    fun mergeKeepsFirstDiscoveryAndCandidateIdWhileUpdatingRealHeaders() {
        val first =
            candidate(
                observation(
                    url = "https://media.example/movie.mp4",
                    requestHeaders =
                        linkedMapOf(
                            "User-Agent" to "Kiyori/1",
                            "Referer" to "https://page.example/watch",
                            "Range" to "bytes=0-",
                        ),
                    discoveredAt = 100L,
                ),
            )
        val merged =
            requireNotNull(
                mergeBrowserMediaCandidate(
                    current = first,
                    observation =
                        observation(
                            url = first.url,
                            requestHeaders =
                                linkedMapOf(
                                    "user-agent" to "Kiyori/2",
                                    "Cookie" to "session=exact",
                                    "Accept" to "video/mp4",
                                ),
                            source = BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC,
                            discoveredAt = 200L,
                        ),
                    newCandidateId = "must-not-replace",
                ),
            )

        assertEquals("candidate-id", merged.id)
        assertEquals(100L, merged.firstDiscoveredAt)
        assertEquals(200L, merged.lastDiscoveredAt)
        assertEquals("Kiyori/2", merged.userAgent)
        assertEquals("https://page.example/watch", merged.referer)
        assertEquals("session=exact", merged.cookie)
        assertEquals("bytes=0-", merged.range)
        assertEquals("video/mp4", merged.accept)
        assertEquals(2, merged.discoverySources.size)
        assertEquals(5, merged.requestHeaders.size)
    }

    @Test
    fun unrelatedNetworkRequestsAreNotCandidates() {
        val observation = observation(url = "https://api.example/graphql")

        assertNull(mergeBrowserMediaCandidate(null, observation, "candidate-id"))
    }

    @Test
    fun playerRequestKeepsExactCandidateUrlHeadersAndSessionOwner() {
        val candidate =
            candidate(
                observation(
                    url = "https://media.example/movie.mp4?token=exact",
                    requestHeaders =
                        linkedMapOf(
                            "Origin" to "https://page.example",
                            "User-Agent" to "Kiyori/1",
                            "Referer" to "https://page.example/watch",
                            "Cookie" to "session=exact",
                            "Range" to "bytes=0-",
                            "Accept" to "video/mp4",
                        ),
                ),
            )

        val request = createBrowserPlayerMediaRequest("web-session", "Episode", candidate)

        assertEquals(candidate.id, request.requestId)
        assertEquals(candidate.url, request.uri)
        assertEquals(candidate.requestHeaders, request.headers)
        assertEquals("web-session", request.sourceSessionId)
        assertEquals(candidate.cookieScopeUrl, request.cookieScopeUrl)
    }

    @Test
    fun backgroundCandidateCaptureUsesCachedUserAgentWithoutReplacingObservedIdentity() {
        val captured =
            buildBrowserMediaCandidateHeaders(
                observedHeaders = mapOf("Accept" to "video/mp4"),
                appliedUserAgent = "Kiyori/Cached",
                cookie = "session=exact",
            )
        val observedWins =
            buildBrowserMediaCandidateHeaders(
                observedHeaders = mapOf("User-Agent" to "Request/Exact", "Cookie" to "request=cookie"),
                appliedUserAgent = "Kiyori/Cached",
                cookie = "session=exact",
            )

        assertEquals("Kiyori/Cached", captured["User-Agent"])
        assertEquals("session=exact", captured["Cookie"])
        assertEquals("Request/Exact", observedWins["User-Agent"])
        assertEquals("request=cookie", observedWins["Cookie"])
    }

    @Test
    fun automaticFloatingSelectionPrefersActiveDomMediaOverNewerRequestNoise() {
        val activeVideo =
            uiCandidate(
                id = "active",
                url = "https://media.example/opaque-stream",
                sources = setOf(BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT),
                lastDiscoveredAt = 100L,
            )
        val newerManifest =
            uiCandidate(
                id = "manifest",
                url = "https://media.example/master.m3u8",
                sources = setOf(BrowserMediaCandidateDiscoverySource.NETWORK_REQUEST),
                lastDiscoveredAt = 200L,
            )

        assertEquals(activeVideo, selectAutomaticFloatingMediaCandidate(listOf(newerManifest, activeVideo)))
    }

    @Test
    fun automaticFloatingSelectionRejectsMimeOnlyAndBlobCandidates() {
        val mimeOnly =
            uiCandidate(
                id = "mime-only",
                url = "https://api.example/video?id=42",
                sources = setOf(BrowserMediaCandidateDiscoverySource.INTERCEPTED_RESPONSE),
                directPlaybackReady = false,
            )
        val blob =
            uiCandidate(
                id = "blob",
                url = "blob:https://page.example/01234567",
                sources = setOf(BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT),
                directPlaybackReady = false,
                isBlob = true,
            )

        assertNull(selectAutomaticFloatingMediaCandidate(listOf(mimeOnly, blob)))
    }

    @Test
    fun networkLogPlaybackMapsOnlyTheExactExecutableCandidateUrl() {
        val direct =
            candidate(
                observation(
                    url = "https://media.example/movie.mp4?token=exact",
                ),
            )
        val mimeOnly =
            candidate(
                observation(
                    url = "https://api.example/video?id=42",
                    source = BrowserMediaCandidateDiscoverySource.INTERCEPTED_RESPONSE,
                    responseMimeType = "video/mp4",
                ),
            )

        assertEquals(
            direct.id,
            findDirectMediaCandidateIdForNetworkEntry(listOf(direct, mimeOnly), direct.url),
        )
        assertNull(
            findDirectMediaCandidateIdForNetworkEntry(
                listOf(direct, mimeOnly),
                "https://media.example/movie.mp4?token=changed",
            ),
        )
        assertNull(findDirectMediaCandidateIdForNetworkEntry(listOf(direct, mimeOnly), mimeOnly.url))
    }

    @Test
    fun domObserverNeverChangesPageMediaState() {
        assertTrue(BROWSER_MEDIA_CANDIDATE_OBSERVER_SCRIPT.contains("MutationObserver"))
        assertTrue(BROWSER_MEDIA_CANDIDATE_OBSERVER_SCRIPT.contains("addEventListener('play'"))
        assertFalse(Regex("\\.(pause|play|load)\\s*\\(", RegexOption.IGNORE_CASE)
            .containsMatchIn(BROWSER_MEDIA_CANDIDATE_OBSERVER_SCRIPT))
    }

    private fun candidate(observation: BrowserMediaCandidateObservation): BrowserMediaCandidate =
        requireNotNull(mergeBrowserMediaCandidate(null, observation, "candidate-id"))

    private fun observation(
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
        source: BrowserMediaCandidateDiscoverySource = BrowserMediaCandidateDiscoverySource.NETWORK_REQUEST,
        responseMimeType: String? = null,
        discoveredAt: Long = 100L,
    ): BrowserMediaCandidateObservation =
        BrowserMediaCandidateObservation(
            url = url,
            requestHeaders = requestHeaders,
            pageUrl = "https://page.example/watch",
            pageTitle = "Episode",
            sourceSessionId = "web-session",
            sourceProfile = "normal",
            cookieScope = "normal",
            cookieScopeUrl = url,
            source = source,
            responseMimeType = responseMimeType,
            discoveredAt = discoveredAt,
        )

    private fun uiCandidate(
        id: String,
        url: String,
        sources: Set<BrowserMediaCandidateDiscoverySource>,
        lastDiscoveredAt: Long = 100L,
        directPlaybackReady: Boolean = true,
        isBlob: Boolean = false,
    ): WebSessionBrowserMediaCandidate =
        WebSessionBrowserMediaCandidate(
            id = id,
            url = url,
            pageUrl = "https://page.example/watch",
            mimeType = null,
            urlEvidence = classifyBrowserMediaCandidateUrl(url),
            discoverySources = sources,
            firstDiscoveredAt = 50L,
            lastDiscoveredAt = lastDiscoveredAt,
            directPlaybackReady = directPlaybackReady,
            downloadReady = directPlaybackReady,
            isBlob = isBlob,
        )
}
