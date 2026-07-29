package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDownloadRuntimePolicyTest {
    @Test
    fun `Android 14 and newer use UIDT while older devices use dataSync service`() {
        assertEquals(
            BrowserDownloadRuntimeKind.DATA_SYNC_FOREGROUND_SERVICE,
            resolveBrowserDownloadRuntimeKind(33),
        )
        assertEquals(
            BrowserDownloadRuntimeKind.USER_INITIATED_JOB,
            resolveBrowserDownloadRuntimeKind(34),
        )
        assertEquals(
            BrowserDownloadRuntimeKind.USER_INITIATED_JOB,
            resolveBrowserDownloadRuntimeKind(36),
        )
    }

    @Test
    fun `network policy blocks metered and roaming connections independently`() {
        assertTrue(
            isBrowserDownloadNetworkAllowed(
                policy = BrowserDownloadNetworkPolicy.ANY,
                allowRoaming = false,
                isMetered = true,
                isRoaming = false,
            ),
        )
        assertFalse(
            isBrowserDownloadNetworkAllowed(
                policy = BrowserDownloadNetworkPolicy.UNMETERED,
                allowRoaming = false,
                isMetered = true,
                isRoaming = false,
            ),
        )
        assertFalse(
            isBrowserDownloadNetworkAllowed(
                policy = BrowserDownloadNetworkPolicy.ANY,
                allowRoaming = false,
                isMetered = false,
                isRoaming = true,
            ),
        )
        assertTrue(
            isBrowserDownloadNetworkAllowed(
                policy = BrowserDownloadNetworkPolicy.UNMETERED,
                allowRoaming = true,
                isMetered = false,
                isRoaming = true,
            ),
        )
    }

    @Test
    fun `runtime requests unmetered host only when every runnable network task requires it`() {
        val unmetered =
            task(
                id = "unmetered",
                fileName = "unmetered.bin",
                status = BrowserDownloadStatus.QUEUED,
                totalBytes = 100L,
                downloadedBytes = 0L,
            ).copy(networkPolicy = BrowserDownloadNetworkPolicy.UNMETERED)
        val anyNetwork =
            task(
                id = "any",
                fileName = "any.bin",
                status = BrowserDownloadStatus.QUEUED,
                totalBytes = 100L,
                downloadedBytes = 0L,
            ).copy(networkPolicy = BrowserDownloadNetworkPolicy.ANY)

        assertTrue(browserDownloadRuntimeRequiresUnmeteredNetwork(listOf(unmetered)))
        assertFalse(
            browserDownloadRuntimeRequiresUnmeteredNetwork(
                listOf(unmetered, anyNetwork),
            ),
        )
        assertFalse(browserDownloadRuntimeRequiresUnmeteredNetwork(emptyList()))
    }

    @Test
    fun `runtime summary aggregates runnable tasks and excludes terminal history`() {
        val summary =
            buildBrowserDownloadRuntimeSummary(
                listOf(
                    task(
                        id = "active",
                        fileName = "active.mp4",
                        status = BrowserDownloadStatus.DOWNLOADING,
                        totalBytes = 1000L,
                        downloadedBytes = 400L,
                        speedBytesPerSecond = 120L,
                    ),
                    task(
                        id = "queued",
                        fileName = "queued.zip",
                        status = BrowserDownloadStatus.QUEUED,
                        totalBytes = 500L,
                        downloadedBytes = 0L,
                    ),
                    task(
                        id = "completed",
                        fileName = "completed.pdf",
                        status = BrowserDownloadStatus.COMPLETED,
                        totalBytes = 800L,
                        downloadedBytes = 800L,
                    ),
                ),
            )

        assertEquals(1, summary.activeCount)
        assertEquals(1, summary.queuedCount)
        assertEquals(2, summary.totalCount)
        assertEquals("active.mp4", summary.currentFileName)
        assertEquals(400L, summary.downloadedBytes)
        assertEquals(1500L, summary.totalBytes)
        assertEquals(120L, summary.speedBytesPerSecond)
        assertEquals(27, summary.progressPercent)
        assertFalse(summary.isIdle)
    }

    @Test
    fun `runtime summary is indeterminate when any runnable task has unknown length`() {
        val summary =
            buildBrowserDownloadRuntimeSummary(
                listOf(
                    task(
                        id = "unknown",
                        fileName = "stream.bin",
                        status = BrowserDownloadStatus.CONNECTING,
                        totalBytes = -1L,
                        downloadedBytes = 12L,
                    ),
                    task(
                        id = "known",
                        fileName = "known.bin",
                        status = BrowserDownloadStatus.QUEUED,
                        totalBytes = 100L,
                        downloadedBytes = 20L,
                    ),
                ),
            )

        assertNull(summary.totalBytes)
        assertNull(summary.progressPercent)
        assertEquals(32L, summary.downloadedBytes)
    }

    @Test
    fun `idle runtime summary contains no synthetic task`() {
        val summary =
            buildBrowserDownloadRuntimeSummary(
                listOf(
                    task(
                        id = "paused",
                        fileName = "paused.bin",
                        status = BrowserDownloadStatus.PAUSED,
                        totalBytes = 100L,
                        downloadedBytes = 20L,
                    ),
                    task(
                        id = "inline",
                        fileName = "inline.txt",
                        status = BrowserDownloadStatus.QUEUED,
                        totalBytes = 12L,
                        downloadedBytes = 0L,
                        type = "data-url",
                    ),
                ),
            )

        assertTrue(summary.isIdle)
        assertEquals(0, summary.totalCount)
        assertNull(summary.currentFileName)
        assertNull(summary.progressPercent)
    }

    @Test
    fun `pending APK install identity has matching task JSON writers and readers`() {
        val source =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/" +
                    "websession/browser/BrowserDownloadSupport.kt",
            ).readText()

        listOf(
            "pending_install_package_name",
            "pending_install_version_code",
            "pending_install_previous_version_code",
        ).forEach { key ->
            assertTrue(source.contains(".put(\"$key\""))
            assertTrue(source.contains("json.optString(\"$key\"") || source.contains("json.optLong(\"$key\""))
        }
    }

    @Test
    fun `manifest registers one UIDT host one dataSync host and private actions`() {
        val manifest = repositoryFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("BrowserDownloadJobService"))
        assertTrue(manifest.contains("android.permission.RUN_USER_INITIATED_JOBS"))
        assertTrue(manifest.contains("android.permission.BIND_JOB_SERVICE"))
        assertTrue(manifest.contains("BrowserDownloadForegroundService"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
        assertTrue(manifest.contains("BrowserDownloadRuntimeActionReceiver"))
        assertTrue(manifest.contains("BrowserDownloadBootReceiver"))
        assertTrue(manifest.contains("BrowserDownloadInstallResultReceiver"))
        assertTrue(manifest.contains("android.intent.action.PACKAGE_ADDED"))
        assertTrue(manifest.contains("android.intent.action.PACKAGE_REPLACED"))
        assertTrue(manifest.contains("android:scheme=\"package\""))
    }

    private fun task(
        id: String,
        fileName: String,
        status: BrowserDownloadStatus,
        totalBytes: Long,
        downloadedBytes: Long,
        speedBytesPerSecond: Long = 0L,
        type: String = BROWSER_DOWNLOAD_TYPE_HTTP,
    ): BrowserDownloadTaskRecord =
        BrowserDownloadTaskRecord(
            id = id,
            sessionId = "session",
            type = type,
            sourceUrl = "https://example.com/$fileName",
            destinationPath = "D:/downloads/$fileName",
            fileName = fileName,
            headers = emptyMap(),
            createdAt = 1L,
            updatedAt = 1L,
            mimeType = "application/octet-stream",
            status = status,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            speedBytesPerSecond = speedBytesPerSecond,
            supportsResume = false,
            threadCount = DEFAULT_BROWSER_DOWNLOAD_SEGMENT_THREAD_COUNT,
            errorMessage = null,
            completedAt = null,
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
