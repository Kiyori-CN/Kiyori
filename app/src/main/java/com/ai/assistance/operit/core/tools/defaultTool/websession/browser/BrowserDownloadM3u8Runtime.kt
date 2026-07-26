package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal data class BrowserM3u8DownloadResult(
    val finalUrl: String,
    val mimeType: String,
    val isPackage: Boolean,
    val storedBytes: Long,
)

/**
 * The playlist and its sidecar files are one atomic browser-download artifact. Keeping the
 * resource directory beside the playlist is required because the rewritten playlist stores local
 * file URIs; leaving either half behind would create an apparently completed but unusable task.
 */
internal suspend fun downloadBrowserM3u8(
    playlistUrl: String,
    headers: Map<String, String>,
    transport: BrowserDownloadTransport,
    autoMerge: Boolean,
    m3u8ThreadCount: Int,
    outputPlaylistFile: File,
    packageDirectory: File,
    onChunk: (Int) -> Unit = {},
    isCancelled: () -> Boolean = { false },
): BrowserM3u8DownloadResult {
    require(playlistUrl.isNotBlank()) { "Browser M3U8 playlist URL is blank" }
    require(isSupportedBrowserDownloadM3u8ThreadCount(m3u8ThreadCount)) {
        "Unsupported browser download M3U8 thread count: $m3u8ThreadCount"
    }

    val reportedBytes = AtomicLong(0L)
    val report: (Int) -> Unit = { bytes ->
        if (bytes != 0) {
            reportedBytes.addAndGet(bytes.toLong())
            onChunk(bytes)
        }
    }

    return try {
        ensureBrowserM3u8Active(isCancelled)
        var playlist = transport.readTextWithRetry(playlistUrl, headers)
        var variantDepth = 0
        if (autoMerge) {
            while (true) {
                currentCoroutineContext().ensureActive()
                ensureBrowserM3u8Active(isCancelled)
                val variant = selectHighestBandwidthBrowserM3u8Variant(playlist.content, playlist.finalUrl)
                    ?: break
                if (variantDepth >= MAX_BROWSER_M3U8_VARIANT_DEPTH) {
                    throw IOException(
                        "Browser M3U8 master playlist exceeds the supported depth of " +
                            "$MAX_BROWSER_M3U8_VARIANT_DEPTH",
                    )
                }
                playlist = transport.readTextWithRetry(variant.url, headers)
                variantDepth += 1
            }
        }

        if (!autoMerge) {
            val playlistBytes = playlist.content.toByteArray(StandardCharsets.UTF_8)
            outputPlaylistFile.parentFile?.mkdirs()
            outputPlaylistFile.writeBytes(playlistBytes)
            reportPlaylistBytes(playlistBytes.size.toLong(), report)
            return BrowserM3u8DownloadResult(
                finalUrl = playlist.finalUrl,
                mimeType = playlist.mimeType,
                isPackage = false,
                storedBytes = playlistBytes.size.toLong(),
            )
        }

        deleteBrowserM3u8PackageDirectory(packageDirectory)
        require(packageDirectory.mkdirs() || packageDirectory.isDirectory) {
            "Unable to create browser M3U8 package directory: ${packageDirectory.absolutePath}"
        }
        val rewrite =
            rewriteBrowserM3u8MediaPlaylist(
                content = playlist.content,
                baseUrl = playlist.finalUrl,
                packageDirectoryUri = packageDirectoryUri(packageDirectory),
            )
        val limiter = Semaphore(m3u8ThreadCount)
        coroutineScope {
            rewrite.resources.map { resource ->
                async(Dispatchers.IO) {
                    limiter.withPermit {
                        currentCoroutineContext().ensureActive()
                        ensureBrowserM3u8Active(isCancelled)
                        val destination = File(packageDirectory, resource.localFileName)
                        transport.downloadResourceWithRetry(
                            url = resource.sourceUrl,
                            headers = headers,
                            destination = destination,
                            onChunk = report,
                            isCancelled = isCancelled,
                        )
                    }
                }
            }.awaitAll()
        }
        currentCoroutineContext().ensureActive()
        ensureBrowserM3u8Active(isCancelled)
        val playlistBytes = rewrite.playlistContent.toByteArray(StandardCharsets.UTF_8)
        outputPlaylistFile.parentFile?.mkdirs()
        outputPlaylistFile.writeBytes(playlistBytes)
        reportPlaylistBytes(playlistBytes.size.toLong(), report)
        BrowserM3u8DownloadResult(
            finalUrl = playlist.finalUrl,
            mimeType = playlist.mimeType,
            isPackage = true,
            storedBytes = playlistBytes.size.toLong() + browserM3u8DirectorySizeBytes(packageDirectory),
        )
    } catch (error: CancellationException) {
        cleanupBrowserM3u8Artifacts(outputPlaylistFile, packageDirectory, reportedBytes, onChunk)
        throw error
    } catch (error: Throwable) {
        cleanupBrowserM3u8Artifacts(outputPlaylistFile, packageDirectory, reportedBytes, onChunk)
        throw error
    }
}

