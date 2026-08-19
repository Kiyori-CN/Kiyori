package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import com.ai.assistance.operit.core.player.PlayerMediaRequest
import com.ai.assistance.operit.core.player.PlayerMediaSource
import com.ai.assistance.operit.core.player.PlayerPresentation
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
    fun responseMimeCanIdentifyAnOpaqueDirectVideoResource() {
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
        assertTrue(candidate.directPlaybackReady)
        assertTrue(candidate.downloadReady)
    }

    @Test
    fun contentDispositionCanIdentifyOpaqueVideoAndAudioResources() {
        val video =
            candidate(
                observation(
                    url = "https://media.example/resource?id=video",
                    source = BrowserMediaCandidateDiscoverySource.INTERCEPTED_RESPONSE,
                    responseMimeType = "application/octet-stream",
                    contentDisposition = "attachment; filename*=UTF-8''episode%2001.mp4",
                ),
            )
        val audio =
            candidate(
                observation(
                    url = "https://media.example/resource?id=audio",
                    source = BrowserMediaCandidateDiscoverySource.INTERCEPTED_RESPONSE,
                    responseMimeType = "application/octet-stream",
                    contentDisposition = "attachment; filename=\"theme song.flac\"",
                ),
            )

        assertEquals(BrowserMediaKind.VIDEO, video.mediaKind)
        assertEquals(BrowserMediaCandidateVideoFormat.MP4, video.videoFormat)
        assertTrue(video.isActionableVideo)
        assertEquals(BrowserMediaKind.AUDIO, audio.mediaKind)
        assertTrue(audio.isActionableAudio)
    }

    @Test
    fun exactVideoFormatsResolveFromUrlOrMimeWithoutInventingAnAudioCandidate() {
        assertEquals(
            BrowserMediaCandidateVideoFormat.MP4,
            candidate(observation(url = "https://media.example/movie.MP4?token=exact")).videoFormat,
        )
        assertEquals(
            BrowserMediaCandidateVideoFormat.M3U8,
            candidate(observation(url = "https://media.example/master.m3u8")).videoFormat,
        )
        assertEquals(
            BrowserMediaCandidateVideoFormat.MPD,
            candidate(observation(url = "https://media.example/manifest.mpd")).videoFormat,
        )
        assertEquals(
            BrowserMediaCandidateVideoFormat.WEBM,
            candidate(
                observation(
                    url = "https://media.example/opaque",
                    source = BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC,
                    declaredMimeType = "video/webm",
                ),
            ).videoFormat,
        )
        val audio =
            requireNotNull(
                mergeBrowserMediaCandidate(
                    current = null,
                    observation =
                        observation(
                            url = "https://media.example/audio.mp3",
                            source = BrowserMediaCandidateDiscoverySource.DOM_AUDIO_CURRENT_SRC,
                            declaredMimeType = "audio/mpeg",
                        ),
                    newCandidateId = "audio",
                ),
            )
        assertEquals(BrowserMediaKind.AUDIO, audio.mediaKind)
        assertTrue(audio.isActionableAudio)
    }

    @Test
    fun mp3SuffixCannotOverrideVideoDomEvidence() {
        val disguisedVideo =
            candidate(
                observation(
                    url = "https://media.example/disguised.mp3?token=exact",
                    source = BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC,
                    declaredMimeType = "audio/mpeg",
                ),
            )

        assertEquals(BrowserMediaKind.VIDEO, disguisedVideo.mediaKind)
        assertEquals(BrowserMediaCandidateVideoFormat.OTHER_VIDEO, disguisedVideo.videoFormat)
        assertTrue(disguisedVideo.isActionableVideo)
        assertEquals(
            disguisedVideo.url,
            com.ai.assistance.operit.core.player.PlayerMediaRequest(
                requestId = disguisedVideo.id,
                uri = disguisedVideo.url,
                title = "disguised",
                source = PlayerMediaSource.BROWSER_CANDIDATE,
                sourceSessionId = "session",
            ).uri,
        )
    }

    @Test
    fun segmentedMediaFragmentsAreNeverActionableResults() {
        listOf(
            "https://media.example/init.mp4",
            "https://media.example/segment-001.m4s",
            "https://media.example/chunk-0001.ts",
        ).forEach { url ->
            val candidate =
                candidate(
                    observation(
                        url = url,
                        source = BrowserMediaCandidateDiscoverySource.DOM_SOURCE,
                    ),
                )
            assertTrue(candidate.isLikelyMediaFragment)
            assertFalse(candidate.isActionableVideo)
            assertFalse(candidate.downloadReady)
        }
    }

    @Test
    fun durationUsesPassiveMetadataAndKeepsTheLongestKnownValue() {
        val headerCandidate =
            candidate(
                observation(
                    url = "https://media.example/movie.mp4",
                    requestHeaders = mapOf("X-Content-Duration" to "02:03"),
                ),
            )
        val merged =
            requireNotNull(
                mergeBrowserMediaCandidate(
                    current = headerCandidate,
                    observation =
                        observation(
                            url = headerCandidate.url,
                            source = BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC,
                            durationMillis = 150_000L,
                            discoveredAt = 200L,
                        ),
                    newCandidateId = "must-not-replace",
                ),
            )
        val queryCandidate =
            candidate(
                observation(
                    url = "https://media.example/clip.mp4?duration_ms=90500",
                ),
            )

        assertEquals(123_000L, headerCandidate.durationMillis)
        assertEquals(150_000L, merged.durationMillis)
        assertEquals(90_500L, queryCandidate.durationMillis)
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
        assertEquals(candidate.pageUrl, request.sourcePageUrl)
        assertTrue(request.persistPlaybackHistory)
        assertFalse(
            createBrowserPlayerMediaRequest(
                "incognito-session",
                "Private episode",
                candidate.copy(sourceProfile = WebSessionProfile.INCOGNITO.wireName),
            ).persistPlaybackHistory,
        )
    }

    @Test
    fun browserPlaybackConsumesOnlyThePageThatOpenedThatCandidate() {
        val request =
            PlayerMediaRequest(
                requestId = "candidate",
                uri = "https://media.example/video.mp4",
                title = "Episode",
                source = PlayerMediaSource.BROWSER_CANDIDATE,
                sourceSessionId = "web-session",
                sourcePageUrl = "https://page.example/watch",
            )

        assertTrue(
            browserPlayerRequestBelongsToPage(
                currentPageKey = "web-session|https://page.example/watch",
                request = request,
            ),
        )
        assertFalse(
            browserPlayerRequestBelongsToPage(
                currentPageKey = "web-session|https://page.example/next",
                request = request,
            ),
        )
        assertFalse(
            browserPlayerRequestBelongsToPage(
                currentPageKey = "web-session|https://page.example/watch",
                request = request.copy(source = PlayerMediaSource.HISTORY_REPLAY),
            ),
        )
        assertEquals(
            "web-session|https://page.example/watch",
            resolveConsumedAutomaticFloatingPageKey(
                currentPageKey = "web-session|https://page.example/watch",
                consumedPageKey = null,
                request = request,
            ),
        )
        assertEquals(
            "web-session|https://page.example/watch",
            resolveConsumedAutomaticFloatingPageKey(
                currentPageKey = "web-session|https://page.example/next",
                consumedPageKey = "web-session|https://page.example/watch",
                request = request,
            ),
        )
    }

    @Test
    fun consumedPageSuppressesOnlyItsAutomaticFloatingRestart() {
        val consumedPage = "web-session|https://page.example/watch"

        assertFalse(
            shouldAttemptAutomaticFloatingPlayback(
                currentPageKey = consumedPage,
                consumedPageKey = consumedPage,
                hasMedia = false,
                presentation = PlayerPresentation.BROWSER_ONLY,
            ),
        )
        assertTrue(
            shouldAttemptAutomaticFloatingPlayback(
                currentPageKey = "web-session|https://page.example/next",
                consumedPageKey = consumedPage,
                hasMedia = false,
                presentation = PlayerPresentation.BROWSER_ONLY,
            ),
        )
        assertFalse(
            shouldAttemptAutomaticFloatingPlayback(
                currentPageKey = "web-session|https://page.example/next",
                consumedPageKey = consumedPage,
                hasMedia = true,
                presentation = PlayerPresentation.FULLSCREEN_PLAYER,
            ),
        )
    }

    @Test
    fun historyReplayKeepsPersistedIdentityWithoutAWebSessionOwner() {
        val entry =
            WebSessionHistoryEntry(
                url = "https://media.example/movie.mp4?token=exact",
                title = "Episode",
                visitedAt = 100L,
                category = WebSessionHistoryCategory.VIDEO,
                mediaOrigin = WebSessionHistoryMediaOrigin.ONLINE,
                sourcePageUrl = "https://page.example/watch",
            )
        val headers =
            linkedMapOf(
                "User-Agent" to "Kiyori/History",
                "Cookie" to "session=exact",
                "Referer" to entry.sourcePageUrl,
            )

        val request =
            createHistoryPlayerMediaRequest(
                entry = entry,
                headers = headers,
                requestId = "history-request",
            )

        assertEquals("history-request", request.requestId)
        assertEquals(entry.url, request.uri)
        assertEquals(headers, request.headers)
        assertEquals(PlayerMediaSource.HISTORY_REPLAY, request.source)
        assertNull(request.sourceSessionId)
        assertEquals(entry.url, request.cookieScopeUrl)
        assertEquals(entry.sourcePageUrl, request.sourcePageUrl)
        assertTrue(request.persistPlaybackHistory)
    }

    @Test
    fun localHistoryKeepsExternalQueueSemantics() {
        val entry =
            WebSessionHistoryEntry(
                url = "content://media/external/video/1",
                title = "Local episode",
                visitedAt = 100L,
                category = WebSessionHistoryCategory.VIDEO,
                mediaOrigin = WebSessionHistoryMediaOrigin.LOCAL,
            )

        val request =
            createHistoryPlayerMediaRequest(
                entry = entry,
                headers = emptyMap(),
                requestId = "local-history",
            )

        assertEquals(PlayerMediaSource.EXTERNAL_INTENT, request.source)
        assertNull(request.sourceSessionId)
        assertNull(request.cookieScopeUrl)
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
                rankingScore = 820,
                automaticFloatingEligible = true,
            )
        val newerManifest =
            uiCandidate(
                id = "manifest",
                url = "https://media.example/master.m3u8",
                sources = setOf(BrowserMediaCandidateDiscoverySource.NETWORK_REQUEST),
                lastDiscoveredAt = 200L,
                videoFormat = BrowserMediaCandidateVideoFormat.M3U8,
                rankingScore = 200,
                automaticFloatingEligible = true,
            )

        assertEquals(
            activeVideo,
            selectAutomaticFloatingMediaCandidate(
                candidates = listOf(newerManifest, activeVideo),
                minimumDurationMillis = 60_000L,
            ),
        )
    }

    @Test
    fun automaticFloatingSelectionRejectsMimeOnlyAndBlobCandidates() {
        val mimeOnly =
            uiCandidate(
                id = "mime-only",
                url = "https://api.example/video?id=42",
                sources = setOf(BrowserMediaCandidateDiscoverySource.INTERCEPTED_RESPONSE),
                directPlaybackReady = false,
                automaticFloatingEligible = false,
            )
        val blob =
            uiCandidate(
                id = "blob",
                url = "blob:https://page.example/01234567",
                sources = setOf(BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT),
                directPlaybackReady = false,
                isBlob = true,
                automaticFloatingEligible = false,
            )

        assertNull(
            selectAutomaticFloatingMediaCandidate(
                candidates = listOf(mimeOnly, blob),
                minimumDurationMillis = 60_000L,
            ),
        )
    }

    @Test
    fun recommendationRankingPrefersActiveMainVideoAndPenalizesSmallMutedLoops() {
        val activeMainVideo =
            candidate(
                observation(
                    url = "https://media.example/episode.mp4",
                    source = BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT,
                    durationMillis = 25L * 60L * 1_000L,
                    videoWidth = 1920,
                    videoHeight = 1080,
                    viewportAreaRatio = 0.75,
                ),
            )
        val backgroundLoop =
            candidate(
                observation(
                    url = "https://media.example/preview.mp4",
                    source = BrowserMediaCandidateDiscoverySource.DOM_SRC,
                    durationMillis = 8_000L,
                    viewportAreaRatio = 0.03,
                    muted = true,
                    looping = true,
                    autoplay = true,
                ),
            )

        val activeRanking = rankBrowserMediaCandidate(activeMainVideo)
        val backgroundRanking = rankBrowserMediaCandidate(backgroundLoop)

        assertTrue(activeRanking.isRecommended)
        assertTrue(activeRanking.automaticFloatingEligible)
        assertFalse(backgroundRanking.isRecommended)
        assertFalse(backgroundRanking.automaticFloatingEligible)
        assertTrue(activeRanking.score > backgroundRanking.score)
        assertEquals(
            listOf(activeMainVideo, backgroundLoop),
            sortBrowserMediaCandidates(listOf(backgroundLoop, activeMainVideo)),
        )
    }

    @Test
    fun recommendedCandidatesSortByResolutionFromHighToLow() {
        val candidate1080 =
            candidate(
                observation(
                    url = "https://media.example/master.m3u8?quality=1080",
                ),
            )
        val candidate720 =
            candidate(
                observation(
                    url = "https://media.example/master.m3u8?quality=720",
                ),
            )
        val candidate480 =
            candidate(
                observation(
                    url = "https://media.example/master.m3u8?quality=480",
                ),
            )

        assertEquals(1080, rankBrowserMediaCandidate(candidate1080).qualityHeight)
        assertEquals("1080P", rankBrowserMediaCandidate(candidate1080).qualityLabel)
        assertEquals(
            listOf(candidate1080, candidate720, candidate480),
            sortBrowserMediaCandidates(listOf(candidate480, candidate1080, candidate720)),
        )
        assertEquals(
            BrowserMediaCandidateQuality(width = 3840, height = 2160, label = "2160P"),
            resolveBrowserMediaCandidateQuality(
                width = null,
                height = null,
                url = "https://media.example/video/3840x2160/index.m3u8",
            ),
        )
    }

    @Test
    fun automaticFloatingUsesHighestEligibleQualityAndDurationThreshold() {
        val candidate720 =
            uiCandidate(
                id = "720",
                url = "https://media.example/720p.mp4",
                sources = setOf(BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT),
                rankingScore = 900,
                automaticFloatingEligible = true,
                qualityHeight = 720,
                durationMillis = 90_000L,
            )
        val candidate1080 =
            uiCandidate(
                id = "1080",
                url = "https://media.example/1080p.mp4",
                sources = setOf(BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC),
                rankingScore = 700,
                automaticFloatingEligible = true,
                qualityHeight = 1080,
                durationMillis = 90_000L,
            )
        val short2160 =
            uiCandidate(
                id = "2160-short",
                url = "https://media.example/2160p.mp4",
                sources = setOf(BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT),
                rankingScore = 1_000,
                automaticFloatingEligible = true,
                qualityHeight = 2160,
                durationMillis = 30_000L,
            )

        assertEquals(
            candidate1080,
            selectAutomaticFloatingMediaCandidate(
                candidates = listOf(candidate720, short2160, candidate1080),
                minimumDurationMillis = 60_000L,
            ),
        )
        assertFalse(
            browserMediaCandidateMeetsAutomaticFloatingDuration(
                candidate = short2160,
                minimumDurationMillis = 60_000L,
            ),
        )
        assertTrue(
            browserMediaCandidateMeetsAutomaticFloatingDuration(
                candidate = candidate1080.copy(durationMillis = 60_000L),
                minimumDurationMillis = 60_000L,
            ),
        )
        assertTrue(
            browserMediaCandidateMeetsAutomaticFloatingDuration(
                candidate = candidate1080.copy(durationMillis = null, isLive = true),
                minimumDurationMillis = 60_000L,
            ),
        )
        assertFalse(
            browserMediaCandidateMeetsAutomaticFloatingDuration(
                candidate = candidate1080.copy(durationMillis = null, isLive = false),
                minimumDurationMillis = 60_000L,
            ),
        )
    }

    @Test
    fun automaticFloatingConfirmationDelayUsesStrongestPassiveEvidence() {
        val play =
            uiCandidate(
                id = "play",
                url = "https://media.example/play.mp4",
                sources = setOf(BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT),
            )
        val current =
            uiCandidate(
                id = "current",
                url = "https://media.example/current.mp4",
                sources = setOf(BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC),
            )
        val manifest =
            uiCandidate(
                id = "manifest",
                url = "https://media.example/master.m3u8",
                sources = setOf(BrowserMediaCandidateDiscoverySource.NETWORK_REQUEST),
                videoFormat = BrowserMediaCandidateVideoFormat.M3U8,
            )

        assertEquals(100L, automaticFloatingCandidateStabilityDelayMillis(play))
        assertEquals(180L, automaticFloatingCandidateStabilityDelayMillis(current))
        assertEquals(300L, automaticFloatingCandidateStabilityDelayMillis(manifest))
    }

    @Test
    fun recommendedCandidateSortsAheadOfHigherScoringRejectedPreview() {
        val rejectedPreview =
            candidate(
                observation(
                    url = "https://media.example/preview.mp4",
                    source = BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT,
                    viewportAreaRatio = 0.8,
                ),
            )
        val recommendedManifest =
            candidate(
                observation(
                    url = "https://media.example/master.m3u8",
                    source = BrowserMediaCandidateDiscoverySource.NETWORK_REQUEST,
                ),
            )

        assertTrue(rankBrowserMediaCandidate(rejectedPreview).score > rankBrowserMediaCandidate(recommendedManifest).score)
        assertFalse(rankBrowserMediaCandidate(rejectedPreview).isRecommended)
        assertTrue(rankBrowserMediaCandidate(recommendedManifest).isRecommended)
        assertEquals(
            listOf(recommendedManifest, rejectedPreview),
            sortBrowserMediaCandidates(listOf(rejectedPreview, recommendedManifest)),
        )
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
        assertEquals(
            mimeOnly.id,
            findDirectMediaCandidateIdForNetworkEntry(listOf(direct, mimeOnly), mimeOnly.url),
        )
    }

    @Test
    fun domObserverNeverChangesPageMediaState() {
        assertTrue(BROWSER_MEDIA_CANDIDATE_OBSERVER_SCRIPT.contains("MutationObserver"))
        assertTrue(BROWSER_MEDIA_CANDIDATE_OBSERVER_SCRIPT.contains("addEventListener('play'"))
        assertTrue(BROWSER_MEDIA_CANDIDATE_OBSERVER_SCRIPT.contains("addEventListener('loadedmetadata'"))
        assertTrue(BROWSER_MEDIA_CANDIDATE_OBSERVER_SCRIPT.contains("addEventListener('durationchange'"))
        assertTrue(BROWSER_MEDIA_CANDIDATE_OBSERVER_SCRIPT.contains("addEventListener('resize'"))
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
        declaredMimeType: String? = null,
        contentDisposition: String? = null,
        durationMillis: Long? = null,
        isLive: Boolean = false,
        videoWidth: Int? = null,
        videoHeight: Int? = null,
        viewportAreaRatio: Double? = null,
        muted: Boolean = false,
        looping: Boolean = false,
        autoplay: Boolean = false,
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
            declaredMimeType = declaredMimeType,
            contentDisposition = contentDisposition,
            durationMillis = durationMillis,
            isLive = isLive,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            viewportAreaRatio = viewportAreaRatio,
            muted = muted,
            looping = looping,
            autoplay = autoplay,
            discoveredAt = discoveredAt,
        )

    private fun uiCandidate(
        id: String,
        url: String,
        sources: Set<BrowserMediaCandidateDiscoverySource>,
        lastDiscoveredAt: Long = 100L,
        directPlaybackReady: Boolean = true,
        isBlob: Boolean = false,
        videoFormat: BrowserMediaCandidateVideoFormat = BrowserMediaCandidateVideoFormat.OTHER_VIDEO,
        rankingScore: Int = 0,
        automaticFloatingEligible: Boolean = false,
        qualityHeight: Int? = null,
        durationMillis: Long? = 120_000L,
        isLive: Boolean = false,
    ): WebSessionBrowserMediaCandidate =
        WebSessionBrowserMediaCandidate(
            id = id,
            url = url,
            pageUrl = "https://page.example/watch",
            mimeType = null,
            urlEvidence = classifyBrowserMediaCandidateUrl(url),
            videoFormat = videoFormat,
            discoverySources = sources,
            firstDiscoveredAt = 50L,
            lastDiscoveredAt = lastDiscoveredAt,
            durationMillis = durationMillis,
            isLive = isLive,
            qualityHeight = qualityHeight,
            qualityLabel = qualityHeight?.let { "${it}P" },
            rankingScore = rankingScore,
            rankingSummary = "",
            isRecommended = rankingScore >= 200,
            automaticFloatingEligible = automaticFloatingEligible,
            directPlaybackReady = directPlaybackReady,
            downloadReady = directPlaybackReady,
            isBlob = isBlob,
        )
}
