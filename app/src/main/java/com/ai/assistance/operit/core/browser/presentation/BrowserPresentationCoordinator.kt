package com.ai.assistance.operit.core.browser.presentation

import android.content.Context
import android.provider.Settings
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSettings
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionIncognitoAvailability
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSheetRoute
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionWebViewHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.activateSessionOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.createSessionTabOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.ensureBrowserPresentationOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.ensureSessionAttachedOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.getSession
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.getActiveSessionOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.openUrlOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.openPluginCenterOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.openUserscriptDetailOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.openUserscriptManagerOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionUserscriptWorkbenchTab
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.refreshSessionUiOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.showToast
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.destroyBrowserPresentationOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.StateFlow

internal class BrowserPresentationReleaseGate {
    private var released = false

    fun runOnce(action: () -> Unit): Boolean {
        if (released) return false
        released = true
        action()
        return true
    }
}

internal enum class BrowserAppPresentationReleaseMode {
    DETACH,
    MINIMIZE,
    DESTROY,
}

internal class BrowserAppPresentationLease(
    val presentation: WebSessionBrowserHost,
    private val onRelease: (BrowserAppPresentationReleaseMode) -> Unit,
) {
    private val releaseGate = BrowserPresentationReleaseGate()

    fun release(mode: BrowserAppPresentationReleaseMode): Boolean =
        releaseGate.runOnce { onRelease(mode) }
}

/**
 * Coordinates the one browser presentation lease shared by Kiyori Browser Home and the
 * 1x1 background anchor. Keeping this outside the UI prevents a second session registry from
 * appearing when Browser Home is composed.
 */
