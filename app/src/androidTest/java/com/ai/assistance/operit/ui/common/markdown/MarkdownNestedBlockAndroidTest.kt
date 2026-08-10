package com.ai.assistance.operit.ui.common.markdown

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    @Test
    fun multipleFormulaBlocksStayIndependentWhenOneCommandIsUnsupported() = runBlocking {
        val nodes =
            parseMarkdownToNodes(
                """
                \[
                x = \frac{1}{2}
                \]

                正文

                \[
                \unsupported
                \]

                \[
                \ce{2H2 + O2 -> 2H2O}
                \]
                """.trimIndent()
            )

        val formulas = nodes.filter { it.type == MarkdownProcessorType.BLOCK_LATEX }
        assertEquals(3, formulas.size)
        assertTrue(formulas[0].content.toString().contains("""\frac{1}{2}"""))
        assertTrue(formulas[1].content.toString().contains("""\unsupported"""))
        assertTrue(formulas[2].content.toString().contains("""\ce{2H2 + O2 -> 2H2O}"""))
    }

    @Test
    fun multilineSpecialSymbolsStayInsideOneFormulaNode() = runBlocking {
        val nodes =
            parseMarkdownToNodes(
                """
                \[
                \infty,\ \partial,\ \nabla,\ \forall,\ \exists,\ \in,\ \notin,\
                \subseteq,\ \supseteq,\ \cup,\ \cap,\ \emptyset,\ \therefore,\ \because
                \]
                """.trimIndent()
            )

        val formula = nodes.single { it.type == MarkdownProcessorType.BLOCK_LATEX }
        assertTrue(formula.content.toString().contains("""\therefore"""))
        assertTrue(formula.content.toString().contains("""\because"""))
        assertTrue(formula.content.toString().contains("\\notin,\\\n\\subseteq"))
    }

    private fun collectNodes(nodes: List<MarkdownNodeStable>): List<MarkdownNodeStable> =
        nodes.flatMap { node -> listOf(node) + collectNodes(node.children) }
}
