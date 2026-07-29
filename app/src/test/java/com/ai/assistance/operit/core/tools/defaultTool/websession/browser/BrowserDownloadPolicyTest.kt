package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.io.IOException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDownloadPolicyTest {
    @Test
    fun `queue selection is FIFO and excludes active tasks`() {
        val entries =
            listOf(
                queueEntry("older", queuedAt = 10L, createdAt = 1L),
                queueEntry("active", queuedAt = 20L, createdAt = 2L),
                queueEntry("newer", queuedAt = 30L, createdAt = 3L),
            )

        assertEquals(
            listOf("older", "newer"),
            selectQueuedBrowserDownloadTaskIds(
                entries = entries,
                activeTaskIds = setOf("active"),
                availableSlots = 2,
            ),
        )
    }

    @Test
    fun `queue selection uses latest queue time for resumed work`() {
        val entries =
            listOf(
                queueEntry("resumed", queuedAt = 30L, createdAt = 1L),
                queueEntry("waiting", queuedAt = 20L, createdAt = 2L),
                queueEntry("paused", queuedAt = 10L, createdAt = 3L, status = BrowserDownloadStatus.PAUSED),
            )

        assertEquals(
            listOf("waiting", "resumed"),
            selectQueuedBrowserDownloadTaskIds(
                entries = entries,
                activeTaskIds = emptySet(),
                availableSlots = 4,
            ),
        )
    }

    @Test
    fun `queue selection returns no work when capacity is exhausted`() {
        assertTrue(
            selectQueuedBrowserDownloadTaskIds(
                entries = listOf(queueEntry("queued", queuedAt = 1L, createdAt = 1L)),
                activeTaskIds = emptySet(),
                availableSlots = 0,
            ).isEmpty(),
        )
    }

    @Test
    fun `segment thread count honors range support and one MiB minimum`() {
        assertEquals(1, resolveBrowserDownloadSegmentThreadCount(-1L, false, 6))
        assertEquals(1, resolveBrowserDownloadSegmentThreadCount(64L * 1024L * 1024L, false, 6))
        assertEquals(1, resolveBrowserDownloadSegmentThreadCount(2L * MIN_BROWSER_DOWNLOAD_SEGMENT_BYTES - 1L, true, 6))
        assertEquals(2, resolveBrowserDownloadSegmentThreadCount(2L * MIN_BROWSER_DOWNLOAD_SEGMENT_BYTES, true, 6))
        assertEquals(6, resolveBrowserDownloadSegmentThreadCount(7L * MIN_BROWSER_DOWNLOAD_SEGMENT_BYTES, true, 6))
        assertEquals(12, resolveBrowserDownloadSegmentThreadCount(12L * MIN_BROWSER_DOWNLOAD_SEGMENT_BYTES, true, 12))
        assertEquals(3, resolveBrowserDownloadSegmentThreadCount(64L * MIN_BROWSER_DOWNLOAD_SEGMENT_BYTES, true, 3))
    }

    @Test
    fun `download setting value sets are strict`() {
        assertTrue(isSupportedBrowserDownloadConcurrency(1))
        assertTrue(isSupportedBrowserDownloadConcurrency(8))
        assertFalse(isSupportedBrowserDownloadConcurrency(0))
        assertFalse(isSupportedBrowserDownloadConcurrency(9))
        assertEquals((1..8).toList(), BROWSER_DOWNLOAD_MAX_CONCURRENT_TASK_OPTIONS)
        assertEquals(listOf(3, 6, 12, 20, 32), BROWSER_DOWNLOAD_SEGMENT_THREAD_OPTIONS)
        assertEquals(listOf(3, 8, 16, 20, 32, 48, 64), BROWSER_DOWNLOAD_M3U8_THREAD_OPTIONS)
        assertEquals(
            listOf(12288, 8192, 4096, 2048, 1024, 512, 256),
            BROWSER_DOWNLOAD_CHUNK_SIZE_KB_OPTIONS,
        )
        assertTrue(isSupportedBrowserDownloadSegmentThreadCount(3))
        assertFalse(isSupportedBrowserDownloadSegmentThreadCount(4))
        assertFalse(isSupportedBrowserDownloadM3u8ThreadCount(4))
        assertFalse(isSupportedBrowserDownloadChunkSizeKb(128))
    }

    @Test
    fun `download settings defaults and dynamic task limit match the refined contract`() {
        val settings = BrowserDownloadSettings()

        assertEquals(BROWSER_DOWNLOAD_SETTINGS_VERSION, settings.version)
        assertEquals(DEFAULT_BROWSER_DOWNLOAD_SEGMENT_THREAD_COUNT, settings.segmentThreadCount)
        assertEquals(DEFAULT_BROWSER_DOWNLOAD_M3U8_THREAD_COUNT, settings.m3u8ThreadCount)
        assertTrue(settings.packageM3u8Offline)
        assertFalse(settings.autoTransferToPublicDirectory)
        assertEquals(DEFAULT_BROWSER_DOWNLOAD_CHUNK_SIZE_KB, settings.chunkSizeKb)
        assertFalse(settings.autoCleanApk)
        assertTrue(settings.enableHttp2)
        assertEquals(4, MAX_BROWSER_M3U8_VARIANT_DEPTH)
        assertEquals(8, resolveBrowserDownloadMaxConcurrentTasksLimit(6, 16))
        assertEquals(6, resolveBrowserDownloadMaxConcurrentTasksLimit(20, 20))
        assertEquals(2, resolveBrowserDownloadMaxConcurrentTasksLimit(32, 64))
    }

    @Test
    fun `directory permission is released only after settings and tasks stop owning it`() {
        val directoryUri = "content://downloads/tree/primary%3AKiyori"

        assertFalse(
            shouldReleaseBrowserDownloadDirectoryPermission(
                candidateUri = directoryUri,
                settingsDirectoryUris = listOf(directoryUri),
                taskDirectoryUris = emptyList(),
            ),
        )
        assertFalse(
            shouldReleaseBrowserDownloadDirectoryPermission(
                candidateUri = directoryUri,
                settingsDirectoryUris = listOf("", directoryUri),
                taskDirectoryUris = emptyList(),
            ),
        )
        assertFalse(
            shouldReleaseBrowserDownloadDirectoryPermission(
                candidateUri = directoryUri,
                settingsDirectoryUris = emptyList(),
                taskDirectoryUris = listOf(directoryUri),
            ),
        )
        assertTrue(
            shouldReleaseBrowserDownloadDirectoryPermission(
                candidateUri = directoryUri,
                settingsDirectoryUris = emptyList(),
                taskDirectoryUris = emptyList(),
            ),
        )
    }

    @Test
    fun `SAF source deletion requires a positive deleted row count`() {
        assertFalse(browserDownloadContentDeleteSucceeded(-1))
        assertFalse(browserDownloadContentDeleteSucceeded(0))
        assertTrue(browserDownloadContentDeleteSucceeded(1))
    }

    @Test
    fun `transport task concurrency respects frozen thread limits`() {
        assertEquals(8, resolveBrowserDownloadTransportMaxConcurrentTasks(8, 6, 16))
        assertEquals(2, resolveBrowserDownloadTransportMaxConcurrentTasks(8, 32, 64))
        assertEquals(1, resolveBrowserDownloadTransportMaxConcurrentTasks(1, 3, 3))
    }

    @Test
    fun `download request policy defaults are explicit and stable`() {
        val settings = BrowserDownloadSettings()

        assertEquals(BROWSER_DOWNLOAD_SETTINGS_VERSION, settings.version)
        assertEquals(2, settings.version)
        assertEquals(BrowserDownloadEngine.INTERNAL, settings.defaultEngine)
        assertEquals("", settings.customDirectoryUri)
        assertEquals("", settings.customDirectoryName)
        assertEquals(BrowserDownloadNetworkPolicy.ANY, settings.networkPolicy)
        assertFalse(settings.allowRoaming)
        assertFalse(settings.skipConfirmation)
        assertTrue(settings.showResultNotifications)
        assertEquals(BrowserDownloadEngine.INTERNAL, BrowserDownloadEngine.fromPersistedId("internal"))
        assertEquals(BrowserDownloadEngine.SYSTEM, BrowserDownloadEngine.fromPersistedId("system"))
        assertEquals(
            BrowserDownloadNetworkPolicy.ANY,
            BrowserDownloadNetworkPolicy.fromPersistedId("any"),
        )
        assertEquals(
            BrowserDownloadNetworkPolicy.UNMETERED,
            BrowserDownloadNetworkPolicy.fromPersistedId("unmetered"),
        )
        assertTrue(isBrowserDownloadNetworkUrl("https://example.com/video.mp4"))
        assertTrue(isBrowserDownloadNetworkUrl("HTTP://example.com/file.bin"))
        assertFalse(isBrowserDownloadNetworkUrl("blob:https://example.com/id"))
        assertFalse(isBrowserDownloadNetworkUrl("data:text/plain;base64,QQ=="))
        assertFalse(isBrowserDownloadNetworkUrl("file:///sdcard/file.bin"))
        assertEquals(
            "https://cdn.example.com:8443/path/app.apk",
            browserDownloadDisplayUrl(
                "https://user:secret@cdn.example.com:8443/path/app.apk" +
                    "?token=signed-value#fragment",
            ),
        )
        assertEquals(
            "[下载地址已隐藏]",
            browserDownloadDisplayUrl("data:text/plain;base64,QQ=="),
        )
    }

    @Test
    fun `public transfer and apk cleanup require exact installed package identity`() {
        assertFalse(
            shouldAutoTransferBrowserDownload(
                autoTransferEnabled = false,
                hasCustomDirectory = false,
                isM3u8Package = false,
            ),
        )
        assertTrue(
            shouldAutoTransferBrowserDownload(
                autoTransferEnabled = true,
                hasCustomDirectory = false,
                isM3u8Package = false,
            ),
        )
        assertFalse(
            shouldAutoTransferBrowserDownload(
                autoTransferEnabled = true,
                hasCustomDirectory = false,
                isM3u8Package = true,
            ),
        )
        runCatching {
            shouldAutoTransferBrowserDownload(
                autoTransferEnabled = true,
                hasCustomDirectory = true,
                isM3u8Package = false,
            )
        }.onSuccess {
            throw AssertionError("Mutually exclusive download destinations were accepted")
        }
        assertTrue(
            isBrowserDownloadApkPackage(
                mimeType = "application/vnd.android.package-archive",
                fileName = "download.bin",
            ),
        )
        assertTrue(isBrowserDownloadApkPackage(mimeType = "application/octet-stream", fileName = "App.APK"))
        assertFalse(isBrowserDownloadApkPackage(mimeType = "application/zip", fileName = "app.zip"))
        assertTrue(
            matchesBrowserDownloadInstalledPackage(
                pendingPackageName = "com.example.app",
                pendingVersionCode = 42L,
                installedPackageName = "com.example.app",
                installedVersionCode = 42L,
            ),
        )
        assertFalse(
            matchesBrowserDownloadInstalledPackage(
                pendingPackageName = "com.example.app",
                pendingVersionCode = 42L,
                installedPackageName = "com.example.other",
                installedVersionCode = 42L,
            ),
        )
        assertFalse(
            matchesBrowserDownloadInstalledPackage(
                pendingPackageName = "com.example.app",
                pendingVersionCode = 42L,
                installedPackageName = "com.example.app",
                installedVersionCode = 43L,
            ),
        )
        assertTrue(
            shouldReconcileBrowserDownloadInstalledPackage(
                pendingPackageName = "com.example.app",
                pendingVersionCode = 42L,
                previouslyInstalledVersionCode = 41L,
                installedPackageName = "com.example.app",
                installedVersionCode = 42L,
            ),
        )
        assertFalse(
            shouldReconcileBrowserDownloadInstalledPackage(
                pendingPackageName = "com.example.app",
                pendingVersionCode = 42L,
                previouslyInstalledVersionCode = 42L,
                installedPackageName = "com.example.app",
                installedVersionCode = 42L,
            ),
        )
    }

    @Test
    fun `M3U8 rewrite deduplicates byte range resources and removes live-only hints`() {
        val rewrite =
            rewriteBrowserM3u8MediaPlaylist(
                content =
                    """
                    #EXTM3U
                    #EXT-X-BYTERANGE:4@0
                    media.bin
                    #EXT-X-BYTERANGE:4
                    media.bin
                    #EXT-X-PRELOAD-HINT:TYPE=PART,URI="future.m4s"
                    #EXT-X-RENDITION-REPORT:URI="other.m3u8"
                    """.trimIndent(),
                baseUrl = "https://media.example.com/path/playlist.m3u8",
                packageDirectoryUri = "file:/tmp/video.m3u8.files/",
            )

        assertEquals(1, rewrite.resources.size)
        val localUri = rewrite.resources.single().localUri
        assertEquals(2, rewrite.playlistContent.lineSequence().count { line -> line == localUri })
        assertFalse(rewrite.playlistContent.contains("PRELOAD-HINT"))
        assertFalse(rewrite.playlistContent.contains("RENDITION-REPORT"))
        assertTrue(isValidBrowserM3u8Playlist("\uFEFF#EXTM3U\n#EXTINF:1,\na.ts"))
        assertFalse(isValidBrowserM3u8Playlist("<html>not a playlist</html>"))
    }

    @Test
    fun `M3U8 rewrite rejects non-network resources`() {
        assertThrows(IllegalArgumentException::class.java) {
            rewriteBrowserM3u8MediaPlaylist(
                content = "#EXTM3U\n#EXTINF:1,\nfile:///sdcard/private.ts",
                baseUrl = "https://media.example.com/playlist.m3u8",
                packageDirectoryUri = "file:/tmp/video.m3u8.files/",
            )
        }
    }

    @Test
    fun `download rename targets preserve legacy name and suffix rules`() {
        assertEquals(
            "renamed.mp4",
            buildBrowserDownloadRenameTarget(
                fileName = "video.mp4",
                mode = BrowserDownloadRenameMode.RENAME,
                rawInput = "renamed",
            ),
        )
        assertEquals(
            "renamed.mkv",
            buildBrowserDownloadRenameTarget(
                fileName = "video.mp4",
                mode = BrowserDownloadRenameMode.RENAME,
                rawInput = "renamed.mkv",
            ),
        )
        assertEquals(
            "video.mkv",
            buildBrowserDownloadRenameTarget(
                fileName = "video.mp4",
                mode = BrowserDownloadRenameMode.SUFFIX,
                rawInput = ".mkv",
            ),
        )
        assertEquals("video", browserDownloadRenameInput("video.mp4", BrowserDownloadRenameMode.RENAME))
        assertEquals("mp4", browserDownloadRenameInput("video.mp4", BrowserDownloadRenameMode.SUFFIX))
        assertEquals("", buildBrowserDownloadRenameTarget("video.mp4", BrowserDownloadRenameMode.RENAME, " "))
    }

    @Test
    fun `saved location prefers SAF URI over local staging path`() {
        assertEquals(
            "content://downloads/video.mp4",
            resolveBrowserDownloadSavedLocation(
                destinationPath = "D:/Download/Kiyori/browser/downloads/video.mp4",
                destinationUri = "content://downloads/video.mp4",
            ),
        )
        assertEquals(
            "D:/Download/Kiyori/browser/downloads/video.mp4",
            resolveBrowserDownloadSavedLocation(
                destinationPath = "D:/Download/Kiyori/browser/downloads/video.mp4",
                destinationUri = null,
            ),
        )
    }

    @Test
    fun `pending download projection keeps the frozen request metadata`() {
        val request =
            PendingBrowserDownloadRequest(
                requestId = "request-1",
                sessionId = "session-1",
                url = "https://example.com/video.mp4",
                fileName = "video.mp4",
                mimeType = "video/mp4",
                contentLength = 4096L,
                headers = mapOf("Referer" to "https://example.com/watch"),
                engine = BrowserDownloadEngine.SYSTEM,
            )

        assertEquals(
            BrowserDownloadPromptState(
                requestId = "request-1",
                fileName = "video.mp4",
                mimeType = "video/mp4",
                contentLength = 4096L,
                engine = BrowserDownloadEngine.SYSTEM,
                destinationName = null,
            ),
            request.toUiState(),
        )
    }

    @Test
    fun `download confirmations are FIFO and bounded without replacing the visible request`() {
        val queue = BrowserDownloadConfirmationQueue(capacity = 2)
        val first = pendingRequest("request-1")
        val second = pendingRequest("request-2")
        val rejected = pendingRequest("request-3")

        assertTrue(queue.enqueue(first))
        assertTrue(queue.enqueue(second))
        assertFalse(queue.enqueue(rejected))
        assertEquals(2, queue.size())
        assertEquals(first, queue.peek())
        assertEquals(null, queue.removeHead(second.requestId))
        assertEquals(first, queue.removeHead(first.requestId))
        assertEquals(second, queue.peek())
        assertEquals(second, queue.removeHead(second.requestId))
        assertEquals(0, queue.size())
    }

    @Test
    fun `download file names are sanitized length bounded and avoid reserved queue names`() {
        assertEquals(
            "report_2026_.pdf",
            normalizeBrowserDownloadFileName(" report/2026?.pdf. "),
        )
        assertEquals("download", normalizeBrowserDownloadFileName(" ... "))
        assertTrue(
            normalizeBrowserDownloadFileName("a".repeat(240) + ".mp4").length <= 180,
        )

        val unavailable = setOf("video.mp4", "video (1).mp4")
        assertEquals(
            "video (2).mp4",
            resolveAvailableBrowserDownloadFileName("video.mp4") { candidate ->
                candidate in unavailable
            },
        )
    }

    @Test
    fun `download error projection removes URLs and frozen credentials`() {
        val url = "https://cdn.example.com/file.bin?token=secret"
        val authorization = "Bearer private-token"

        val message =
            browserDownloadSafeErrorMessage(
                error = IOException("Request $url failed with $authorization"),
                sensitiveValues = listOf(url, authorization),
            )

        assertFalse(message.contains("secret"))
        assertFalse(message.contains("private-token"))
        assertFalse(message.contains("https://"))
        assertTrue(message.contains("[已隐藏]"))
    }

    @Test
    fun `download task credentials are excluded from backup and device transfer`() {
        val backupRules = repositoryFile("app/src/main/res/xml/backup_rules.xml").readText()
        val extractionRules =
            repositoryFile("app/src/main/res/xml/data_extraction_rules.xml").readText()
        val exclusion = """<exclude domain="file" path="browser_download_tasks.json"/>"""

        assertTrue(backupRules.contains(exclusion))
        assertEquals(2, extractionRules.windowed(exclusion.length).count { value -> value == exclusion })
    }

    @Test
    fun `player document-tree download projection freezes destination and internal engine`() {
        val request =
            PendingBrowserDownloadRequest(
                requestId = "request-player-1",
                sessionId = "session-1",
                url = "https://example.com/video.mp4",
                fileName = "video.mp4",
                mimeType = "video/mp4",
                contentLength = -1L,
                headers = emptyMap(),
                engine = BrowserDownloadEngine.INTERNAL,
                destination =
                    BrowserDownloadDestination.DocumentTree(
                        treeUri = "content://downloads/tree/primary%3APlayer",
                        displayName = "播放器视频",
                    ),
            )

        assertEquals("播放器视频", request.toUiState().destinationName)
        assertEquals(BrowserDownloadEngine.INTERNAL, request.toUiState().engine)
    }

    @Test
    fun `complete segment plan must be contiguous and cover the file`() {
        val valid =
            listOf(
                segment(index = 0, start = 0L, end = 9L),
                segment(index = 1, start = 10L, end = 19L),
            )
        val gap =
            listOf(
                segment(index = 0, start = 0L, end = 8L),
                segment(index = 1, start = 10L, end = 19L),
            )

        assertTrue(isCompleteBrowserDownloadSegmentPlan(valid, totalBytes = 20L))
        assertFalse(isCompleteBrowserDownloadSegmentPlan(gap, totalBytes = 20L))
        assertFalse(isCompleteBrowserDownloadSegmentPlan(valid, totalBytes = 21L))
    }

    @Test
    fun `chunk range plan is contiguous and preserves one MiB lower bound`() {
        val totalBytes = 3L * MIN_BROWSER_DOWNLOAD_SEGMENT_BYTES + 123L
        val ranges = buildBrowserDownloadRangePlan(totalBytes, chunkSizeKb = 256)

        assertEquals(4, ranges.size)
        assertEquals(0L, ranges.first().startInclusive)
        assertEquals(totalBytes - 1L, ranges.last().endInclusive)
        assertTrue(isCompleteBrowserDownloadRangePlan(ranges, totalBytes))
        assertTrue(
            ranges.dropLast(1).all { range ->
                range.endInclusive - range.startInclusive + 1L >=
                    MIN_BROWSER_DOWNLOAD_SEGMENT_BYTES
            },
        )
        assertEquals(MIN_BROWSER_DOWNLOAD_SEGMENT_BYTES, ranges.first().endInclusive + 1L)
    }

    @Test
    fun `m3u8 detection chooses highest bandwidth variant and resolves relative URLs`() {
        assertTrue(
            isBrowserDownloadM3u8Resource(
                url = "https://cdn.example.com/master.m3u8?token=1",
                fileName = "download.bin",
                mimeType = "application/vnd.apple.mpegurl; charset=utf-8",
            ),
        )
        assertTrue(
            isBrowserDownloadM3u8Resource(
                url = "https://cdn.example.com/download",
                fileName = "playlist.M3U8",
                mimeType = "application/octet-stream",
            ),
        )
        assertFalse(
            isBrowserDownloadM3u8Resource(
                url = "https://cdn.example.com/video.mp4",
                fileName = "video.mp4",
                mimeType = "video/mp4",
            ),
        )

        val selected =
            requireNotNull(
                selectHighestBandwidthBrowserM3u8Variant(
                    content =
                        """
                        #EXTM3U
                        #EXT-X-STREAM-INF:BANDWIDTH=120000
                        low/index.m3u8
                        #EXT-X-STREAM-INF:BANDWIDTH=900000,AUDIO="aac-main",SUBTITLES="subs"
                        ../high/index.m3u8
                        """.trimIndent(),
                    baseUrl = "https://cdn.example.com/video/master.m3u8",
                ),
            )
        assertEquals(900000L, selected.bandwidth)
        assertEquals("https://cdn.example.com/high/index.m3u8", selected.url)
        assertEquals("aac-main", selected.audioGroupId)
        assertEquals("subs", selected.subtitlesGroupId)
        assertEquals(
            listOf(
                "https://cdn.example.com/video/audio/main.m3u8",
                "https://cdn.example.com/video/subtitles/zh.m3u8",
            ),
            browserM3u8ExternalRenditionUrls(
                content =
                    """
                    #EXTM3U
                    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac-main",NAME="Main",URI="audio/main.m3u8"
                    #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="中文",URI="subtitles/zh.m3u8"
                    """.trimIndent(),
                baseUrl = "https://cdn.example.com/video/master.m3u8",
                variant = selected,
            ),
        )
    }

    @Test
    fun `m3u8 rewrite preserves comments and rewrites key map and media resources`() {
        val result =
            rewriteBrowserM3u8MediaPlaylist(
                content =
                    """
                    #EXTM3U
                    #EXT-X-KEY:METHOD=AES-128,URI="keys/key.bin"
                    #EXT-X-MAP:URI="init.mp4"
                    #EXTINF:4,
                    segments/one.ts?token=1
                    """.trimIndent(),
                baseUrl = "https://cdn.example.com/video/index.m3u8",
                packageDirectoryUri = "file:///offline/video.m3u8.files",
            )

        assertEquals(
            listOf(
                "https://cdn.example.com/video/keys/key.bin" to "resource_0.bin",
                "https://cdn.example.com/video/init.mp4" to "resource_1.mp4",
                "https://cdn.example.com/video/segments/one.ts?token=1" to "segment_0.ts",
            ),
            result.resources.map { resource -> resource.sourceUrl to resource.localFileName },
        )
        assertTrue(result.playlistContent.contains("URI=\"file:/offline/video.m3u8.files/resource_0.bin\""))
        assertTrue(result.playlistContent.contains("URI=\"file:/offline/video.m3u8.files/resource_1.mp4\""))
        assertTrue(result.playlistContent.contains("file:/offline/video.m3u8.files/segment_0.ts"))
        assertTrue(result.playlistContent.contains("#EXTINF:4,"))
    }

    @Test
    fun `m3u8 offline package naming follows the legacy companion directory contract`() {
        assertEquals("video.m3u8.files", browserM3u8PackageDirectoryName("video.m3u8"))
        assertEquals(".jpg", browserM3u8ResourceExtension("cover.jpeg?token=1"))
        assertEquals(".bin", browserM3u8ResourceExtension("https://cdn.example.com/key"))
    }

    private fun queueEntry(
        taskId: String,
        queuedAt: Long,
        createdAt: Long,
        status: BrowserDownloadStatus = BrowserDownloadStatus.QUEUED,
    ): BrowserDownloadQueueEntry =
        BrowserDownloadQueueEntry(
            taskId = taskId,
            status = status,
            queuedAt = queuedAt,
            createdAt = createdAt,
        )

    private fun segment(index: Int, start: Long, end: Long): BrowserDownloadSegmentRecord =
        BrowserDownloadSegmentRecord(
            index = index,
            startInclusive = start,
            endInclusive = end,
            tempPath = "segment-$index.part",
        )

    private fun pendingRequest(requestId: String): PendingBrowserDownloadRequest =
        PendingBrowserDownloadRequest(
            requestId = requestId,
            sessionId = "session-1",
            url = "https://example.com/$requestId.bin",
            fileName = "$requestId.bin",
            mimeType = "application/octet-stream",
            contentLength = -1L,
            headers = emptyMap(),
            engine = BrowserDownloadEngine.INTERNAL,
        )

    private fun repositoryFile(relativePath: String): File {
        var current: File? =
            File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(4) {
            val candidate = current?.let { directory -> File(directory, relativePath) }
            if (candidate?.isFile == true) {
                return candidate
            }
            current = current?.parentFile
        }
        throw AssertionError("Repository file not found: $relativePath")
    }

}