internal fun packageDirectoryUri(directory: File): String =
    directory.toURI().toString().let { uri ->
        if (uri.endsWith('/')) uri else "$uri/"
    }

internal fun browserM3u8PackageDirectoryFor(playlistFile: File): File =
    File(playlistFile.parentFile, browserM3u8PackageDirectoryName(playlistFile.name))

internal fun deleteBrowserM3u8PackageDirectory(packageDirectory: File) {
    if (packageDirectory.exists() && !packageDirectory.deleteRecursively()) {
        throw IOException("Unable to remove browser M3U8 package directory: ${packageDirectory.absolutePath}")
    }
}

internal fun browserM3u8DirectorySizeBytes(directory: File): Long =
    if (!directory.exists()) {
        0L
    } else {
        directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

internal fun renameBrowserM3u8Package(
    currentPlaylistFile: File,
    renamedPlaylistFile: File,
) {
    require(currentPlaylistFile.exists()) { "当前文件不存在" }
    require(renamedPlaylistFile.absolutePath != currentPlaylistFile.absolutePath) { "文件名未发生变化" }
    require(!renamedPlaylistFile.exists()) { "目标文件已存在" }
    val currentDirectory = browserM3u8PackageDirectoryFor(currentPlaylistFile)
    val renamedDirectory = browserM3u8PackageDirectoryFor(renamedPlaylistFile)
    require(currentDirectory.isDirectory) { "M3U8离线包目录不存在" }
    require(!renamedDirectory.exists()) { "目标文件名的M3U8离线包已存在" }
    val originalPlaylist = currentPlaylistFile.readBytes()
    val rewrittenPlaylist =
        String(originalPlaylist, StandardCharsets.UTF_8).replace(
            packageDirectoryUri(currentDirectory),
            packageDirectoryUri(renamedDirectory),
        ).toByteArray(StandardCharsets.UTF_8)

    require(currentPlaylistFile.renameTo(renamedPlaylistFile)) { "重命名失败" }
    try {
        if (currentDirectory.exists()) {
            require(currentDirectory.renameTo(renamedDirectory)) { "M3U8离线包重命名失败" }
        }
        renamedPlaylistFile.writeBytes(rewrittenPlaylist)
    } catch (error: Throwable) {
        runCatching { renamedPlaylistFile.writeBytes(originalPlaylist) }
        runCatching {
            if (renamedDirectory.exists()) {
                renamedDirectory.renameTo(currentDirectory)
            }
        }
        runCatching { renamedPlaylistFile.renameTo(currentPlaylistFile) }
        throw error
    }
}

private fun cleanupBrowserM3u8Artifacts(
    outputPlaylistFile: File,
    packageDirectory: File,
    reportedBytes: AtomicLong,
    onChunk: (Int) -> Unit,
) {
    runCatching { outputPlaylistFile.delete() }
    runCatching { deleteBrowserM3u8PackageDirectory(packageDirectory) }
    var remaining = reportedBytes.getAndSet(0L).coerceAtLeast(0L)
    while (remaining > 0L) {
        val delta = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()
        onChunk(-delta)
        remaining -= delta.toLong()
    }
}

private fun ensureBrowserM3u8Active(isCancelled: () -> Boolean) {
    if (isCancelled()) {
        throw CancellationException("Browser M3U8 download cancelled")
    }
}

private fun reportPlaylistBytes(bytes: Long, report: (Int) -> Unit) {
    var remaining = bytes
    while (remaining > 0L) {
        val delta = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()
        report(delta)
        remaining -= delta.toLong()
    }
}
