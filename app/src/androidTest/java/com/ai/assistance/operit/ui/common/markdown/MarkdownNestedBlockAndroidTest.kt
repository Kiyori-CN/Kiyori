package com.ai.assistance.operit.ui.common.markdown

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownNestedBlockAndroidTest {

    @Test
    fun blockQuoteRecursivelyParsesDisplayMath() = runBlocking {
        val nodes =
            parseMarkdownToNodes(
                """
                > \[
                > E=mc^2
                > \tag{1}
                > \]
                """.trimIndent()
            )

        val quote = nodes.single { it.type == MarkdownProcessorType.BLOCK_QUOTE }
        val children = quote.toStableNode().children
        val formula = children.single { it.type == MarkdownProcessorType.BLOCK_LATEX }

        assertTrue(formula.content.contains("E=mc^2"))
        assertFalse(formula.content.contains(">"))
    }

    @Test
    fun blockQuoteKeepsInlineCodeOpaqueWhileParsingRealFormula() = runBlocking {
        val nodes =
            parseMarkdownToNodes(
                """
                > 定界符写作 `\[...\]`。
                >
                > \[
                > E=mc^2
                > \tag{2}
                > \]
                """.trimIndent()
            )

        val quote = nodes.single { it.type == MarkdownProcessorType.BLOCK_QUOTE }
        val children = quote.toStableNode().children
        val inlineCodeNodes = collectNodes(children).filter { it.type == MarkdownProcessorType.INLINE_CODE }

        assertTrue(inlineCodeNodes.any { it.content == """\[...\]""" })
        assertTrue(children.any { it.type == MarkdownProcessorType.BLOCK_LATEX })
        assertFalse(
            collectNodes(inlineCodeNodes).any { it.type == MarkdownProcessorType.BLOCK_LATEX }
        )
    }

    private fun collectNodes(nodes: List<MarkdownNodeStable>): List<MarkdownNodeStable> =
        nodes.flatMap { node -> listOf(node) + collectNodes(node.children) }
}
