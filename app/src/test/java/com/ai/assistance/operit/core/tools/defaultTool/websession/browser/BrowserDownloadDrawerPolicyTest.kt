package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDownloadDrawerPolicyTest {
    @Test
    fun `drawer tabs keep completed separate from all actionable non-completed states`() {
        val items =
            listOf(
                item("completed", "completed"),
                item("queued", "queued"),
                item("downloading", "downloading"),
                item("paused", "paused"),
                item("failed", "failed"),
                item("canceled", "canceled"),
            )

        assertEquals(
            listOf("completed"),
            filterBrowserDownloadDrawerItems(items, BrowserDownloadDrawerTab.DOWNLOADED)
                .map(BrowserDownloadItem::id),
        )
        assertEquals(
            listOf("queued", "downloading", "paused", "failed", "canceled"),
            filterBrowserDownloadDrawerItems(items, BrowserDownloadDrawerTab.DOWNLOADING)
                .map(BrowserDownloadItem::id),
        )
    }

    @Test
    fun `downloading status filters and search query compose without leaking other tabs`() {
        val items =
            listOf(
                item("completed", "completed", fileName = "archive.zip"),
                item("queued", "queued", fileName = "Episode-01.mp4"),
                item("connecting", "connecting", fileName = "episode-02.mp4"),
                item("active", "downloading", sourceUrl = "https://cdn.example.com/Season-2.bin"),
                item("paused", "paused", mimeType = "video/MP2T"),
                item("failed", "failed", errorMessage = "HTTP 503 Service Unavailable"),
                item("canceled", "canceled"),
            )

        assertEquals(
            listOf("connecting", "active"),
            filterBrowserDownloadDrawerItems(
                items = items,
                tab = BrowserDownloadDrawerTab.DOWNLOADING,
                statusFilter = BrowserDownloadStatusFilter.ACTIVE,
            ).map(BrowserDownloadItem::id),
        )
        assertEquals(
            listOf("queued", "connecting"),
            filterBrowserDownloadDrawerItems(
                items = items,
                tab = BrowserDownloadDrawerTab.DOWNLOADING,
                query = "EPISODE",
            ).map(BrowserDownloadItem::id),
        )
        assertEquals(
            listOf("active"),
            filterBrowserDownloadDrawerItems(
                items = items,
                tab = BrowserDownloadDrawerTab.DOWNLOADING,
                query = "season-2",
            ).map(BrowserDownloadItem::id),
        )
        assertEquals(
            listOf("paused"),
            filterBrowserDownloadDrawerItems(
                items = items,
                tab = BrowserDownloadDrawerTab.DOWNLOADING,
                query = "mp2t",
            ).map(BrowserDownloadItem::id),
        )
        assertEquals(
            listOf("failed"),
            filterBrowserDownloadDrawerItems(
                items = items,
                tab = BrowserDownloadDrawerTab.DOWNLOADING,
                statusFilter = BrowserDownloadStatusFilter.FAILED,
                query = "service unavailable",
            ).map(BrowserDownloadItem::id),
        )
        assertTrue(
            filterBrowserDownloadDrawerItems(
                items = items,
                tab = BrowserDownloadDrawerTab.DOWNLOADED,
                query = "503",
            ).isEmpty(),
        )
    }

    @Test
    fun `drawer sort modes use completion time and stable file names`() {
        val items =
            listOf(
                item("b", "completed", fileName = "Beta.mp4", createdAt = 10L, completedAt = 30L),
                item("a", "completed", fileName = "alpha.mp4", createdAt = 20L, completedAt = 40L),
                item("c", "completed", fileName = "charlie.mp4", createdAt = 5L, completedAt = 20L),
            )

        assertEquals(
            listOf("a", "b", "c"),
            sortBrowserDownloadDrawerItems(items, BrowserDownloadSortMode.NEWEST)
                .map(BrowserDownloadItem::id),
        )
        assertEquals(
            listOf("c", "b", "a"),
            sortBrowserDownloadDrawerItems(items, BrowserDownloadSortMode.OLDEST)
                .map(BrowserDownloadItem::id),
        )
        assertEquals(
            listOf("a", "b", "c"),
            sortBrowserDownloadDrawerItems(items, BrowserDownloadSortMode.NAME)
                .map(BrowserDownloadItem::id),
        )
    }

    @Test
    fun `classification uses mime type and extension across all visible groups`() {
        val items =
            listOf(
                item("video", "completed", fileName = "movie.bin", mimeType = "video/mp4"),
                item("audio", "completed", fileName = "track.flac"),
                item("image", "completed", fileName = "cover.webp"),
                item("app", "completed", fileName = "client.apk"),
                item("archive", "completed", fileName = "bundle.7z"),
                item("document", "completed", fileName = "notes.pdf"),
                item("other", "completed", fileName = "payload.bin"),
            )

        assertEquals(
            BrowserDownloadCategory.entries,
            buildBrowserDownloadSections(items, classify = true).mapNotNull(BrowserDownloadSection::category),
        )
    }

    @Test
    fun `manual file name combines explicit name url name and suffix once`() {
        assertEquals(
            "episode.mp4",
            resolveManualBrowserDownloadFileName(
                requestedFileName = "episode",
                url = "https://example.com/source.m3u8",
                requestedSuffix = ".mp4",
            ),
        )
        assertEquals(
            "source.m3u8",
            resolveManualBrowserDownloadFileName(
                requestedFileName = "",
                url = "https://example.com/media/source.m3u8?token=1",
                requestedSuffix = "",
            ),
        )
        assertEquals(
            "episode.MP4",
            resolveManualBrowserDownloadFileName(
                requestedFileName = "episode.MP4",
                url = "https://example.com/source",
                requestedSuffix = "mp4",
            ),
        )
        assertEquals("m3u8", extractBrowserDownloadSuffix("", "https://example.com/live/index.m3u8?a=1"))
    }

    @Test
    fun `selection toggle and mp4 target naming are deterministic`() {
        assertEquals(setOf("one"), toggleBrowserDownloadSelection(emptySet(), "one"))
        assertEquals(emptySet<String>(), toggleBrowserDownloadSelection(setOf("one"), "one"))
        assertEquals("episode.mp4", resolveBrowserDownloadMp4FileName("episode.m3u8"))
    }

    @Test
    fun `batch cancel selects only cancelable tasks while batch delete keeps record targets`() {
        val active = item(id = "active", status = "downloading", canCancel = true)
        val failed = item(id = "failed", status = "failed", canRetry = true)

        assertTrue(
            browserDownloadBatchSelectionEligible(active, BrowserDownloadBatchAction.CANCEL),
        )
        assertFalse(
            browserDownloadBatchSelectionEligible(failed, BrowserDownloadBatchAction.CANCEL),
        )
        assertTrue(
            browserDownloadBatchSelectionEligible(failed, BrowserDownloadBatchAction.DELETE),
        )
        assertEquals(
            setOf("active"),
            browserDownloadBatchEligibleTaskIds(
                items = listOf(active, failed),
                action = BrowserDownloadBatchAction.CANCEL,
            ),
        )
        assertEquals(
            setOf("active", "failed"),
            browserDownloadBatchEligibleTaskIds(
                items = listOf(active, failed),
                action = BrowserDownloadBatchAction.DELETE,
            ),
        )
    }

    @Test
    fun `select all toggles only visible eligible ids and preserves hidden selections`() {
        assertEquals(
            setOf("hidden", "one", "two"),
            toggleAllBrowserDownloadSelections(
                selectedTaskIds = setOf("hidden"),
                eligibleTaskIds = setOf("one", "two"),
            ),
        )
        assertEquals(
            setOf("hidden"),
            toggleAllBrowserDownloadSelections(
                selectedTaskIds = setOf("hidden", "one", "two"),
                eligibleTaskIds = setOf("one", "two"),
            ),
        )
        assertEquals(
            setOf("hidden"),
            toggleAllBrowserDownloadSelections(
                selectedTaskIds = setOf("hidden"),
                eligibleTaskIds = emptySet(),
            ),
        )
    }

    @Test
    fun `completed ordinary files and m3u8 packages expose distinct action matrices`() {
        val ordinary =
            item(
                id = "ordinary",
                status = "completed",
                canRedownload = true,
            )
        val m3u8Package =
            item(
                id = "m3u8",
                status = "completed",
                fileName = "video.m3u8",
                isM3u8Package = true,
                canRedownload = true,
                canMergeToMp4 = true,
            )

        assertEquals(
            listOf(
                BrowserDownloadDrawerActionType.DELETE,
                BrowserDownloadDrawerActionType.BATCH_DELETE,
                BrowserDownloadDrawerActionType.REDOWNLOAD,
                BrowserDownloadDrawerActionType.RENAME,
                BrowserDownloadDrawerActionType.CHANGE_SUFFIX,
                BrowserDownloadDrawerActionType.MOVE_FOLDER,
                BrowserDownloadDrawerActionType.COPY_URL,
                BrowserDownloadDrawerActionType.SHARE_FILE,
                BrowserDownloadDrawerActionType.COPY_LOCATION,
                BrowserDownloadDrawerActionType.TRANSFER_TO_PUBLIC,
            ),
            browserDownloadDrawerActions(ordinary),
        )
        assertEquals(
            listOf(
                BrowserDownloadDrawerActionType.DELETE,
                BrowserDownloadDrawerActionType.BATCH_DELETE,
                BrowserDownloadDrawerActionType.REDOWNLOAD,
                BrowserDownloadDrawerActionType.RENAME,
                BrowserDownloadDrawerActionType.COPY_URL,
                BrowserDownloadDrawerActionType.MERGE_TO_MP4,
            ),
            browserDownloadDrawerActions(m3u8Package),
        )
    }

    @Test
    fun `active paused failed and canceled tasks expose status specific actions`() {
        assertEquals(
            listOf(
                BrowserDownloadDrawerActionType.PAUSE,
                BrowserDownloadDrawerActionType.CANCEL,
                BrowserDownloadDrawerActionType.BATCH_CANCEL,
            ),
            browserDownloadDrawerActions(
                item(
                    id = "active",
                    status = "downloading",
                    canPause = true,
                    canCancel = true,
                ),
            ),
        )
        assertEquals(
            listOf(
                BrowserDownloadDrawerActionType.RESUME,
                BrowserDownloadDrawerActionType.CANCEL,
                BrowserDownloadDrawerActionType.BATCH_CANCEL,
            ),
            browserDownloadDrawerActions(
                item(
                    id = "paused",
                    status = "paused",
                    canResume = true,
                    canCancel = true,
                ),
            ),
        )
        listOf("failed", "canceled").forEach { status ->
            assertEquals(
                listOf(
                    BrowserDownloadDrawerActionType.RETRY,
                    BrowserDownloadDrawerActionType.DELETE,
                    BrowserDownloadDrawerActionType.BATCH_DELETE,
                ),
                browserDownloadDrawerActions(
                    item(
                        id = status,
                        status = status,
                        canRetry = true,
                    ),
                ),
            )
        }
    }

    private fun item(
        id: String,
        status: String,
        fileName: String = "$id.bin",
        sourceUrl: String = "https://example.com/$fileName",
        mimeType: String? = null,
        errorMessage: String? = null,
        createdAt: Long = 1L,
        completedAt: Long? = null,
        isM3u8Package: Boolean = false,
        canPause: Boolean = false,
        canResume: Boolean = false,
        canCancel: Boolean = false,
        canRetry: Boolean = false,
        canRedownload: Boolean = status == "completed",
        canMergeToMp4: Boolean = false,
    ): BrowserDownloadItem =
        BrowserDownloadItem(
            id = id,
            fileName = fileName,
            sourceUrl = sourceUrl,
            mimeType = mimeType,
            status = status,
            type = "http",
            progress = null,
            downloadedBytes = 0L,
            totalBytes = -1L,
            speedBytesPerSecond = 0L,
            destinationPath = "D:/$fileName",
            createdAt = createdAt,
            completedAt = completedAt,
            isM3u8Package = isM3u8Package,
            errorMessage = errorMessage,
            canPause = canPause,
            canResume = canResume,
            canCancel = canCancel,
            canRetry = canRetry,
            canDelete = true,
            canDeleteFile = true,
            canOpenFile = status == "completed",
            canOpenLocation = status == "completed",
            canRedownload = canRedownload,
            canMergeToMp4 = canMergeToMp4,
        )
}
