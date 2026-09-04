package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch

private const val BROWSER_RECOVERY_TAG = "BrowserSessionRecovery"

internal fun StandardBrowserSessionTools.scheduleBrowserRecoverySnapshotWrite() {
    browserRecoveryRevision.incrementAndGet()
    if (!browserRecoveryWriterRunning.compareAndSet(false, true)) {
        return
    }
    ioScope.launch {
        var completedRevision = -1L
        try {
            while (true) {
                val targetRevision = browserRecoveryRevision.get()
                val settings = browserSettingsStore.current.toBrowserRecoverySettings()
                val snapshot =
                    runOnMainSync {
                        buildBrowserRecoverySnapshotOnMain(settings)
                    }
                if (snapshot == null) {
                    browserSessionRecoveryStore.clear()
                } else {
                    browserSessionRecoveryStore.writeSnapshot(snapshot)
                }
                completedRevision = targetRevision
                if (browserRecoveryRevision.get() == targetRevision) {
                    break
                }
            }
        } catch (error: Exception) {
            AppLogger.e(
                BROWSER_RECOVERY_TAG,
                "Failed to persist the normal browser-window projection",
                error,
            )
        } finally {
            browserRecoveryWriterRunning.set(false)
            if (browserRecoveryRevision.get() != completedRevision) {
                scheduleBrowserRecoverySnapshotWrite()
            }
        }
    }
}

private fun StandardBrowserSessionTools.buildBrowserRecoverySnapshotOnMain(
    settings: BrowserRecoverySettings,
): BrowserSessionRecoverySnapshot? {
    val sessions =
        orderedSessionIds()
            .mapNotNull(::sessionById)
    val sessionIds = sessions.mapTo(mutableSetOf(), BrowserToolSession::id)
    return buildBrowserSessionRecoverySnapshot(
        settings = settings,
        snapshotId = browserRecoverySnapshotId,
        capturedAt = System.currentTimeMillis(),
        activeWindowId = StandardBrowserSessionTools.activeSessionId,
        candidates =
            sessions.map { session ->
                BrowserSessionRecoveryCandidate(
                    profile = session.profile,
                    window =
                        BrowserSessionRecoveryWindow(
                            windowId = session.id,
                            currentUrl = session.currentUrl,
                            title = session.pageTitle.take(MAX_BROWSER_RECOVERY_TITLE_LENGTH),
                            createdAt = session.createdAt,
                            lastActivatedAt = session.lastActivatedAt,
                            creationReason = session.creationReason,
                            openerHomeWindowId =
                                session.openerHomeSessionId?.takeIf(sessionIds::contains),
                            lastSearch = session.lastSearchRecovery,
                        ),
                )
            },
    )
}

internal suspend fun StandardBrowserSessionTools.prepareBrowserHumanLaunch():
    BrowserLaunchRestorationPrompt? {
    val hasLiveSessions = runOnMainSync { orderedSessionIds().isNotEmpty() }
    if (hasLiveSessions) {
        browserLaunchRestorationState = BrowserLaunchRestorationState.Ready
        scheduleBrowserRecoverySnapshotWrite()
        return null
    }
    when (val state = browserLaunchRestorationState) {
        BrowserLaunchRestorationState.Ready -> {
            runOnMainSync { createConfiguredHomeSessionOnMain() }
            return null
        }
        is BrowserLaunchRestorationState.WaitingForDecision ->
            return state.plan.toPrompt(state.snapshotId)
        is BrowserLaunchRestorationState.Applying -> return null
        BrowserLaunchRestorationState.Uninitialized -> Unit
    }

    val settings = browserSettingsStore.current.toBrowserRecoverySettings()
    val snapshot =
        if (shouldPersistBrowserRecovery(settings)) {
            try {
                browserSessionRecoveryStore.readSnapshot()
            } catch (error: Exception) {
                AppLogger.e(
                    BROWSER_RECOVERY_TAG,
                    "Rejected an invalid browser recovery snapshot",
                    error,
                )
                browserSessionRecoveryStore.clear()
                null
            }
        } else {
            browserSessionRecoveryStore.clear()
            null
        }
    val plan = resolveBrowserLaunchRestorationPlan(settings, snapshot)
    val snapshotId = snapshot?.snapshotId ?: browserRecoverySnapshotId
    val requiresDecision =
        when (plan) {
            is BrowserLaunchRestorationPlan.RestoreAllWindows ->
                plan.requiresConfirmation
            is BrowserLaunchRestorationPlan.RestoreSearchResult ->
                plan.requiresConfirmation
            is BrowserLaunchRestorationPlan.RestoreActivePage -> true
            BrowserLaunchRestorationPlan.OpenConfiguredHome -> false
        }
    if (requiresDecision) {
        browserLaunchRestorationState =
            BrowserLaunchRestorationState.WaitingForDecision(plan, snapshotId)
        return plan.toPrompt(snapshotId)
    }
    browserLaunchRestorationState =
        BrowserLaunchRestorationState.Applying(snapshotId)
    runOnMainSync {
        applyBrowserLaunchRestorationPlanOnMain(plan)
    }
    browserLaunchRestorationState = BrowserLaunchRestorationState.Ready
    scheduleBrowserRecoverySnapshotWrite()
    return null
}

