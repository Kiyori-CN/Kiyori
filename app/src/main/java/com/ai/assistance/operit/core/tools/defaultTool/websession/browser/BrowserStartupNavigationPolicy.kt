package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import com.kiyori.platform.network.KiyoriNetworkProxyReadiness
import java.net.URI
import java.util.Locale

/**
 * The startup barrier applies only to a remote top-level document that can escape through the
 * WebView. Local pages are safe to create while the process-wide proxy override is being installed.
 */
internal fun shouldAwaitStartupProxyBeforeBrowserNavigation(
    targetUrl: String,
    readiness: KiyoriNetworkProxyReadiness,
): Boolean {
    if (readiness == KiyoriNetworkProxyReadiness.READY) return false
    val scheme = runCatching { URI(targetUrl.trim()).scheme?.lowercase(Locale.ROOT) }.getOrNull()
    return scheme == "http" || scheme == "https"
}
