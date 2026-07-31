package com.ai.assistance.operit.util.streamnative

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeMarkdownSplitterTest {

    @Test
    fun stripsSingleMatchingOuterTickRun() {
        assertEquals(
            """\[x^2\]""",
            stripMarkdownInlineCodeDelimiters("""`\[x^2\]`"""),
        )
    }

    @Test
    fun preservesShorterTickRunsInsideDoubleTickCode() {
        assertEquals(
            """ `\[` and `\]` """,
            stripMarkdownInlineCodeDelimiters("""`` `\[` and `\]` ``"""),
        )
    }

    @Test
    fun malformedCodeSpanRemainsLiteral() {
        assertEquals(
            """`\[x^2\]""",
            stripMarkdownInlineCodeDelimiters("""`\[x^2\]"""),
        )
    }
}
