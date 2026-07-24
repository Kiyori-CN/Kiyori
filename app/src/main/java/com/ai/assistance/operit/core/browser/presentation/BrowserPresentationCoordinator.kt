package com.ai.assistance.operit.core.browser.presentation

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionWebViewHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.createSessionTabOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.ensureBrowserPresentationOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.ensureSessionAttachedOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.getSession
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.openUrlOnMain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.refreshSessionUiOnMain

/**
 * Coordinates the one browser presentation lease shared by Kiyori Browser Home and the
 * existing overlay. Keeping this outside the UI prevents a second session registry from
 * appearing when Browser Home is composed.
 */
internal class BrowserPresentationCoordinator private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val tools = ToolGetter.getBrowserSessionTools(appContext)

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

    fun releaseAppPresentation(
        presentation: WebSessionBrowserHost,
        webViewHost: WebSessionWebViewHost,
    ) {
        tools.runOnMainSync<Unit> {
            presentation.releaseAppPresentation(webViewHost)
        }
    }

    fun openUrl(url: String) {
        tools.runOnMainSync<Unit> {
            tools.ensureBrowserPresentationOnMain(appContext)
            tools.openUrlOnMain(appContext, url)
            tools.refreshSessionUiOnMain()
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
