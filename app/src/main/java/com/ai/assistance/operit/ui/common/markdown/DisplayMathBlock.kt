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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.gestures.ownAiContentHorizontalGestureOnTouch
import com.ai.assistance.operit.ui.common.gestures.rememberAiContentHorizontalGestureOwner
import com.ai.assistance.operit.ui.common.displays.LatexCache
import com.ai.assistance.operit.ui.common.displays.logLatexRenderFailure
import com.ai.assistance.operit.ui.common.displays.prepareLatexForJLatexMath
import ru.noties.jlatexmath.JLatexMathDrawable

@Composable
internal fun DisplayMathBlock(
    latexContent: String,
    textSizePx: Float,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val horizontalGestureOwner = rememberAiContentHorizontalGestureOwner()
    val expressionResult =
        remember(latexContent) {
            captureLatexException { parseDisplayMathExpression(latexContent) }
        }
    val expressionError = expressionResult.exceptionOrNull()
    if (expressionError != null) {
        LaunchedEffect(latexContent, expressionError) {
            logLatexRenderFailure(
                surface = "display-tag",
                originalFormula = latexContent,
                preparedFormula = null,
                error = expressionError,
            )
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
                    .ownAiContentHorizontalGestureOnTouch(
                        owner = horizontalGestureOwner,
                        enabled = scrollState.maxValue > 0,
                    )
                    .horizontalScroll(scrollState)
        ) {
            Layout(
                content = {
                    LatexFormulaTextView(
                        formula = expression.body,
                        sourceText = expression.body,
                        maxWidthPx = viewportWidthPx,
                        textSizePx = textSizePx,
                        textColor = textColor,
                    )
                    expression.tag?.let { tag ->
                        LatexFormulaTextView(
                            formula = tag.renderedLatex,
                            sourceText = tag.renderedLatex,
                            maxWidthPx = viewportWidthPx,
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
    maxWidthPx: Int,
    textSizePx: Float,
    textColor: Color,
) {
    val textColorArgb = textColor.toArgb()
    val preparation =
        remember(formula) {
            captureLatexException { prepareLatexForJLatexMath(formula.trim()) }
        }

    if (preparation.isFailure) {
        val error = requireNotNull(preparation.exceptionOrNull())
        LaunchedEffect(formula, error) {
            logLatexRenderFailure(
                surface = "display",
                originalFormula = formula,
                preparedFormula = null,
                error = error,
            )
        }
        LatexFormulaFailureText(
            sourceText = sourceText,
            maxWidthPx = maxWidthPx,
            textColor = textColor,
        )
        return
    }

    val prepared = requireNotNull(preparation.getOrNull())
    val drawableResult =
        remember(prepared.rendered, textSizePx, textColorArgb) {
            captureLatexException {
                LatexCache.getDrawable(
                    prepared.rendered,
                    JLatexMathDrawable.builder(prepared.rendered)
                        .textSize(textSizePx)
                        .padding(2)
                        .background(0x00000000)
                        .align(JLatexMathDrawable.ALIGN_LEFT)
                        .color(textColorArgb)
                )
            }
        }

    if (drawableResult.isFailure) {
        val error = requireNotNull(drawableResult.exceptionOrNull())
        LaunchedEffect(prepared, error) {
            logLatexRenderFailure(
                surface = "display",
                originalFormula = formula,
                preparedFormula = prepared,
                error = error,
            )
        }
        LatexFormulaFailureText(
            sourceText = sourceText,
            maxWidthPx = maxWidthPx,
            textColor = textColor,
        )
        return
    }

    val drawable = requireNotNull(drawableResult.getOrNull())
    val drawableLayout =
        remember(drawable, maxWidthPx) {
            resolveLatexDrawableLayout(
                viewportWidth = maxWidthPx,
                intrinsicWidth = drawable.intrinsicWidth,
                intrinsicHeight = drawable.intrinsicHeight,
            )
        }

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
            val formulaText = SpannableStringBuilder(INLINE_LATEX_PLACEHOLDER.toString())
            formulaText.setSpan(
                LatexDrawableSpan(
                    drawable = drawable,
                    targetWidth = drawableLayout.width,
                    targetHeight = drawableLayout.height,
                ),
                0,
                formulaText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            textView.apply {
                includeFontPadding = false
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                setTextColor(textColorArgb)
                typeface = Typeface.DEFAULT
                text = formulaText
            }
        }
    )
}

@Composable
private fun LatexFormulaFailureText(
    sourceText: String,
    maxWidthPx: Int,
    textColor: Color,
) {
    val density = LocalDensity.current
    val errorColor = MaterialTheme.colorScheme.error
    Column(
        modifier =
            Modifier
                .width(with(density) { maxWidthPx.coerceAtLeast(1).toDp() })
                .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = stringResource(R.string.common_render_failed),
                tint = errorColor,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.common_render_failed),
                color = errorColor,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        SelectionContainer {
            Text(
                text = sourceText,
                color = textColor,
                fontFamily = FontFamily.Monospace,
                softWrap = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private inline fun <T> captureLatexException(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (error: Exception) {
        Result.failure(error)
    }
}
