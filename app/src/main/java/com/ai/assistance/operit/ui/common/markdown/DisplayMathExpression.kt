package com.ai.assistance.operit.ui.common.markdown

import kotlin.math.roundToInt

internal const val MIN_LATEX_FORMULA_SCALE = 0.8f

internal data class DisplayMathTag(
    val latex: String,
    val parenthesized: Boolean,
) {
    val renderedLatex: String
        get() = if (parenthesized) "($latex)" else latex
}

internal data class DisplayMathExpression(
    val body: String,
    val tag: DisplayMathTag?,
)

internal data class DisplayMathLayoutResult(
    val width: Int,
    val height: Int,
    val bodyX: Int,
    val bodyY: Int,
    val tagX: Int?,
    val tagY: Int?,
)

internal data class LatexDrawableLayout(
    val width: Int,
    val height: Int,
    val scale: Float,
    val requiresHorizontalScroll: Boolean,
)

/**
 * 轻微超宽时缩小公式；超过可接受比例后保留可读尺寸，由公式区域横向滚动承载。
 */
internal fun resolveLatexDrawableLayout(
    viewportWidth: Int,
    intrinsicWidth: Int,
    intrinsicHeight: Int,
    minimumScale: Float = MIN_LATEX_FORMULA_SCALE,
): LatexDrawableLayout {
    val safeViewportWidth = viewportWidth.coerceAtLeast(1)
    val safeIntrinsicWidth = intrinsicWidth.coerceAtLeast(1)
    val safeIntrinsicHeight = intrinsicHeight.coerceAtLeast(1)
    val safeMinimumScale =
        if (minimumScale.isFinite()) {
            minimumScale.coerceIn(0.1f, 1f)
        } else {
            MIN_LATEX_FORMULA_SCALE
        }
    val fitScale = safeViewportWidth.toFloat() / safeIntrinsicWidth.toFloat()
    val scale =
        if (fitScale >= 1f) {
            1f
        } else {
            maxOf(fitScale, safeMinimumScale)
        }

    val width = (safeIntrinsicWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (safeIntrinsicHeight * scale).roundToInt().coerceAtLeast(1)
    return LatexDrawableLayout(
        width = width,
        height = height,
        scale = scale,
        requiresHorizontalScroll = width > safeViewportWidth,
    )
}

/**
 * 从显示公式中提取顶层 `\tag{...}`。
 *
 * JLaTeXMath 不支持 amsmath 的公式编号语义。这里保留公式主体交给原渲染器，
 * 编号由 Compose 布局独立测量和右对齐；如果把编号直接拼进公式主体，窄屏时
 * 无法同时保证主体居中和编号靠右。
 */
internal fun parseDisplayMathExpression(source: String): DisplayMathExpression {
    val formula = source.trim()
    var braceDepth = 0
    var index = 0
    var tag: DisplayMathTag? = null
    var tagStart = -1
    var tagEnd = -1

    while (index < formula.length) {
        when (formula[index]) {
            '\\' -> {
                if (index + 1 >= formula.length) {
                    index++
                    continue
                }

                val next = formula[index + 1]
                if (!next.isLetter()) {
                    index += 2
                    continue
                }

                var commandEnd = index + 2
                while (commandEnd < formula.length && formula[commandEnd].isLetter()) {
                    commandEnd++
                }

                val command = formula.substring(index + 1, commandEnd)
                if (braceDepth != 0 || command != "tag") {
                    index = commandEnd
                    continue
                }

                require(tag == null) { "Display math contains more than one top-level \\tag" }

                var cursor = commandEnd
                val starred = cursor < formula.length && formula[cursor] == '*'
                if (starred) cursor++
                while (cursor < formula.length && formula[cursor].isWhitespace()) {
                    cursor++
                }
                require(cursor < formula.length && formula[cursor] == '{') {
                    "Display math \\tag must be followed by a braced argument"
                }

                val group = readBalancedGroup(formula, cursor)
                tag =
                    DisplayMathTag(
                        latex = group.content.trim(),
                        parenthesized = !starred,
                    )
                tagStart = index
                tagEnd = group.endExclusive
                index = group.endExclusive
            }

            '{' -> {
                braceDepth++
                index++
            }

            '}' -> {
                if (braceDepth > 0) braceDepth--
                index++
            }

            else -> index++
        }
    }

    if (tag == null) {
        return DisplayMathExpression(body = formula, tag = null)
    }

    val body = (formula.substring(0, tagStart) + formula.substring(tagEnd)).trim()
    return DisplayMathExpression(body = body, tag = tag)
}

internal fun resolveDisplayMathLayout(
    viewportWidth: Int,
    bodyWidth: Int,
    bodyHeight: Int,
    tagWidth: Int,
    tagHeight: Int,
    tagGap: Int,
): DisplayMathLayoutResult {
    val safeViewportWidth = viewportWidth.coerceAtLeast(0)
    val safeBodyWidth = bodyWidth.coerceAtLeast(0)
    val safeBodyHeight = bodyHeight.coerceAtLeast(0)
    val safeTagWidth = tagWidth.coerceAtLeast(0)
    val safeTagHeight = tagHeight.coerceAtLeast(0)
    val safeTagGap = tagGap.coerceAtLeast(0)

    val hasTag = safeTagWidth > 0
    val requiredWidth =
        if (hasTag) {
            safeBodyWidth + 2 * (safeTagWidth + safeTagGap)
        } else {
            safeBodyWidth
        }
    val width = maxOf(safeViewportWidth, requiredWidth)
    val height = maxOf(safeBodyHeight, safeTagHeight)
    val bodyX = (width - safeBodyWidth) / 2
    val bodyY = (height - safeBodyHeight) / 2
    val tagX = if (hasTag) width - safeTagWidth else null
    val tagY = if (hasTag) (height - safeTagHeight) / 2 else null

    return DisplayMathLayoutResult(
        width = width,
        height = height,
        bodyX = bodyX,
        bodyY = bodyY,
        tagX = tagX,
        tagY = tagY,
    )
}

private data class BalancedGroup(
    val content: String,
    val endExclusive: Int,
)

private fun readBalancedGroup(source: String, openingBraceIndex: Int): BalancedGroup {
    var depth = 1
    var index = openingBraceIndex + 1

    while (index < source.length) {
        when (source[index]) {
            '\\' -> index += if (index + 1 < source.length) 2 else 1
            '{' -> {
                depth++
                index++
            }
            '}' -> {
                depth--
                if (depth == 0) {
                    return BalancedGroup(
                        content = source.substring(openingBraceIndex + 1, index),
                        endExclusive = index + 1,
                    )
                }
                index++
            }
            else -> index++
        }
    }

    throw IllegalArgumentException("Display math \\tag contains an unclosed argument")
}
