package com.ai.assistance.operit.ui.common.markdown

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.common.displays.LatexCache
import com.ai.assistance.operit.util.AppLogger
import ru.noties.jlatexmath.JLatexMathDrawable

private const val TAG = "DisplayMathBlock"

@Composable
internal fun DisplayMathBlock(
    latexContent: String,
    textSizePx: Float,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val expressionResult =
        remember(latexContent) {
            runCatching { parseDisplayMathExpression(latexContent) }
                .onFailure {
                    AppLogger.w(TAG, "Display math tag syntax is invalid: $latexContent", it)
                }
        }
    val expression =
        expressionResult.getOrElse {
            DisplayMathExpression(body = latexContent.trim(), tag = null)
        }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportWidthPx = with(density) { maxWidth.roundToPx() }
        val tagGapPx = with(density) { 8.dp.roundToPx() }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
        ) {
            Layout(
                content = {
                    LatexFormulaTextView(
                        formula = expression.body,
                        sourceText = expression.body,
                        textSizePx = textSizePx,
                        textColor = textColor,
                    )
                    expression.tag?.let { tag ->
                        LatexFormulaTextView(
                            formula = tag.renderedLatex,
                            sourceText = tag.renderedLatex,
                            textSizePx = textSizePx,
                            textColor = textColor,
                        )
                    }
                }
            ) { measurables, constraints ->
                val childConstraints =
                    Constraints(
                        minWidth = 0,
                        maxWidth = Constraints.Infinity,
                        minHeight = 0,
                        maxHeight = constraints.maxHeight,
                    )
                val body = measurables[0].measure(childConstraints)
                val tag = measurables.getOrNull(1)?.measure(childConstraints)
                val resolved =
                    resolveDisplayMathLayout(
                        viewportWidth = viewportWidthPx,
                        bodyWidth = body.width,
                        bodyHeight = body.height,
                        tagWidth = tag?.width ?: 0,
                        tagHeight = tag?.height ?: 0,
                        tagGap = tagGapPx,
                    )

                layout(resolved.width, resolved.height) {
                    body.placeRelative(resolved.bodyX, resolved.bodyY)
                    if (tag != null && resolved.tagX != null && resolved.tagY != null) {
                        tag.placeRelative(resolved.tagX, resolved.tagY)
                    }
                }
            }
        }
    }
}

@Composable
private fun LatexFormulaTextView(
    formula: String,
    sourceText: String,
    textSizePx: Float,
    textColor: Color,
) {
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                includeFontPadding = false
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            }
        },
        update = { textView ->
            try {
                val drawable =
                    LatexCache.getDrawable(
                        formula.trim(),
                        JLatexMathDrawable.builder(formula)
                            .textSize(textSizePx)
                            .padding(2)
                            .background(0x00000000)
                            .align(JLatexMathDrawable.ALIGN_LEFT)
                            .color(textColor.toArgb())
                    )
                val formulaText = SpannableStringBuilder(INLINE_LATEX_PLACEHOLDER.toString())
                formulaText.setSpan(
                    LatexDrawableSpan(drawable),
                    0,
                    formulaText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                textView.apply {
                    includeFontPadding = false
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 0)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                    setTextColor(textColor.toArgb())
                    typeface = Typeface.DEFAULT
                    text = formulaText
                }
            } catch (error: Exception) {
                AppLogger.w(TAG, "Display math render failed; showing source: $formula", error)
                textView.apply {
                    text = sourceText
                    setTextColor(textColor.toArgb())
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                    typeface = Typeface.MONOSPACE
                }
            }
        }
    )
}
