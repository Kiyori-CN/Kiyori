package com.ai.assistance.operit.core.browser.navigation

import android.net.Uri
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
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
        return !isExplicitAddress(lower) && !looksLikeHost(value, lower)
    }

    private fun isExplicitAddress(lower: String): Boolean =
        lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("about:")

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
