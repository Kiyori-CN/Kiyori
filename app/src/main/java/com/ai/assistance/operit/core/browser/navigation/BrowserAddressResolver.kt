package com.ai.assistance.operit.core.browser.navigation

import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import java.net.URI
import java.util.Locale

internal object BrowserAddressResolver {
    private const val BLANK_URL = "about:blank"
    private val ipv4WithOptionalPort =
        Regex("""^(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?(?:/.*)?$""")

    fun resolve(
        raw: String,
        searchEngine: WebSessionSearchEngine = WebSessionSearchEngine.DEFAULT,
    ): String {
        val value = raw.trim()
        if (value.isBlank()) {
            return BLANK_URL
        }

        val lower = value.lowercase(Locale.ROOT)
        if (isExplicitAddress(lower)) {
            return value
        }

        if (looksLikeLocalFilePath(value)) {
            return URI("file", "", value, null).toASCIIString()
        }

        if (looksLikeHost(value, lower)) {
            return "https://$value"
        }

        return searchEngine.buildSearchUrl(value)
    }

    fun isSearchQuery(raw: String): Boolean {
        val value = raw.trim()
        if (value.isBlank()) {
            return false
        }

        val lower = value.lowercase(Locale.ROOT)
        return !isExplicitAddress(lower) && !looksLikeLocalFilePath(value) && !looksLikeHost(value, lower)
    }

    private fun isExplicitAddress(lower: String): Boolean =
        lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("about:") ||
            lower.startsWith("file://") ||
            lower.startsWith("content://")

    /**
     * 用户经常从文件管理器复制不带 scheme 的绝对路径（如 `/storage/emulated/0/Download/a.html`）
     * 直接粘贴到地址栏。这类字符串不含 "://"，`looksLikeHost` 会把它当成普通搜索词交给搜索引擎，
     * 导致"浏览器打不开本地 HTML 文件"；这里显式识别为本地文件路径并转成 file:// 地址。
     */
    private fun looksLikeLocalFilePath(value: String): Boolean =
        value.startsWith("/") && !value.contains("://")

    private fun looksLikeHost(value: String, lower: String): Boolean {
        if (value.any(Char::isWhitespace) || value.contains("://")) {
            return false
        }

        val authority = value.substringBefore('/')
        return authority.equals("localhost", ignoreCase = true) ||
            lower.startsWith("localhost:") ||
            ipv4WithOptionalPort.matches(value) ||
            authority.contains('.')
    }
}
