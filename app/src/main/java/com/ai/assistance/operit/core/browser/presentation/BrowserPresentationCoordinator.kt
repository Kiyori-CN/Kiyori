package com.ai.assistance.operit.core.browser.presentation

import android.content.Context
import android.provider.Settings
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSettings
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserCredentialVaultSnapshot
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserSavedCredential
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionIncognitoAvailability
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSheetRoute
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserPluginRoute
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserWindowCreationReason
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserHomeMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserSessionSearchRecovery
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserLaunchRestorationPrompt
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserWindowCountState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.prepareBrowserHumanLaunch
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveBrowserHumanLaunch
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.scheduleBrowserRecoverySnapshotWrite
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionWebViewHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.activateSessionOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.applyBrowserDisplaySettingsOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.applyBrowserWebElementLongPressMenuSettingOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.applyWebsitePasswordSavingSettingOnMain
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
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.sessionById
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.showToast
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.destroyBrowserPresentationOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.capability.browser.presentation.KiyoriBrowserSearchSource
import com.kiyori.capability.browser.presentation.KiyoriBrowserWorkspaceRoute
import com.kiyori.platform.lifecycle.KiyoriActivityLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BrowserPresentationReleaseGate {
    private var released = false

    fun runOnce(action: () -> Unit): Boolean {
        if (released) return false
        released = true
        action()
        return true
    }
}

internal const val KIYORI_MAIN_ACTIVITY_CLASS_NAME =
    "com.ai.assistance.operit.ui.main.MainActivity"

