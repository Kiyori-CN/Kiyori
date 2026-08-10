package com.ai.assistance.operit.util.streamnative

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.markdown.toCharStream
import com.ai.assistance.operit.util.stream.asStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeMarkdownSplitterAndroidTest {

    @Test
    fun inlineCodeCannotOpenBlockMathFromInsideParagraph() = runBlocking {
        val groups =
            splitBlock(
                "正文 `prefix \\[x^2\\] suffix`、" +
                    "`price ${'$'}${'$'}y^2${'$'}${'$'} literal` 与后续文字"
            )

        assertFalse(groups.any { it.first == MarkdownProcessorType.BLOCK_LATEX })
        assertTrue(
            groups.joinToString("") { it.second }
                .contains("""`prefix \[x^2\] suffix`""")
        )
        assertTrue(
            groups.joinToString("") { it.second }
                .contains("`price ${'$'}${'$'}y^2${'$'}${'$'} literal`")
        )
    }

    @Test
    fun differentLengthCodeTicksRemainOpaqueToBlockMath() = runBlocking {
        val groups = splitBlock("正文 `` `\\[` and `\\]` `` 与后续文字")

        assertFalse(groups.any { it.first == MarkdownProcessorType.BLOCK_LATEX })
        assertTrue(
            groups.joinToString("") { it.second }
                .contains("`` `\\[` and `\\]` ``")
        )
    }

    @Test
    fun realDisplayMathStillUsesBlockMath() = runBlocking {
        val groups =
            splitBlock(
                """
                \[
                x^2+y^2=1
                \]
                """.trimIndent()
            )

        assertTrue(groups.any { it.first == MarkdownProcessorType.BLOCK_LATEX })
    }

    @Test
    fun fencedCodeStillOwnsItsDisplayDelimiters() = runBlocking {
        val groups =
            splitBlock(
                """
                ```latex
                \[
                x^2+y^2=1
                \]
                ```
                """.trimIndent()
            )

        assertTrue(groups.any { it.first == MarkdownProcessorType.CODE_BLOCK })
        assertFalse(groups.any { it.first == MarkdownProcessorType.BLOCK_LATEX })
    }

    @Test
    fun blockQuoteRemovesOneMarkerFromEveryQuotedLine() = runBlocking {
        val groups =
            splitBlock(
                """
                > first
                > second
                >
                > \[
                > x=1
                > \]
                """.trimIndent()
            )

        val quote = groups.single { it.first == MarkdownProcessorType.BLOCK_QUOTE }.second
        assertFalse(quote.lines().any { it.startsWith(">") })
        assertTrue(quote.contains("""\[${'\n'}x=1${'\n'}\]"""))
    }

    @Test
    fun unfinishedDisplayMathCompletesOnlyAfterClosingDelimiterArrives() = runBlocking {
        val input = Channel<Char>(Channel.UNLIMITED)
        val formulaGroupOpened = CompletableDeferred<Unit>()
        val completedFormula = CompletableDeferred<String>()
        val collector =
            launch {
                flow {
                    for (character in input) {
                        emit(character)
                    }
                }
                    .asStream()
                    .nativeMarkdownSplitByBlock()
                    .collect { group ->
                        if (group.tag == MarkdownProcessorType.BLOCK_LATEX) {
                            formulaGroupOpened.complete(Unit)
                            val formula = StringBuilder()
                            group.stream.collect(formula::append)
                            completedFormula.complete(formula.toString())
                        } else {
                            group.stream.collect { }
                        }
                    }
            }

        """
        \[
        \ce{2H2 + O2 -> 2H2O}
        """.trimIndent().forEach { input.send(it) }

        withTimeout(2_000) { formulaGroupOpened.await() }
        assertFalse(completedFormula.isCompleted)

        "\n\\]".forEach { input.send(it) }
        input.close()

        val formula = withTimeout(2_000) { completedFormula.await() }
        withTimeout(2_000) { collector.join() }
        assertTrue(formula.startsWith("""\["""))
        assertTrue(formula.endsWith("""\]"""))
        assertTrue(formula.contains("""\ce{2H2 + O2 -> 2H2O}"""))
    }

    @Test
    fun multipleDisplayFormulaBlocksRemainIndependent() = runBlocking {
        val groups =
            splitBlock(
                """
                \[
                x = \frac{1}{2}
                \]

                中间正文

                \[
                \ce{Ag+ + Cl- -> AgCl v}
                \]
                """.trimIndent()
            )

        val formulas = groups.filter { it.first == MarkdownProcessorType.BLOCK_LATEX }
        assertEquals(2, formulas.size)
        assertTrue(formulas[0].second.contains("""\frac{1}{2}"""))
        assertTrue(formulas[1].second.contains("""\ce{Ag+ + Cl- -> AgCl v}"""))
    }

    private suspend fun splitBlock(content: String): List<Pair<MarkdownProcessorType?, String>> {
        val groups = mutableListOf<Pair<MarkdownProcessorType?, String>>()
        content.toCharStream().nativeMarkdownSplitByBlock().collect { group ->
            val text = StringBuilder()
            group.stream.collect(text::append)
            groups += group.tag to text.toString()
        }
        return groups
    }
}
