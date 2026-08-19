package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.net.URI
import java.util.Locale

internal enum class BrowserNetworkRequestCategory {
    VIDEO,
    AUDIO,
    IMAGE,
    WEB,
    SCRIPT,
    STYLE,
    DATA,
    FONT,
    OTHER,
}

internal enum class BrowserExternalNavigationDecision {
    ALLOW,
    ASK,
    BLOCK,
}

internal fun resolveBrowserExternalNavigationDecision(
    policy: BrowserAdMarkingNavigationPolicy,
    pageUrl: String,
    targetUrl: String,
): BrowserExternalNavigationDecision {
    if (
        policy == BrowserAdMarkingNavigationPolicy.DEFAULT ||
            !isThirdPartyBrowserNetworkRequest(pageUrl, targetUrl)
    ) {
        return BrowserExternalNavigationDecision.ALLOW
    }
    return when (policy) {
        BrowserAdMarkingNavigationPolicy.DEFAULT -> BrowserExternalNavigationDecision.ALLOW
        BrowserAdMarkingNavigationPolicy.ASK -> BrowserExternalNavigationDecision.ASK
        BrowserAdMarkingNavigationPolicy.BLOCK -> BrowserExternalNavigationDecision.BLOCK
    }
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
        accept.contains("javascript") || extension in BrowserNetworkScriptExtensions ->
            BrowserNetworkRequestCategory.SCRIPT
        accept.contains("text/css") || extension in BrowserNetworkStyleExtensions ->
            BrowserNetworkRequestCategory.STYLE
        accept.contains("font/") || extension in BrowserNetworkFontExtensions ->
            BrowserNetworkRequestCategory.FONT
        accept.contains("application/json") ||
            accept.contains("application/xml") ||
            accept.contains("text/xml") ||
            accept.contains("text/plain") ||
            extension in BrowserNetworkDataExtensions -> BrowserNetworkRequestCategory.DATA
        accept.contains("text/html") ||
            accept.contains("application/xhtml") ||
            extension in BrowserNetworkDocumentExtensions -> BrowserNetworkRequestCategory.WEB
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
    return entries
        .sortedWith(
            compareBy<WebSessionBrowserNetworkEntry>(
                { entry -> entry.category.ordinal },
                { entry -> browserNetworkHost(entry.url) },
                { entry -> entry.url },
            ),
        )
        .filter { entry ->
            (category == null || entry.category == category) &&
                (!blockedOnly || entry.blocked) &&
                (
                    normalizedQuery.isBlank() ||
                        entry.url.contains(normalizedQuery, ignoreCase = true) ||
                        entry.method.contains(normalizedQuery, ignoreCase = true) ||
                        entry.blockingRule?.contains(normalizedQuery, ignoreCase = true) == true ||
                        entry.blockingSourceName?.contains(normalizedQuery, ignoreCase = true) == true ||
                        entry.elementSelector?.contains(normalizedQuery, ignoreCase = true) == true
                    )
        }
}

internal fun buildBrowserNetworkImageViewerSnapshot(
    entries: List<WebSessionBrowserNetworkEntry>,
    selectedResourceIdentity: String,
): BrowserImageViewerSnapshot? {
    val imageEntries =
        entries.filter { entry ->
            entry.kind == BrowserNetworkLogEntryKind.REQUEST &&
                entry.category == BrowserNetworkRequestCategory.IMAGE &&
                isHttpBrowserNetworkUrl(entry.url)
        }
    val initialPage =
        imageEntries.indexOfFirst { entry ->
            entry.resourceIdentity == selectedResourceIdentity
        }
    if (initialPage < 0) {
        return null
    }
    return BrowserImageViewerSnapshot(
        items =
            imageEntries.map { entry ->
                BrowserImageViewerItem(
                    identity = entry.resourceIdentity,
                    url = entry.url,
                    requestHeaders =
                        sanitizeBrowserImageRequestHeaders(entry.requestHeaders),
                    mediaCandidateId = entry.mediaCandidateId,
                )
            },
        initialPage = initialPage,
    )
}

