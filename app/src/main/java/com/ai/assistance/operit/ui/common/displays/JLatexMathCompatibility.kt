package com.ai.assistance.operit.ui.common.displays

import com.ai.assistance.operit.util.AppLogger
import org.scilab.forge.jlatexmath.Atom
import org.scilab.forge.jlatexmath.Box
import org.scilab.forge.jlatexmath.ColorAtom
import org.scilab.forge.jlatexmath.EmptyAtom
import org.scilab.forge.jlatexmath.MacroInfo
import org.scilab.forge.jlatexmath.RowAtom
import org.scilab.forge.jlatexmath.SpaceAtom
import org.scilab.forge.jlatexmath.SymbolAtom
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXEnvironment
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXParser
import org.scilab.forge.jlatexmath.TypedAtom
import ru.noties.jlatexmath.awt.Graphics2D
import ru.noties.jlatexmath.awt.geom.Rectangle2D

private fun advanceTeXParserPosition(parser: TeXParser, consumedLength: Int) {
    runCatching {
        val positionField = TeXParser::class.java.getDeclaredField("pos").apply {
            isAccessible = true
        }
        positionField.setInt(parser, parser.getPos() + consumedLength)
    }.onFailure {
        AppLogger.w("JLatexMathCompatibility", "Failed to advance TeXParser after color macro", it)
    }
}

@Suppress("UNUSED_PARAMETER")
internal class JLatexMathCompatMacros {
    fun color_macro(tp: TeXParser, args: Array<String>): Atom? {
        val color = ColorAtom.getColor(args[1])
        val remaining = tp.getStringFromCurrentPos()
        val nextContentIndex = remaining.indexOfFirst { !it.isWhitespace() }

        if (nextContentIndex == -1) {
            return null
        }

        return if (remaining[nextContentIndex] == '{') {
            ColorAtom(tp.getArgument(), null, color)
        } else {
            val atom = TeXFormula(remaining).root ?: EmptyAtom()
            advanceTeXParserPosition(tp, remaining.length)
            ColorAtom(atom, null, color)
        }
    }

    fun oiint_macro(tp: TeXParser, args: Array<String>): Atom {
        return buildClosedIntegralAtom(extraIntegrals = 1)
    }

    fun oiiint_macro(tp: TeXParser, args: Array<String>): Atom {
        return buildClosedIntegralAtom(extraIntegrals = 2)
    }

    fun boxed_macro(tp: TeXParser, args: Array<String>): Atom {
        return createFillPreservingFrameAtom(tp, args)
    }

    fun fbox_macro(tp: TeXParser, args: Array<String>): Atom {
        return createFillPreservingFrameAtom(tp, args)
    }

    private fun createFillPreservingFrameAtom(tp: TeXParser, args: Array<String>): Atom {
        val base = ContextualTeXFormula(tp, args[1]).root ?: EmptyAtom()
        return FillPreservingFrameAtom(base)
    }

    private fun buildClosedIntegralAtom(extraIntegrals: Int): Atom {
        val contourIntegral = SymbolAtom.get("oint").clone().apply {
            type_limits = TeXConstants.SCRIPT_NOLIMITS
        }
        val openIntegral = SymbolAtom.get("int").clone().apply {
            type_limits = TeXConstants.SCRIPT_NOLIMITS
        }

        val row = RowAtom(contourIntegral)
        repeat(extraIntegrals) {
            row.add(SpaceAtom(TeXConstants.UNIT_MU, -6f, 0f, 0f))
            row.add(openIntegral.clone())
        }
        row.lookAtLastAtom = true

        return TypedAtom(
            TeXConstants.TYPE_BIG_OPERATOR,
            TeXConstants.TYPE_BIG_OPERATOR,
            row
        )
    }
}

private class ContextualTeXFormula(
    parser: TeXParser,
    latex: String,
) : TeXFormula(parser, latex, false)

private class FillPreservingFrameAtom(
    private val base: Atom,
) : Atom() {
    init {
        type = base.type
    }

    override fun createBox(environment: TeXEnvironment): Box {
        val inner = base.createBox(environment)
        val thickness =
            environment.teXFont.getDefaultRuleThickness(environment.style)
        val space =
            FRAME_INTERSPACE_EM *
                SpaceAtom.getFactor(TeXConstants.UNIT_EM, environment)
        return FillPreservingFramedBox(
            inner = inner,
            thickness = thickness,
            space = space,
        )
    }

    private companion object {
        const val FRAME_INTERSPACE_EM = 0.65f
    }
}

/**
 * `jlatexmath-android` 的矩形描边会把共享 Paint 留在 STROKE，随后绘制的字符因此变成空心。
 * 使用四个填充矩形构成相同边框，使内部公式和框后的字符继续以 FILL 绘制。
 */
internal class FillPreservingFramedBox(
    private val inner: Box,
    private val thickness: Float,
    private val space: Float,
) : Box() {
    init {
        width = inner.width + 2f * thickness + 2f * space
        height = inner.height + thickness + space
        depth = inner.depth + thickness + space
        shift = inner.shift
    }

    override fun draw(graphics: Graphics2D, x: Float, y: Float) {
        val totalHeight = height + depth
        val sideHeight = (totalHeight - 2f * thickness).coerceAtLeast(0f)
        val top = y - height
        val bottom = y + depth - thickness
        val right = x + width - thickness

        graphics.fill(Rectangle2D.Float(x, top, width, thickness))
        graphics.fill(Rectangle2D.Float(x, bottom, width, thickness))
        if (sideHeight > 0f) {
            graphics.fill(Rectangle2D.Float(x, top + thickness, thickness, sideHeight))
            graphics.fill(Rectangle2D.Float(right, top + thickness, thickness, sideHeight))
        }

        inner.draw(graphics, x + thickness + space, y)
    }

    override fun getLastFontId(): Int = inner.lastFontId
}

internal object JLatexMathCompatibility {
    private const val TAG = "JLatexMathCompatibility"

    @Volatile
    private var registered = false

    fun ensureRegistered() {
        if (registered) return

        synchronized(this) {
            if (registered) return

            // 先触发 JLaTeXMath 内置命令初始化，随后再替换存在绘制缺陷的框命令。
            TeXFormula.symbolFormulaMappings.size
            registerCommandIfMissing("color", "color_macro")
            registerCommandIfMissing("oiint", "oiint_macro")
            registerCommandIfMissing("oiiint", "oiiint_macro")
            replaceCommand("boxed", "boxed_macro", argumentCount = 1)
            replaceCommand("fbox", "fbox_macro", argumentCount = 1)
            registerUnicodeFormulaIfMissing('\u222F', "\\oiint")
            registerUnicodeFormulaIfMissing('\u2230', "\\oiiint")

            registered = true
            AppLogger.d(TAG, "Registered LaTeX compatibility macros")
        }
    }

    private fun registerCommandIfMissing(commandName: String, methodName: String) {
        if (MacroInfo.Commands.containsKey(commandName)) return

        MacroInfo.Commands[commandName] =
            MacroInfo(
                JLatexMathCompatMacros::class.java.name,
                methodName,
                0f
            )
    }

    private fun replaceCommand(
        commandName: String,
        methodName: String,
        argumentCount: Int,
    ) {
        MacroInfo.Commands[commandName] =
            MacroInfo(
                JLatexMathCompatMacros::class.java.name,
                methodName,
                argumentCount.toFloat(),
            )
    }

    private fun registerUnicodeFormulaIfMissing(char: Char, formula: String) {
        if (TeXFormula.symbolFormulaMappings[char.code] != null) return
        TeXFormula.symbolFormulaMappings[char.code] = formula
    }
}
