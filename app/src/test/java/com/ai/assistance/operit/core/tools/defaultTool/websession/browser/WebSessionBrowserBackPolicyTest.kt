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
            WebSessionBrowserBackAction.EXIT_BROWSER,
            resolveWebSessionBrowserBackAction(WebSessionBrowserHostState()),
        )
    }
}
