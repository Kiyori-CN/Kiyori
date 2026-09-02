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
    fun shellPidExpansionInsideProseDoesNotOpenBlockMath() = runBlocking {
        val content =
            "回归 shell 崩溃恢复（exit / kill -9 ${'$'}${'$'} 自动重建）后继续显示正文"
        val groups = splitBlock(content)

        assertFalse(groups.any { it.first == MarkdownProcessorType.BLOCK_LATEX })
        assertEquals(content, groups.joinToString("") { it.second })
    }

    @Test
    fun indentedDisplayMathStillUsesBlockMath() = runBlocking {
        val groups =
            splitBlock(
                """
                    ${'$'}${'$'}
                    x^2+y^2=1
                    ${'$'}${'$'}
                """.trimIndent()
            )

        assertTrue(groups.any { it.first == MarkdownProcessorType.BLOCK_LATEX })
    }

    @Test
    fun protocolToolBlocksStayXmlWhenAttachedToProseAndContainSpecialSymbols() = runBlocking {
        val content =
            """|结果紧贴标签<tool_A1 name="package_proxy">
               |  <param name="params">{&quot;command&quot;:&quot;echo PID=${'$'}${'$'}; printf '\"&lt;&amp;&gt;' &gt; /tmp/out&quot;}</param>
               |</tool_A1><tool_result_A1 name="super_admin:terminal" status="success">
               |  <content>{"command":"echo PID=${'$'}${'$'}","output":"&lt;ok&gt;\\n","exitCode":0}</content>
               |</tool_result_A1>尾部正文
            """.trimMargin()
        val groups = splitBlock(content)
        val xmlGroups = groups.filter { it.first == MarkdownProcessorType.XML_BLOCK }

        assertEquals(2, xmlGroups.size)
        assertTrue(xmlGroups[0].second.startsWith("<tool_A1"))
        assertTrue(xmlGroups[1].second.startsWith("<tool_result_A1"))
        assertFalse(groups.any { it.first == MarkdownProcessorType.BLOCK_LATEX })
        assertEquals(content, groups.joinToString("") { it.second })
    }

    @Test
    fun protocolAttributesMayContainQuotedAngleBrackets() = runBlocking {
        val content =
            "前缀<status\ttitle=\"a > b < c\">ready</status>后缀"
        val groups = splitBlock(content)

        val xmlGroup = groups.single { it.first == MarkdownProcessorType.XML_BLOCK }
        assertEquals("<status\ttitle=\"a > b < c\">ready</status>", xmlGroup.second)
        assertEquals(content, groups.joinToString("") { it.second })
    }

    @Test
    fun protocolClosingTagIsCaseInsensitive() = runBlocking {
        val content = "前缀<TOOL_RESULT_A1 name=\"terminal\">ok</tool_result_a1>后缀"
        val groups = splitBlock(content)

        val xmlGroup = groups.single { it.first == MarkdownProcessorType.XML_BLOCK }
        assertEquals(content.substringAfter("前缀").substringBefore("后缀"), xmlGroup.second)
        assertEquals(content, groups.joinToString("") { it.second })
    }

    @Test
    fun unknownXmlLikeTextAttachedToProseRemainsPlainText() = runBlocking {
        val content = "比较 a<sample>value</sample> 与 b，随后正文不应变成 XML 卡片"
        val groups = splitBlock(content)

        assertFalse(groups.any { it.first == MarkdownProcessorType.XML_BLOCK })
        assertEquals(content, groups.joinToString("") { it.second })
    }

    @Test
    fun malformedToolSuffixDoesNotBecomeProtocolXml() = runBlocking {
        val content = "文本<tool_>x</tool_>以及<tool_result_>y</tool_result_>"
        val groups = splitBlock(content)

        assertFalse(groups.any { it.first == MarkdownProcessorType.XML_BLOCK })
        assertEquals(content, groups.joinToString("") { it.second })
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
    fun displayEnvironmentStillUsesBlockMathAndPreservesWrapper() = runBlocking {
        val content =
            "说明\n\\begin{equation}\n\\nabla \\cdot \\vec{E} = \\frac{\\rho}{\\varepsilon_0}\n\\tag{1}\n\\end{equation}\n后续"
        val groups = splitBlock(content)

        val formula = groups.single { it.first == MarkdownProcessorType.BLOCK_LATEX }
        assertEquals(
            "\\begin{equation}\n\\nabla \\cdot \\vec{E} = \\frac{\\rho}{\\varepsilon_0}\n\\tag{1}\n\\end{equation}",
            formula.second,
        )
        assertEquals("后续", groups.last().second)
    }

    @Test
    fun displayEnvironmentInsideFenceRemainsOpaqueCode() = runBlocking {
        val content = "```latex\n\\begin{equation}\nx=1\n\\end{equation}\n```"
        val groups = splitBlock(content)

        val code = groups.single { it.first == MarkdownProcessorType.CODE_BLOCK }.second
        assertTrue(code.contains("\\begin{equation}"))
        assertFalse(groups.any { it.first == MarkdownProcessorType.BLOCK_LATEX })
    }

    @Test
    fun displayEnvironmentCloseDoesNotReopenOnSamePhysicalLine() = runBlocking {
        val content =
            "\\begin{equation}x=1\\end{equation}\\begin{equation}y=2\\end{equation}"
        val groups = splitBlock(content)

        assertEquals(1, groups.count { it.first == MarkdownProcessorType.BLOCK_LATEX })
        assertEquals(
            "\\begin{equation}x=1\\end{equation}",
            groups.single { it.first == MarkdownProcessorType.BLOCK_LATEX }.second,
        )
        assertTrue(groups.last { it.first == MarkdownProcessorType.PLAIN_TEXT }.second.contains(
            "\\begin{equation}y=2\\end{equation}"
        ))
    }

    @Test
    fun unknownDisplayEnvironmentRemainsObservableAsOneFormulaBlock() = runBlocking {
        val content = "\\begin{gather}\nx=1\n\\end{gather}\n后续"
        val groups = splitBlock(content)

        assertEquals(
            "\\begin{gather}\nx=1\n\\end{gather}",
            groups.single { it.first == MarkdownProcessorType.BLOCK_LATEX }.second,
        )
        assertEquals("后续", groups.last().second)
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
    fun fencedCodeWinsBeforeLanguageAndKeepsSpecialSymbolsOpaque() = runBlocking {
        val content =
            "```mermaid\ngraph TD\nA-->B\n**正文** ${'$'}${'$'}x${'$'}${'$'} \\[not-math\\]\n```\n尾"
        val groups = splitBlock(content)

        val code = groups.single { it.first == MarkdownProcessorType.CODE_BLOCK }.second
        assertTrue(code.contains("```mermaid"))
        assertTrue(code.contains("**正文**"))
        assertTrue(code.contains("${'$'}${'$'}x${'$'}${'$'}"))
        assertTrue(code.contains("\\[not-math\\]"))
        assertEquals(content, groups.joinToString("") { it.second })
    }

    @Test
    fun fencedCodeRequiresLineStartAndMatchingOpeningLength() = runBlocking {
        val content =
            "    ```not-a-block\nplain\n````python\r\nprint(1)\r\n  ```\r\nstill code\r\n  ````\r\n尾"
        val groups = splitBlock(content)

        assertTrue(groups.none { it.first == MarkdownProcessorType.CODE_BLOCK && it.second.contains("not-a-block") })
        val code = groups.single { it.first == MarkdownProcessorType.CODE_BLOCK }.second
        assertTrue(code.contains("````python\r\n"))
        assertTrue(code.contains("  ```\r\nstill code"))
        assertEquals("尾", groups.last().second)
        assertEquals(content, groups.joinToString("") { it.second })
    }

    @Test
    fun unfinishedFencedCodeRemainsOneOpaqueBlockAtEof() = runBlocking {
        val content = "```bash\r\necho ${'$'}${'$'}\r\n<xml> **markdown**"
        val groups = splitBlock(content)

        val code = groups.single { it.first == MarkdownProcessorType.CODE_BLOCK }.second
        assertEquals(content, code)
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
