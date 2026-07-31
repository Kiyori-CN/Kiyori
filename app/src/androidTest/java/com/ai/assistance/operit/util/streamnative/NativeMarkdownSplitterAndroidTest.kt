package com.ai.assistance.operit.util.streamnative

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.markdown.toCharStream
import kotlinx.coroutines.runBlocking
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
