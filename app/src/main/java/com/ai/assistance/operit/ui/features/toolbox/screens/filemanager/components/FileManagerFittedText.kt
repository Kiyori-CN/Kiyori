package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/** 时间和容量必须保持单行完整；按实际字形测量适配，不按字符数猜测、不隐藏溢出文字。 */
@Composable
internal fun FileManagerFittedText(text: String, modifier: Modifier = Modifier, style: TextStyle) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.toPx() }
        val natural = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
        // 留一个像素给缩放后的字形取整，避免最后一位落到测量边界外。
        val ratio = if (natural > 0) ((width - 1f).coerceAtLeast(0f) / natural).coerceIn(0.01f, 1f) else 1f
        Text(text, style = style.copy(fontSize = style.fontSize * ratio,
            lineHeight = if (style.lineHeight.isSp) style.lineHeight else 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = false, maxLines = 1)
    }
}