internal fun isHttpBrowserNetworkUrl(url: String): Boolean =
    runCatching {
        val uri = URI(url.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        scheme == "http" || scheme == "https"
    }.getOrDefault(false)

internal fun buildBrowserNetworkRequestHeaders(
    observedHeaders: Map<String, String>,
    appliedUserAgent: String,
    cookie: String?,
    pageUrl: String,
): Map<String, String> {
    val headers = LinkedHashMap<String, String>()
    observedHeaders.forEach { (name, value) ->
        if (name.isNotBlank()) {
            headers.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(headers::remove)
            headers[name] = value
        }
    }
    addBrowserNetworkHeaderIfMissing(
        headers = headers,
        name = "User-Agent",
        value = appliedUserAgent,
    )
    addBrowserNetworkHeaderIfMissing(
        headers = headers,
        name = "Cookie",
        value = cookie,
    )
    addBrowserNetworkHeaderIfMissing(
        headers = headers,
        name = "Referer",
        value = pageUrl.takeIf(::isHttpBrowserNetworkUrl),
    )
    return headers.toMap()
}

private fun addBrowserNetworkHeaderIfMissing(
    headers: LinkedHashMap<String, String>,
    name: String,
    value: String?,
) {
    val existingKey = headers.keys.firstOrNull { it.equals(name, ignoreCase = true) }
    if (existingKey != null && headers[existingKey].orEmpty().isNotBlank()) {
        return
    }
    existingKey?.let(headers::remove)
    value?.takeIf(String::isNotBlank)?.let { headers[name] = it }
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

/**
 * The static resource directory keeps query parameters because signed URLs and CDN variants can
 * address different bytes. Only the fragment is presentation metadata and is excluded.
 */
internal fun normalizeBrowserResourceIdentityUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return ""
    return runCatching {
        val uri = URI(trimmed)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)
        if (scheme == null || host == null) {
            return@runCatching trimmed.substringBefore('#')
        }
        val port =
            if (
                (scheme == "http" && uri.port == 80) ||
                    (scheme == "https" && uri.port == 443)
            ) {
                -1
            } else {
                uri.port
            }
        URI(
                scheme,
                uri.userInfo,
                host,
                port,
                uri.rawPath.orEmpty().ifBlank { "/" },
                uri.rawQuery,
                null,
            )
            .toASCIIString()
    }.getOrElse {
        trimmed.substringBefore('#')
    }
}

internal fun browserNetworkCategoryPriority(
    category: BrowserNetworkRequestCategory,
): Int =
    when (category) {
        BrowserNetworkRequestCategory.VIDEO -> 100
        BrowserNetworkRequestCategory.AUDIO -> 95
        BrowserNetworkRequestCategory.IMAGE -> 90
        BrowserNetworkRequestCategory.WEB -> 70
        BrowserNetworkRequestCategory.SCRIPT -> 60
        BrowserNetworkRequestCategory.STYLE -> 55
        BrowserNetworkRequestCategory.DATA -> 50
        BrowserNetworkRequestCategory.FONT -> 45
        BrowserNetworkRequestCategory.OTHER -> 0
    }

internal fun mergeBrowserNetworkRequestCategory(
    current: BrowserNetworkRequestCategory,
    observed: BrowserNetworkRequestCategory,
): BrowserNetworkRequestCategory =
    if (browserNetworkCategoryPriority(observed) > browserNetworkCategoryPriority(current)) {
        observed
    } else {
        current
    }

private val BrowserNetworkVideoExtensions =
    setOf("m3u8", "mpd", "mp4", "m4v", "mkv", "webm", "flv", "mov", "avi", "ts")

private val BrowserNetworkAudioExtensions =
    setOf("mp3", "aac", "m4a", "flac", "wav", "ogg", "opus", "amr")

private val BrowserNetworkImageExtensions =
    setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "avif", "heic")

private val BrowserNetworkDocumentExtensions =
    setOf("html", "htm", "xhtml")

private val BrowserNetworkScriptExtensions =
    setOf("js", "mjs", "jsx", "ts", "tsx")

private val BrowserNetworkStyleExtensions =
    setOf("css", "scss", "less")

private val BrowserNetworkDataExtensions =
    setOf("json", "xml", "txt", "csv", "yaml", "yml")

private val BrowserNetworkFontExtensions =
    setOf("woff", "woff2", "ttf", "otf")
