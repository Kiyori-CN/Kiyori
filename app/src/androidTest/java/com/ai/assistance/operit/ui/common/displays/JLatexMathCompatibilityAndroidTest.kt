package com.ai.assistance.operit.ui.common.displays

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.scilab.forge.jlatexmath.Box
import org.scilab.forge.jlatexmath.MacroInfo
import org.scilab.forge.jlatexmath.TeXFormula
import ru.noties.jlatexmath.JLatexMathDrawable
import ru.noties.jlatexmath.awt.AndroidGraphics2D
import ru.noties.jlatexmath.awt.Graphics2D

@RunWith(AndroidJUnit4::class)
class JLatexMathCompatibilityAndroidTest {
    @Test
    fun ensureRegistered_replacesBoxCommandsWithFillPreservingMacro() {
        JLatexMathCompatibility.ensureRegistered()

        val boxed = requireNotNull(MacroInfo.Commands["boxed"])
        val fbox = requireNotNull(MacroInfo.Commands["fbox"])

        assertNotNull(boxed.macro)
        assertNotNull(fbox.macro)
        assertEquals("boxed_macro", boxed.macro.name)
        assertEquals("fbox_macro", fbox.macro.name)
        assertEquals(1, boxed.nbArgs)
        assertEquals(1, fbox.nbArgs)
    }

    @Test
    fun frameDraw_restoresFillBeforeDrawingInnerFormula() {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val graphics =
            AndroidGraphics2D().apply {
                setCanvas(Canvas(bitmap))
            }
        var innerPaintStyle: Paint.Style? = null
        val inner =
            object : Box() {
                init {
                    width = 32f
                    height = 20f
                    depth = 4f
                }

                override fun draw(graphics2D: Graphics2D, x: Float, y: Float) {
                    val paintField =
                        AndroidGraphics2D::class.java.getDeclaredField("paint").apply {
                            isAccessible = true
                        }
                    innerPaintStyle = (paintField.get(graphics2D) as Paint).style
                }

                override fun getLastFontId(): Int = 0
            }

        FillPreservingFramedBox(
            inner = inner,
            thickness = 2f,
            space = 4f
        ).draw(graphics, 16f, 48f)

        assertEquals(Paint.Style.FILL, innerPaintStyle)
    }

    @Test
    fun backendVersion_matchesTheDiagnosedRendererContract() {
        assertEquals(JLATEXMATH_CORE_VERSION, TeXFormula.VERSION)
    }

    @Test
    fun backendRendersOrdinaryPhysicsDiracAndSpecialSymbolFormulas() {
        assertFormulaRenders("""x = \frac{1}{2} + \int_0^1 t^2\,dt""")
        assertFormulaRenders("""A = \begin{pmatrix}1 & 2 \\ 3 & 4\end{pmatrix}""")
        assertFormulaRenders(
            """
            i\hbar\frac{\partial}{\partial t}
            \lvert\psi(t)\rangle
            =
            \hat H\lvert\psi(t)\rangle
            """.trimIndent()
        )
        assertFormulaRenders(
            """
            \langle\phi\mid\psi\rangle,\qquad
            \lvert\psi\rangle
            =
            \sum_n c_n\lvert n\rangle
            """.trimIndent()
        )
        assertFormulaRenders(
            """
            \infty,\ \partial,\ \nabla,\ \forall,\ \exists,\ \in,\ \notin,\
            \subseteq,\ \supseteq,\ \cup,\ \cap,\ \emptyset,\ \therefore,\ \because
            """.trimIndent()
        )
    }

    @Test
    fun backendRendersSupportedChemicalAndNuclearExamples() {
        listOf(
            """\ce{2H2 + O2 -> 2H2O}""",
            """\ce{N2 + 3H2 <=> 2NH3}""",
            """\ce{Ag+ + Cl- -> AgCl v}""",
            """\ce{^{14}_{6}C -> ^{14}_{7}N + e- + \bar{\nu}_e}""",
        ).forEach(::assertFormulaRenders)
    }

    @Test
    fun backendRendersDisplayEnvironmentExamplesAfterPreparation() {
        assertFormulaRenders(
            "\\begin{equation}\n\\nabla \\cdot \\vec{E} = \\frac{\\rho}{\\varepsilon_0}\n\\end{equation}"
        )
        assertFormulaRenders("\\begin{equation*}x=1\\end{equation*}")
        assertFormulaRenders("\\begin{displaymath}x=1\\end{displaymath}")
        assertFormulaRenders("\\begin{align}x&=1\\\\y&=2\\end{align}")
        assertFormulaRenders("\\begin{align*}x&=1\\\\y&=2\\end{align*}")
    }

    private fun assertFormulaRenders(source: String) {
        val prepared = prepareLatexForJLatexMath(source)
        val drawable =
            LatexCache.getDrawable(
                prepared.rendered,
                JLatexMathDrawable.builder(prepared.rendered)
                    .textSize(24f)
                    .padding(2)
            )

        assertTrue("Formula width must be positive: $source", drawable.intrinsicWidth > 0)
        assertTrue("Formula height must be positive: $source", drawable.intrinsicHeight > 0)
    }
}
