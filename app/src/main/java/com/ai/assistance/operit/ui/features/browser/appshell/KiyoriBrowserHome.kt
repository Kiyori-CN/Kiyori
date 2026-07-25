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
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionWebViewHost

@Composable
internal fun KiyoriBrowserHome(
    onExitBrowser: () -> Unit,
    onOpenAiDialogue: () -> Unit,
    onCloseBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coordinator = remember(context) {
        BrowserPresentationCoordinator.getInstance(context.applicationContext)
    }
    val webViewHost = remember { WebSessionWebViewHost() }
    var presentation by remember { mutableStateOf<WebSessionBrowserHost?>(null) }

    DisposableEffect(coordinator, webViewHost) {
        val acquired = coordinator.acquireAppPresentation(webViewHost)
        presentation = acquired
        onDispose {
            coordinator.releaseAppPresentation(acquired, webViewHost)
        }
    }

    fun releaseAppPresentation() {
        presentation?.let { acquired ->
            coordinator.releaseAppPresentation(acquired, webViewHost)
        }
    }

    BackHandler(enabled = presentation != null) {
        val handledByBrowser = presentation?.handleBack() == true
        if (!handledByBrowser) {
            releaseAppPresentation()
            onExitBrowser()
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        presentation?.BrowserContent(
            webViewHost = webViewHost,
            onTopBarBack = {
                releaseAppPresentation()
                onExitBrowser()
            },
            onOpenAiDialogue = {
                releaseAppPresentation()
                onOpenAiDialogue()
            },
            onExitBrowser = {
                presentation?.let { acquired ->
                    coordinator.releaseAppPresentationAndDestroy(acquired, webViewHost)
                }
                onCloseBrowser()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
