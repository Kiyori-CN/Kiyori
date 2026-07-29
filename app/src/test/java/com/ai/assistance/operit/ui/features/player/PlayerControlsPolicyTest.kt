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
            null,
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
}
