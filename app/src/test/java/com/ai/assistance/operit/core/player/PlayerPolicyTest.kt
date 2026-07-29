package com.ai.assistance.operit.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPolicyTest {
    @Test
    fun unpublishedPlayerBaselineUsesRecommendedFastPreset() {
        assertEquals(
            PlayerDecoderPreset.FAST,
            PlayerSettings().decoderPreset,
        )
        assertEquals(
            PlayerDecoderPreset.FAST,
            PlayerSessionState().decoderPreset,
        )
        assertFalse(PlayerSettings().followGravityRotation)
        assertTrue(PlayerSettings().screenshotDirectoryUri.isBlank())
        assertTrue(PlayerSettings().videoDownloadDirectoryUri.isBlank())
    }

    @Test
    fun screenshotDestinationUsesPlayerDirectoryBeforeTheDownloaderPolicy() {
        assertEquals(
            PlayerScreenshotDestination(
                kind = PlayerScreenshotDestinationKind.DOCUMENT_TREE,
                treeUri = "content://player/screenshots",
            ),
            resolvePlayerScreenshotDestination(
                playerDirectoryUri = "content://player/screenshots",
                browserUsesSystemDownloader = true,
                browserDirectoryUri = "content://downloads/main",
                browserAutoTransferToPublicDirectory = true,
            ),
        )
        assertEquals(
            PlayerScreenshotDestination(
                kind = PlayerScreenshotDestinationKind.DOCUMENT_TREE,
                treeUri = "content://downloads/main",
            ),
            resolvePlayerScreenshotDestination(
                playerDirectoryUri = "",
                browserUsesSystemDownloader = false,
                browserDirectoryUri = "content://downloads/main",
                browserAutoTransferToPublicDirectory = false,
            ),
        )
        assertEquals(
            PlayerScreenshotDestination(
                kind = PlayerScreenshotDestinationKind.PUBLIC_DOWNLOAD_DIRECTORY,
            ),
            resolvePlayerScreenshotDestination(
                playerDirectoryUri = "",
                browserUsesSystemDownloader = false,
                browserDirectoryUri = "",
                browserAutoTransferToPublicDirectory = true,
            ),
        )
        assertEquals(
            PlayerScreenshotDestination(
                kind = PlayerScreenshotDestinationKind.APPLICATION_DOWNLOAD_DIRECTORY,
            ),
            resolvePlayerScreenshotDestination(
                playerDirectoryUri = "",
                browserUsesSystemDownloader = false,
                browserDirectoryUri = "",
                browserAutoTransferToPublicDirectory = false,
            ),
        )
        assertEquals(
            PlayerScreenshotDestination(
                kind = PlayerScreenshotDestinationKind.PUBLIC_DOWNLOAD_DIRECTORY,
            ),
            resolvePlayerScreenshotDestination(
                playerDirectoryUri = "",
                browserUsesSystemDownloader = true,
                browserDirectoryUri = "content://downloads/main",
                browserAutoTransferToPublicDirectory = false,
            ),
        )
    }

    @Test
    fun persistedSettingsMapToExactRuntimeValues() {
        PlayerDecoderPreset.entries.forEach { value ->
            assertEquals(value, PlayerDecoderPreset.fromPersistedId(value.persistedId))
            assertTrue(value.displayName.isNotBlank())
            assertTrue(value.description.isNotBlank())
        }
        PlayerBackgroundBehavior.entries.forEach { value ->
            assertEquals(value, PlayerBackgroundBehavior.fromPersistedId(value.persistedId))
        }
        PlayerFullscreenExitBehavior.entries.forEach { value ->
            assertEquals(value, PlayerFullscreenExitBehavior.fromPersistedId(value.persistedId))
        }
        Anime4KMode.entries.forEach { value ->
            assertEquals(value, Anime4KMode.fromPersistedId(value.persistedId))
        }
        PlayerNetworkCachePolicy.entries.forEach { value ->
            assertEquals(value, PlayerNetworkCachePolicy.fromPersistedId(value.persistedId))
            assertTrue(value.forwardBytes > value.backwardBytes)
            assertTrue(value.cacheSeconds > 0)
        }
        PlayerDoubleTapAction.entries.forEach { value ->
            assertEquals(value, PlayerDoubleTapAction.fromPersistedId(value.persistedId))
        }
        PlayerQueueEndBehavior.entries.forEach { value ->
            assertEquals(value, PlayerQueueEndBehavior.fromPersistedId(value.persistedId))
        }
    }

    @Test
    fun anime4KModesOwnRealShaderFiles() {
        assertTrue(Anime4KMode.OFF.shaderFiles.isEmpty())
        Anime4KMode.entries.filterNot { it == Anime4KMode.OFF }.forEach { mode ->
            assertTrue(mode.shaderFiles.isNotEmpty())
            assertTrue(mode.shaderFiles.all { it.startsWith("Anime4K_") && it.endsWith(".glsl") })
        }
    }

    @Test
    fun videoFitModesFollowLegacyPlayerOrder() {
        assertEquals(
            listOf(PlayerVideoFitMode.FIT, PlayerVideoFitMode.STRETCH, PlayerVideoFitMode.CROP),
            PlayerVideoFitMode.entries,
        )
        assertEquals(PlayerVideoFitMode.STRETCH, PlayerVideoFitMode.FIT.next())
        assertEquals(PlayerVideoFitMode.CROP, PlayerVideoFitMode.STRETCH.next())
        assertEquals(PlayerVideoFitMode.FIT, PlayerVideoFitMode.CROP.next())
    }

    @Test
    fun playerDebugLogBufferNormalizesAndClearsEntries() {
        PlayerDebugLogBuffer.clear()
        PlayerDebugLogBuffer.append(
            PlayerDebugLogLevel.ERROR,
            "PlayerSession",
            "line one\nline two",
        )

        val snapshot = PlayerDebugLogBuffer.snapshot()

        assertTrue(snapshot.contains("E/PlayerSession"))
        assertTrue(snapshot.contains("PlayerSession: line one line two"))
        PlayerDebugLogBuffer.clear()
        assertTrue(PlayerDebugLogBuffer.snapshot().isBlank())
    }

    @Test
    fun playerDiagnosticsPreserveOnlineShapeAndRemoveSensitiveValues() {
        val sanitized =
            sanitizePlayerDiagnosticMessage(
                "open https://media.example/video/master.m3u8?token=secret " +
                    "Cookie: session-secret; Authorization: Bearer auth-secret",
            )

        assertTrue(
            sanitized.contains(
                "https://media.example/video/master.m3u8?<redacted>",
            ),
        )
        assertTrue(sanitized.contains("Cookie=<redacted>"))
        assertTrue(sanitized.contains("Authorization=<redacted>"))
        assertFalse(sanitized.contains("session-secret"))
        assertFalse(sanitized.contains("auth-secret"))
        assertFalse(sanitized.contains("token=secret"))
        assertEquals(
            "content://media/<redacted>",
            describePlayerMediaUriForDiagnostics("content://media/external/video/1"),
        )
        assertEquals(
            "file://<redacted>",
            describePlayerMediaUriForDiagnostics("file:///storage/emulated/0/private.mp4"),
        )
    }

    @Test
    fun playerLogFiltersSelectWarningsAndErrorsWithoutChangingTheBuffer() {
        PlayerDebugLogBuffer.clear()
        PlayerDebugLogBuffer.append(PlayerDebugLogLevel.DEBUG, "Player", "debug")
        PlayerDebugLogBuffer.append(PlayerDebugLogLevel.INFO, "Player", "info")
        PlayerDebugLogBuffer.append(PlayerDebugLogLevel.WARN, "Player", "warn")
        PlayerDebugLogBuffer.append(PlayerDebugLogLevel.ERROR, "Player", "error")

        val warningSnapshot =
            PlayerDebugLogBuffer.snapshotState(PlayerDebugLogFilter.WARN_AND_ERROR)
        val errorSnapshot =
            PlayerDebugLogBuffer.snapshotState(PlayerDebugLogFilter.ERROR_ONLY)

        assertEquals(2, warningSnapshot.lineCount)
        assertEquals(4, warningSnapshot.totalLineCount)
        assertTrue(warningSnapshot.text.contains("W/Player: warn"))
        assertTrue(warningSnapshot.text.contains("E/Player: error"))
        assertFalse(warningSnapshot.text.contains("I/Player: info"))
        assertEquals(1, errorSnapshot.lineCount)
        assertEquals(4, errorSnapshot.totalLineCount)
        assertTrue(errorSnapshot.text.contains("E/Player: error"))
        PlayerDebugLogBuffer.clear()
    }

    @Test
    fun playerLogTopicFiltersKeepCrossLayerDiagnosticContext() {
        PlayerDebugLogBuffer.clear()
        PlayerDebugLogBuffer.append(
            PlayerDebugLogLevel.INFO,
            "HttpMediaResolver",
            "加载 https://media.example/video/master.m3u8?token=secret",
        )
        PlayerDebugLogBuffer.append(
            PlayerDebugLogLevel.DEBUG,
            "PlayerSession",
            "发送暂停命令 paused=true",
        )
        PlayerDebugLogBuffer.append(
            PlayerDebugLogLevel.INFO,
            "mpv.vo",
            "video reconfig Surface size=1920x1080",
        )
        PlayerDebugLogBuffer.append(
            PlayerDebugLogLevel.DEBUG,
            "PlayerSession",
            "发送音轨切换命令 track=2",
        )

        val networkSnapshot =
            PlayerDebugLogBuffer.snapshotState(PlayerDebugLogFilter.NETWORK_AND_LOADING)
        val playbackSnapshot =
            PlayerDebugLogBuffer.snapshotState(PlayerDebugLogFilter.PLAYBACK_CONTROL)
        val renderSnapshot =
            PlayerDebugLogBuffer.snapshotState(PlayerDebugLogFilter.SURFACE_AND_RENDER)
        val trackSnapshot =
            PlayerDebugLogBuffer.snapshotState(PlayerDebugLogFilter.TRACKS)
        val runtimeSnapshot =
            PlayerDebugLogBuffer.snapshotState(PlayerDebugLogFilter.RUNTIME_AND_MPV)

        assertTrue(networkSnapshot.text.contains("HttpMediaResolver"))
        assertFalse(networkSnapshot.text.contains("token=secret"))
        assertTrue(playbackSnapshot.text.contains("paused=true"))
        assertTrue(renderSnapshot.text.contains("Surface size=1920x1080"))
        assertTrue(trackSnapshot.text.contains("track=2"))
        assertTrue(runtimeSnapshot.text.contains("mpv.vo"))
        assertTrue(runtimeSnapshot.entries.zipWithNext().all { (first, second) -> first.id < second.id })
        PlayerDebugLogBuffer.clear()
    }

    @Test
    fun playerLogRevisionChangesAfterAppendAndClear() {
        PlayerDebugLogBuffer.clear()
        val beforeAppend = PlayerDebugLogBuffer.revision.value

        PlayerDebugLogBuffer.append(PlayerDebugLogLevel.INFO, "Player", "revision")
        val afterAppend = PlayerDebugLogBuffer.revision.value
        PlayerDebugLogBuffer.clear()
        val afterClear = PlayerDebugLogBuffer.revision.value

        assertTrue(afterAppend > beforeAppend)
        assertTrue(afterClear > afterAppend)
    }

    @Test
    fun browserRequestRequiresItsWebSessionIdentity() {
        assertThrows(IllegalArgumentException::class.java) {
            PlayerMediaRequest(
                requestId = "candidate-1",
                uri = "https://media.example/video.mp4",
                title = "Video",
                source = PlayerMediaSource.BROWSER_CANDIDATE,
            )
        }
    }

    @Test
    fun sameRequestChangesOnlyPresentationWithoutReload() {
        val request = externalRequest("request-1")
        val current =
            PlayerSessionState(
                request = request,
                presentation = PlayerPresentation.FLOATING_PLAYER,
                positionSeconds = 87.5,
                durationSeconds = 120.0,
                paused = true,
                speed = 1.5,
                loadGeneration = 4L,
            )

        val transition =
            resolvePlayerOpenTransition(
                current = current,
                request = request,
                presentation = PlayerPresentation.FULLSCREEN_PLAYER,
                settings = PlayerSettings(),
            )

        assertFalse(transition.shouldLoad)
        assertEquals(PlayerPresentation.FULLSCREEN_PLAYER, transition.state.presentation)
        assertEquals(87.5, transition.state.positionSeconds, 0.0)
        assertEquals(1.5, transition.state.speed, 0.0)
        assertEquals(4L, transition.state.loadGeneration)
    }

    @Test
    fun browserFloatingFullscreenRoundTripKeepsOneLoadGeneration() {
        val request =
            PlayerMediaRequest(
                requestId = "candidate-1",
                uri = "https://media.example/video.mp4",
                title = "Video",
                source = PlayerMediaSource.BROWSER_CANDIDATE,
                sourceSessionId = "web-session-1",
            )
        val preparedLease =
            preparePlayerSurfaceLease(
                PlayerSurfaceLeaseState(),
                PlayerSurfaceRole.FLOATING,
            )
        val registration =
            registerPlayerSurfaceOwner(
                preparedLease,
                PlayerSurfaceRole.FLOATING,
                "floating-1",
            )
        val surfaceLease =
            activatePendingPlayerSurface(
                beginPendingPlayerSurfaceAttach(
                    registration.state,
                    PlayerSurfaceRole.FLOATING,
                    "floating-1",
                    requireNotNull(registration.generation),
                ),
                PlayerSurfaceRole.FLOATING,
                "floating-1",
                requireNotNull(registration.generation),
            )
        val floating =
            PlayerSessionState(
                request = request,
                presentation = PlayerPresentation.FLOATING_PLAYER,
                surfaceLease = surfaceLease,
                positionSeconds = 31.0,
                paused = false,
                loadGeneration = 7L,
            )

        val fullscreen =
            resolvePlayerOpenTransition(
                current = floating,
                request = request,
                presentation = PlayerPresentation.FULLSCREEN_PLAYER,
                settings = PlayerSettings(),
            )
        val returnedFloating =
            resolvePlayerOpenTransition(
                current = fullscreen.state,
                request = request,
                presentation = PlayerPresentation.FLOATING_PLAYER,
                settings = PlayerSettings(),
            )

        assertFalse(fullscreen.shouldLoad)
        assertFalse(returnedFloating.shouldLoad)
        assertEquals(7L, returnedFloating.state.loadGeneration)
        assertEquals(31.0, returnedFloating.state.positionSeconds, 0.0)
        assertEquals(PlayerPresentation.FLOATING_PLAYER, returnedFloating.state.presentation)
        assertEquals(surfaceLease, returnedFloating.state.surfaceLease)
    }

    @Test
    fun newRequestResetsMediaStateAndIncrementsLoadGeneration() {
        val current =
            PlayerSessionState(
                request = externalRequest("request-1"),
                presentation = PlayerPresentation.FULLSCREEN_PLAYER,
                positionSeconds = 87.5,
                durationSeconds = 120.0,
                paused = true,
                speed = 1.5,
                loadGeneration = 4L,
            )
        val settings =
            PlayerSettings(
                defaultSpeed = 1.25,
                decoderPreset = PlayerDecoderPreset.HIGH_QUALITY,
                anime4KMode = Anime4KMode.BALANCED,
                rememberAnime4KMode = true,
            )

        val transition =
            resolvePlayerOpenTransition(
                current = current,
                request = externalRequest("request-2"),
                presentation = PlayerPresentation.FULLSCREEN_PLAYER,
                settings = settings,
            )

        assertTrue(transition.shouldLoad)
        assertEquals(0.0, transition.state.positionSeconds, 0.0)
        assertEquals(1.25, transition.state.speed, 0.0)
        assertEquals(5L, transition.state.loadGeneration)
        assertEquals(PlayerDecoderPreset.HIGH_QUALITY, transition.state.decoderPreset)
        assertEquals(Anime4KMode.BALANCED, transition.state.anime4KMode)
    }

    @Test
    fun mediaStartRespectsSpeedAndAnime4KMemorySwitches() {
        val remembered =
            PlayerSettings(
                defaultSpeed = 1.0,
                lastPlaybackSpeed = 1.5,
                rememberPlaybackSpeed = true,
                anime4KMode = Anime4KMode.QUALITY,
                rememberAnime4KMode = true,
            )
        assertEquals(1.5, resolveInitialPlayerSpeed(remembered), 0.0)
        assertEquals(Anime4KMode.QUALITY, resolveInitialAnime4KMode(remembered))

        val resetForNewMedia =
            remembered.copy(
                rememberPlaybackSpeed = false,
                rememberAnime4KMode = false,
            )
        assertEquals(1.0, resolveInitialPlayerSpeed(resetForNewMedia), 0.0)
        assertEquals(Anime4KMode.OFF, resolveInitialAnime4KMode(resetForNewMedia))
    }

    @Test
    fun onlyBrowserRequestWithReturnSettingReturnsToFloating() {
        val browserState =
            PlayerSessionState(
                request =
                    PlayerMediaRequest(
                        requestId = "candidate-1",
                        uri = "https://media.example/video.mp4",
                        title = "Video",
                        source = PlayerMediaSource.BROWSER_CANDIDATE,
                        sourceSessionId = "web-session-1",
                    ),
                presentation = PlayerPresentation.FULLSCREEN_PLAYER,
            )
        assertTrue(
            shouldReturnFullscreenPlayerToFloating(
                browserState,
                PlayerSettings(
                    fullscreenExitBehavior = PlayerFullscreenExitBehavior.RETURN_TO_FLOATING,
                ),
            ),
        )
        assertFalse(
            shouldReturnFullscreenPlayerToFloating(
                browserState,
                PlayerSettings(fullscreenExitBehavior = PlayerFullscreenExitBehavior.CLOSE),
            ),
        )
        assertFalse(
            shouldReturnFullscreenPlayerToFloating(
                browserState.copy(request = externalRequest("external")),
                PlayerSettings(),
            ),
        )
    }

    @Test
    fun naturalEndRequiresFiniteDurationAndPositionNearTheEnd() {
        assertTrue(isPlayerAtNaturalEnd(positionSeconds = 119.5, durationSeconds = 120.0))
        assertFalse(isPlayerAtNaturalEnd(positionSeconds = 100.0, durationSeconds = 120.0))
        assertFalse(isPlayerAtNaturalEnd(positionSeconds = 0.0, durationSeconds = 0.0))
        assertFalse(isPlayerAtNaturalEnd(positionSeconds = Double.NaN, durationSeconds = 120.0))
    }

    @Test
    fun chapterStateResolvesTheLatestStartedChapter() {
        val chapters =
            listOf(
                PlayerChapter("片头", 0.0),
                PlayerChapter("第一章", 15.0),
                PlayerChapter("第二章", 45.0),
            )

        assertEquals("片头", PlayerSessionState(positionSeconds = 2.0, chapters = chapters).currentChapter?.title)
        assertEquals(
            "第一章",
            PlayerSessionState(positionSeconds = 30.0, chapters = chapters).currentChapter?.title,
        )
        assertEquals(
            "第二章",
            PlayerSessionState(positionSeconds = 60.0, chapters = chapters).currentChapter?.title,
        )
    }

    @Test
    fun queueStateExposesOnlyRealAdjacentItems() {
        assertFalse(PlayerSessionState(queueIndex = 0, queueSize = 1).hasPreviousQueueItem)
        assertFalse(PlayerSessionState(queueIndex = 0, queueSize = 1).hasNextQueueItem)
        assertTrue(PlayerSessionState(queueIndex = 1, queueSize = 3).hasPreviousQueueItem)
        assertTrue(PlayerSessionState(queueIndex = 1, queueSize = 3).hasNextQueueItem)
        assertFalse(PlayerSessionState(queueIndex = 2, queueSize = 3).hasNextQueueItem)
    }

    @Test
    fun localSeriesKeysIgnoreEpisodeAndQualityTokens() {
        assertEquals(
            playerSeriesKey("[Group] My Show S01E02 1080p.mkv"),
            playerSeriesKey("My.Show.E03.720p.mp4"),
        )
        assertEquals(
            playerSeriesKey("动画 第01集.mp4"),
            playerSeriesKey("动画 第12集.mkv"),
        )
        assertTrue(playerSeriesKey("Episode 01.mp4").isBlank())
    }

    @Test
    fun naturalTitleOrderingUsesNumericEpisodeOrder() {
        val titles = listOf("Show 10.mp4", "Show 2.mp4", "Show 01.mp4")
        assertEquals(
            listOf("Show 01.mp4", "Show 2.mp4", "Show 10.mp4"),
            titles.sortedWith(::naturalPlayerTitleCompare),
        )
    }

    @Test
    fun mediaLoadWaitsForTheAttachedSurface() {
        assertFalse(isPlayerMediaLoadReady(hasPendingLoad = true, hasAttachedSurface = false))
        assertFalse(isPlayerMediaLoadReady(hasPendingLoad = false, hasAttachedSurface = true))
        assertTrue(isPlayerMediaLoadReady(hasPendingLoad = true, hasAttachedSurface = true))
    }

    private fun externalRequest(id: String): PlayerMediaRequest =
        PlayerMediaRequest(
            requestId = id,
            uri = "content://media/external/video/1",
            title = "Video",
            source = PlayerMediaSource.EXTERNAL_INTENT,
        )
}