internal fun shouldPersistBrowserRecoveryOnActivityStop(
    activityClassName: String,
    isChangingConfigurations: Boolean,
): Boolean =
    activityClassName == KIYORI_MAIN_ACTIVITY_CLASS_NAME &&
        !isChangingConfigurations

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
    val browserSettings: StateFlow<WebSessionBrowserSettings> = tools.browserSettingsStore.state
    val adBlockState: StateFlow<BrowserAdBlockState> = tools.adBlockStore.state
    val browserCredentialVaultState: StateFlow<BrowserCredentialVaultSnapshot> =
        tools.browserCredentialVault.state
    val userscriptState: StateFlow<WebSessionUserscriptUiState> = tools.userscriptManager.uiStore.state
    val browserExtensions get() = tools.extensionRepository.state
    val browserExtensionDiagnostics get() = tools.extensionRuntime.diagnostics

    suspend fun setBrowserExtensionEnabled(id: String, revision: String, enabled: Boolean) =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            if (enabled) require(tools.runOnMainSync { tools.extensionRuntime.supported }) { "UNSUPPORTED_RUNTIME" }
            tools.extensionRepository.setEnabled(id, enabled, revision)
            tools.runOnMainSync { tools.extensionRuntime.reconcile() }
        }

    suspend fun deleteBrowserExtension(id: String, revision: String) =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            tools.extensionRepository.delete(id, revision)
            tools.runOnMainSync { tools.extensionRuntime.reconcile() }
        }

    fun invokeBrowserExtensionAction(id: String) {
        tools.runOnMainSync {
            val session = tools.getActiveSessionOnMain() ?: error("NO_ACTIVE_PAGE")
            tools.extensionRuntime.invokeAction(session.id, id)
        }
    }

    init {
        KiyoriActivityLifecycle.registerActivityStoppedListener { activity ->
            if (
                shouldPersistBrowserRecoveryOnActivityStop(
                    activityClassName = activity.javaClass.name,
                    isChangingConfigurations = activity.isChangingConfigurations,
                )
            ) {
                persistBrowserRecoverySnapshot()
            }
        }
    }

    fun acquireAppPresentation(webViewHost: WebSessionWebViewHost): BrowserAppPresentationLease =
        tools.runOnMainSync {
            val presentation = tools.ensureBrowserPresentationOnMain(appContext)
            presentation.acquireAppPresentation(webViewHost)

            tools.getActiveSessionOnMain()?.let { session ->
                tools.ensureSessionAttachedOnMain(session.id)
                tools.refreshSessionUiOnMain(session.id)
            } ?: tools.refreshSessionUiOnMain()
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
                    creationReason = BrowserWindowCreationReason.OPEN_IN_NEW_WINDOW,
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

    fun activeSessionId(): String? =
        tools.runOnMainSync {
            tools.getActiveSessionOnMain()?.id
        }

    suspend fun prepareHumanBrowserLaunch(): BrowserLaunchRestorationPrompt? =
        tools.prepareBrowserHumanLaunch()

    suspend fun resolveHumanBrowserLaunch(
        snapshotId: String,
        restore: Boolean,
    ) {
        tools.resolveBrowserHumanLaunch(snapshotId, restore)
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
        tools.runOnMainSync<Unit> {
            tools.refreshSessionUiOnMain()
        }
    }

    fun setBrowserHomeMode(mode: BrowserHomeMode) {
        tools.browserSettingsStore.setHomeMode(mode)
        tools.runOnMainSync<Unit> {
            tools.refreshSessionUiOnMain()
        }
    }

    fun setBrowserHomeSettings(mode: BrowserHomeMode, customUrl: String) {
        tools.browserSettingsStore.setHomeSettings(mode = mode, customUrl = customUrl)
        tools.runOnMainSync<Unit> {
            tools.refreshSessionUiOnMain()
        }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        if (!tools.adBlockStore.setEnabled(enabled)) {
            tools.showToast("广告拦截规则仍在初始化，请稍后再试")
        }
    }

    fun setReturnWithoutReloadEnabled(enabled: Boolean) {
        tools.browserSettingsStore.setReturnWithoutReloadEnabled(enabled)
    }

    fun setForcePageZoomEnabled(enabled: Boolean) {
        tools.runOnMainSync<Unit> {
            tools.browserSettingsStore.setForcePageZoomEnabled(enabled)
            tools.applyBrowserDisplaySettingsOnMain()
        }
    }

    fun setWebTextZoomPercent(percent: Int) {
        tools.runOnMainSync<Unit> {
            tools.browserSettingsStore.setWebTextZoomPercent(percent)
            tools.applyBrowserDisplaySettingsOnMain()
        }
    }

    fun setAllowWebPageOpenApp(enabled: Boolean) {
        tools.runOnMainSync<Unit> {
            tools.browserSettingsStore.setAllowWebPageOpenApp(enabled)
        }
    }

    fun setAllowWebPageGeolocation(enabled: Boolean) {
        tools.browserSettingsStore.setAllowWebPageGeolocation(enabled)
    }

    fun setWebsitePasswordSavingEnabled(enabled: Boolean) {
        tools.runOnMainSync<Unit> {
            tools.browserSettingsStore.setWebsitePasswordSavingEnabled(enabled)
            tools.applyWebsitePasswordSavingSettingOnMain()
        }
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

    fun setWebElementLongPressMenuEnabled(enabled: Boolean) {
        tools.runOnMainSync<Unit> {
            tools.browserSettingsStore.setWebElementLongPressMenuEnabled(enabled)
            tools.applyBrowserWebElementLongPressMenuSettingOnMain()
        }
    }

    fun setSwipeHistoryNavigationEnabled(enabled: Boolean) {
        tools.browserSettingsStore.setSwipeHistoryNavigationEnabled(enabled)
    }

    fun setSearchEngineQuickSwitchBarEnabled(enabled: Boolean) {
        tools.browserSettingsStore.setSearchEngineQuickSwitchBarEnabled(enabled)
    }

    fun setRestoreLastSearchResultEnabled(enabled: Boolean) {
        tools.browserSettingsStore.setRestoreLastSearchResultEnabled(enabled)
        tools.scheduleBrowserRecoverySnapshotWrite()
    }

    fun setAskBeforeRestoringPagesEnabled(enabled: Boolean) {
        tools.browserSettingsStore.setAskBeforeRestoringPagesEnabled(enabled)
        tools.scheduleBrowserRecoverySnapshotWrite()
    }

    fun setRetainMultipleWindowsEnabled(enabled: Boolean) {
        tools.browserSettingsStore.setRetainMultipleWindowsEnabled(enabled)
        tools.scheduleBrowserRecoverySnapshotWrite()
    }

    fun persistBrowserRecoverySnapshot() {
        tools.scheduleBrowserRecoverySnapshotWrite()
    }

    suspend fun browserCredential(id: String): BrowserSavedCredential? =
        withContext(Dispatchers.IO) {
            tools.browserCredentialVault.credential(id)
        }

    suspend fun updateBrowserCredential(
        id: String,
        username: String,
        password: String,
    ) {
        withContext(Dispatchers.IO) {
            tools.browserCredentialVault.updateCredential(
                id = id,
                username = username,
                password = password,
            )
        }
    }

    suspend fun deleteBrowserCredential(id: String): Boolean =
        withContext(Dispatchers.IO) {
            tools.browserCredentialVault.deleteCredential(id)
        }

    fun setUserScriptsAllowed(enabled: Boolean) {
        tools.userscriptManager.setUserScriptsAllowed(enabled)
    }

    fun openPluginCenter() {
        tools.runOnMainSync<Unit> {
            tools.openPluginCenterOnMain()
        }
    }

    fun openBrowserWorkspace(
        route: KiyoriBrowserWorkspaceRoute,
        onClosed: () -> Unit,
    ) {
        val pluginRoute =
            when (route) {
                KiyoriBrowserWorkspaceRoute.Overview ->
                    WebSessionBrowserPluginRoute.Overview
                KiyoriBrowserWorkspaceRoute.Diagnostics ->
                    WebSessionBrowserPluginRoute.Userscripts(
                        WebSessionUserscriptWorkbenchTab.CURRENT_PAGE,
                    )
                is KiyoriBrowserWorkspaceRoute.UserscriptDetail ->
                    WebSessionBrowserPluginRoute.UserscriptDetail(route.scriptId)
            }
        tools.runOnMainSync<Unit> {
            tools.browserWorkspaceClosedListener = onClosed
            val host = tools.ensureBrowserPresentationOnMain(appContext)
            host.showPluginRoute(pluginRoute)
            tools.refreshSessionUiOnMain()
        }
    }

    fun clearBrowserWorkspaceClosedListener() {
        tools.runOnMainSync<Unit> {
            tools.browserWorkspaceClosedListener = null
        }
    }

    fun restoreBrowserWorkspaceSourceSession(sessionId: String?) {
        if (sessionId == null) {
            return
        }
        tools.runOnMainSync<Unit> {
            check(tools.sessionById(sessionId) != null) {
                "Browser workspace source session is no longer registered: $sessionId"
            }
            tools.activateSessionOnMain(sessionId)
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

    fun openSearchResultInNewSession(
        url: String,
        profile: WebSessionProfile,
        query: String,
        engineId: String,
        source: KiyoriBrowserSearchSource,
    ): String? =
        tools.runOnMainSync {
            val searchRecovery =
                BrowserSessionSearchRecovery(
                    query = query,
                    engineId = engineId,
                    source = source,
                    requestedUrl = url,
                    resolvedResultUrl = url,
                    submittedAt = System.currentTimeMillis(),
                )
            try {
                tools.ensureBrowserPresentationOnMain(appContext)
                val session =
                    tools.createSessionTabOnMain(
                        appContext = appContext,
                        initialUrl = url,
                        profile = profile,
                        creationReason = BrowserWindowCreationReason.SOFTWARE_HOME_SEARCH,
                    )
                session.lastSearchRecovery = searchRecovery
                session.searchRecoveryPending = true
                tools.scheduleBrowserRecoverySnapshotWrite()
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

        val browserWindowCount: StateFlow<Int> = BrowserWindowCountState.windowCount

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
