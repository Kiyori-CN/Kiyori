package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.net.URI
import java.util.Locale

internal const val MAX_BROWSER_M3U8_VARIANT_DEPTH = 4

internal data class BrowserDownloadByteRange(
    val index: Int,
    val startInclusive: Long,
    val endInclusive: Long,
)

internal data class BrowserM3u8Variant(
    val bandwidth: Long,
    val url: String,
)

internal data class BrowserM3u8ResourcePlan(
    val sourceUrl: String,
    val localFileName: String,
    val localUri: String,
)

internal data class BrowserM3u8RewriteResult(
    val playlistContent: String,
    val resources: List<BrowserM3u8ResourcePlan>,
)

internal fun resolveBrowserDownloadRangeChunkBytes(chunkSizeKb: Int): Long {
    require(isSupportedBrowserDownloadChunkSizeKb(chunkSizeKb)) {
        "Unsupported browser download chunk size: $chunkSizeKb"
    }
    return maxOf(chunkSizeKb.toLong() * 1024L, MIN_BROWSER_DOWNLOAD_SEGMENT_BYTES)
}

internal fun buildBrowserDownloadRangePlan(
    totalBytes: Long,
    chunkSizeKb: Int,
): List<BrowserDownloadByteRange> {
    require(totalBytes > 0L) { "Browser download total bytes must be positive: $totalBytes" }
    val chunkBytes = resolveBrowserDownloadRangeChunkBytes(chunkSizeKb)
    val ranges = ArrayList<BrowserDownloadByteRange>()
    var startInclusive = 0L
    var index = 0
    while (startInclusive < totalBytes) {
        val length = minOf(chunkBytes, totalBytes - startInclusive)
        val endInclusive = startInclusive + length - 1L
        ranges +=
            BrowserDownloadByteRange(
                index = index,
                startInclusive = startInclusive,
                endInclusive = endInclusive,
            )
        startInclusive = endInclusive + 1L
        index += 1
    }
    return ranges
}

internal fun isCompleteBrowserDownloadRangePlan(
    ranges: List<BrowserDownloadByteRange>,
    totalBytes: Long,
): Boolean {
    if (totalBytes <= 0L || ranges.isEmpty()) {
        return false
    }
    val ordered = ranges.sortedBy { it.index }
    if (ordered.map { it.index } != ordered.indices.toList()) {
        return false
    }
    var nextStart = 0L
    ordered.forEachIndexed { index, range ->
        if (range.startInclusive != nextStart || range.endInclusive < range.startInclusive) {
            return false
        }
        if (range.endInclusive == Long.MAX_VALUE) {
            return index == ordered.lastIndex && totalBytes == Long.MAX_VALUE
        }
        nextStart = range.endInclusive + 1L
    }
    return nextStart == totalBytes
}

internal fun isBrowserDownloadM3u8Resource(
    url: String,
    fileName: String,
    mimeType: String,
): Boolean {
    val normalizedMimeType = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
    if (normalizedMimeType.contains("mpegurl") ||
        normalizedMimeType.contains("vnd.apple.mpegurl")
    ) {
        return true
    }
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return extension == "m3u8" ||
        url.substringBefore('?').lowercase(Locale.ROOT).endsWith(".m3u8")
}

internal fun selectHighestBandwidthBrowserM3u8Variant(
    content: String,
    baseUrl: String,
): BrowserM3u8Variant? {
    require(baseUrl.isNotBlank()) { "Browser M3U8 base URL is blank" }
    var pendingBandwidth = -1L
    var hasPendingVariant = false
    var selected: BrowserM3u8Variant? = null
    content.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                hasPendingVariant = true
                pendingBandwidth = extractBrowserM3u8LongAttribute(line, "BANDWIDTH") ?: -1L
            }
            hasPendingVariant && line.isNotBlank() && !line.startsWith("#") -> {
                val candidate =
                    BrowserM3u8Variant(
                        bandwidth = pendingBandwidth,
                        url = resolveBrowserM3u8Url(baseUrl, line),
                    )
                if (selected == null || candidate.bandwidth > selected.bandwidth) {
                    selected = candidate
                }
                hasPendingVariant = false
                pendingBandwidth = -1L
            }
            line.isNotBlank() && !line.startsWith("#") -> {
                hasPendingVariant = false
                pendingBandwidth = -1L
            }
        }
    }
    return selected
}

