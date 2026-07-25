package com.ai.assistance.operit.core.browser.presentation

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionIncognitoAvailability
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSheetRoute
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionWebViewHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.createSessionTabOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.ensureBrowserPresentationOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.ensureOverlayOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.ensureSessionAttachedOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.getSession
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.openUrlOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.refreshSessionUiOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.setDesktopModeEnabled
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.showToast
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.destroyBrowserPresentationOnMain
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinates the one browser presentation lease shared by Kiyori Browser Home and the
 * existing overlay. Keeping this outside the UI prevents a second session registry from
 * appearing when Browser Home is composed.
 */
internal class BrowserPresentationCoordinator private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val tools = ToolGetter.getBrowserSessionTools(appContext)
    val browserWindowCount: StateFlow<Int> = tools.browserWindowCount

    fun acquireAppPresentation(webViewHost: WebSessionWebViewHost): WebSessionBrowserHost =
        tools.runOnMainSync {
            val presentation = tools.ensureBrowserPresentationOnMain(appContext)
            presentation.acquireAppPresentation(webViewHost)

            val session = tools.getSession(null)
                ?: tools.createSessionTabOnMain(appContext, initialUrl = "about:blank")
            tools.ensureSessionAttachedOnMain(session.id)
            tools.refreshSessionUiOnMain(session.id)
            presentation
        }

    fun prepareBrowserForAiHome() {
        tools.runOnMainSync<Unit> {
            tools.ensureOverlayOnMain(appContext, initialExpanded = false)
            val session =
                tools.getSession(null)
                    ?: tools.createSessionTabOnMain(appContext, initialUrl = "about:blank")
            tools.ensureSessionAttachedOnMain(session.id)
            tools.refreshSessionUiOnMain(session.id)
        }
    }

    fun openWindowOverview() {
        tools.runOnMainSync<Unit> {
            val presentation = tools.ensureBrowserPresentationOnMain(appContext)
            val session =
                tools.getSession(null)
                    ?: tools.createSessionTabOnMain(appContext, initialUrl = "about:blank")
            tools.ensureSessionAttachedOnMain(session.id)
            tools.refreshSessionUiOnMain(session.id)
            presentation.showSheet(WebSessionBrowserSheetRoute.TABS)
        }
    }

    fun releaseAppPresentation(
        presentation: WebSessionBrowserHost,
        webViewHost: WebSessionWebViewHost,
    ) {
        tools.runOnMainSync<Unit> {
            presentation.releaseAppPresentation(webViewHost)
        }
    }

    fun releaseAppPresentationAndDestroy(
        presentation: WebSessionBrowserHost,
        webViewHost: WebSessionWebViewHost,
    ) {
        tools.runOnMainSync<Unit> {
            presentation.releaseAppPresentation(webViewHost)
            tools.destroyBrowserPresentationOnMain()
        }
    }

    fun openUrl(url: String) {
        tools.runOnMainSync<Unit> {
            tools.ensureBrowserPresentationOnMain(appContext)
            tools.openUrlOnMain(appContext, url)
            tools.refreshSessionUiOnMain()
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

    fun browserSettingsState(): BrowserRuntimeSettingsState =
        tools.runOnMainSync {
            BrowserRuntimeSettingsState(
                defaultProfile = tools.defaultSessionProfile,
                incognitoAvailability = tools.profileManager.incognitoAvailability,
                isDesktopMode = StandardBrowserSessionTools.desktopModeEnabled,
            )
        }

    fun setDesktopMode(enabled: Boolean) {
        tools.setDesktopModeEnabled(enabled)
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

internal data class BrowserRuntimeSettingsState(
    val defaultProfile: WebSessionProfile,
    val incognitoAvailability: WebSessionIncognitoAvailability,
    val isDesktopMode: Boolean,
)
