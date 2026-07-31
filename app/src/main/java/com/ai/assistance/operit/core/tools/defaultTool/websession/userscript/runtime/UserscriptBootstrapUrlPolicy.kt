package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.runtime

import java.net.URI
import java.util.Locale

/**
 * Resolves the actual document URL used for userscript matching at document-start.
 *
 * WebView.url may still describe the previous document when the injected runtime posts its first
 * message. The isolated world is not reachable by page JavaScript, so its own location.href is
 * authoritative. Page-world callers must additionally prove that the reported URL belongs to the
 * WebMessageListener source origin before Kiyori returns any script source.
 */
internal object UserscriptBootstrapUrlPolicy {
    fun resolve(
        runtimeHref: String,
        sourceOrigin: String,
        isolatedWorld: Boolean,
    ): String? {
        val href = runtimeHref.trim()
        if (href.isBlank()) {
            return null
        }
        if (isolatedWorld) {
            return href
        }
        val hrefOrigin = originOf(href) ?: return null
        val messageOrigin = originOf(sourceOrigin) ?: return null
        return href.takeIf { hrefOrigin == messageOrigin }
    }

    private fun originOf(rawUrl: String): String? {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val portPart =
            when {
                uri.port < 0 -> ""
                scheme == "http" && uri.port == 80 -> ""
                scheme == "https" && uri.port == 443 -> ""
                else -> ":${uri.port}"
            }
        return "$scheme://$host$portPart"
    }
}
