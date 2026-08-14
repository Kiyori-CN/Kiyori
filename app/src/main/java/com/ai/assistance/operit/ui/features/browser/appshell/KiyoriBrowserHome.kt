package com.ai.assistance.operit.ui.features.browser.appshell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.core.browser.presentation.BrowserAppPresentationReleaseMode
import com.ai.assistance.operit.core.browser.presentation.BrowserAppPresentationLease
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionWebViewHost
import com.kiyori.capability.browser.presentation.KiyoriBrowserExitPresentation

internal enum class KiyoriBrowserHomeBackSource {
    TOP_BAR,
    SYSTEM_BACK,
}

internal enum class KiyoriBrowserHomeBackAction {
    EXIT_TO_ENTRY_PAGE,
    HANDLE_BROWSER_BACK_STACK,
}

internal fun resolveKiyoriBrowserHomeBackAction(
    source: KiyoriBrowserHomeBackSource,
): KiyoriBrowserHomeBackAction =
    when (source) {
        KiyoriBrowserHomeBackSource.TOP_BAR ->
            KiyoriBrowserHomeBackAction.EXIT_TO_ENTRY_PAGE
        KiyoriBrowserHomeBackSource.SYSTEM_BACK ->
            KiyoriBrowserHomeBackAction.HANDLE_BROWSER_BACK_STACK
    }

internal fun resolveBrowserAppPresentationReleaseMode(
    exitPresentation: KiyoriBrowserExitPresentation,
): BrowserAppPresentationReleaseMode =
    when (exitPresentation) {
        KiyoriBrowserExitPresentation.CLOSE -> BrowserAppPresentationReleaseMode.DESTROY
        KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR ->
            BrowserAppPresentationReleaseMode.MINIMIZE
    }

internal fun shouldDispatchPendingForegroundBrowserUrl(
    hasPresentationLease: Boolean,
    pendingForegroundUrl: String?,
): Boolean = hasPresentationLease && !pendingForegroundUrl.isNullOrBlank()

@Composable
internal fun KiyoriBrowserHome(
    onExitBrowser: () -> Unit,
    onOpenAiDialogue: () -> Unit,
    onOpenSettingsHome: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onCloseBrowser: () -> Unit,
    pendingForegroundUrl: String?,
    onPendingForegroundUrlHandled: (String) -> Unit,
    exitPresentation: KiyoriBrowserExitPresentation,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coordinator = remember(context) {
        BrowserPresentationCoordinator.getInstance(context.applicationContext)
    }
    val webViewHost = remember { WebSessionWebViewHost() }
    var presentationLease by remember { mutableStateOf<BrowserAppPresentationLease?>(null) }

    DisposableEffect(coordinator, webViewHost) {
        val acquired = coordinator.acquireAppPresentation(webViewHost)
        presentationLease = acquired
        onDispose {
            // A Compose rebuild is not a user request to show the minimized indicator.
            acquired.release(BrowserAppPresentationReleaseMode.DETACH)
        }
    }

    LaunchedEffect(presentationLease, pendingForegroundUrl) {
        val targetUrl = pendingForegroundUrl ?: return@LaunchedEffect
        // 负一屏 URL 必须等 Browser Home 获得前台 presentation lease 后再导航。
        // 若在 Shell 切页前调用 openUrl，协调器会先创建后台锚点并显示悬浮入口。
        if (
            shouldDispatchPendingForegroundBrowserUrl(
                hasPresentationLease = presentationLease != null,
                pendingForegroundUrl = targetUrl,
            )
        ) {
            coordinator.openUrl(targetUrl)
            onPendingForegroundUrlHandled(targetUrl)
        }
    }

    fun finishPresentation(
        releaseMode: BrowserAppPresentationReleaseMode,
        onFinished: () -> Unit,
    ) {
        val lease = presentationLease ?: return
        if (lease.release(releaseMode)) {
            onFinished()
        }
    }

    fun finishBrowserHome() {
        finishPresentation(
            resolveBrowserAppPresentationReleaseMode(exitPresentation),
            onExitBrowser,
        )
    }

    fun handleBrowserBack(source: KiyoriBrowserHomeBackSource) {
        when (resolveKiyoriBrowserHomeBackAction(source)) {
            KiyoriBrowserHomeBackAction.EXIT_TO_ENTRY_PAGE -> {
                // 顶栏返回属于 App Shell 层级；复用网页 Back 会先消费 WebView 历史，无法回到入口页。
                finishBrowserHome()
            }
            KiyoriBrowserHomeBackAction.HANDLE_BROWSER_BACK_STACK -> {
                val handledByBrowser = presentationLease?.presentation?.handleBack() == true
                if (!handledByBrowser) {
                    finishBrowserHome()
                }
            }
        }
    }

    BackHandler(enabled = presentationLease != null) {
        handleBrowserBack(KiyoriBrowserHomeBackSource.SYSTEM_BACK)
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        presentationLease?.presentation?.BrowserContent(
            webViewHost = webViewHost,
            onTopBarBack = {
                handleBrowserBack(KiyoriBrowserHomeBackSource.TOP_BAR)
            },
            onOpenAiDialogue = {
                finishPresentation(
                    BrowserAppPresentationReleaseMode.MINIMIZE,
                    onOpenAiDialogue,
                )
            },
            onOpenSettingsHome = onOpenSettingsHome,
            onOpenDownloadSettings = onOpenDownloadSettings,
            onExitBrowser = {
                finishPresentation(
                    BrowserAppPresentationReleaseMode.DESTROY,
                    onCloseBrowser,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
