package com.ai.assistance.operit.core.tools.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressionParserQualifiedApiTest {
    @Test
    fun `qualified Math functions and constants evaluate`() {
        assertEquals(1.0, evaluate("Math.sin(Math.PI / 2)"), 0.001)
        assertEquals(Math.PI, evaluate("Math.PI"), 0.001)
        assertEquals(Math.E, evaluate("Math.E"), 0.001)
    }

    @Test
    fun `qualified stats functions evaluate`() {
        assertEquals(5.0, evaluate("stats.mean(2, 4, 6, 8)"), 0.001)
    }

    private fun evaluate(expression: String): Double =
        ExpressionParser(expression).parse().evaluate()
}
