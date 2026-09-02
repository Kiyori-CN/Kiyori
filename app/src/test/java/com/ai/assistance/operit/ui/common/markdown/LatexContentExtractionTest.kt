package com.ai.assistance.operit.ui.common.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class LatexContentExtractionTest {
    @Test
    fun stripsOnlyStructuralWhitespaceAroundBracketDelimiters() {
        val source = "  \\[\r\n  x^2 + y^2 = 1  \r\n\\]  \t"

        assertEquals("\r\n  x^2 + y^2 = 1  \r\n", extractLatexContent(source))
    }

    @Test
    fun keepsUndelimitedFormulaBytesUntouched() {
        val source = "\n  \\begin{equation}\n x=1\n\\end{equation}\n"

        assertEquals(source, extractLatexContent(source))
    }

    @Test
    fun doesNotTreatAStandaloneDoubleDollarAsSingleDollarMath() {
        assertEquals("$$", extractLatexContent("$$"))
    }
}
