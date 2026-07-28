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
        PlayerEndBehavior.entries.forEach { value ->
            assertEquals(value, PlayerEndBehavior.fromPersistedId(value.persistedId))
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
        PlayerDebugLogBuffer.append("PlayerSession", "line one\nline two")

        val snapshot = PlayerDebugLogBuffer.snapshot()

        assertTrue(snapshot.contains("PlayerSession: line one line two"))
        PlayerDebugLogBuffer.clear()
        assertTrue(PlayerDebugLogBuffer.snapshot().isBlank())
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
                registration.state,
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
