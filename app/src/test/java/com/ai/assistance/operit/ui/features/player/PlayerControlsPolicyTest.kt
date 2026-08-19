package com.ai.assistance.operit.ui.features.player

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControlsPolicyTest {
    @Test
    fun `gravity rotation changes only when the policy changes`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            resolvePlayerGravityOrientationRequest(previousEnabled = false, enabled = true),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            resolvePlayerGravityOrientationRequest(previousEnabled = true, enabled = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            resolvePlayerGravityOrientationRequest(previousEnabled = null, enabled = false),
        )
        assertEquals(
            null,
            resolvePlayerGravityOrientationRequest(previousEnabled = true, enabled = true),
        )
    }

    @Test
    fun `single tap toggles controls and reveals unlock buttons while locked`() {
        assertEquals(
            PlayerControlsTapAction.SHOW_CONTROLS,
            resolvePlayerControlsTapAction(controlsVisible = false, controlsLocked = false),
        )
        assertEquals(
            PlayerControlsTapAction.HIDE_CONTROLS,
            resolvePlayerControlsTapAction(controlsVisible = true, controlsLocked = false),
        )
        assertEquals(
            PlayerControlsTapAction.SHOW_UNLOCK_BUTTONS,
            resolvePlayerControlsTapAction(controlsVisible = false, controlsLocked = true),
        )
        assertEquals(
            PlayerControlsTapAction.SHOW_UNLOCK_BUTTONS,
            resolvePlayerControlsTapAction(controlsVisible = true, controlsLocked = true),
        )
    }

    @Test
    fun `playing controls auto hide only when no interaction owner is active`() {
        val idlePlaying =
            PlayerControlsAutoHideInputs(
                controlsVisible = true,
                controlsLocked = false,
                paused = false,
                loading = false,
                popupVisible = false,
                logVisible = false,
                seekActive = false,
                gestureActive = false,
            )
        assertTrue(shouldAutoHidePlayerControls(idlePlaying))

        assertFalse(shouldAutoHidePlayerControls(idlePlaying.copy(controlsVisible = false)))
        assertFalse(shouldAutoHidePlayerControls(idlePlaying.copy(controlsLocked = true)))
        assertFalse(shouldAutoHidePlayerControls(idlePlaying.copy(paused = true)))
        assertFalse(shouldAutoHidePlayerControls(idlePlaying.copy(loading = true)))
        assertFalse(shouldAutoHidePlayerControls(idlePlaying.copy(popupVisible = true)))
        assertFalse(shouldAutoHidePlayerControls(idlePlaying.copy(logVisible = true)))
        assertFalse(shouldAutoHidePlayerControls(idlePlaying.copy(seekActive = true)))
        assertFalse(shouldAutoHidePlayerControls(idlePlaying.copy(gestureActive = true)))
    }

    @Test
    fun `horizontal seek uses the current press position and the shared bounded span`() {
        assertEquals(
            310.0,
            resolvePlayerHorizontalGestureSeekTarget(
                basePositionSeconds = 10.0,
                durationSeconds = 1_000.0,
                horizontalDeltaPx = 1_000f,
                gestureWidthPx = 1_000,
            ),
            0.0,
        )
        assertEquals(
            0.0,
            resolvePlayerHorizontalGestureSeekTarget(
                basePositionSeconds = 10.0,
                durationSeconds = 1_000.0,
                horizontalDeltaPx = -1_000f,
                gestureWidthPx = 1_000,
            ),
            0.0,
        )
        assertEquals(
            60.0,
            resolvePlayerHorizontalGestureSeekTarget(
                basePositionSeconds = 30.0,
                durationSeconds = 120.0,
                horizontalDeltaPx = 250f,
                gestureWidthPx = 1_000,
            ),
            0.0,
        )
    }
}
