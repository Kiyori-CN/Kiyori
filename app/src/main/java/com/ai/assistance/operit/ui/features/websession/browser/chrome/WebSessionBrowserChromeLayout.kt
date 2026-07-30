package com.ai.assistance.operit.ui.features.websession.browser.chrome

import androidx.compose.runtime.Immutable

// App Shell and browser bottom bars share one five-slot Kiyori geometry contract.
internal const val WEB_SESSION_BROWSER_CHROME_SLOT_COUNT = 5
internal const val WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP = 16
internal const val WEB_SESSION_BROWSER_BOTTOM_TOP_PADDING_DP = 0
internal const val WEB_SESSION_BROWSER_BOTTOM_BOTTOM_PADDING_DP = 6
internal const val WEB_SESSION_BROWSER_BOTTOM_ACTION_SIZE_DP = 44
internal const val WEB_SESSION_BROWSER_BOTTOM_ICON_SIZE_DP = 26
internal const val WEB_SESSION_BROWSER_BOTTOM_CENTER_ICON_SIZE_DP = 25
// The drawer remains content-sized so changing the fourth-row anchor cannot stretch the first row.
internal const val WEB_SESSION_BROWSER_MENU_START_PADDING_DP = 14
internal const val WEB_SESSION_BROWSER_MENU_TOP_PADDING_DP = 18
internal const val WEB_SESSION_BROWSER_MENU_END_PADDING_DP = 14
internal const val WEB_SESSION_BROWSER_MENU_BOTTOM_PADDING_DP = 8
internal const val WEB_SESSION_BROWSER_MENU_ROW_SPACING_DP = 1
internal const val WEB_SESSION_BROWSER_MENU_CELL_HORIZONTAL_PADDING_DP = 2
internal const val WEB_SESSION_BROWSER_MENU_CELL_VERTICAL_PADDING_DP = 8
internal const val WEB_SESSION_BROWSER_MENU_ICON_CONTAINER_SIZE_DP = 32
internal const val WEB_SESSION_BROWSER_MENU_ICON_SIZE_DP = 21
internal const val WEB_SESSION_BROWSER_MENU_ICON_LABEL_SPACING_DP = 6
internal const val WEB_SESSION_BROWSER_MENU_LABEL_SIZE_SP = 11
internal const val WEB_SESSION_BROWSER_MENU_BOTTOM_ROW_HORIZONTAL_PADDING_DP = 6
internal const val WEB_SESSION_BROWSER_MENU_BOTTOM_ROW_TOP_PADDING_DP = 8
internal const val WEB_SESSION_BROWSER_MENU_BOTTOM_ACTION_WIDTH_DP = 46
internal const val WEB_SESSION_BROWSER_MENU_BOTTOM_ACTION_HEIGHT_DP = 36
internal const val WEB_SESSION_BROWSER_MENU_BOTTOM_ICON_SIZE_DP = 22
internal const val WEB_SESSION_BROWSER_MENU_BOTTOM_SIDE_SLOT_WEIGHT = 2
internal const val WEB_SESSION_BROWSER_MENU_BOTTOM_CENTER_SLOT_WEIGHT = 1
internal const val WEB_SESSION_BROWSER_TOP_HORIZONTAL_PADDING_DP = 8
internal const val WEB_SESSION_BROWSER_TOP_VERTICAL_PADDING_DP = 8
internal const val WEB_SESSION_BROWSER_TOP_ACTION_SIZE_DP = 40
internal const val WEB_SESSION_BROWSER_TOP_SEARCH_HEIGHT_DP = 42
internal const val WEB_SESSION_BROWSER_TOP_GAP_DP = 6

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
