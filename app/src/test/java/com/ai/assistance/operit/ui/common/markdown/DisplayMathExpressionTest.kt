package com.ai.assistance.operit.ui.common.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DisplayMathExpressionTest {
    @Test
    fun parse_withoutTag_preservesFormulaBody() {
        val expression = parseDisplayMathExpression("""x = \frac{1}{2}""")

        assertEquals("""x = \frac{1}{2}""", expression.body)
        assertNull(expression.tag)
    }

    @Test
    fun parse_extractsTopLevelTagAndRemovesItFromBody() {
        val expression =
            parseDisplayMathExpression(
                """
                x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
                \tag{1}
                """.trimIndent()
            )

        assertEquals("""x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}""", expression.body)
        assertEquals(DisplayMathTag(latex = "1", parenthesized = true), expression.tag)
    }

    @Test
    fun parse_extractsStarredTagWithoutParentheses() {
        val expression = parseDisplayMathExpression("""E = mc^2 \tag*{A}""")

        assertEquals("E = mc^2", expression.body)
        assertEquals(DisplayMathTag(latex = "A", parenthesized = false), expression.tag)
    }

    @Test
    fun parse_supportsNestedBracesInsideTag() {
        val expression = parseDisplayMathExpression("""x = 1 \tag{\text{Eq. {A}}}""")

        assertEquals("x = 1", expression.body)
        assertEquals(
            DisplayMathTag(latex = """\text{Eq. {A}}""", parenthesized = true),
            expression.tag
        )
    }

    @Test
    fun parse_ignoresTagCommandNestedInsideFormulaGroup() {
        val source = """\text{literal \tag{A}} + x"""
        val expression = parseDisplayMathExpression(source)

        assertEquals(source, expression.body)
        assertNull(expression.tag)
    }

    @Test
    fun parse_ignoresEscapedBackslashBeforeTagText() {
        val source = """x \\tag{A}"""
        val expression = parseDisplayMathExpression(source)

        assertEquals(source, expression.body)
        assertNull(expression.tag)
    }

    @Test
    fun parse_rejectsMultipleTopLevelTags() {
        assertThrows(IllegalArgumentException::class.java) {
            parseDisplayMathExpression("""x \tag{1} \tag{2}""")
        }
    }

    @Test
    fun parse_rejectsUnclosedTagGroup() {
        assertThrows(IllegalArgumentException::class.java) {
            parseDisplayMathExpression("""x \tag{1""")
        }
    }

    @Test
    fun layout_centersBodyAndPinsTagToRightWithoutOverlap() {
        val layout =
            resolveDisplayMathLayout(
                viewportWidth = 320,
                bodyWidth = 180,
                bodyHeight = 48,
                tagWidth = 36,
                tagHeight = 24,
                tagGap = 12,
            )

        assertEquals(320, layout.width)
        assertEquals(70, layout.bodyX)
        assertEquals(284, layout.tagX)
        assertEquals(0, layout.bodyY)
        assertEquals(12, layout.tagY)
    }

    @Test
    fun layout_expandsWideFormulaSymmetricallyAroundBody() {
        val layout =
            resolveDisplayMathLayout(
                viewportWidth = 240,
                bodyWidth = 220,
                bodyHeight = 40,
                tagWidth = 40,
                tagHeight = 20,
                tagGap = 10,
            )

        assertEquals(320, layout.width)
        assertEquals(50, layout.bodyX)
        assertEquals(280, layout.tagX)
        assertEquals(10, layout.tagY)
    }
}
