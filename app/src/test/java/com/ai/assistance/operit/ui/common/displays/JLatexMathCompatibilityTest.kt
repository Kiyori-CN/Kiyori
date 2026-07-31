package com.ai.assistance.operit.ui.common.displays

import org.junit.Assert.assertEquals
import org.junit.Test
import org.scilab.forge.jlatexmath.Box
import ru.noties.jlatexmath.awt.Color
import ru.noties.jlatexmath.awt.Font
import ru.noties.jlatexmath.awt.Graphics2D
import ru.noties.jlatexmath.awt.RenderingHints
import ru.noties.jlatexmath.awt.Stroke
import ru.noties.jlatexmath.awt.font.FontRenderContext
import ru.noties.jlatexmath.awt.geom.AffineTransform
import ru.noties.jlatexmath.awt.geom.Line2D
import ru.noties.jlatexmath.awt.geom.Rectangle2D
import ru.noties.jlatexmath.awt.geom.RoundRectangle2D

class JLatexMathCompatibilityTest {
    @Test
    fun frameDraw_usesFillBeforeDrawingInnerFormula() {
        val graphics = RecordingGraphics2D()
        var innerMode: DrawMode? = null
        val inner =
            object : Box() {
                init {
                    width = 32f
                    height = 20f
                    depth = 4f
                }

                override fun draw(graphics2D: Graphics2D, x: Float, y: Float) {
                    innerMode = (graphics2D as RecordingGraphics2D).mode
                }

                override fun getLastFontId(): Int = 0
            }

        FillPreservingFramedBox(
            inner = inner,
            thickness = 2f,
            space = 4f,
        ).draw(graphics, 16f, 48f)

        assertEquals(DrawMode.FILL, innerMode)
        assertEquals(4, graphics.filledRectangles)
    }

    private enum class DrawMode {
        FILL,
        STROKE,
    }

    private class RecordingGraphics2D : Graphics2D {
        var mode = DrawMode.STROKE
        var filledRectangles = 0

        override fun getColor(): Color = error("Not used")

        override fun setColor(color: Color) = Unit

        override fun fill(rectangle: Rectangle2D.Float) {
            mode = DrawMode.FILL
            filledRectangles++
        }

        override fun draw(rectangle: Rectangle2D.Float) {
            mode = DrawMode.STROKE
        }

        override fun getStroke(): Stroke = error("Not used")

        override fun setStroke(stroke: Stroke) = Unit

        override fun getTransform(): AffineTransform = error("Not used")

        override fun translate(x: Double, y: Double) = Unit

        override fun scale(x: Double, y: Double) = Unit

        override fun getFont(): Font = error("Not used")

        override fun setFont(font: Font) = Unit

        override fun drawChars(
            data: CharArray,
            offset: Int,
            length: Int,
            x: Int,
            y: Int,
        ) = Unit

        override fun setTransform(transform: AffineTransform) = Unit

        override fun draw(line: Line2D.Float) {
            mode = DrawMode.STROKE
        }

        override fun rotate(angle: Double) = Unit

        override fun rotate(angle: Double, x: Double, y: Double) = Unit

        override fun drawArc(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            startAngle: Int,
            arcAngle: Int,
        ) {
            mode = DrawMode.STROKE
        }

        override fun fillArc(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            startAngle: Int,
            arcAngle: Int,
        ) {
            mode = DrawMode.FILL
        }

        override fun draw(rectangle: RoundRectangle2D.Float) {
            mode = DrawMode.STROKE
        }

        override fun getFontRenderContext(): FontRenderContext = error("Not used")

        override fun fillRect(x: Int, y: Int, width: Int, height: Int) {
            mode = DrawMode.FILL
        }

        override fun getRenderingHints(): RenderingHints = RenderingHints()

        override fun setRenderingHint(key: RenderingHints.Key, value: Any) = Unit

        override fun setRenderingHints(hints: RenderingHints) = Unit
    }
}
