package com.ai.assistance.operit.ui.features.websession.browser

import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchRecord
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_MENU_ICON_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_MENU_LABEL_SIZE_SP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_ACTION_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_GAP_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_HORIZONTAL_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_SEARCH_HEIGHT_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_VERTICAL_PADDING_DP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionSearchUiPolicyTest {
    private val history =
        listOf(
            WebSessionSearchRecord(
                id = 1L,
                query = "美感",
                targetUrl = "https://www.baidu.com/s?wd=%E7%BE%8E%E6%84%9F",
                createdAt = 3L,
            ),
            WebSessionSearchRecord(
                id = 2L,
                query = "https://example.com",
                targetUrl = "https://example.com/",
                createdAt = 2L,
            ),
            WebSessionSearchRecord(
                id = 3L,
                query = "现代浏览器",
                targetUrl = "https://www.baidu.com/s?wd=%E7%8E%B0%E4%BB%A3%E6%B5%8F%E8%A7%88%E5%99%A8",
                createdAt = 1L,
            ),
        )

    @Test
    fun `history edit ignores ids that are no longer active`() {
        val commit =
            resolveSearchHistoryEditCommit(
                searchHistory = history,
                pendingDeletionIds = setOf(2L, 99L),
            )

        assertFalse(commit.clearAll)
        assertEquals(setOf(2L), commit.recordIds)
    }

    @Test
    fun `history edit clears the store only when every active tag is selected`() {
        val commit =
            resolveSearchHistoryEditCommit(
                searchHistory = history,
                pendingDeletionIds = setOf(1L, 2L, 3L),
            )

        assertTrue(commit.clearAll)
        assertEquals(setOf(1L, 2L, 3L), commit.recordIds)
    }

    @Test
    fun `every search engine exposes a real icon resource`() {
        assertTrue(WebSessionSearchEngine.entries.all { engine -> engine.iconResId != 0 })
        assertEquals(
            WebSessionSearchEngine.entries.size,
            WebSessionSearchEngine.entries.map { engine -> engine.iconResId }.toSet().size,
        )
    }

    @Test
    fun `full screen search uses the compact browser menu scale`() {
        assertEquals(8, WEB_SESSION_BROWSER_TOP_HORIZONTAL_PADDING_DP)
        assertEquals(8, WEB_SESSION_BROWSER_TOP_VERTICAL_PADDING_DP)
        assertEquals(40, WEB_SESSION_BROWSER_TOP_ACTION_SIZE_DP)
        assertEquals(42, WEB_SESSION_BROWSER_TOP_SEARCH_HEIGHT_DP)
        assertEquals(6, WEB_SESSION_BROWSER_TOP_GAP_DP)
        assertEquals(3, WEB_SESSION_SEARCH_SCREEN_INPUT_MAX_LINES)
        assertEquals(18, WEB_SESSION_SEARCH_SCREEN_INPUT_LINE_HEIGHT_SP)
        assertEquals(32, WEB_SESSION_SEARCH_SCREEN_CLEAR_ACTION_SIZE_DP)
        assertTrue(
            WEB_SESSION_SEARCH_SCREEN_CLEAR_ACTION_SIZE_DP < WEB_SESSION_BROWSER_TOP_ACTION_SIZE_DP,
        )
        assertEquals(18, WEB_SESSION_SEARCH_SCREEN_ENGINE_ICON_SIZE_DP)
        assertEquals(42, WEB_SESSION_SEARCH_SCREEN_ENGINE_CARD_HEIGHT_DP)
        assertEquals(19, WEB_SESSION_SEARCH_SCREEN_ENGINE_CARD_ICON_SIZE_DP)
        assertEquals(46, WEB_SESSION_SEARCH_SCREEN_CURRENT_ACTION_WIDTH_DP)
        assertEquals(14, WEB_SESSION_SEARCH_SCREEN_CURRENT_ACTION_ICON_SIZE_DP)
        assertEquals(10, WEB_SESSION_SEARCH_SCREEN_CURRENT_ACTION_LABEL_SIZE_SP)
        assertEquals(4, WEB_SESSION_SEARCH_SCREEN_CURRENT_OUTER_VERTICAL_PADDING_DP)
        assertEquals(4, WEB_SESSION_SEARCH_SCREEN_CURRENT_INFO_VERTICAL_PADDING_DP)
        assertEquals(12, WEB_SESSION_SEARCH_SCREEN_CURRENT_TITLE_SIZE_SP)
        assertEquals(10, WEB_SESSION_SEARCH_SCREEN_CURRENT_URL_SIZE_SP)
        assertEquals(18, WEB_SESSION_SEARCH_SCREEN_HISTORY_TITLE_SIZE_SP)
        assertEquals(14, WEB_SESSION_SEARCH_SCREEN_HISTORY_EMPTY_SIZE_SP)
        assertEquals(14, WEB_SESSION_SEARCH_SCREEN_HISTORY_ACTION_SIZE_SP)
        assertEquals(34, WEB_SESSION_SEARCH_SCREEN_HISTORY_HEADER_HEIGHT_DP)
        assertEquals(34, WEB_SESSION_SEARCH_SCREEN_HISTORY_PROFILE_ACTION_SIZE_DP)
        assertEquals(4, WEB_SESSION_SEARCH_SCREEN_HISTORY_PROFILE_GAP_DP)
        assertEquals(
            WEB_SESSION_SEARCH_SCREEN_HISTORY_HEADER_HEIGHT_DP,
            WEB_SESSION_SEARCH_SCREEN_HISTORY_PROFILE_ACTION_SIZE_DP,
        )
        assertEquals(24, WEB_SESSION_SEARCH_SCREEN_HISTORY_DELETE_ICON_SIZE_DP)
        assertEquals(250, WEB_SESSION_SEARCH_SCREEN_TAG_MAX_WIDTH_DP)
        assertEquals(28, WEB_SESSION_SEARCH_ENGINE_SWITCH_BAR_CHIP_HEIGHT_DP)
        assertEquals(12, WEB_SESSION_SEARCH_ENGINE_SWITCH_BAR_ICON_SIZE_DP)
        assertEquals(22, WEB_SESSION_SEARCH_ENGINE_SWITCH_BAR_CLOSE_SIZE_DP)
        assertEquals(21, WEB_SESSION_BROWSER_MENU_ICON_SIZE_DP)
        assertEquals(11, WEB_SESSION_BROWSER_MENU_LABEL_SIZE_SP)
        assertTrue(
            WEB_SESSION_SEARCH_SCREEN_ENGINE_CARD_ICON_SIZE_DP <=
                WEB_SESSION_BROWSER_MENU_ICON_SIZE_DP
        )
    }
}
