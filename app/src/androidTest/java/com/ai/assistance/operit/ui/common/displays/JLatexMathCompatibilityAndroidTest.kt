package com.ai.assistance.operit.ui.common.displays

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.scilab.forge.jlatexmath.Box
import org.scilab.forge.jlatexmath.MacroInfo
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
}