internal suspend fun StandardBrowserSessionTools.resolveBrowserHumanLaunch(
    snapshotId: String,
    restore: Boolean,
) {
    val waiting =
        browserLaunchRestorationState as? BrowserLaunchRestorationState.WaitingForDecision
            ?: return
    if (waiting.snapshotId != snapshotId) {
        return
    }
    browserLaunchRestorationState =
        BrowserLaunchRestorationState.Applying(snapshotId)
    if (restore) {
        runOnMainSync {
            applyBrowserLaunchRestorationPlanOnMain(waiting.plan)
        }
    } else {
        browserSessionRecoveryStore.clear()
        runOnMainSync {
            createConfiguredHomeSessionOnMain()
        }
    }
    browserRecoverySnapshotId = java.util.UUID.randomUUID().toString()
    browserLaunchRestorationState = BrowserLaunchRestorationState.Ready
    scheduleBrowserRecoverySnapshotWrite()
}

private fun StandardBrowserSessionTools.applyBrowserLaunchRestorationPlanOnMain(
    plan: BrowserLaunchRestorationPlan,
) {
    check(orderedSessionIds().isEmpty()) {
        "Browser recovery cannot be applied after live sessions exist"
    }
    when (plan) {
        BrowserLaunchRestorationPlan.OpenConfiguredHome ->
            createConfiguredHomeSessionOnMain()
        is BrowserLaunchRestorationPlan.RestoreSearchResult ->
            restoreBrowserWindowOnMain(
                window = plan.window,
                targetUrl = plan.search.resolvedResultUrl,
                lastSearch = plan.search,
            )
        is BrowserLaunchRestorationPlan.RestoreActivePage ->
            restoreBrowserWindowOnMain(
                window = plan.window,
                targetUrl = plan.window.currentUrl,
                lastSearch = plan.window.lastSearch,
            )
        is BrowserLaunchRestorationPlan.RestoreAllWindows -> {
            plan.snapshot.windows.forEach { window ->
                restoreBrowserWindowOnMain(
                    window = window,
                    targetUrl = window.currentUrl,
                    lastSearch = window.lastSearch,
                )
            }
            activateSessionOnMain(plan.snapshot.activeWindowId)
        }
    }
}

private fun StandardBrowserSessionTools.restoreBrowserWindowOnMain(
    window: BrowserSessionRecoveryWindow,
    targetUrl: String,
    lastSearch: BrowserSessionSearchRecovery?,
) {
    val session =
        createSessionTabOnMain(
            appContext = context.applicationContext,
            initialUrl = targetUrl,
            sessionId = window.windowId,
            profile = WebSessionProfile.NORMAL,
            createdAt = window.createdAt,
            creationReason =
                if (
                    window.creationReason ==
                    BrowserWindowCreationReason.HOME_CROSS_SITE_USER_NAVIGATION
                ) {
                    window.creationReason
                } else {
                    BrowserWindowCreationReason.RESTORED_NORMAL_WINDOW
                },
            openerHomeSessionId = window.openerHomeWindowId,
        )
    session.pageTitle = window.title
    session.lastActivatedAt = window.lastActivatedAt
    session.lastSearchRecovery = lastSearch
}

private fun StandardBrowserSessionTools.createConfiguredHomeSessionOnMain() {
    if (orderedSessionIds().isNotEmpty()) {
        return
    }
    createSessionTabOnMain(
        appContext = context.applicationContext,
        initialUrl =
            browserHomeSeedUrl(
                mode = browserSettingsStore.current.homeMode,
                customHomeUrl = browserSettingsStore.current.customHomeUrl,
            ),
        profile = WebSessionProfile.NORMAL,
        creationReason = BrowserWindowCreationReason.MANUAL_NEW_WINDOW,
    ).also {
        if (browserSettingsStore.current.homeMode == BrowserHomeMode.NATIVE) {
            browserHost?.showNativeHome(canReturnToPage = false)
        }
    }
}

private fun WebSessionBrowserSettings.toBrowserRecoverySettings(): BrowserRecoverySettings =
    BrowserRecoverySettings(
        restoreLastSearchResultEnabled = restoreLastSearchResultEnabled,
        askBeforeRestoringPagesEnabled = askBeforeRestoringPagesEnabled,
        retainMultipleWindowsEnabled = retainMultipleWindowsEnabled,
    )

private fun BrowserLaunchRestorationPlan.toPrompt(
    snapshotId: String,
): BrowserLaunchRestorationPrompt =
    when (this) {
        is BrowserLaunchRestorationPlan.RestoreAllWindows ->
            BrowserLaunchRestorationPrompt(
                snapshotId = snapshotId,
                title = "恢复上次页面？",
                summary = "上次有 ${snapshot.windows.size} 个普通窗口未关闭",
            )
        is BrowserLaunchRestorationPlan.RestoreSearchResult ->
            BrowserLaunchRestorationPrompt(
                snapshotId = snapshotId,
                title = "恢复上次页面？",
                summary =
                    window.title.ifBlank {
                        browserSiteIdentity(search.resolvedResultUrl)?.key.orEmpty()
                    },
            )
        is BrowserLaunchRestorationPlan.RestoreActivePage ->
            BrowserLaunchRestorationPrompt(
                snapshotId = snapshotId,
                title = "恢复上次页面？",
                summary =
                    window.title.ifBlank {
                        browserSiteIdentity(window.currentUrl)?.key.orEmpty()
                    },
            )
        BrowserLaunchRestorationPlan.OpenConfiguredHome ->
            error("Configured-home launch does not require a restoration prompt")
    }
