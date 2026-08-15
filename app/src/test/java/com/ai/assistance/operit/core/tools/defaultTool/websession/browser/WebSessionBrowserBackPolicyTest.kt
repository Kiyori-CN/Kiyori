package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class WebSessionBrowserBackPolicyTest {
    @Test
    fun `browser Back follows chrome dialog search history and exit order`() {
        val base =
            WebSessionBrowserHostState(
                browserState = WebSessionBrowserState(canGoBack = true),
                sheetRoute = WebSessionBrowserSheetRoute.MENU,
                isSearchVisible = true,
                isSearchEnginePanelVisible = true,
                textSelectionActions = WebSessionTextSelectionActionsState(12, 24),
                downloadPrompt =
                    BrowserDownloadPromptState(
                        requestId = "download-1",
                        fileName = "sample.bin",
                        mimeType = null,
                        contentLength = 0L,
                        engine = BrowserDownloadEngine.INTERNAL,
                        destinationName = null,
                    ),
            )

        assertEquals(
            WebSessionBrowserBackAction.DISMISS_AD_MARKING_NAVIGATION_REQUEST,
            resolveWebSessionBrowserBackAction(
                base.copy(
                    adMarking = WebSessionAdMarkingState(active = true),
                    adMarkingNavigationRequest =
                        WebSessionAdMarkingNavigationRequest(
                            url = "https://outside.example",
                            text = "Outside",
                        ),
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.DISMISS_AD_MARKING_OVERLAY,
            resolveWebSessionBrowserBackAction(
                base.copy(
                    adMarking = WebSessionAdMarkingState(active = true),
                    adMarkingOverlay = WebSessionAdMarkingOverlay.EDIT_RULE,
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.CLOSE_SHEET,
            resolveWebSessionBrowserBackAction(
                base.copy(
                    sheetRoute = WebSessionBrowserSheetRoute.PAGE_SOURCE,
                    adMarking = WebSessionAdMarkingState(active = true),
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.EXIT_AD_MARKING,
            resolveWebSessionBrowserBackAction(
                base.copy(
                    adMarking = WebSessionAdMarkingState(active = true),
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.DISMISS_WEB_ELEMENT_ACTION,
            resolveWebSessionBrowserBackAction(
                base.copy(
                    webElementAction =
                        WebSessionWebElementActionState(
                            sessionId = "window-1",
                            pageUrl = "https://example.com",
                            tagName = "div",
                            text = "",
                            linkUrl = null,
                            resourceUrl = null,
                            selector = "div",
                            html = "<div></div>",
                            clientX = 1.0,
                            clientY = 2.0,
                        ),
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.DISMISS_TEXT_SELECTION,
            resolveWebSessionBrowserBackAction(base),
        )
        assertEquals(
            WebSessionBrowserBackAction.DISMISS_PENDING_DIALOG,
            resolveWebSessionBrowserBackAction(
                base.copy(
                    textSelectionActions = null,
                    browserState =
                        base.browserState.copy(
                            pendingDialog =
                                WebSessionPendingDialogState(
                                    type = "confirm",
                                    message = "Confirm",
                                ),
                        ),
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.CANCEL_DOWNLOAD_PROMPT,
            resolveWebSessionBrowserBackAction(base.copy(textSelectionActions = null)),
        )
        assertEquals(
            WebSessionBrowserBackAction.DISMISS_PAGE_SOURCE_EXIT_PROMPT,
            resolveWebSessionBrowserBackAction(
                base.copy(
                    textSelectionActions = null,
                    downloadPrompt = null,
                    pageSource =
                        WebSessionPageSourceState(
                            exitPromptVisible = true,
                        ),
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.DISMISS_PLUGIN_EDITOR_EXIT_PROMPT,
            resolveWebSessionBrowserBackAction(
                base.copy(
                    textSelectionActions = null,
                    downloadPrompt = null,
                    pluginEditorExitPromptDraftId = "script-1",
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.POP_PLUGIN_ROUTE,
            resolveWebSessionBrowserBackAction(
                base.copy(
                    textSelectionActions = null,
                    downloadPrompt = null,
                    sheetRoute = WebSessionBrowserSheetRoute.PLUGINS,
                    pluginRouteStack =
                        listOf(
                            WebSessionBrowserPluginRoute.Overview,
                            WebSessionBrowserPluginRoute.Userscripts(),
                        ),
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.CLOSE_SHEET,
            resolveWebSessionBrowserBackAction(
                base.copy(textSelectionActions = null, downloadPrompt = null),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.CLOSE_SEARCH_ENGINE_PANEL,
            resolveWebSessionBrowserBackAction(
                base.copy(
                    textSelectionActions = null,
                    downloadPrompt = null,
                    sheetRoute = WebSessionBrowserSheetRoute.NONE,
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.CLOSE_SEARCH,
            resolveWebSessionBrowserBackAction(
                base.copy(
                    textSelectionActions = null,
                    downloadPrompt = null,
                    sheetRoute = WebSessionBrowserSheetRoute.NONE,
                    isSearchEnginePanelVisible = false,
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.NAVIGATE_WEB_HISTORY,
            resolveWebSessionBrowserBackAction(
                base.copy(
                    textSelectionActions = null,
                    downloadPrompt = null,
                    sheetRoute = WebSessionBrowserSheetRoute.NONE,
                    isSearchEnginePanelVisible = false,
                    isSearchVisible = false,
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.RETURN_TO_HOME,
            resolveWebSessionBrowserBackAction(
                WebSessionBrowserHostState(
                    browserState =
                        WebSessionBrowserState(
                            activeSessionId = "window-1",
                            currentUrl = "https://example.com/result",
                            canReturnToHome = true,
                        ),
                ),
            ),
        )
        assertEquals(
            WebSessionBrowserBackAction.EXIT_BROWSER,
            resolveWebSessionBrowserBackAction(WebSessionBrowserHostState()),
        )
    }

    @Test
    fun `plugin route stack retains the overview root and pops one child at a time`() {
        val userscripts =
            pushBrowserPluginRoute(
                listOf(WebSessionBrowserPluginRoute.Overview),
                WebSessionBrowserPluginRoute.Userscripts(),
            )

        assertEquals(
            listOf(
                WebSessionBrowserPluginRoute.Overview,
                WebSessionBrowserPluginRoute.Userscripts(),
            ),
            userscripts,
        )
        assertEquals(
            listOf(WebSessionBrowserPluginRoute.Overview),
            popBrowserPluginRoute(userscripts),
        )
        assertEquals(
            listOf(WebSessionBrowserPluginRoute.Overview),
            popBrowserPluginRoute(emptyList()),
        )

        val detail =
            browserPluginRouteStackFor(
                WebSessionBrowserPluginRoute.UserscriptDetail(7L),
            )
        assertEquals(
            listOf(
                WebSessionBrowserPluginRoute.Overview,
                WebSessionBrowserPluginRoute.Userscripts(),
                WebSessionBrowserPluginRoute.UserscriptDetail(7L),
            ),
            detail,
        )

        assertEquals(
            detail +
                WebSessionBrowserPluginRoute.UserscriptEditor(
                    draftId = "script-7",
                    scriptId = 7L,
                ),
            browserPluginRouteStackFor(
                WebSessionBrowserPluginRoute.UserscriptEditor(
                    draftId = "script-7",
                    scriptId = 7L,
                ),
            ),
        )
    }

    @Test
    fun `ad-marking navigation policy defaults normal browsing to allow`() {
        assertEquals("allow", BrowserAdMarkingNavigationPolicy.DEFAULT.toJavascriptValue())
        assertEquals("ask", BrowserAdMarkingNavigationPolicy.ASK.toJavascriptValue())
        assertEquals("block", BrowserAdMarkingNavigationPolicy.BLOCK.toJavascriptValue())
    }
}
