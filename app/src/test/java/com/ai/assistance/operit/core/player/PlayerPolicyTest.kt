package com.ai.assistance.operit.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPolicyTest {
    @Test
    fun initialPlayerDefaultsUseRequestedPlaybackProfile() {
        val settings = FRESH_INSTALL_PLAYER_SETTINGS
        assertEquals(PlayerDecoderBackend.SOFTWARE, settings.decoderBackend)
        assertEquals(PlayerRenderingProfile.FAST, settings.renderingProfile)
        assertFalse(settings.rememberPlaybackSpeed)
        assertFalse(settings.longPressSpeedBoostEnabled)
        assertFalse(settings.rememberAnime4KMode)
        assertEquals(Anime4KMode.OFF, settings.anime4KMode)
        assertEquals(PlayerNetworkCachePolicy.BALANCED, settings.networkCachePolicy)
        assertFalse(settings.preciseSeeking)
        assertFalse(settings.seekbarThumbnailEnabled)
        assertFalse(PlayerSettings().followGravityRotation)
        assertEquals(PlayerDoubleTapAction.PLAY_PAUSE, PlayerSettings().doubleTapAction)
        assertTrue(PlayerSettings().screenshotDirectoryUri.isBlank())
        assertTrue(PlayerSettings().videoDownloadDirectoryUri.isBlank())
    }

    @Test
    fun networkCachePoliciesHaveOneOwnerAndAFullVideoMode() {
        assertEquals(
            listOf(
                PlayerNetworkCachePolicy.COMPACT,
                PlayerNetworkCachePolicy.BALANCED,
                PlayerNetworkCachePolicy.LARGE,
                PlayerNetworkCachePolicy.FULL_VIDEO,
            ),
            PlayerNetworkCachePolicy.entries,
        )
        assertFalse(PlayerNetworkCachePolicy.COMPACT.usesSessionDiskCache)
        assertFalse(PlayerNetworkCachePolicy.BALANCED.usesSessionDiskCache)
        assertFalse(PlayerNetworkCachePolicy.LARGE.usesSessionDiskCache)
        assertTrue(PlayerNetworkCachePolicy.FULL_VIDEO.usesSessionDiskCache)
        assertEquals(
            PlayerNetworkCachePolicy.LARGE.forwardBytes,
            PlayerNetworkCachePolicy.FULL_VIDEO.forwardBytes,
        )
        assertEquals(
            PlayerNetworkCachePolicy.LARGE.backwardBytes,
            PlayerNetworkCachePolicy.FULL_VIDEO.backwardBytes,
        )
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
        PlayerDecoderBackend.entries.forEach { value ->
            assertEquals(value, PlayerDecoderBackend.fromPersistedId(value.persistedId))
        }
        assertEquals("no", PlayerDecoderBackend.SOFTWARE.mpvValue)
        assertFalse(PlayerDecoderBackend.SOFTWARE.hardwareAccelerated)
        assertEquals("mediacodec", PlayerDecoderBackend.MEDIACODEC.mpvValue)
        assertTrue(PlayerDecoderBackend.MEDIACODEC.hardwareAccelerated)
        assertEquals("mediacodec-copy", PlayerDecoderBackend.MEDIACODEC_COPY.mpvValue)
        assertTrue(PlayerDecoderBackend.MEDIACODEC_COPY.hardwareAccelerated)
        PlayerRenderingProfile.entries.forEach { value ->
            assertEquals(value, PlayerRenderingProfile.fromPersistedId(value.persistedId))
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
        assertEquals(
            listOf(
                Anime4KMode.OFF,
                Anime4KMode.A,
                Anime4KMode.B,
                Anime4KMode.C,
                Anime4KMode.A_PLUS,
                Anime4KMode.B_PLUS,
                Anime4KMode.C_PLUS,
            ),
            Anime4KMode.entries,
        )
        val clamp = "Anime4K_Clamp_Highlights.glsl"
        val downscaleX2 = "Anime4K_AutoDownscalePre_x2.glsl"
        val downscaleX4 = "Anime4K_AutoDownscalePre_x4.glsl"
        val upscaleM = "Anime4K_Upscale_CNN_x2_M.glsl"
        val upscaleS = "Anime4K_Upscale_CNN_x2_S.glsl"
        assertEquals(
            mapOf(
                Anime4KMode.A to
                    listOf(
                        clamp,
                        "Anime4K_Restore_CNN_M.glsl",
                        upscaleM,
                        downscaleX2,
                        downscaleX4,
                        upscaleS,
                    ),
                Anime4KMode.B to
                    listOf(
                        clamp,
                        "Anime4K_Restore_CNN_Soft_M.glsl",
                        upscaleM,
                        downscaleX2,
                        downscaleX4,
                        upscaleS,
                    ),
                Anime4KMode.C to
                    listOf(
                        clamp,
                        "Anime4K_Upscale_Denoise_CNN_x2_M.glsl",
                        downscaleX2,
                        downscaleX4,
                        upscaleS,
                    ),
                Anime4KMode.A_PLUS to
                    listOf(
                        clamp,
                        "Anime4K_Restore_CNN_M.glsl",
                        upscaleM,
                        downscaleX2,
                        downscaleX4,
                        "Anime4K_Restore_CNN_S.glsl",
                        upscaleS,
                    ),
                Anime4KMode.B_PLUS to
                    listOf(
                        clamp,
                        "Anime4K_Restore_CNN_Soft_M.glsl",
                        upscaleM,
                        downscaleX2,
                        downscaleX4,
                        "Anime4K_Restore_CNN_Soft_S.glsl",
                        upscaleS,
                    ),
                Anime4KMode.C_PLUS to
                    listOf(
                        clamp,
                        "Anime4K_Upscale_Denoise_CNN_x2_M.glsl",
                        downscaleX2,
                        downscaleX4,
                        "Anime4K_Restore_CNN_S.glsl",
                        upscaleS,
                    ),
            ),
            Anime4KMode.entries
                .filterNot { it == Anime4KMode.OFF }
                .associateWith(Anime4KMode::shaderFiles),
        )
        Anime4KMode.entries.filterNot { it == Anime4KMode.OFF }.forEach { mode ->
            assertTrue(mode.shaderFiles.isNotEmpty())
            assertTrue(mode.shaderFiles.all { it.startsWith("Anime4K_") && it.endsWith(".glsl") })
        }
    }

    @Test
    fun legacyAnime4KPreferencesMigrateToTheSevenModeSchema() {
        assertEquals(Anime4KMode.OFF.persistedId, migrateLegacyAnime4KPersistedId("off"))
        assertEquals(Anime4KMode.B.persistedId, migrateLegacyAnime4KPersistedId("fast"))
        assertEquals(Anime4KMode.A.persistedId, migrateLegacyAnime4KPersistedId("balanced"))
        assertEquals(Anime4KMode.A_PLUS.persistedId, migrateLegacyAnime4KPersistedId("quality"))
        Anime4KMode.entries.forEach { mode ->
            assertEquals(mode.persistedId, migrateLegacyAnime4KPersistedId(mode.persistedId))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Anime4KMode.fromPersistedId(migrateLegacyAnime4KPersistedId("unexpected"))
        }
    }

    @Test
    fun playerSpeedMenuCustomInputAndLongPressBucketsHaveExactContracts() {
        assertEquals(
            listOf(
                3.0,
                2.75,
                2.5,
                2.25,
                2.0,
                1.75,
                1.5,
                1.25,
                1.0,
                0.75,
                0.5,
                0.25,
            ),
            PLAYER_SPEED_MENU_OPTIONS,
        )
        assertEquals(
            listOf(
                "3.0x",
                "2.75x",
                "2.5x",
                "2.25x",
                "2.0x",
                "1.75x",
                "1.5x",
                "1.25x",
                "1.0x",
                "0.75x",
                "0.5x",
                "0.25x",
            ),
            PLAYER_SPEED_MENU_OPTIONS.map(::formatPlayerSpeedLabel),
        )
        assertEquals(0.0, parsePlayerSpeedInput("0.00")!!, 0.0)
        assertEquals(0.01, parsePlayerSpeedInput("0.01")!!, 0.0)
        assertEquals(1.23, parsePlayerSpeedInput("1.23")!!, 0.0)
        assertEquals(3.0, parsePlayerSpeedInput("3.00")!!, 0.0)
        assertEquals(null, parsePlayerSpeedInput("-0.25"))
        assertEquals(null, parsePlayerSpeedInput("1.234"))
        assertEquals(null, parsePlayerSpeedInput("3.01"))
        assertTrue(isSupportedPlayerSpeed(0.01))
        assertTrue(isSupportedPlayerSpeed(3.0))
        assertFalse(isSupportedPlayerSpeed(0.0))
        assertFalse(isSupportedPlayerSpeed(3.01))
        assertEquals(1.0, resolveLongPressPlayerSpeed(0.25))
        assertEquals(1.0, resolveLongPressPlayerSpeed(0.99))
        assertEquals(2.0, resolveLongPressPlayerSpeed(1.0))
        assertEquals(2.0, resolveLongPressPlayerSpeed(1.99))
        assertEquals(3.0, resolveLongPressPlayerSpeed(2.0))
        assertEquals(3.0, resolveLongPressPlayerSpeed(2.99))
        assertEquals(null, resolveLongPressPlayerSpeed(3.0))
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
    fun historyReplayRequestDoesNotRequireAWebSessionIdentity() {
        val request =
            PlayerMediaRequest(
                requestId = "history-1",
                uri = "https://media.example/video.mp4",
                title = "Video",
                source = PlayerMediaSource.HISTORY_REPLAY,
            )

        assertNull(request.sourceSessionId)
    }

    @Test
    fun seekPreviewAllowsLocalMediaAndOnlyCompletedFullCacheNetworkMedia() {
        val localState =
            PlayerSessionState(
                request =
                    PlayerMediaRequest(
                        requestId = "local",
                        uri = "content://media/external/video/1",
                        title = "Local",
                        source = PlayerMediaSource.EXTERNAL_INTENT,
                    ),
                durationSeconds = 120.0,
                runtimeState = PlayerRuntimeState.ACTIVE,
            )
        val networkState =
            PlayerSessionState(
                request =
                    PlayerMediaRequest(
                        requestId = "network",
                        uri = "https://media.example/video.mp4",
                        title = "Network",
                        source = PlayerMediaSource.BROWSER_CANDIDATE,
                        sourceSessionId = "web-session",
                    ),
                durationSeconds = 120.0,
                runtimeState = PlayerRuntimeState.ACTIVE,
            )

        assertTrue(canRequestPlayerSeekPreview(settingsEnabled = true, state = localState))
        assertFalse(canRequestPlayerSeekPreview(settingsEnabled = true, state = networkState))
        assertTrue(
            canRequestPlayerSeekPreview(
                settingsEnabled = true,
                state = networkState.copy(fullVideoCacheComplete = true),
            ),
        )
        assertFalse(
            canRequestPlayerSeekPreview(
                settingsEnabled = false,
                state = networkState.copy(fullVideoCacheComplete = true),
            ),
        )
    }

    @Test
    fun latestSeekTargetIsSubmittedAfterTheActiveSeekCompletes() {
        assertEquals(
            PlayerSeekCompletionPlan(
                pendingTargetSeconds = null,
                followUpTargetSeconds = null,
            ),
            resolvePlayerSeekCompletionPlan(
                completedCommandTargetSeconds = 120.0,
                latestRequestedTargetSeconds = 120.0,
            ),
        )
        assertEquals(
            PlayerSeekCompletionPlan(
                pendingTargetSeconds = 180.0,
                followUpTargetSeconds = 180.0,
            ),
            resolvePlayerSeekCompletionPlan(
                completedCommandTargetSeconds = 120.0,
                latestRequestedTargetSeconds = 180.0,
            ),
        )
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
                seeking = true,
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
        assertTrue(transition.state.seeking)
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
                sourcePageUrl = "https://page.example/watch",
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
                durationSeconds = 420.0,
                paused = false,
                speed = 1.75,
                buffering = true,
                seeking = true,
                pendingSeekTargetSeconds = 84.25,
                audioTracks =
                    listOf(
                        PlayerTrack(
                            id = 1,
                            title = "Main",
                            language = "zh",
                            selected = true,
                        ),
                    ),
                subtitleTracks =
                    listOf(
                        PlayerTrack(
                            id = 2,
                            title = "中文",
                            language = "zh",
                            selected = true,
                        ),
                    ),
                selectedAudioTrackId = 1,
                selectedSubtitleTrackId = 2,
                chapters = listOf(PlayerChapter(title = "Chapter", startSeconds = 12.0)),
                seekPreview =
                    PlayerSeekPreview(
                        positionSeconds = 84.25,
                        bitmap = null,
                        loading = true,
                    ),
                mediaContainer = "mp4",
                videoCodec = "h264",
                audioCodec = "aac",
                videoTrackCount = 1,
                fullVideoCacheActive = true,
                fullVideoCacheComplete = true,
                fullVideoCacheStartSeconds = 0.0,
                fullVideoCacheEndSeconds = 420.0,
                fullVideoCachePhase = "COMPLETE",
                fullVideoCacheReason = "complete",
                fullVideoCacheStateEvidence = "AVAILABLE",
                fullVideoCacheFileBytes = 440_401_920L,
                fullVideoCacheExpectedBytes = 440_401_920L,
                queueIndex = 1,
                queueSize = 3,
                decoderBackend = PlayerDecoderBackend.MEDIACODEC_COPY,
                renderingProfile = PlayerRenderingProfile.HIGH_QUALITY,
                activeHardwareDecoder = "mediacodec-copy",
                videoPixelFormat = "nv12",
                videoCodecProfile = "High",
                anime4KMode = Anime4KMode.B_PLUS,
                activeShaderFiles = listOf("/private/anime4k.glsl"),
                networkSpeedBytesPerSecond = 8_388_608L,
                videoFitMode = PlayerVideoFitMode.CROP,
                loadGeneration = 7L,
                runtimeGeneration = 5L,
                runtimeState = PlayerRuntimeState.ACTIVE,
                runtimeProcessId = 4242,
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
        assertSame(request, fullscreen.state.request)
        assertSame(request, returnedFloating.state.request)
        assertEquals(
            floating.copy(presentation = PlayerPresentation.FULLSCREEN_PLAYER),
            fullscreen.state,
        )
        assertEquals(floating, returnedFloating.state)
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
                seeking = true,
                loadGeneration = 4L,
            )
        val settings =
            PlayerSettings(
                defaultSpeed = 1.25,
                decoderBackend = PlayerDecoderBackend.MEDIACODEC_COPY,
                renderingProfile = PlayerRenderingProfile.HIGH_QUALITY,
                anime4KMode = Anime4KMode.B,
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
        assertFalse(transition.state.seeking)
        assertNull(transition.state.pendingSeekTargetSeconds)
        assertEquals(5L, transition.state.loadGeneration)
        assertEquals(PlayerDecoderBackend.MEDIACODEC_COPY, transition.state.decoderBackend)
        assertEquals(PlayerRenderingProfile.HIGH_QUALITY, transition.state.renderingProfile)
        assertEquals(Anime4KMode.B, transition.state.anime4KMode)
    }

    @Test
    fun mediaStartRespectsSpeedAndAnime4KMemorySwitches() {
        val remembered =
            PlayerSettings(
                defaultSpeed = 1.0,
                lastPlaybackSpeed = 1.5,
                rememberPlaybackSpeed = true,
                anime4KMode = Anime4KMode.C_PLUS,
                rememberAnime4KMode = true,
            )
        assertEquals(1.5, resolveInitialPlayerSpeed(remembered), 0.0)
        assertEquals(Anime4KMode.C_PLUS, resolveInitialAnime4KMode(remembered))

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
        assertFalse(
            shouldReturnFullscreenPlayerToFloating(
                browserState.copy(
                    request =
                        PlayerMediaRequest(
                            requestId = "history",
                            uri = "https://media.example/video.mp4",
                            title = "History",
                            source = PlayerMediaSource.HISTORY_REPLAY,
                        ),
                ),
                PlayerSettings(
                    fullscreenExitBehavior = PlayerFullscreenExitBehavior.RETURN_TO_FLOATING,
                ),
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
