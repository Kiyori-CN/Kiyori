package com.ai.assistance.operit.ui.common.displays

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexFormulaSupportTest {
    @Test
    fun prepare_preservesOrdinaryLatex() {
        val source = """x = \frac{1}{2} + \int_0^1 t^2\,dt"""

        val prepared = prepareLatexForJLatexMath(source)

        assertEquals(source, prepared.rendered)
        assertTrue(prepared.transformations.isEmpty())
    }

    @Test
    fun prepare_mapsMissingVerticalDelimiterCommandsOnly() {
        val source =
            """
            \langle\phi\mid\psi\rangle,\qquad
            \lvert\psi\rangle + \lVert A\psi\rVert + \lvert y \rvert
            """.trimIndent()

        val prepared = prepareLatexForJLatexMath(source)

        assertEquals(
            """
            \langle\phi\mid\psi\rangle,\qquad
            \mathopen{\vert}\psi\rangle + \mathopen{\Vert} A\psi\mathclose{\Vert} + \mathopen{\vert} y \mathclose{\vert}
            """.trimIndent(),
            prepared.rendered,
        )
        assertEquals(
            listOf("lvert", "lVert", "rVert", "lvert", "rvert"),
            prepared.transformations.map { it.substringBefore('@') },
        )
    }

    @Test
    fun prepare_normalizesControlSpacesUsedBetweenSymbols() {
        val source = """\infty,\ \partial,\ \therefore,\ \because"""

        val prepared = prepareLatexForJLatexMath(source)

        assertEquals(
            """\infty,\;\partial,\;\therefore,\;\because""",
            prepared.rendered,
        )
        assertEquals(3, prepared.transformations.count { it.startsWith("control-space@") })
        assertFalse(prepared.rendered.contains("\\ "))
    }

    @Test
    fun prepare_normalizesBackslashLfWithoutRemovingTheLineBoundary() {
        val source = "\\notin,\\\n\\subseteq"

        val prepared = prepareLatexForJLatexMath(source)

        assertEquals("\\notin,\\;\n\\subseteq", prepared.rendered)
        assertEquals(listOf("control-line-break@7"), prepared.transformations)
    }

    @Test
    fun prepare_normalizesBackslashCrLfToOneLineBoundary() {
        val source = "\\notin,\\\r\n\\subseteq"

        val prepared = prepareLatexForJLatexMath(source)

        assertEquals("\\notin,\\;\n\\subseteq", prepared.rendered)
        assertEquals(listOf("control-line-break@7"), prepared.transformations)
    }

    @Test
    fun prepare_doesNotRewriteDoubleBackslash() {
        val source = """a\\b"""

        val prepared = prepareLatexForJLatexMath(source)

        assertEquals(source, prepared.rendered)
        assertTrue(prepared.transformations.isEmpty())
    }

    @Test
    fun prepare_preservesSupportedSpecialSymbolsAfterWhitespaceNormalization() {
        val source =
            """
            \infty,\ \partial,\ \nabla,\ \forall,\ \exists,\ \in,\ \notin,\
            \subseteq,\ \supseteq,\ \cup,\ \cap,\ \emptyset,\ \therefore,\ \because
            """.trimIndent()

        val prepared = prepareLatexForJLatexMath(source)

        assertTrue(prepared.rendered.contains("""\therefore"""))
        assertTrue(prepared.rendered.contains("""\because"""))
        assertTrue(prepared.rendered.contains("\\notin,\\;\n\\subseteq"))
        assertFalse(prepared.rendered.contains("\\ "))
    }

    @Test
    fun prepare_convertsBasicChemicalEquations() {
        assertEquals(
            """2\mathrm{H}_{2}\; + \;\mathrm{O}_{2}\;\rightarrow\;2\mathrm{H}_{2}\mathrm{O}""",
            prepareLatexForJLatexMath("""\ce{2H2 + O2 -> 2H2O}""").rendered,
        )
        assertEquals(
            """\mathrm{N}_{2}\; + \;3\mathrm{H}_{2}\;\rightleftharpoons\;2\mathrm{N}\mathrm{H}_{3}""",
            prepareLatexForJLatexMath("""\ce{N2 + 3H2 <=> 2NH3}""").rendered,
        )
        assertEquals(
            """\mathrm{Ag}^{+}\; + \;\mathrm{Cl}^{-}\;\rightarrow\;\mathrm{Ag}\mathrm{Cl}\,\downarrow""",
            prepareLatexForJLatexMath("""\ce{Ag+ + Cl- -> AgCl v}""").rendered,
        )
    }

    @Test
    fun prepare_convertsNuclearReactionAndPreservesMixedLatex() {
        val prepared =
            prepareLatexForJLatexMath(
                """\ce{^{14}_{6}C -> ^{14}_{7}N + e- + \bar{\nu}_e}"""
            )

        assertEquals(
            """^{14}_{6}\mathrm{C}\;\rightarrow\;^{14}_{7}\mathrm{N}\; + \;\mathrm{e}^{-}\; + \;\bar{\nu}_e""",
            prepared.rendered,
        )
    }

    @Test
    fun prepare_supportsGroupsStatesAndExplicitChargeScripts() {
        val prepared =
            prepareLatexForJLatexMath(
                """\ce{Ca(OH)2(aq) + Fe^{3+} -> Ca^{2+} + Fe(s)}"""
            )

        assertEquals(
            """\mathrm{Ca}(\mathrm{O}\mathrm{H})_{2}\mathrm{(aq)}\; + \;\mathrm{Fe}^{3+}\;\rightarrow\;\mathrm{Ca}^{2+}\; + \;\mathrm{Fe}\mathrm{(s)}""",
            prepared.rendered,
        )
    }

    @Test
    fun prepare_convertsMultipleChemicalExpressionsWithoutTouchingOtherCommands() {
        val prepared =
            prepareLatexForJLatexMath(
                """\ce{H2 -> 2H+}\qquad\ce{Cl- -> Cl}"""
            )

        assertEquals(
            """\mathrm{H}_{2}\;\rightarrow\;2\mathrm{H}^{+}\qquad\mathrm{Cl}^{-}\;\rightarrow\;\mathrm{Cl}""",
            prepared.rendered,
        )
        assertEquals(2, prepared.transformations.count { it.startsWith("ce@") })
    }

    @Test
    fun prepare_reportsMalformedChemicalInputWithCommandAndOriginalPosition() {
        val missingArgument =
            assertThrows(LatexCompatibilityException::class.java) {
                prepareLatexForJLatexMath("""\ce H2""")
            }
        assertEquals("""\ce""", missingArgument.command)
        assertEquals(0, missingArgument.sourceIndex)

        val emptyExpression =
            assertThrows(LatexCompatibilityException::class.java) {
                prepareLatexForJLatexMath("""\ce{}""")
            }
        assertEquals("""\ce""", emptyExpression.command)
        assertEquals(4, emptyExpression.sourceIndex)

        val unclosedExpression =
            assertThrows(LatexCompatibilityException::class.java) {
                prepareLatexForJLatexMath("""\ce{H2O""")
            }
        assertEquals("""\ce""", unclosedExpression.command)
        assertEquals(3, unclosedExpression.sourceIndex)
    }

    @Test
    fun diagnose_extractsUnknownCommandAndDerivedLineColumn() {
        val formula = "x + 1\n\\unsupported"
        val error =
            IllegalArgumentException(
                "Unknown symbol or command or predefined TeXFormula: 'unsupported'"
            )

        val details = diagnoseLatexFailure(formula, error)

        assertEquals("""\unsupported""", details.command)
        assertEquals(2, details.line)
        assertEquals(1, details.column)
        assertEquals(6, details.sourceIndex)
    }

    @Test
    fun diagnose_preservesBackendReportedLineAndColumn() {
        val details =
            diagnoseLatexFailure(
                formula = """\begin{unsupported}x\end{unsupported}""",
                error = IllegalArgumentException("Unknown environment: unsupported at position 3:7"),
            )

        assertEquals(3, details.line)
        assertEquals(7, details.column)
    }

    @Test
    fun formatLog_includesBackendInputsTransformationsAndPositionSource() {
        val original = """\lvert x \rvert + \unsupported"""
        val prepared = prepareLatexForJLatexMath(original)
        val error =
            IllegalArgumentException(
                "Unknown symbol or command or predefined TeXFormula: 'unsupported'"
            )

        val log =
            formatLatexFailureLog(
                surface = "display",
                originalFormula = original,
                preparedFormula = prepared,
                error = error,
                includeFormulaContent = true,
            )

        assertTrue(log.contains("backend=$JLATEXMATH_BACKEND_DESCRIPTION"))
        assertTrue(log.contains("""command=\unsupported"""))
        assertTrue(log.contains("positionSource=preprocessed"))
        assertTrue(log.contains("original=$original"))
        assertTrue(log.contains("preprocessed=${prepared.rendered}"))
        assertTrue(log.contains("transformations=lvert@0, rvert@9"))
    }
}
