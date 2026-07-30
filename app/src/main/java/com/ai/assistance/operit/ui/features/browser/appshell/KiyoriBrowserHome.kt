package com.ai.assistance.operit.ui.features.browser.appshell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.ai.assistance.operit.ui.main.shell.KiyoriBrowserExitPresentation

@Composable
internal fun KiyoriBrowserHome(
    onExitBrowser: () -> Unit,
    onOpenAiDialogue: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onCloseBrowser: () -> Unit,
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

    fun finishPresentation(
        releaseMode: BrowserAppPresentationReleaseMode,
        onFinished: () -> Unit,
    ) {
        val lease = presentationLease ?: return
        if (lease.release(releaseMode)) {
            onFinished()
        }
    }

    fun handleBrowserBack() {
        val handledByBrowser = presentationLease?.presentation?.handleBack() == true
        if (!handledByBrowser) {
            val releaseMode =
                when (exitPresentation) {
                    KiyoriBrowserExitPresentation.CLOSE ->
                        BrowserAppPresentationReleaseMode.DESTROY
                    KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR ->
                        BrowserAppPresentationReleaseMode.MINIMIZE
                }
            finishPresentation(releaseMode, onExitBrowser)
        }
    }

    BackHandler(enabled = presentationLease != null) {
        handleBrowserBack()
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        presentationLease?.presentation?.BrowserContent(
            webViewHost = webViewHost,
            onTopBarBack = ::handleBrowserBack,
            onOpenAiDialogue = {
                finishPresentation(
                    BrowserAppPresentationReleaseMode.MINIMIZE,
                    onOpenAiDialogue,
                )
            },
            onOpenBrowserSettings = onOpenBrowserSettings,
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
