package com.ai.assistance.operit.core.tools.defaultTool.websession.extension

import java.net.URI

internal object BrowserExtensionMessagePolicy {
    fun acceptsPage(href: String, origin: String, actualUrl: String?, mainFrame: Boolean): Boolean {
        if (!mainFrame || actualUrl?.substringBefore('#') != href.substringBefore('#')) return false
        // Fragment 不参与来源授权；重复 # 对浏览器有效，但 java.net.URI 会拒绝完整字符串。
        val page = runCatching { URI(href.substringBefore('#')) }.getOrNull() ?: return false
        val source = runCatching { URI(origin) }.getOrNull() ?: return false
        return page.scheme in setOf("http", "https") && page.host != null && page.rawUserInfo == null &&
            page.scheme == source.scheme && page.host.equals(source.host, ignoreCase = true) && port(page) == port(source)
    }

    fun acceptsDocument(current: String, incoming: String, state: String): Boolean =
        incoming.length in 1..100 &&
            if (state == "READY") current.isEmpty() else current.isNotEmpty() && current == incoming

    private fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme == "https") 443 else 80
}
