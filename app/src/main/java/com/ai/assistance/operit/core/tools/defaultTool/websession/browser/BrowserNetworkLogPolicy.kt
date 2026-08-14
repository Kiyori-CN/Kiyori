package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.net.URI
import java.util.Locale

internal enum class BrowserNetworkRequestCategory {
    VIDEO,
    AUDIO,
    IMAGE,
    WEB,
    OTHER,
}

internal fun classifyBrowserNetworkRequest(
    url: String,
    acceptHeader: String?,
    isMainFrame: Boolean,
): BrowserNetworkRequestCategory {
    if (isMainFrame) {
        return BrowserNetworkRequestCategory.WEB
    }
    val accept = acceptHeader.orEmpty().lowercase(Locale.ROOT)
    val extension = browserNetworkUrlExtension(url)
    return when {
        accept.contains("video/") || extension in BrowserNetworkVideoExtensions ->
            BrowserNetworkRequestCategory.VIDEO
        accept.contains("audio/") || extension in BrowserNetworkAudioExtensions ->
            BrowserNetworkRequestCategory.AUDIO
        accept.contains("image/") || extension in BrowserNetworkImageExtensions ->
            BrowserNetworkRequestCategory.IMAGE
        accept.contains("text/") ||
            accept.contains("javascript") ||
            accept.contains("json") ||
            accept.contains("xml") ||
            accept.contains("font/") ||
            extension in BrowserNetworkWebExtensions -> BrowserNetworkRequestCategory.WEB
        else -> BrowserNetworkRequestCategory.OTHER
    }
}

internal fun filterBrowserNetworkLogEntries(
    entries: List<WebSessionBrowserNetworkEntry>,
    category: BrowserNetworkRequestCategory?,
    query: String,
    blockedOnly: Boolean = false,
): List<WebSessionBrowserNetworkEntry> {
    val normalizedQuery = query.trim()
    return entries.asReversed().filter { entry ->
        (category == null || entry.category == category) &&
            (!blockedOnly || entry.blocked) &&
            (
                normalizedQuery.isBlank() ||
                    entry.url.contains(normalizedQuery, ignoreCase = true) ||
                    entry.method.contains(normalizedQuery, ignoreCase = true) ||
                    entry.blockingRule?.contains(normalizedQuery, ignoreCase = true) == true ||
                    entry.blockingSourceName?.contains(normalizedQuery, ignoreCase = true) == true
                )
    }
}

internal fun isThirdPartyBrowserNetworkRequest(
    pageUrl: String,
    requestUrl: String,
): Boolean {
    val pageHost = browserNetworkHost(pageUrl)
    val requestHost = browserNetworkHost(requestUrl)
    if (pageHost.isBlank() || requestHost.isBlank()) {
        return false
    }
    val sameHostFamily =
        pageHost == requestHost ||
            pageHost.endsWith(".$requestHost") ||
            requestHost.endsWith(".$pageHost")
    return !sameHostFamily
}

internal fun compactBrowserNetworkLogUrl(url: String, maxLength: Int = 96): String {
    require(maxLength >= 16) { "Network log URL width must be at least 16 characters." }
    if (url.length <= maxLength) {
        return url
    }
    val host = browserNetworkHost(url)
    if (host.isBlank() || host.length + 7 >= maxLength) {
        return url.take(maxLength - 3) + "..."
    }
    val tailLength = maxLength - host.length - 4
    return "$host/...${url.takeLast(tailLength)}"
}

internal fun browserNetworkUrlExtension(url: String): String =
    url.substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('/')
        .substringAfterLast('.', "")
        .lowercase(Locale.ROOT)
        .takeIf { extension -> extension.length in 1..8 && extension.all(Char::isLetterOrDigit) }
        .orEmpty()

internal fun browserNetworkHost(url: String): String =
    runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT).trimEnd('.') }
        .getOrDefault("")

private val BrowserNetworkVideoExtensions =
    setOf("m3u8", "mpd", "mp4", "m4v", "mkv", "webm", "flv", "mov", "avi", "ts")

private val BrowserNetworkAudioExtensions =
    setOf("mp3", "aac", "m4a", "flac", "wav", "ogg", "opus", "amr")

private val BrowserNetworkImageExtensions =
    setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "avif", "heic")

private val BrowserNetworkWebExtensions =
    setOf("html", "htm", "xhtml", "css", "js", "mjs", "json", "xml", "woff", "woff2", "ttf", "otf")
