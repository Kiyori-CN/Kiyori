package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ai.assistance.operit.util.AppLogger
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

internal const val KIYORI_INCOGNITO_PROFILE_PREFIX = "kiyori-incognito-"

internal enum class WebSessionProfile(
    val wireName: String,
    val shouldPersistBrowserHistory: Boolean,
) {
    NORMAL("normal", shouldPersistBrowserHistory = true),
    INCOGNITO("incognito", shouldPersistBrowserHistory = false),
    ;

    companion object {
        fun fromWireName(value: String?): WebSessionProfile? =
            entries.firstOrNull { profile ->
                profile.wireName == value?.trim()?.lowercase(Locale.ROOT)
            }
    }
}

internal fun isKiyoriIncognitoProfileName(profileName: String): Boolean =
    profileName.startsWith(KIYORI_INCOGNITO_PROFILE_PREFIX)

internal fun deleteStaleKiyoriIncognitoProfiles(
    existingProfileNames: Collection<String>,
    deleteProfile: (String) -> Boolean,
    remainingProfileNames: () -> Collection<String>,
): Boolean {
    existingProfileNames
        .filter(::isKiyoriIncognitoProfileName)
        .forEach { profileName -> deleteProfile(profileName) }
    return remainingProfileNames().none(::isKiyoriIncognitoProfileName)
}

internal class WebSessionIncognitoGeneration(
    private val createGenerationId: () -> String = { UUID.randomUUID().toString() },
) {
    private var activeProfileName: String? = null

    @Synchronized
    fun acquireProfileName(): String =
        activeProfileName
            ?: "$KIYORI_INCOGNITO_PROFILE_PREFIX${createGenerationId()}".also { profileName ->
                activeProfileName = profileName
            }

    @Synchronized
    fun retireProfile(): String? =
        activeProfileName.also {
            activeProfileName = null
        }
}

internal fun WebSessionProfile.opposite(): WebSessionProfile =
    when (this) {
        WebSessionProfile.NORMAL -> WebSessionProfile.INCOGNITO
        WebSessionProfile.INCOGNITO -> WebSessionProfile.NORMAL
    }

internal fun resolveWebSessionProfileToggleTarget(
    currentProfile: WebSessionProfile,
    incognitoAvailability: WebSessionIncognitoAvailability,
): WebSessionProfile? =
    when (currentProfile) {
        WebSessionProfile.NORMAL ->
            WebSessionProfile.INCOGNITO.takeIf { incognitoAvailability.isAvailable }
        WebSessionProfile.INCOGNITO -> WebSessionProfile.NORMAL
    }

internal fun shouldCreateSessionForSearch(
    activeProfile: WebSessionProfile?,
    requestedProfile: WebSessionProfile,
): Boolean = activeProfile != requestedProfile

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
 * A live incognito window group shares one uniquely named generation. AndroidX does not allow a
 * loaded Profile to be deleted in the same process, even after every WebView is destroyed. The
 * final window therefore retires the generation immediately, while the next cold start deletes
 * every retired Kiyori Profile before any of them can be loaded again.
 */
internal class WebSessionProfileManager {
    companion object {
        private const val TAG = "WebSessionProfile"
    }

    private val incognitoGeneration = WebSessionIncognitoGeneration()
    private var activeIncognitoProfile: Profile? = null

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
        val resetSucceeded =
            try {
                deleteStaleKiyoriIncognitoProfiles(
                    existingProfileNames = store.allProfileNames,
                    deleteProfile = store::deleteProfile,
                    remainingProfileNames = { store.allProfileNames },
                )
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
            val profileName = incognitoGeneration.acquireProfileName()
            val androidXProfile =
                activeIncognitoProfile
                    ?: ProfileStore.getInstance().getOrCreateProfile(profileName).also { created ->
                        check(created.name == profileName) {
                            "AndroidX returned an unexpected incognito Profile: ${created.name}"
                        }
                        activeIncognitoProfile = created
                    }
            check(androidXProfile.name == profileName) {
                "Incognito generation changed while WebViews are still active"
            }
            WebViewCompat.setProfile(webView, profileName)
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

    fun retireIncognitoProfileAfterLastWebView() {
        val retiredProfileName = incognitoGeneration.retireProfile()
        val retiredProfile = activeIncognitoProfile
        activeIncognitoProfile = null
        if (retiredProfile == null) {
            return
        }

        // The retired name is never reused. These clears reduce retained data immediately; the
        // next cold start performs the physical Profile deletion allowed by AndroidX.
        runCatching {
            retiredProfile.cookieManager.removeAllCookies {
                runCatching { retiredProfile.cookieManager.flush() }
                    .onFailure { error ->
                        AppLogger.w(TAG, "Failed to flush retired incognito cookies", error)
                    }
            }
        }.onFailure { error ->
            AppLogger.w(TAG, "Failed to clear retired incognito cookies", error)
        }
        runCatching { retiredProfile.webStorage.deleteAllData() }
            .onFailure { error ->
                AppLogger.w(TAG, "Failed to clear retired incognito WebStorage", error)
            }
        runCatching { retiredProfile.geolocationPermissions.clearAll() }
            .onFailure { error ->
                AppLogger.w(TAG, "Failed to clear retired incognito geolocation permissions", error)
            }
        AppLogger.d(TAG, "Retired Kiyori incognito Profile: $retiredProfileName")
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
