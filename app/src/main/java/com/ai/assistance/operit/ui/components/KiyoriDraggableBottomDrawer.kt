package com.ai.assistance.operit.ui.components

import com.kiyori.design.theme.kiyoriSurfaceColors

import com.kiyori.design.theme.KiyoriSurfaceTokens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.kiyori.design.theme.KiyoriUiShapes
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal enum class KiyoriBottomDrawerValue {
    HIDDEN,
    PARTIAL,
    EXPANDED,
}

internal const val KIYORI_BOTTOM_DRAWER_HANDLE_HEIGHT_DP = 28

private val KiyoriBottomDrawerAnimationSpec =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

@Composable
internal fun KiyoriDraggableBottomDrawer(
    isVisible: Boolean,
    partialVisibleFraction: Float,
    onDismissRequest: () -> Unit,
    onHidden: () -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    confirmDismiss: () -> Boolean = { true },
    content: @Composable ColumnScope.() -> Unit,
) {
    require(partialVisibleFraction in 0f..1f) {
        "partialVisibleFraction must be between 0 and 1"
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetFraction = remember { Animatable(1f) }
    var drawerValue by remember { mutableStateOf(KiyoriBottomDrawerValue.HIDDEN) }
    var dragStartFraction by remember { mutableFloatStateOf(1f) }
    var dragDistancePx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var draggedFraction by remember { mutableFloatStateOf(1f) }
    val presentedFraction = if (isDragging) draggedFraction else offsetFraction.value

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val statusBarInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val drawerHeight = (maxHeight - statusBarInset).coerceAtLeast(1.dp)
        val drawerHeightPx = with(density) { drawerHeight.toPx() }.coerceAtLeast(1f)
        val partialOffsetFraction = 1f - partialVisibleFraction
        val contentViewportHeight =
            resolveKiyoriBottomDrawerContentViewportHeight(
                drawerHeightDp = drawerHeight.value,
                offsetFraction = presentedFraction.coerceIn(0f, 1f),
            ).dp

        LaunchedEffect(isVisible, partialOffsetFraction) {
            if (isDragging) { offsetFraction.snapTo(draggedFraction); isDragging = false }
            if (isVisible) {
                if (drawerValue == KiyoriBottomDrawerValue.HIDDEN) {
                    drawerValue = KiyoriBottomDrawerValue.PARTIAL
                }
                offsetFraction.animateTo(
                    targetValue = drawerValue.offsetFraction(partialOffsetFraction),
                    animationSpec = KiyoriBottomDrawerAnimationSpec,
                )
            } else {
                drawerValue = KiyoriBottomDrawerValue.HIDDEN
                offsetFraction.animateTo(1f, KiyoriBottomDrawerAnimationSpec)
                onHidden()
            }
        }

        val visibleFraction = 1f - presentedFraction.coerceIn(0f, 1f)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = KiyoriSurfaceTokens.modalScrimAlpha * visibleFraction),
                    )
                    .clickable(
                        enabled = isVisible,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
        )

        val dragState =
            rememberDraggableState { deltaPx ->
                dragDistancePx += deltaPx
                // 每个触摸事件同步累加，不为每个像素启动协程，避免动画与拖动互相覆盖。
                draggedFraction = (dragStartFraction + dragDistancePx / drawerHeightPx).coerceIn(0f, 1f)
            }
        val dragModifier =
            Modifier.draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = isVisible && gesturesEnabled,
                onDragStarted = {
                    dragStartFraction = offsetFraction.value
                    dragDistancePx = 0f
                    draggedFraction = dragStartFraction
                    isDragging = true
                    scope.launch { offsetFraction.stop() }
                },
                onDragStopped = { velocityPxPerSecond ->
                    val startValue =
                        nearestKiyoriBottomDrawerValue(
                            fraction = dragStartFraction,
                            partialOffsetFraction = partialOffsetFraction,
                        )
                    val movedEnough = abs(dragDistancePx) >= drawerHeightPx * 0.08f
                    val flungEnough = abs(velocityPxPerSecond) >= 900f
                    val direction = kiyoriBottomDrawerDragDirection(dragDistancePx, velocityPxPerSecond)
                    val movingUp = direction < 0
                    val movingDown = direction > 0
                    val targetValue =
                        when {
                            !movedEnough && !flungEnough ->
                                nearestKiyoriBottomDrawerValue(
                                    fraction = draggedFraction,
                                    partialOffsetFraction = partialOffsetFraction,
                                )
                            movingUp -> KiyoriBottomDrawerValue.EXPANDED
                            movingDown && startValue == KiyoriBottomDrawerValue.EXPANDED ->
                                KiyoriBottomDrawerValue.PARTIAL
                            movingDown -> KiyoriBottomDrawerValue.HIDDEN
                            else ->
                                nearestKiyoriBottomDrawerValue(
                                    fraction = draggedFraction,
                                    partialOffsetFraction = partialOffsetFraction,
                                )
                        }
                    // 先由宿主确认关闭；带未保存内容的页面可以拒绝，不能先隐藏后困住用户。
                    val settledValue = if (targetValue == KiyoriBottomDrawerValue.HIDDEN && !confirmDismiss()) {
                        KiyoriBottomDrawerValue.PARTIAL
                    } else targetValue
                    drawerValue = settledValue
                    scope.launch {
                        offsetFraction.stop()
                        offsetFraction.snapTo(draggedFraction)
                        isDragging = false
                        offsetFraction.animateTo(
                            settledValue.offsetFraction(partialOffsetFraction),
                            KiyoriBottomDrawerAnimationSpec,
                        )
                        if (settledValue == KiyoriBottomDrawerValue.HIDDEN) onDismissRequest()
                    }
                    dragDistancePx = 0f
                },
            )

        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(drawerHeight)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (presentedFraction * drawerHeightPx).roundToInt(),
                        )
                    },
            shape = KiyoriUiShapes.sheet,
            color = kiyoriSurfaceColors().sheet,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val handleDescription =
                    "${stringResource(R.string.expand_verb)} / ${stringResource(R.string.collapse_verb)}"
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(KIYORI_BOTTOM_DRAWER_HANDLE_HEIGHT_DP.dp)
                            .then(dragModifier)
                            .clickable(enabled = isVisible && gesturesEnabled, role = Role.Button) {
                                val targetValue =
                                    if (drawerValue == KiyoriBottomDrawerValue.EXPANDED) {
                                        KiyoriBottomDrawerValue.PARTIAL
                                    } else {
                                        KiyoriBottomDrawerValue.EXPANDED
                                    }
                                drawerValue = targetValue
                                scope.launch {
                                    offsetFraction.stop()
                                    offsetFraction.animateTo(
                                        targetValue.offsetFraction(partialOffsetFraction),
                                        KiyoriBottomDrawerAnimationSpec,
                                    )
                                }
                            }
                            .semantics {
                                contentDescription = handleDescription
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Spacer(
                        modifier =
                            Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(999.dp),
                                ),
                    )
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // 抽屉 Surface 保持完整高度以稳定偏移动画；内容视口必须跟随实际可见高度，
                            // 否则部分展开时列表会测量到屏幕外，末尾项目与固定操作区无法触达。
                            .height(contentViewportHeight)
                            .navigationBarsPadding()
                            .imePadding(),
                    content = content,
                )
            }
        }
    }
}

