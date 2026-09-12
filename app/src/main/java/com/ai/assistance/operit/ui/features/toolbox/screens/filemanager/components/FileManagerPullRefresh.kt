package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** 只消费列表未消费的竖向距离，横向选择仍由文件行处理；惯性到顶不会触发。 */
@Composable
internal fun FileManagerPullRefresh(listState: LazyListState, refreshing: Boolean, enabled: Boolean,
    onRefresh: () -> Unit, content: @Composable () -> Unit,
) {
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    var distance by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val latestRefresh by rememberUpdatedState(onRefresh)
    val latestEnabled by rememberUpdatedState(enabled && !refreshing)
    val haptics = LocalHapticFeedback.current
    val armed = distance >= threshold
    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val connection = remember(listState, threshold) { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y >= 0 || distance <= 0 || settling) return Offset.Zero
            val used = minOf(distance, -available.y)
            distance -= used
            return Offset(0f, -used)
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!latestEnabled || settling || source != NestedScrollSource.UserInput || available.y <= 0 || listState.canScrollBackward) return Offset.Zero
            distance = (distance + available.y * (0.55f / (1f + distance / threshold))).coerceAtMost(threshold * 1.8f)
            return Offset(0f, available.y)
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (distance <= 0) return Velocity.Zero
            settling = true
            try {
                if (distance >= threshold && latestEnabled) latestRefresh()
                Animatable(distance).animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 380f)) { distance = value }
            } finally { distance = 0f; settling = false }
            return Velocity(0f, available.y)
        }
    } }
    Box(Modifier.fillMaxSize().nestedScroll(connection)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = distance }) { content() }
        if (distance > 0 || refreshing) Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(32.dp)
                .graphicsLayer { alpha = if (refreshing) 1f else (distance / (threshold * 0.4f)).coerceIn(0f, 1f) },
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else CircularProgressIndicator(progress = { (distance / threshold).coerceIn(0f, 1f) },
                    modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}
