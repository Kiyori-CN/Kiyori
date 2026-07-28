package com.ai.assistance.operit.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPolicyTest {
    @Test
    fun persistedSettingsMapToExactRuntimeValues() {
        PlayerHardwareDecodingPolicy.entries.forEach { value ->
            assertEquals(value, PlayerHardwareDecodingPolicy.fromPersistedId(value.persistedId))
            assertTrue(value.mpvValue.isNotBlank())
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
        val floating =
            PlayerSessionState(
                request = request,
                presentation = PlayerPresentation.FLOATING_PLAYER,
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
                hardwareDecodingPolicy = PlayerHardwareDecodingPolicy.MEDIA_CODEC_COPY,
                anime4KMode = Anime4KMode.BALANCED,
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
        assertEquals(PlayerHardwareDecodingPolicy.MEDIA_CODEC_COPY, transition.state.hardwareDecodingPolicy)
        assertEquals(Anime4KMode.BALANCED, transition.state.anime4KMode)
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

    private fun externalRequest(id: String): PlayerMediaRequest =
        PlayerMediaRequest(
            requestId = id,
            uri = "content://media/external/video/1",
            title = "Video",
            source = PlayerMediaSource.EXTERNAL_INTENT,
        )
}
