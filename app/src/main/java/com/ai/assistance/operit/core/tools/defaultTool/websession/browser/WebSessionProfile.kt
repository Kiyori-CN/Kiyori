package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ai.assistance.operit.util.AppLogger
import java.util.Locale
import kotlin.math.abs

internal enum class WebSessionProfile(val wireName: String) {
    NORMAL("normal"),
    INCOGNITO("incognito"),
    ;

    companion object {
        fun fromWireName(value: String?): WebSessionProfile? =
            entries.firstOrNull { profile ->
                profile.wireName == value?.trim()?.lowercase(Locale.ROOT)
            }
    }
}

internal enum class WebSessionIncognitoAvailability {
    AVAILABLE,
    UNSUPPORTED,
    PROFILE_RESET_FAILED,
    ;

    val isAvailable: Boolean
        get() = this == AVAILABLE
}

internal enum class WebSessionProfileRejection {
    UNKNOWN_PROFILE,
    INCOGNITO_UNAVAILABLE,
}

internal sealed interface WebSessionProfileResolution {
    data class Accepted(val profile: WebSessionProfile) : WebSessionProfileResolution

    data class Rejected(val reason: WebSessionProfileRejection) : WebSessionProfileResolution
}

internal fun resolveSessionProfileRequest(
    requestedWireName: String?,
    defaultProfile: WebSessionProfile,
    incognitoAvailability: WebSessionIncognitoAvailability,
): WebSessionProfileResolution {
    val profile =
        if (requestedWireName == null) {
            defaultProfile
        } else {
            WebSessionProfile.fromWireName(requestedWireName)
                ?: return WebSessionProfileResolution.Rejected(
                    WebSessionProfileRejection.UNKNOWN_PROFILE
                )
        }
    if (
        profile == WebSessionProfile.INCOGNITO &&
            !incognitoAvailability.isAvailable
    ) {
        return WebSessionProfileResolution.Rejected(
            WebSessionProfileRejection.INCOGNITO_UNAVAILABLE
        )
    }
    return WebSessionProfileResolution.Accepted(profile)
}

/**
 * Owns the AndroidX WebKit profile lifecycle for the shared Browser Runtime.
 *
 * The incognito profile is process-session state: stale Kiyori profiles are deleted before the
 * first WebView is created, all live incognito WebViews share one profile, and the profile is
 * deleted only after the final incognito WebView has been destroyed.
 */
internal class WebSessionProfileManager {
    companion object {
        private const val TAG = "WebSessionProfile"
        private const val INCOGNITO_PROFILE_PREFIX = "kiyori-incognito-"
        private const val INCOGNITO_PROFILE_NAME = "${INCOGNITO_PROFILE_PREFIX}session-v1"
    }

    @Volatile
    var incognitoAvailability: WebSessionIncognitoAvailability =
        WebSessionIncognitoAvailability.UNSUPPORTED
        private set

    fun initialize() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            incognitoAvailability = WebSessionIncognitoAvailability.UNSUPPORTED
            return
        }

        val store = ProfileStore.getInstance()
        val staleProfiles =
            store.allProfileNames.filter { profileName ->
                profileName.startsWith(INCOGNITO_PROFILE_PREFIX)
            }
        val resetSucceeded =
            try {
                staleProfiles.all(store::deleteProfile)
            } catch (error: Exception) {
                AppLogger.e(TAG, "Failed to inspect or delete stale Kiyori incognito profiles", error)
                false
            }
        incognitoAvailability =
            if (resetSucceeded) {
                WebSessionIncognitoAvailability.AVAILABLE
            } else {
                AppLogger.e(TAG, "Failed to delete a stale Kiyori incognito WebView profile")
                WebSessionIncognitoAvailability.PROFILE_RESET_FAILED
            }
    }

    fun bindProfileBeforeConfiguration(
        webView: WebView,
        profile: WebSessionProfile,
    ) {
        if (profile == WebSessionProfile.NORMAL) {
            return
        }
        requireProfileAvailable(profile)
        try {
            ProfileStore.getInstance().getOrCreateProfile(INCOGNITO_PROFILE_NAME)
            WebViewCompat.setProfile(webView, INCOGNITO_PROFILE_NAME)
        } catch (error: Exception) {
            incognitoAvailability = WebSessionIncognitoAvailability.PROFILE_RESET_FAILED
            AppLogger.e(TAG, "Failed to bind the Kiyori incognito WebView profile", error)
            throw IllegalStateException("Unable to bind incognito WebView profile", error)
        }
    }

    fun requireProfileAvailable(profile: WebSessionProfile) {
        if (profile == WebSessionProfile.INCOGNITO) {
            check(incognitoAvailability.isAvailable) {
                "Incognito WebView profile is unavailable: $incognitoAvailability"
            }
        }
    }

    fun cookieManagerFor(
        webView: WebView,
        profile: WebSessionProfile,
    ): CookieManager =
        when (profile) {
            WebSessionProfile.NORMAL -> CookieManager.getInstance()
            WebSessionProfile.INCOGNITO -> {
                requireProfileAvailable(profile)
                WebViewCompat.getProfile(webView).cookieManager
            }
        }

    fun deleteIncognitoProfileAfterLastWebView(): Boolean {
        if (!incognitoAvailability.isAvailable) {
            return false
        }
        val store = ProfileStore.getInstance()
        if (INCOGNITO_PROFILE_NAME !in store.allProfileNames) {
            return true
        }
        val deleted =
            try {
                store.deleteProfile(INCOGNITO_PROFILE_NAME)
            } catch (error: Exception) {
                AppLogger.e(TAG, "Failed to delete the final Kiyori incognito WebView profile", error)
                false
            }
        if (!deleted) {
            incognitoAvailability = WebSessionIncognitoAvailability.PROFILE_RESET_FAILED
            AppLogger.e(TAG, "Failed to delete the final Kiyori incognito WebView profile")
        }
        return deleted
    }
}

internal data class BrowserSessionProfileEntry(
    val sessionId: String,
    val profile: WebSessionProfile,
)

internal fun resolveSessionAfterClose(
    orderedBeforeClose: List<BrowserSessionProfileEntry>,
    closedSessionId: String,
    remainingSessionIds: Set<String>,
    previouslyActiveSessionId: String?,
    wasActive: Boolean,
): String? {
    if (!wasActive) {
        return previouslyActiveSessionId?.takeIf(remainingSessionIds::contains)
    }

    val closedIndex = orderedBeforeClose.indexOfFirst { entry -> entry.sessionId == closedSessionId }
    if (closedIndex < 0) {
        return null
    }
    val closedProfile = orderedBeforeClose[closedIndex].profile
    val candidates =
        orderedBeforeClose
            .mapIndexedNotNull { index, entry ->
                entry.takeIf { remainingSessionIds.contains(it.sessionId) }
                    ?.let { candidate ->
                        Triple(
                            candidate,
                            abs(index - closedIndex),
                            if (index > closedIndex) 0 else 1,
                        )
                    }
            }
            .sortedWith(compareBy<Triple<BrowserSessionProfileEntry, Int, Int>> { it.second }.thenBy { it.third })

    return candidates.firstOrNull { (entry) -> entry.profile == closedProfile }?.first?.sessionId
        ?: candidates.firstOrNull()?.first?.sessionId
}

internal fun resolveSelectedProfileAfterRemoval(
    selectedProfile: WebSessionProfile,
    remainingProfiles: List<WebSessionProfile>,
): WebSessionProfile {
    if (selectedProfile in remainingProfiles || remainingProfiles.isEmpty()) {
        return selectedProfile
    }
    return remainingProfiles.first()
}
