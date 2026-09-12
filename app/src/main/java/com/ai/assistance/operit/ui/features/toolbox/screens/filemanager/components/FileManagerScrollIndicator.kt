package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerScrollThumb
import kotlinx.coroutines.channels.Channel
import kotlin.math.roundToInt

/** 拖柄只投影原列表的位置；合并快速拖动请求，避免积压协程造成松手后继续滚动。 */
@Composable
internal fun FileManagerScrollIndicator(listState: LazyListState, modifier: Modifier = Modifier) {
    val requests = remember(listState) { Channel<Float>(Channel.CONFLATED) }
    DisposableEffect(requests) { onDispose { requests.close() } }
    LaunchedEffect(listState, requests) {
        for (fraction in requests) {
            val indicator = listState.scrollIndicatorState ?: continue
            when {
                fraction <= 0f -> listState.scrollToItem(0)
                fraction >= 1f -> listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                else -> listState.scrollBy(fraction * (indicator.contentSize.toFloat() - indicator.viewportSize).coerceAtLeast(0f) - indicator.scrollOffset)
            }
        }
    }
    BoxWithConstraints(modifier) {
        val track = with(LocalDensity.current) { maxHeight.toPx() }
        val thumbLength = with(LocalDensity.current) { 48.dp.toPx() }
        val indicator = listState.scrollIndicatorState ?: return@BoxWithConstraints
        val thumb = fileManagerScrollThumb(indicator.contentSize, indicator.viewportSize, indicator.scrollOffset,
            track, thumbLength, listState.canScrollBackward, listState.canScrollForward) ?: return@BoxWithConstraints
        val travel = (track - thumb.height).coerceAtLeast(1f)
        var dragging by remember { mutableStateOf(false) }
        var dragFraction by remember { mutableFloatStateOf(0f) }
        val fraction = if (dragging) dragFraction else thumb.top / travel
        Surface(
            modifier = Modifier.align(Alignment.TopEnd)
                .offset { IntOffset(0, (fraction * travel).roundToInt()) }
                .width(32.dp).height(with(LocalDensity.current) { thumb.height.toDp() })
                .semantics {
                    contentDescription = "拖动以快速滚动列表"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { requests.trySend(it.coerceIn(0f, 1f)).isSuccess }
                }
                .draggable(rememberDraggableState { delta ->
                    dragFraction = (dragFraction + delta / travel).coerceIn(0f, 1f)
                    requests.trySend(dragFraction)
                }, Orientation.Vertical,
                    onDragStarted = { dragFraction = thumb.top / travel; dragging = true },
                    onDragStopped = { dragging = false }),
            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
            color = Color.White, contentColor = Color.Black,
            border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.12f)),
            shadowElevation = 0.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.KeyboardArrowUp, null, Modifier.size(20.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(20.dp))
            }
        }
    }
}
