package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

internal data class BrowserImageViewerItem(
    val identity: String,
    val url: String,
    val requestHeaders: Map<String, String>,
    val mediaCandidateId: String? = null,
)

internal data class BrowserImageViewerSnapshot(
    val items: List<BrowserImageViewerItem>,
    val initialPage: Int,
)

internal const val BROWSER_IMAGE_VIEWER_MIN_SCALE = 1f
internal const val BROWSER_IMAGE_VIEWER_MAX_SCALE = 5f
internal const val MAX_BROWSER_WEB_ELEMENT_IMAGE_COUNT = 200

internal fun clampBrowserImageViewerScale(scale: Float): Float =
    scale.coerceIn(
        BROWSER_IMAGE_VIEWER_MIN_SCALE,
        BROWSER_IMAGE_VIEWER_MAX_SCALE,
    )

internal fun browserImageViewerBackgroundAlpha(
    verticalOffsetPx: Float,
    viewportHeightPx: Float,
): Float {
    if (!verticalOffsetPx.isFinite() || !viewportHeightPx.isFinite() || viewportHeightPx <= 0f) {
        return 1f
    }
    val fadeDistance = viewportHeightPx * 0.55f
    return (1f - (kotlin.math.abs(verticalOffsetPx) / fadeDistance)).coerceIn(0f, 1f)
}

internal fun shouldDismissBrowserImageViewer(
    verticalOffsetPx: Float,
    touchSlopPx: Float,
): Boolean =
    verticalOffsetPx.isFinite() &&
        touchSlopPx.isFinite() &&
        touchSlopPx >= 0f &&
        kotlin.math.abs(verticalOffsetPx) > touchSlopPx

internal fun buildSingleBrowserImageViewerSnapshot(
    url: String,
    requestHeaders: Map<String, String>,
): BrowserImageViewerSnapshot? {
    val normalizedUrl = url.trim()
    if (!isHttpBrowserNetworkUrl(normalizedUrl)) {
        return null
    }
    return BrowserImageViewerSnapshot(
        items =
            listOf(
                BrowserImageViewerItem(
                    identity = normalizeBrowserResourceIdentityUrl(normalizedUrl),
                    url = normalizedUrl,
                    requestHeaders = sanitizeBrowserImageRequestHeaders(requestHeaders),
                ),
            ),
        initialPage = 0,
    )
}

internal fun buildBrowserWebElementImageViewerSnapshot(
    urls: List<String>,
    selectedUrl: String,
    requestHeadersFor: (String) -> Map<String, String>,
): BrowserImageViewerSnapshot? {
    val items =
        urls
            .asSequence()
            .map(String::trim)
            .filter(::isHttpBrowserNetworkUrl)
            .map { url ->
                normalizeBrowserResourceIdentityUrl(url) to url
            }
            .filter { (identity, _) -> identity.isNotBlank() }
            .distinctBy { (identity, _) -> identity }
            .take(MAX_BROWSER_WEB_ELEMENT_IMAGE_COUNT)
            .map { (identity, url) ->
                BrowserImageViewerItem(
                    identity = identity,
                    url = url,
                    requestHeaders =
                        sanitizeBrowserImageRequestHeaders(requestHeadersFor(url)),
                )
            }
            .toList()
    val selectedIdentity = normalizeBrowserResourceIdentityUrl(selectedUrl)
    val initialPage = items.indexOfFirst { item -> item.identity == selectedIdentity }
    if (initialPage < 0) {
        return null
    }
    return BrowserImageViewerSnapshot(items = items, initialPage = initialPage)
}

internal fun sanitizeBrowserImageRequestHeaders(
    headers: Map<String, String>,
): Map<String, String> {
    val sanitized = LinkedHashMap<String, String>()
    headers.forEach { (name, value) ->
        if (
            BrowserImageRequestHeaderNames.any { allowed ->
                allowed.equals(name, ignoreCase = true)
            } &&
                name.isNotBlank() &&
                value.isNotBlank() &&
                '\r' !in value &&
                '\n' !in value
        ) {
            sanitized[name] = value
        }
    }
    return sanitized.toMap()
}

private val BrowserImageRequestHeaderNames =
    setOf(
        "Accept",
        "Cookie",
        "Origin",
        "Referer",
        "User-Agent",
    )
