package com.ai.assistance.operit.ui.features.player

import android.content.pm.ActivityInfo

internal const val PLAYER_CONTROLS_AUTO_HIDE_MILLIS = 3_000L
internal const val PLAYER_UNLOCK_BUTTONS_AUTO_HIDE_MILLIS = 3_000L

internal enum class PlayerControlsTapAction {
    SHOW_CONTROLS,
    HIDE_CONTROLS,
    SHOW_UNLOCK_BUTTONS,
}

internal data class PlayerControlsAutoHideInputs(
    val controlsVisible: Boolean,
    val controlsLocked: Boolean,
    val paused: Boolean,
    val loading: Boolean,
    val popupVisible: Boolean,
    val logVisible: Boolean,
    val seekActive: Boolean,
    val gestureActive: Boolean,
)

internal fun resolvePlayerControlsTapAction(
    controlsVisible: Boolean,
    controlsLocked: Boolean,
): PlayerControlsTapAction =
    when {
        controlsLocked -> PlayerControlsTapAction.SHOW_UNLOCK_BUTTONS
        controlsVisible -> PlayerControlsTapAction.HIDE_CONTROLS
        else -> PlayerControlsTapAction.SHOW_CONTROLS
    }

internal fun shouldAutoHidePlayerControls(inputs: PlayerControlsAutoHideInputs): Boolean =
    inputs.controlsVisible &&
        !inputs.controlsLocked &&
        !inputs.paused &&
        !inputs.loading &&
        !inputs.popupVisible &&
        !inputs.logVisible &&
        !inputs.seekActive &&
        !inputs.gestureActive

internal fun resolvePlayerGravityOrientationRequest(
    previousEnabled: Boolean?,
    enabled: Boolean,
): Int? =
    when {
        previousEnabled == enabled -> null
        enabled -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        previousEnabled == true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> null
    }
