package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.util.Locale
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
internal enum class BrowserWindowCreationReason {
    MANUAL_NEW_WINDOW,
    OPEN_IN_NEW_WINDOW,
    SOFTWARE_HOME_SEARCH,
    PROFILE_BOUNDARY_SEARCH,
    AI_EXPLICIT_CREATE,
    HOME_CROSS_SITE_USER_NAVIGATION,
    RESTORED_NORMAL_WINDOW,
}

internal data class BrowserSiteIdentity(
    val key: String,
)

internal fun browserSiteIdentity(url: String?): BrowserSiteIdentity? {
    val parsed = url?.trim()?.toHttpUrlOrNull() ?: return null
    if (parsed.scheme != "http" && parsed.scheme != "https") {
        return null
    }
    val host = parsed.host.lowercase(Locale.ROOT).trimEnd('.')
    if (host.isBlank()) {
        return null
    }
    val exactHost =
        isIpAddress(host) ||
            host == "localhost" ||
            !host.contains('.')
    val siteKey =
        if (exactHost) {
            host
        } else {
            parsed.topPrivateDomain() ?: host
        }
    return BrowserSiteIdentity(siteKey)
}

internal fun areBrowserUrlsSameSite(
    firstUrl: String?,
    secondUrl: String?,
): Boolean {
    val first = browserSiteIdentity(firstUrl) ?: return false
    val second = browserSiteIdentity(secondUrl) ?: return false
    return first == second
}

internal fun shouldClearBrowserSearchRecoveryOnNavigation(
    pageLoaded: Boolean,
    searchRecoveryPending: Boolean,
    resolvedResultUrl: String?,
    targetUrl: String,
): Boolean =
    pageLoaded &&
        !searchRecoveryPending &&
        resolvedResultUrl != null &&
        !areBrowserHomeUrlsEquivalent(resolvedResultUrl, targetUrl)

internal fun isDuplicateBrowserDocumentCompletion(
    finishedDocumentToken: String?,
    finishedDocumentUrl: String,
    currentDocumentToken: String,
    callbackUrl: String,
): Boolean =
    finishedDocumentToken == currentDocumentToken &&
        finishedDocumentUrl.isNotBlank() &&
        finishedDocumentUrl == callbackUrl

internal fun shouldUseBrowserHistoryBack(
    creationReason: BrowserWindowCreationReason,
    canGoBack: Boolean,
    backTargetUrl: String?,
    backTargetIsInitialSyntheticEntry: Boolean,
): Boolean {
    if (!canGoBack) {
        return false
    }
    val syntheticSearchEntry =
        creationReason in
            setOf(
                BrowserWindowCreationReason.SOFTWARE_HOME_SEARCH,
                BrowserWindowCreationReason.PROFILE_BOUNDARY_SEARCH,
            ) &&
            backTargetIsInitialSyntheticEntry &&
            backTargetUrl?.equals("about:blank", ignoreCase = true) == true
    return !syntheticSearchEntry
}

private fun isIpAddress(host: String): Boolean {
    if (host.contains(':')) {
        return host.all { character ->
            character.isDigit() ||
                character.lowercaseChar() in 'a'..'f' ||
                character == ':' ||
                character == '.'
        }
    }
    val segments = host.split('.')
    return segments.size == 4 &&
        segments.all { segment ->
            segment.isNotEmpty() &&
                segment.all(Char::isDigit) &&
                segment.toIntOrNull() in 0..255
        }
}

internal enum class BrowserWindowNavigationDecision {
    CURRENT_SESSION,
    CREATE_CHILD_SESSION,
    REJECT,
}

internal data class BrowserWindowNavigationRequest(
    val sourceUrl: String,
    val targetUrl: String,
    val isMainFrame: Boolean,
    val hasUserGesture: Boolean,
    val isPopup: Boolean,
    val sourceAtConfiguredHome: Boolean,
    val navigationLocked: Boolean = false,
)

internal fun resolveBrowserWindowNavigationDecision(
    request: BrowserWindowNavigationRequest,
): BrowserWindowNavigationDecision {
    if (request.navigationLocked) {
        return BrowserWindowNavigationDecision.REJECT
    }
    if (!request.isMainFrame) {
        return BrowserWindowNavigationDecision.CURRENT_SESSION
    }
    val targetIdentity = browserSiteIdentity(request.targetUrl)
    if (request.isPopup && (!request.hasUserGesture || targetIdentity == null)) {
        return BrowserWindowNavigationDecision.REJECT
    }
    if (
        request.sourceAtConfiguredHome &&
            request.hasUserGesture &&
            targetIdentity != null &&
            !areBrowserUrlsSameSite(request.sourceUrl, request.targetUrl)
    ) {
        return BrowserWindowNavigationDecision.CREATE_CHILD_SESSION
    }
    return BrowserWindowNavigationDecision.CURRENT_SESSION
}

internal enum class BrowserSessionRootBackAction {
    CLOSE_AND_ACTIVATE_OPENER_HOME,
    NAVIGATE_TO_CONFIGURED_HOME,
}

internal fun resolveBrowserSessionRootBackAction(
    creationReason: BrowserWindowCreationReason,
    openerHomeSessionExists: Boolean,
    openerProfileMatches: Boolean,
    openerStillAtConfiguredHome: Boolean,
): BrowserSessionRootBackAction =
    if (
        creationReason == BrowserWindowCreationReason.HOME_CROSS_SITE_USER_NAVIGATION &&
            openerHomeSessionExists &&
            openerProfileMatches &&
            openerStillAtConfiguredHome
    ) {
        BrowserSessionRootBackAction.CLOSE_AND_ACTIVATE_OPENER_HOME
    } else {
        BrowserSessionRootBackAction.NAVIGATE_TO_CONFIGURED_HOME
    }
