package com.ai.assistance.operit.ui.features.websession.browser.chrome

import androidx.compose.runtime.Immutable

@Immutable
internal data class WebSessionBrowserChromeLayout(
    val tabColumnCount: Int,
    val drawerMaxWidthDp: Float,
    val drawerPartialFraction: Float,
)

internal fun resolveWebSessionBrowserChromeLayout(
    widthDp: Float,
    heightDp: Float,
): WebSessionBrowserChromeLayout {
    require(widthDp > 0f) { "widthDp must be positive" }
    require(heightDp > 0f) { "heightDp must be positive" }

    return when {
        widthDp < 600f ->
            WebSessionBrowserChromeLayout(
                tabColumnCount = 2,
                drawerMaxWidthDp = (widthDp - 12f).coerceAtLeast(1f),
                drawerPartialFraction = if (widthDp > heightDp) 0.78f else 0.64f,
            )
        widthDp < 840f ->
            WebSessionBrowserChromeLayout(
                tabColumnCount = 3,
                drawerMaxWidthDp = 600f,
                drawerPartialFraction = 0.68f,
            )
        else ->
            WebSessionBrowserChromeLayout(
                tabColumnCount = 4,
                drawerMaxWidthDp = 680f,
                drawerPartialFraction = 0.72f,
            )
    }
}