internal fun extractBrowserM3u8UriAttribute(line: String): String? =
    BROWSER_M3U8_URI_ATTRIBUTE_PATTERN.find(line)?.groupValues?.getOrNull(1)

internal fun extractBrowserM3u8LongAttribute(line: String, name: String): Long? {
    // HLS attributes follow the tag's colon and subsequent attributes use commas.
    val pattern = Regex("(?:^|[:,])\\s*${Regex.escape(name)}=(\\d+)", RegexOption.IGNORE_CASE)
    return pattern.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()
}

internal fun rewriteBrowserM3u8UriAttribute(line: String, replacementUri: String): String {
    val match = BROWSER_M3U8_URI_ATTRIBUTE_PATTERN.find(line) ?: return line
    return line.replaceRange(match.groups[1]!!.range, replacementUri)
}

internal fun resolveBrowserM3u8Url(baseUrl: String, value: String): String {
    require(baseUrl.isNotBlank()) { "Browser M3U8 base URL is blank" }
    require(value.isNotBlank()) { "Browser M3U8 URL value is blank" }
    return URI(baseUrl).resolve(value).toString()
}

internal fun browserM3u8ResourceExtension(value: String): String {
    val path = value.substringBefore('?').substringBefore('#')
    val extension =
        path.substringAfterLast('.', "")
            .takeIf { it.length in 1..8 && it.all(Char::isLetterOrDigit) }
            ?.lowercase(Locale.ROOT)
    return when {
        extension.isNullOrBlank() -> ".bin"
        extension == "jpeg" -> ".jpg"
        else -> ".$extension"
    }
}

internal fun browserM3u8PackageDirectoryName(playlistFileName: String): String {
    require(playlistFileName.isNotBlank()) { "Browser M3U8 playlist file name is blank" }
    return "$playlistFileName.files"
}

internal fun rewriteBrowserM3u8MediaPlaylist(
    content: String,
    baseUrl: String,
    packageDirectoryUri: String,
): BrowserM3u8RewriteResult {
    require(baseUrl.isNotBlank()) { "Browser M3U8 base URL is blank" }
    require(packageDirectoryUri.isNotBlank()) { "Browser M3U8 package directory URI is blank" }
    val directoryUri =
        if (packageDirectoryUri.endsWith('/')) packageDirectoryUri else "$packageDirectoryUri/"
    val resources = mutableListOf<BrowserM3u8ResourcePlan>()
    val rewrittenLines = mutableListOf<String>()
    var mediaIndex = 0
    var sidecarIndex = 0
    content.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isBlank() -> rewrittenLines += rawLine
            line.startsWith("#EXT-X-KEY:", ignoreCase = true) ||
                line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                val sourceUri = extractBrowserM3u8UriAttribute(line)
                if (sourceUri.isNullOrBlank()) {
                    rewrittenLines += rawLine
                } else {
                    val localFileName =
                        "resource_${sidecarIndex++}${browserM3u8ResourceExtension(sourceUri)}"
                    val localUri = resolveBrowserM3u8Url(directoryUri, localFileName)
                    resources +=
                        BrowserM3u8ResourcePlan(
                            sourceUrl = resolveBrowserM3u8Url(baseUrl, sourceUri),
                            localFileName = localFileName,
                            localUri = localUri,
                        )
                    rewrittenLines += rewriteBrowserM3u8UriAttribute(line, localUri)
                }
            }
            line.startsWith("#") -> rewrittenLines += rawLine
            else -> {
                val localFileName =
                    "segment_${mediaIndex++}${browserM3u8ResourceExtension(line)}"
                val localUri = resolveBrowserM3u8Url(directoryUri, localFileName)
                resources +=
                    BrowserM3u8ResourcePlan(
                        sourceUrl = resolveBrowserM3u8Url(baseUrl, line),
                        localFileName = localFileName,
                        localUri = localUri,
                    )
                rewrittenLines += localUri
            }
        }
    }
    return BrowserM3u8RewriteResult(
        playlistContent = rewrittenLines.joinToString("\n"),
        resources = resources,
    )
}

private val BROWSER_M3U8_URI_ATTRIBUTE_PATTERN = Regex("URI=\"([^\"]+)\"")