internal class BrowserPresentationCoordinator private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val tools = ToolGetter.getBrowserSessionTools(appContext)
    val browserWindowCount: StateFlow<Int> = tools.browserWindowCount
    val browserSettings: StateFlow<WebSessionBrowserSettings> = tools.browserSettingsStore.state
    val userscriptState: StateFlow<WebSessionUserscriptUiState> = tools.userscriptManager.uiStore.state

    fun acquireAppPresentation(webViewHost: WebSessionWebViewHost): BrowserAppPresentationLease =
        tools.runOnMainSync {
            val presentation = tools.ensureBrowserPresentationOnMain(appContext)
            presentation.acquireAppPresentation(webViewHost)

            val session = tools.getSession(null)
                ?: tools.createSessionTabOnMain(
                    appContext,
                    initialUrl = tools.browserSettingsStore.current.homeUrl,
                )
            tools.ensureSessionAttachedOnMain(session.id)
            tools.refreshSessionUiOnMain(session.id)
            BrowserAppPresentationLease(
                presentation = presentation,
                onRelease = { mode ->
                    releaseAppPresentation(presentation, webViewHost, mode)
                },
            )
        }

    fun openWindowOverview() {
        tools.runOnMainSync<Unit> {
            val presentation = tools.ensureBrowserPresentationOnMain(appContext)
            presentation.showSheet(WebSessionBrowserSheetRoute.TABS)
        }
    }

    private fun releaseAppPresentation(
        presentation: WebSessionBrowserHost,
        webViewHost: WebSessionWebViewHost,
        mode: BrowserAppPresentationReleaseMode,
    ) {
        tools.runOnMainSync<Unit> {
            val keepInBackgroundAnchor =
                mode == BrowserAppPresentationReleaseMode.MINIMIZE &&
                    Settings.canDrawOverlays(appContext)
            presentation.releaseAppPresentation(
                webViewHost = webViewHost,
                keepInBackgroundAnchor = keepInBackgroundAnchor,
            )
            if (mode == BrowserAppPresentationReleaseMode.DESTROY) {
                tools.destroyBrowserPresentationOnMain()
            }
        }
    }

    fun openUrl(url: String) {
        tools.runOnMainSync<Unit> {
            tools.ensureBrowserPresentationOnMain(appContext)
            tools.openUrlOnMain(appContext, url)
            tools.refreshSessionUiOnMain()
        }
    }

    fun openUrlInSiblingSession(url: String, active: Boolean) {
        tools.runOnMainSync<Unit> {
            tools.ensureBrowserPresentationOnMain(appContext)
            val sourceSession = tools.getActiveSessionOnMain()
            val sourceSessionId = sourceSession?.id
            val session =
                tools.createSessionTabOnMain(
                    appContext = appContext,
                    initialUrl = url,
                    profile = sourceSession?.profile ?: tools.defaultSessionProfile,
                )
            if (!active && sourceSessionId != null) {
                tools.activateSessionOnMain(sourceSessionId)
            } else {
                tools.refreshSessionUiOnMain(session.id)
            }
        }
    }

    /**
     * Starts a product-level browsing task in a new shared WebSession.
     *
     * Software Home must never reuse the active tab because the user can be returning to an
     * existing page. Creating through the shared StandardBrowserSessionTools instance also makes
     * the new tab immediately discoverable by the browser_* AI tools.
     */
    fun newSessionProfileState(): BrowserNewSessionProfileState =
        tools.runOnMainSync {
            BrowserNewSessionProfileState(
                defaultProfile = tools.defaultSessionProfile,
                incognitoAvailability = tools.profileManager.incognitoAvailability,
            )
        }

    fun setDefaultSessionProfile(profile: WebSessionProfile): Boolean =
        tools.runOnMainSync {
            if (
                profile == WebSessionProfile.INCOGNITO &&
                    !tools.profileManager.incognitoAvailability.isAvailable
            ) {
                false
            } else {
                tools.defaultSessionProfile = profile
                tools.refreshSessionUiOnMain()
                true
            }
        }

    fun setBrowserHomeUrl(url: String) {
        tools.browserSettingsStore.setHomeUrl(url)
    }

    fun setAllowWebPageOpenApp(enabled: Boolean) {
        tools.runOnMainSync<Unit> {
            tools.browserSettingsStore.setAllowWebPageOpenApp(enabled)
        }
    }

    fun setAllowWebPageGeolocation(enabled: Boolean) {
        tools.browserSettingsStore.setAllowWebPageGeolocation(enabled)
    }

    fun setShowMediaCandidateBadge(enabled: Boolean) {
        tools.browserSettingsStore.setShowMediaCandidateBadge(enabled)
    }

    fun setAutomaticFloatingPlaybackEnabled(enabled: Boolean) {
        tools.browserSettingsStore.setAutomaticFloatingPlaybackEnabled(enabled)
    }

    fun setAutomaticFloatingMinimumDurationMillis(durationMillis: Long) {
        tools.browserSettingsStore.setAutomaticFloatingMinimumDurationMillis(durationMillis)
    }

    fun setUserScriptsAllowed(enabled: Boolean) {
        tools.userscriptManager.setUserScriptsAllowed(enabled)
    }

    fun openPluginCenter() {
        tools.runOnMainSync<Unit> {
            tools.openPluginCenterOnMain()
        }
    }

    fun openUserscriptManager(
        initialTab: WebSessionUserscriptWorkbenchTab = WebSessionUserscriptWorkbenchTab.CURRENT_PAGE,
        initialSearchQuery: String = "",
    ) {
        tools.runOnMainSync<Unit> {
            tools.openUserscriptManagerOnMain(initialTab, initialSearchQuery)
        }
    }

    fun openUserscriptDetail(scriptId: Long) {
        tools.runOnMainSync<Unit> {
            tools.openUserscriptDetailOnMain(scriptId)
        }
    }

    fun openUrlInNewSession(
        url: String,
        profile: WebSessionProfile,
    ): String? =
        tools.runOnMainSync {
            try {
                tools.ensureBrowserPresentationOnMain(appContext)
                val session =
                    tools.createSessionTabOnMain(
                        appContext = appContext,
                        initialUrl = url,
                        profile = profile,
                    )
                tools.refreshSessionUiOnMain(session.id)
                session.id
            } catch (error: IllegalStateException) {
                if (profile != WebSessionProfile.INCOGNITO) {
                    throw error
                }
                AppLogger.e("BrowserPresentation", "Unable to create browser profile session", error)
                tools.showToast(appContext.getString(R.string.web_session_incognito_reset_failed))
                tools.refreshSessionUiOnMain()
                null
            }
        }

    companion object {
        @Volatile private var instance: BrowserPresentationCoordinator? = null

        fun getInstance(context: Context): BrowserPresentationCoordinator =
            instance ?: synchronized(this) {
                instance
                    ?: BrowserPresentationCoordinator(context.applicationContext).also { coordinator ->
                        instance = coordinator
                    }
            }
    }
}

internal data class BrowserNewSessionProfileState(
    val defaultProfile: WebSessionProfile,
    val incognitoAvailability: WebSessionIncognitoAvailability,
)
