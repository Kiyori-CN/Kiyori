package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserWebElementActionPolicyTest {
    @Test
    fun `link image exposes link image qr and blocking actions`() {
        val plan =
            buildBrowserWebElementActionPlan(
                actionState(
                    text = "Kiyori",
                    linkUrl = "https://example.com/article",
                    resourceUrl = "https://cdn.example.com/image.webp",
                    resourceKind = BrowserWebElementResourceKind.IMAGE,
                ),
            )

        assertEquals(BrowserWebElementTargetKind.IMAGE_LINK, plan.targetKind)
        assertEquals(
            listOf(
                BrowserWebElementAction.OPEN_NEW_WINDOW,
                BrowserWebElementAction.OPEN_BACKGROUND,
                BrowserWebElementAction.VIEW_IMAGE,
                BrowserWebElementAction.SAVE_IMAGE,
                BrowserWebElementAction.IMAGE_MODE,
                BrowserWebElementAction.COPY_LINK,
                BrowserWebElementAction.COPY_IMAGE_LINK,
                BrowserWebElementAction.OPEN_EXTERNAL,
            ),
            plan.quickActions,
        )
        assertEquals(
            listOf(
                BrowserWebElementAction.COPY_TEXT,
                BrowserWebElementAction.SELECT_TEXT,
                BrowserWebElementAction.RECOGNIZE_QR,
            ),
            plan.contentActions,
        )
        assertEquals(
            listOf(
                BrowserWebElementAction.BLOCK_ELEMENT_QUICK,
                BrowserWebElementAction.BLOCK_ELEMENT_ADVANCED,
                BrowserWebElementAction.BLOCK_URL,
            ),
            plan.blockingActions,
        )
    }

    @Test
    fun `plain text does not expose link image or url actions`() {
        val plan = buildBrowserWebElementActionPlan(actionState(text = "可复制正文"))

        assertEquals(BrowserWebElementTargetKind.TEXT, plan.targetKind)
        assertTrue(plan.quickActions.isEmpty())
        assertEquals(
            listOf(
                BrowserWebElementAction.COPY_TEXT,
                BrowserWebElementAction.SELECT_TEXT,
            ),
            plan.contentActions,
        )
        assertFalse(plan.blockingActions.contains(BrowserWebElementAction.BLOCK_URL))
    }

    @Test
    fun `media target never exposes image or qr actions`() {
        val plan =
            buildBrowserWebElementActionPlan(
                actionState(
                    resourceUrl = "https://media.example.com/video.mp4",
                    resourceKind = BrowserWebElementResourceKind.VIDEO,
                ),
            )

        assertEquals(BrowserWebElementTargetKind.MEDIA, plan.targetKind)
        assertTrue(plan.quickActions.contains(BrowserWebElementAction.COPY_RESOURCE_LINK))
        assertFalse(plan.quickActions.contains(BrowserWebElementAction.VIEW_IMAGE))
        assertFalse(plan.quickActions.contains(BrowserWebElementAction.SAVE_IMAGE))
        assertFalse(plan.contentActions.contains(BrowserWebElementAction.RECOGNIZE_QR))
    }

    @Test
    fun `page image snapshot deduplicates limits and preserves selected index`() {
        val urls =
            buildList {
                add("https://example.com/first.png")
                add("https://example.com/selected.png")
                add("https://example.com/selected.png#duplicate")
                repeat(MAX_BROWSER_WEB_ELEMENT_IMAGE_COUNT + 20) { index ->
                    add("https://cdn.example.com/$index.webp")
                }
                add("data:image/png;base64,ignored")
            }
        val snapshot =
            buildBrowserWebElementImageViewerSnapshot(
                urls = urls,
                selectedUrl = "https://example.com/selected.png",
                requestHeadersFor = {
                    mapOf(
                        "User-Agent" to "Kiyori",
                        "Cookie" to "session=1",
                        "Authorization" to "not-forwarded",
                    )
                },
            )

        assertNotNull(snapshot)
        requireNotNull(snapshot)
        assertEquals(MAX_BROWSER_WEB_ELEMENT_IMAGE_COUNT, snapshot.items.size)
        assertEquals(1, snapshot.initialPage)
        assertTrue(snapshot.items.all { item -> "Authorization" !in item.requestHeaders })
    }

    @Test
    fun `standard qr pixels decode exact content`() {
        val content = "https://kiyori.example/qr"
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 256, 256)
        val pixels =
            IntArray(matrix.width * matrix.height) { index ->
                val x = index % matrix.width
                val y = index / matrix.width
                if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }

        assertEquals(
            content,
            decodeBrowserQrCodePixels(matrix.width, matrix.height, pixels),
        )
        assertNull(
            decodeBrowserQrCodePixels(
                width = 32,
                height = 32,
                pixels = IntArray(32 * 32) { 0xFFFFFFFF.toInt() },
            ),
        )
    }

    private fun actionState(
        text: String = "",
        linkUrl: String? = null,
        resourceUrl: String? = null,
        resourceKind: BrowserWebElementResourceKind = BrowserWebElementResourceKind.NONE,
    ): WebSessionWebElementActionState =
        WebSessionWebElementActionState(
            sessionId = "session",
            pageUrl = "https://example.com/page",
            tagName = "div",
            text = text,
            linkUrl = linkUrl,
            resourceUrl = resourceUrl,
            resourceKind = resourceKind,
            selector = "body&&div:eq(0)",
            html = "<div></div>",
            clientX = 10.0,
            clientY = 20.0,
        )
}
