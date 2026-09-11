package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerScrollThumb
import kotlinx.coroutines.delay

/** 只消费列表自身的滚动指标，不建立第二份滚动位置，也不拦截文件手势。 */
@Composable
internal fun FileManagerScrollIndicator(listState: LazyListState, modifier: Modifier = Modifier) {
    var visible by remember(listState) { mutableStateOf(false) }
    val scrolling = listState.isScrollInProgress
    LaunchedEffect(scrolling) {
        if (scrolling) visible = true
        else { delay(700); visible = false }
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(160), label = "fileScrollIndicator")
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        if (alpha <= 0f) return@Canvas
        val indicator = listState.scrollIndicatorState ?: return@Canvas
        val inset = 2.dp.toPx()
        val track = (size.height - inset * 2).coerceAtLeast(0f)
        val thumb = fileManagerScrollThumb(
            content = indicator.contentSize, viewport = indicator.viewportSize, offset = indicator.scrollOffset,
            track = track, thumbLength = 24.dp.toPx(),
            canScrollBackward = listState.canScrollBackward, canScrollForward = listState.canScrollForward,
        ) ?: return@Canvas
        val width = 3.dp.toPx()
        val x = (size.width - width - 1.dp.toPx()).coerceAtLeast(0f)
        // 仅显示位置滑块，不绘制贯穿整栏的轨道；未测量的不等高行沿用 LazyColumn 估算。
        drawRoundRect(color.copy(alpha = 0.58f * alpha), Offset(x, inset + thumb.top),
            Size(width, thumb.height), CornerRadius(width))
    }
}
