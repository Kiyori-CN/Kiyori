package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

internal enum class WebSessionUserAgentMode(val persistedId: String) {
    ANDROID("android"),
    PC_DESKTOP("pc_desktop"),
    IPHONE("iphone"),
    SYMBIAN_WAP("symbian_wap"),
    CUSTOM_GLOBAL("custom_global");

    companion object {
        fun fromPersistedId(id: String): WebSessionUserAgentMode =
            requireNotNull(entries.singleOrNull { it.persistedId == id }) {
                "Unknown browser user-agent mode: $id"
            }
    }
}

internal data class WebSessionSiteUserAgentRule(
    val domain: String,
    val userAgent: String,
)

internal data class WebSessionResolvedUserAgent(
    val userAgent: String,
    val usesDesktopLayout: Boolean,
)

internal const val WEB_SESSION_ANDROID_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
internal const val WEB_SESSION_PC_DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
internal const val WEB_SESSION_IPHONE_USER_AGENT =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) " +
        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
internal const val WEB_SESSION_SYMBIAN_WAP_USER_AGENT =
    "Mozilla/5.0 (SymbianOS/9.4; Series60/5.0 Nokia5800 XpressMusic/52.0.007; " +
        "Profile/MIDP-2.1 Configuration/CLDC-1.1) AppleWebKit/413 " +
        "(KHTML, like Gecko) Safari/413"

internal fun resolveWebSessionPresetUserAgent(
    mode: WebSessionUserAgentMode,
    customGlobalUserAgent: String,
): String =
    when (mode) {
        WebSessionUserAgentMode.ANDROID -> WEB_SESSION_ANDROID_USER_AGENT
        WebSessionUserAgentMode.PC_DESKTOP -> WEB_SESSION_PC_DESKTOP_USER_AGENT
        WebSessionUserAgentMode.IPHONE -> WEB_SESSION_IPHONE_USER_AGENT
        WebSessionUserAgentMode.SYMBIAN_WAP -> WEB_SESSION_SYMBIAN_WAP_USER_AGENT
        WebSessionUserAgentMode.CUSTOM_GLOBAL -> {
            val normalized = customGlobalUserAgent.trim()
            require(normalized.isNotEmpty()) { "Custom global user-agent must not be blank" }
            normalized
        }
    }

internal fun resolveWebSessionUserAgent(
    settings: WebSessionBrowserSettings,
    targetUrl: String,
    sessionUserAgent: String?,
): WebSessionResolvedUserAgent {
    val resolved =
        if (sessionUserAgent != null) {
            sessionUserAgent.trim().also {
                require(it.isNotEmpty()) { "Explicit session user-agent must not be blank" }
            }
        } else {
            val siteRule = resolveWebSessionSiteUserAgentRule(settings.siteUserAgentRules, targetUrl)
            if (siteRule != null) {
                siteRule.userAgent
            } else {
                resolveWebSessionPresetUserAgent(
                    mode = settings.userAgentMode,
                    customGlobalUserAgent = settings.customGlobalUserAgent,
                )
            }
        }
    return WebSessionResolvedUserAgent(
        userAgent = resolved,
        usesDesktopLayout = isWebSessionDesktopUserAgent(resolved),
    )
}

internal fun resolveWebSessionSiteUserAgentRule(
    rules: List<WebSessionSiteUserAgentRule>,
    targetUrl: String,
): WebSessionSiteUserAgentRule? {
    val host = extractWebSessionUserAgentHost(targetUrl) ?: return null
    return rules
        .asSequence()
        .filter { rule -> host == rule.domain || host.endsWith(".${rule.domain}") }
        .maxByOrNull { rule -> rule.domain.length }
}

internal fun normalizeWebSessionUserAgentDomain(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    val uriText = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    return try {
        extractWebSessionUserAgentHost(uriText)
    } catch (_: URISyntaxException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun extractWebSessionUserAgentHost(url: String): String? {
    val uri = URI(url.trim())
    val host =
        (uri.host ?: uri.rawAuthority?.let(::extractHostFromWebSessionAuthority))
            ?.trimEnd('.')
            ?.lowercase(Locale.ROOT)
            ?: return null
    if (host.isEmpty()) {
        return null
    }
    return if (host.contains(':')) host else IDN.toASCII(host).lowercase(Locale.ROOT)
}

private fun extractHostFromWebSessionAuthority(authority: String): String? {
    val hostAndPort = authority.substringAfterLast('@')
    if (hostAndPort.startsWith('[')) {
        val closingBracket = hostAndPort.indexOf(']')
        return hostAndPort.substring(1, closingBracket.takeIf { it > 1 } ?: return null)
    }
    return if (hostAndPort.count { it == ':' } == 1) {
        hostAndPort.substringBefore(':')
    } else {
        hostAndPort
    }
}

internal fun isWebSessionDesktopUserAgent(userAgent: String): Boolean {
    val normalized = userAgent.lowercase(Locale.ROOT)
    return listOf("mobile", "android", "iphone", "ipad", "symbian", "midp")
        .none(normalized::contains)
}
