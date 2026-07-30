package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionHistoryPolicyTest {
    @Test
    fun `history filter keeps category order and searches source page`() {
        val entries =
            listOf(
                entry(
                    url = "https://cdn.example.com/video.mp4",
                    title = "Episode 1",
                    visitedAt = 300L,
                    category = WebSessionHistoryCategory.VIDEO,
                    sourcePageUrl = "https://example.com/watch/1",
                ),
                entry(
                    url = "https://example.com/",
                    title = "Example",
                    visitedAt = 200L,
                    category = WebSessionHistoryCategory.WEB,
                ),
                entry(
                    url = "content://media/video/2",
                    title = "Local episode",
                    visitedAt = 100L,
                    category = WebSessionHistoryCategory.VIDEO,
                    mediaOrigin = WebSessionHistoryMediaOrigin.LOCAL,
                ),
            )

        assertEquals(
            listOf("https://cdn.example.com/video.mp4", "content://media/video/2"),
            filterWebSessionHistoryEntries(
                entries = entries,
                filter = WebSessionHistoryFilter.VIDEO,
                query = "",
            ).map(WebSessionHistoryEntry::url),
        )
        assertEquals(
            listOf("https://cdn.example.com/video.mp4"),
            filterWebSessionHistoryEntries(
                entries = entries,
                filter = WebSessionHistoryFilter.ALL,
                query = "watch/1",
            ).map(WebSessionHistoryEntry::url),
        )
    }

    @Test
    fun `delete predicate scopes category and cutoff independently`() {
        val web = entry("https://example.com", "Web", 900L, WebSessionHistoryCategory.WEB)
        val video =
            entry(
                "https://cdn.example.com/video.mp4",
                "Video",
                1_100L,
                WebSessionHistoryCategory.VIDEO,
            )

        assertFalse(
            shouldDeleteWebSessionHistoryEntry(
                entry = web,
                category = WebSessionHistoryCategory.VIDEO,
                cutoffTimeMillis = 1_000L,
            )
        )
        assertTrue(
            shouldDeleteWebSessionHistoryEntry(
                entry = video,
                category = WebSessionHistoryCategory.VIDEO,
                cutoffTimeMillis = 1_000L,
            )
        )
        assertTrue(
            shouldDeleteWebSessionHistoryEntry(
                entry = web,
                category = null,
                cutoffTimeMillis = null,
            )
        )
    }

    @Test
    fun `delete ranges and media origins preserve requested semantics`() {
        val now = 10L * 24L * 60L * 60L * 1_000L

        assertEquals(
            now - 60L * 60L * 1_000L,
            WebSessionHistoryDeleteRange.PAST_HOUR.cutoffTimeMillis(now),
        )
        assertEquals(
            now - 7L * 24L * 60L * 60L * 1_000L,
            WebSessionHistoryDeleteRange.PAST_WEEK.cutoffTimeMillis(now),
        )
        assertEquals(null, WebSessionHistoryDeleteRange.ALL_TIME.cutoffTimeMillis(now))
        assertEquals(
            WebSessionHistoryMediaOrigin.LOCAL,
            resolveWebSessionHistoryMediaOrigin("content://media/external/video/1"),
        )
        assertEquals(
            WebSessionHistoryMediaOrigin.LOCAL,
            resolveWebSessionHistoryMediaOrigin("file:///storage/emulated/0/movie.mp4"),
        )
        assertEquals(
            WebSessionHistoryMediaOrigin.ONLINE,
            resolveWebSessionHistoryMediaOrigin("https://cdn.example.com/movie.mp4"),
        )
    }

    private fun entry(
        url: String,
        title: String,
        visitedAt: Long,
        category: WebSessionHistoryCategory,
        mediaOrigin: WebSessionHistoryMediaOrigin? = null,
        sourcePageUrl: String = "",
    ): WebSessionHistoryEntry =
        WebSessionHistoryEntry(
            url = url,
            title = title,
            visitedAt = visitedAt,
            category = category,
            mediaOrigin = mediaOrigin,
            sourcePageUrl = sourcePageUrl,
        )
}
