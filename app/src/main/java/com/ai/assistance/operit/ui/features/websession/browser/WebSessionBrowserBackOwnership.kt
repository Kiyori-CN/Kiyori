package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the retained Browser presentation currently owns Android system Back.
 *
 * Browser screens remain composed while a Shell settings surface is visible. Every BackHandler in
 * that retained subtree must observe the same owner bit so callback registration order cannot route
 * a settings Back event into hidden webpage state.
 */
internal val LocalWebSessionBrowserSystemBackEnabled =
    staticCompositionLocalOf<Boolean> {
        error("Browser system Back ownership must be provided by the current Kiyori host.")
    }
