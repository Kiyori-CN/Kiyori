package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSessionState
import com.ai.assistance.operit.ui.features.player.PlayerSurfaceView
import kotlin.math.roundToInt

@Composable
internal fun WebSessionFloatingPlayer(
    session: PlayerSession,
    state: PlayerSessionState,
    onTogglePause: () -> Unit,
    onFullscreen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        var containerWidthPx by remember { mutableFloatStateOf(0f) }
        var containerHeightPx by remember { mutableFloatStateOf(0f) }
        var scale by remember(state.request?.requestId) { mutableFloatStateOf(1f) }
        var offset by remember(state.request?.requestId) { mutableStateOf(Offset.Zero) }
        var positioned by remember(state.request?.requestId) { mutableStateOf(false) }
        val horizontalMarginPx = with(density) { 12.dp.toPx() }
        val verticalMarginPx = with(density) { 12.dp.toPx() }
        val maximumBaseWidthPx = with(density) { 360.dp.toPx() }
        val minimumBaseWidthPx = with(density) { 180.dp.toPx() }
        val availableBaseWidthPx = (containerWidthPx - horizontalMarginPx * 2f).coerceAtLeast(1f)
        val baseWidthPx =
            (containerWidthPx * 0.66f)
                .coerceAtMost(maximumBaseWidthPx)
                .coerceAtMost(availableBaseWidthPx)
                .coerceAtLeast(minimumBaseWidthPx.coerceAtMost(availableBaseWidthPx))
        val playerWidthPx = (baseWidthPx * scale).coerceAtMost(availableBaseWidthPx)
        val playerHeightPx = playerWidthPx * 9f / 16f
        val maximumX = (containerWidthPx - playerWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx)
        val maximumY = (containerHeightPx - playerHeightPx - verticalMarginPx).coerceAtLeast(verticalMarginPx)

        LaunchedEffect(containerWidthPx, containerHeightPx, playerWidthPx, playerHeightPx) {
            if (containerWidthPx <= 0f || containerHeightPx <= 0f) return@LaunchedEffect
            if (!positioned) {
                offset = Offset(maximumX, maximumY)
                positioned = true
            } else {
                offset = Offset(
                    offset.x.coerceIn(horizontalMarginPx, maximumX),
                    offset.y.coerceIn(verticalMarginPx, maximumY),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        containerWidthPx = it.width.toFloat()
                        containerHeightPx = it.height.toFloat()
                    },
        ) {
            Box(
                modifier =
                    Modifier
                        .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                        .width(with(density) { playerWidthPx.toDp() })
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .pointerInput(maximumX, maximumY, playerWidthPx) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.72f, 1.5f)
                                offset =
                                    Offset(
                                        (offset.x + pan.x).coerceIn(horizontalMarginPx, maximumX),
                                        (offset.y + pan.y).coerceIn(verticalMarginPx, maximumY),
                                    )
                            }
                        },
            ) {
                AndroidView(
                    factory = { context -> PlayerSurfaceView(context, session, mediaOverlay = true) },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(modifier = Modifier.fillMaxWidth().background(Color(0x66000000))) {
                    Text(
                        text = state.request?.title.orEmpty(),
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 8.dp, end = 76.dp),
                    )
                    Row(modifier = Modifier.align(Alignment.TopEnd)) {
                        IconButton(onClick = onFullscreen, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Fullscreen, "全屏", tint = Color.White)
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Close, "关闭", tint = Color.White)
                        }
                    }
                }
                if (!state.loading) {
                    IconButton(
                        onClick = onTogglePause,
                        modifier = Modifier.align(Alignment.Center).size(48.dp).background(Color(0x66000000)),
                    ) {
                        Icon(
                            if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            if (state.paused) "播放" else "暂停",
                            tint = Color.White,
                        )
                    }
                }
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }
                state.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xB3000000)).padding(8.dp),
                    )
                }
                val progress =
                    if (state.durationSeconds > 0.0) {
                        (state.positionSeconds / state.durationSeconds).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.24f),
                )
            }
        }
    }
}
