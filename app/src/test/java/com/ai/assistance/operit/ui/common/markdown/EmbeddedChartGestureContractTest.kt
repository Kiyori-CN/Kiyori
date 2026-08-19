package com.ai.assistance.operit.ui.common.markdown

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedChartGestureContractTest {
    @Test
    fun `all chat web chart surfaces own touch without forwarding nested fling to home pager`() {
        val codeBlockSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/common/markdown/" +
                    "EnhancedCodeBlock.kt",
            ).readText()
        val xmlRendererSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/part/" +
                    "CustomXmlRenderer.kt",
            ).readText()

        assertTrue(codeBlockSource.contains("horizontalGestureOwner.updateFromMotionEvent(event)"))
        assertTrue(xmlRendererSource.contains("horizontalGestureOwner.updateFromMotionEvent(event)"))
        assertTrue(
            xmlRendererSource.contains(
                "view.parent.requestDisallowInterceptTouchEvent(true)",
            ),
        )
        assertFalse(codeBlockSource.contains("rememberNestedScrollInteropConnection"))
        assertFalse(xmlRendererSource.contains("rememberNestedScrollInteropConnection"))
        assertFalse(codeBlockSource.contains(".nestedScroll(nestedScrollInterop)"))
        assertFalse(xmlRendererSource.contains(".nestedScroll(nestedScrollInterop)"))
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
