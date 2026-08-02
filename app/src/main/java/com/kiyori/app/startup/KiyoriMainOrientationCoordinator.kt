package com.kiyori.app.startup

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.kiyori.platform.logging.KiyoriLogger

internal data class KiyoriMainOrientationState(
    val lastOrientation: Int? = null,
    val showChangeDialog: Boolean = false,
)

internal fun resolveKiyoriMainOrientationChange(
    currentState: KiyoriMainOrientationState,
    newOrientation: Int,
): KiyoriMainOrientationState {
    if (newOrientation == currentState.lastOrientation) {
        return currentState
    }
    return KiyoriMainOrientationState(
        lastOrientation = newOrientation,
        showChangeDialog = true,
    )
}

/**
 * MainActivity 方向变化状态与确认对话框的唯一状态 owner。
 *
 * Activity 继续负责系统 lifecycle 回调和 recreate 副作用；该 coordinator 只保存方向状态，
 * 应用既有状态转换并暴露对话框是否可见。
 */
internal class KiyoriMainOrientationCoordinator(
    private val orientationChangeLogger: (newOrientation: Int, lastOrientation: Int?) -> Unit =
        { newOrientation, lastOrientation ->
            KiyoriLogger.d(
                "MainActivity",
                "onConfigurationChanged: new orientation=$newOrientation, " +
                    "last orientation=$lastOrientation",
            )
        },
) {
    private var state by mutableStateOf(KiyoriMainOrientationState())

    val showChangeDialog: Boolean
        get() = state.showChangeDialog

    fun initialize(initialOrientation: Int) {
        state =
            KiyoriMainOrientationState(
                lastOrientation = initialOrientation,
                showChangeDialog = false,
            )
    }

    fun onConfigurationChanged(newOrientation: Int) {
        orientationChangeLogger(
            newOrientation,
            state.lastOrientation,
        )
        state =
            resolveKiyoriMainOrientationChange(
                currentState = state,
                newOrientation = newOrientation,
            )
    }

    fun dismissChangeDialog() {
        if (!state.showChangeDialog) {
            return
        }
        state = state.copy(showChangeDialog = false)
    }
}

@Composable
internal fun KiyoriMainOrientationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = R.string.dialog_title_orientation_change))
        },
        text = {
            Text(text = stringResource(id = R.string.dialog_message_orientation_change))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(id = R.string.dialog_button_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.dialog_button_dismiss))
            }
        },
    )
}
