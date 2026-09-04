package com.ai.assistance.operit.ui.features.websession.browser

import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmark
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserBackAction
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserHostState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryCategory
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveWebSessionBrowserBackAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserHomeDashboardPolicyTest {
    @Test
    fun `configured home is first and secret or duplicate entries are not projected`() {
        val entries =
            buildBrowserHomeDashboardEntries(
                bookmarks =
                    listOf(
                        WebSessionBookmark(
                            url = "https://example.com/",
                            title = "Example",
                            createdAt = 1L,
                            updatedAt = 1L,
                            id = 1L,
                        ),
                        WebSessionBookmark(
                            url = "https://private.example/",
                            title = "Private",
                            createdAt = 2L,
                            updatedAt = 2L,
                            id = 2L,
                            secret = true,
                        ),
                        WebSessionBookmark(
                            url = "https://web.gotab.cn/",
                            title = "GoTab duplicate",
                            createdAt = 3L,
                            updatedAt = 3L,
                            id = 3L,
                        ),
                    ),
                history = emptyList(),
                configuredHomeUrl = "https://web.gotab.cn/",
            )

        assertEquals("configured-home", entries.first().id)
        assertEquals("https://web.gotab.cn/", entries.first().url)
        assertEquals(listOf("https://web.gotab.cn/", "https://example.com/"), entries.map { it.url })
        assertFalse(entries.any { it.title == "Private" })
    }

    @Test
    fun `recent projection accepts web history and excludes non-web records`() {
        val entries =
            buildBrowserHomeDashboardEntries(
                bookmarks = emptyList(),
                history =
                    listOf(
                        WebSessionHistoryEntry(
                            url = "https://news.example/article",
                            title = "News",
                            visitedAt = 20L,
                            category = WebSessionHistoryCategory.WEB,
                        ),
                        WebSessionHistoryEntry(
                            url = "https://video.example/watch",
                            title = "Video",
                            visitedAt = 19L,
                            category = WebSessionHistoryCategory.VIDEO,
                        ),
                    ),
                configuredHomeUrl = "https://web.gotab.cn/",
            )

        assertEquals(2, entries.size)
        assertEquals(BrowserHomeDashboardEntryKind.HISTORY, entries.last().kind)
        assertEquals("news.example", entries.last().host)
        assertTrue(entries.none { it.title == "Video" })
    }

    @Test
    fun `host projection normalizes host casing without changing the url`() {
        assertEquals("example.com", browserHomeDashboardHost("HTTPS://EXAMPLE.COM/path"))
    }

    @Test
    fun `system back closes native home only when it can return to a page`() {
        assertEquals(
            WebSessionBrowserBackAction.CLOSE_NATIVE_HOME,
            resolveWebSessionBrowserBackAction(
                WebSessionBrowserHostState(
                    isNativeHomeVisible = true,
                    nativeHomeCanReturnToPage = true,
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.EXIT_BROWSER,
            resolveWebSessionBrowserBackAction(
                WebSessionBrowserHostState(isNativeHomeVisible = true),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.SHOW_NATIVE_HOME,
            resolveWebSessionBrowserBackAction(
                WebSessionBrowserHostState(
                    browserState =
                        com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserState(
                            homeMode =
                                com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserHomeMode.NATIVE,
                            canShowNativeHome = true,
                        ),
                ),
            ),
        )
    }
}