internal fun resolveKiyoriBottomDrawerContentViewportHeight(
    drawerHeightDp: Float,
    offsetFraction: Float,
): Float {
    require(drawerHeightDp > 0f) { "drawerHeightDp must be positive" }
    require(offsetFraction in 0f..1f) { "offsetFraction must be between 0 and 1" }
    val visibleDrawerHeightDp = drawerHeightDp * (1f - offsetFraction)
    return (visibleDrawerHeightDp - KIYORI_BOTTOM_DRAWER_HANDLE_HEIGHT_DP).coerceAtLeast(0f)
}

/** 快速反向甩动以松手速度为准，慢拖才以总位移决定方向。 */
internal fun kiyoriBottomDrawerDragDirection(distancePx: Float, velocityPxPerSecond: Float): Int =
    when {
        abs(velocityPxPerSecond) >= 900f -> if (velocityPxPerSecond < 0f) -1 else 1
        distancePx < 0f -> -1
        distancePx > 0f -> 1
        else -> 0
    }

internal fun resolveKiyoriBottomDrawerPartialFraction(
    widthDp: Float,
    heightDp: Float,
): Float {
    require(widthDp > 0f) { "widthDp must be positive" }
    require(heightDp > 0f) { "heightDp must be positive" }
    return when {
        widthDp < 600f -> if (widthDp > heightDp) 0.78f else 0.64f
        widthDp < 840f -> 0.68f
        else -> 0.72f
    }
}

private fun KiyoriBottomDrawerValue.offsetFraction(partialOffsetFraction: Float): Float =
    when (this) {
        KiyoriBottomDrawerValue.HIDDEN -> 1f
        KiyoriBottomDrawerValue.PARTIAL -> partialOffsetFraction
        KiyoriBottomDrawerValue.EXPANDED -> 0f
    }

private fun nearestKiyoriBottomDrawerValue(
    fraction: Float,
    partialOffsetFraction: Float,
): KiyoriBottomDrawerValue =
    KiyoriBottomDrawerValue.entries.minBy { value ->
        abs(fraction - value.offsetFraction(partialOffsetFraction))
    }
