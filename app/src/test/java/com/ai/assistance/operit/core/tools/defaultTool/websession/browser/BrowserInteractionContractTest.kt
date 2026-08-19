package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserInteractionContractTest {
    @Test
    fun `editable targets use native WebView selection and ordinary elements keep Kiyori actions`() {
        val pageExecutionSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserPageExecutionSupport.kt",
            ).readText()
        val webViewSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserWebViewSupport.kt",
            ).readText()

        val handlerIndex = pageExecutionSource.indexOf("handleLongPress: function(x, y)")
        val guardIndex =
            pageExecutionSource.indexOf(
                "if (textSelectionTargetFor(element))",
                startIndex = handlerIndex,
            )
        val payloadIndex =
            pageExecutionSource.indexOf(
                "const payload = payloadFor(element, x, y);",
                startIndex = handlerIndex,
            )
        val showActionsIndex =
            pageExecutionSource.indexOf(
                "window.OperitWebElementBridge.showActions(JSON.stringify(payload));",
                startIndex = handlerIndex,
            )
        val nativeEditableGateIndex =
            pageExecutionSource.indexOf(
                "if (isEditableTextControl(pressedElement))",
            )
        val customSelectionIndex =
            pageExecutionSource.indexOf(
                "selectAtPoint(state.startX, state.startY);",
                startIndex = nativeEditableGateIndex,
            )

        assertTrue(guardIndex > handlerIndex)
        assertTrue(payloadIndex > guardIndex)
        assertTrue(showActionsIndex > payloadIndex)
        assertTrue(
            pageExecutionSource
                .substring(guardIndex, payloadIndex)
                .contains("return false;"),
        )
        assertTrue(
            pageExecutionSource.contains(
                "element.closest(\"input, textarea, [contenteditable]\")",
            ),
        )
        assertTrue(pageExecutionSource.contains("target.isContentEditable"))
        assertTrue(nativeEditableGateIndex >= 0)
        assertTrue(customSelectionIndex > nativeEditableGateIndex)
        assertTrue(
            pageExecutionSource
                .substring(nativeEditableGateIndex, customSelectionIndex)
                .contains("return;"),
        )
        assertTrue(webViewSource.contains("isLongClickable = true"))
        assertTrue(
            webViewSource.contains(
                "!textSelectionBridge.isEditableLongPressTarget()",
            ),
        )
        assertTrue(
            webViewSource.contains(
                "hitTestResult.type != WebView.HitTestResult.EDIT_TEXT_TYPE",
            ),
        )
        assertTrue(
            pageExecutionSource.contains(
                "window.OperitTextSelectionBridge.setEditableLongPressTarget(",
            ),
        )
        assertFalse(pageExecutionSource.contains("function selectControlContents("))
        assertFalse(pageExecutionSource.contains("function renderControlSelection("))
        assertFalse(pageExecutionSource.contains("state.control"))
        assertFalse(webViewSource.contains("setOnLongClickListener { true }"))
        assertFalse(webViewSource.contains("isLongClickable = false"))
    }

    @Test
    fun `network image viewer owns an edge to edge paged image surface`() {
        val viewerSourceFile =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/websession/browser/WebSessionBrowserImageViewer.kt",
            ).readText()
        val networkLogSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/websession/browser/WebSessionNetworkLogSheet.kt",
            ).readText()
        val viewerStart =
            viewerSourceFile.indexOf("internal fun WebSessionBrowserImageViewer(")
        val viewerEnd =
            viewerSourceFile.indexOf(
                "internal fun buildBrowserResourceImageRequest(",
                viewerStart,
            )
        assertTrue(viewerStart >= 0)
        assertTrue(viewerEnd > viewerStart)
        val viewerSource = viewerSourceFile.substring(viewerStart, viewerEnd)

        assertTrue(viewerSource.contains("Dialog("))
        assertTrue(viewerSource.contains("usePlatformDefaultWidth = false"))
        assertTrue(viewerSource.contains("decorFitsSystemWindows = false"))
        assertTrue(viewerSource.contains("FLAG_DIM_BEHIND"))
        assertTrue(viewerSource.contains("HorizontalPager("))
        assertTrue(viewerSource.contains("SubcomposeAsyncImage("))
        assertTrue(viewerSource.contains("ContentScale.Fit"))
        assertTrue(viewerSource.contains("\"${'$'}{pagerState.currentPage + 1}/${'$'}{snapshot.items.size}\""))
        assertTrue(viewerSource.contains("text = \"保存\""))
        assertTrue(viewerSource.contains("text = \"保存原图\""))
        assertTrue(viewerSource.contains("awaitEachGesture"))
        assertTrue(viewerSource.contains("shouldDismissBrowserImageViewer("))
        assertTrue(viewerSource.contains("pressedPointers.size >= 2"))
        assertTrue(viewerSource.contains("pinchGesture"))
        assertTrue(viewerSource.contains("change.consume()"))
        assertTrue(viewerSource.contains("clampBrowserImageViewerScale("))
        assertTrue(viewerSource.contains("LaunchedEffect(pagerState.currentPage)"))
        val saveStateIndex = networkLogSource.indexOf("var pendingImageSaveItem")
        val pendingAssignmentIndex =
            networkLogSource.indexOf(
                "pendingImageSaveItem = item",
                startIndex = saveStateIndex,
            )
        val viewerCloseIndex =
            networkLogSource.indexOf(
                "imageViewerSnapshot = null",
                startIndex = pendingAssignmentIndex,
            )
        val downloadEffectIndex =
            networkLogSource.indexOf("LaunchedEffect(pendingImageSaveItem)")
        val downloadCallIndex =
            networkLogSource.indexOf(
                "startDownloadUrl(item.url, item.mediaCandidateId)",
                startIndex = downloadEffectIndex,
            )
        assertTrue(saveStateIndex >= 0)
        assertTrue(pendingAssignmentIndex > saveStateIndex)
        assertTrue(viewerCloseIndex > saveStateIndex)
        assertTrue(downloadEffectIndex > saveStateIndex)
        assertTrue(downloadCallIndex > downloadEffectIndex)
        assertFalse(viewerSource.contains("WebSessionBrowserModalDialog"))
        assertFalse(viewerSource.contains("rememberAsyncImagePainter"))
        assertFalse(viewerSource.contains("detectTransformGestures"))
        assertFalse(viewerSource.contains("Icons.Filled.Close"))
    }

    @Test
    fun `element menu switch and image mode remain bounded without intercepting editable controls`() {
        val pageExecutionSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserPageExecutionSupport.kt",
            ).readText()
        val settingsSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/WebSessionBrowserSettingsStore.kt",
            ).readText()
        val bridgeSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserPageExecutionSupport.kt",
            ).readText()

        assertTrue(settingsSource.contains("val webElementLongPressMenuEnabled: Boolean = true"))
        assertTrue(pageExecutionSource.contains("state.elementActionsEnabled"))
        assertTrue(pageExecutionSource.contains("setElementActionsEnabled: function(enabled)"))
        assertTrue(pageExecutionSource.contains("if (!state.elementActionsEnabled)"))
        assertTrue(pageExecutionSource.contains("openImageMode: function(selectedUrl)"))
        assertTrue(pageExecutionSource.contains("Array.from(document.querySelectorAll(\"*\")).slice(0, 2000)"))
        assertTrue(pageExecutionSource.contains("urls.length >= 200"))
        assertTrue(pageExecutionSource.contains("img[src], img[data-src], img[data-original]"))
        assertTrue(bridgeSource.contains("fun showImageViewer(payload: String?)"))
        assertTrue(bridgeSource.contains("window.OperitWebElementBridge.showImageViewer("))
    }

    @Test
    fun `network log captures image identity without reading WebView settings`() {
        val source =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserToolSupport.kt",
            ).readText()

        val imageGuard = source.indexOf("if (category == BrowserNetworkRequestCategory.IMAGE)")
        val cookieRead = source.indexOf("session.cookieManager.getCookie(url)", imageGuard)
        val headerBuild = source.indexOf("buildBrowserNetworkRequestHeaders(", cookieRead)
        assertTrue(imageGuard >= 0)
        assertTrue(cookieRead > imageGuard)
        assertTrue(headerBuild > cookieRead)
        assertTrue(source.contains("appliedUserAgent = session.appliedUserAgent"))
        assertTrue(source.contains("pageUrl = session.currentUrl"))
        assertFalse(source.contains("session.webView.settings"))
    }

    private fun repositoryFile(relativePath: String): File {
        var current: File? =
            File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(4) {
            val candidate = current?.let { directory -> File(directory, relativePath) }
            if (candidate?.isFile == true) {
                return candidate
            }
            current = current?.parentFile
        }
        throw AssertionError("Repository file not found: $relativePath")
    }
}
