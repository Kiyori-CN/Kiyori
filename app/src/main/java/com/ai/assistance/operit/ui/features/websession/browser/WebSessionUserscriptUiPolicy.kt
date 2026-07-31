package com.ai.assistance.operit.ui.features.websession.browser

import java.net.URI
import java.util.Locale

internal fun isSupportedUserscriptInstallUrl(rawUrl: String): Boolean {
    val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    return scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}
