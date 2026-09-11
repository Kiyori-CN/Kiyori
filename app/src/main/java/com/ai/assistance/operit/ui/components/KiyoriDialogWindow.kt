package com.ai.assistance.operit.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** 仅用于自己绘制遮罩的 Dialog，防止系统 dim 与 Compose 遮罩叠加后过暗。 */
@Composable
internal fun KiyoriDialogOwnsScrim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setDimAmount(0f) }
}
