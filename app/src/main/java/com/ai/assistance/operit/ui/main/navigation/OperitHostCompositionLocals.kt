package com.ai.assistance.operit.ui.main.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Operit UI screens publish top-bar content to the Kiyori host through this
 * narrow Compose contract. Keeping the contract in the Operit UI root prevents
 * feature screens from depending on the concrete Kiyori app composition.
 */
val LocalTopBarActions =
    compositionLocalOf<(@Composable (RowScope.() -> Unit)) -> Unit> { {} }

/** Requests that the Kiyori host reveal the existing browser runtime. */
val LocalOpenBrowser = compositionLocalOf<() -> Unit> { {} }

class TopBarTitleContent(val content: @Composable () -> Unit)

val LocalTopBarTitleContent =
    compositionLocalOf<(TopBarTitleContent?) -> Unit> { {} }

/**
 * Exposes the current Operit route catalog to Operit-owned screens without
 * making those screens depend on the Kiyori root Composable.
 */
val LocalAppNavigationModel = compositionLocalOf<AppNavigationModel?> { null }
