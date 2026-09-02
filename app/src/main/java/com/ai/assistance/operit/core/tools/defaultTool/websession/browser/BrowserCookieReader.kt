package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.net.URI
import java.util.Locale

internal data class BrowserCookieItem(
    val name: String,
    val value: String,
)

internal fun isSupportedBrowserCookieUrl(url: String): Boolean {
    val parsed = try {
        URI(url.trim())
    } catch (_: Exception) {
        return false
    }
    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
    return scheme in setOf("http", "https") && !parsed.host.isNullOrBlank()
}

internal fun browserCookieHost(url: String): String =
    try {
        URI(url.trim()).host.orEmpty()
    } catch (_: Exception) {
        ""
    }

internal fun parseBrowserCookieHeader(header: String): List<BrowserCookieItem> =
    header
        .split(';')
        .mapNotNull { segment ->
            val separator = segment.indexOf('=')
            if (separator <= 0) {
                null
            } else {
                BrowserCookieItem(
                    name = segment.substring(0, separator).trim(),
                    value = segment.substring(separator + 1).trim(),
                ).takeIf { item -> item.name.isNotBlank() }
            }
        }
