package com.ai.assistance.operit.ui.features.websession.browser.chrome

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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal enum class WebSessionBrowserDrawerValue {
    HIDDEN,
    PARTIAL,
    EXPANDED,
}

private val BrowserDrawerAnimationSpec =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

@Composable
internal fun WebSessionBrowserBottomDrawer(
    isVisible: Boolean,
    layout: WebSessionBrowserChromeLayout,
    onDismissRequest: () -> Unit,
    onHidden: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetFraction = remember { Animatable(1f) }
    var drawerValue by remember { mutableStateOf(WebSessionBrowserDrawerValue.HIDDEN) }
    var dragStartFraction by remember { mutableFloatStateOf(1f) }
    var dragDistancePx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val statusBarInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val drawerHeight = (maxHeight - statusBarInset).coerceAtLeast(1.dp)
        val drawerHeightPx = with(density) { drawerHeight.toPx() }.coerceAtLeast(1f)
        // Child drawers are intentionally edge-to-edge. The reference layout uses the full
        // viewport so the scrim and drawer never leave horizontal seams on phones or tablets.
        val drawerWidth = maxWidth
        val partialOffsetFraction = 1f - layout.drawerPartialFraction

        LaunchedEffect(isVisible, partialOffsetFraction) {
            if (isVisible) {
                if (drawerValue == WebSessionBrowserDrawerValue.HIDDEN) {
                    drawerValue = WebSessionBrowserDrawerValue.PARTIAL
                }
                offsetFraction.animateTo(
                    targetValue = drawerValue.offsetFraction(partialOffsetFraction),
                    animationSpec = BrowserDrawerAnimationSpec,
                )
            } else {
                drawerValue = WebSessionBrowserDrawerValue.HIDDEN
                offsetFraction.animateTo(1f, BrowserDrawerAnimationSpec)
                onHidden()
            }
        }

        val visibleFraction = 1f - offsetFraction.value.coerceIn(0f, 1f)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.44f * visibleFraction),
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
                scope.launch {
                    offsetFraction.stop()
                    offsetFraction.snapTo(
                        (offsetFraction.value + deltaPx / drawerHeightPx).coerceIn(0f, 1f),
                    )
                }
            }
        val dragModifier =
            Modifier.draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = isVisible,
                onDragStarted = {
                    dragStartFraction = offsetFraction.value
                    dragDistancePx = 0f
                    scope.launch { offsetFraction.stop() }
                },
                onDragStopped = { velocityPxPerSecond ->
                    val startValue =
                        nearestDrawerValue(
                            fraction = dragStartFraction,
                            partialOffsetFraction = partialOffsetFraction,
                        )
                    val movedEnough = abs(dragDistancePx) >= drawerHeightPx * 0.08f
                    val flungEnough = abs(velocityPxPerSecond) >= 900f
                    val movingUp = dragDistancePx < 0f || velocityPxPerSecond < -900f
                    val movingDown = dragDistancePx > 0f || velocityPxPerSecond > 900f
                    val targetValue =
                        when {
                            !movedEnough && !flungEnough ->
                                nearestDrawerValue(
                                    fraction = offsetFraction.value,
                                    partialOffsetFraction = partialOffsetFraction,
                                )
                            movingUp -> WebSessionBrowserDrawerValue.EXPANDED
                            movingDown && startValue == WebSessionBrowserDrawerValue.EXPANDED ->
                                WebSessionBrowserDrawerValue.PARTIAL
                            movingDown -> WebSessionBrowserDrawerValue.HIDDEN
                            else ->
                                nearestDrawerValue(
                                    fraction = offsetFraction.value,
                                    partialOffsetFraction = partialOffsetFraction,
                                )
                        }
                    drawerValue = targetValue
                    scope.launch {
                        offsetFraction.stop()
                        offsetFraction.animateTo(
                            targetValue.offsetFraction(partialOffsetFraction),
                            BrowserDrawerAnimationSpec,
                        )
                        if (targetValue == WebSessionBrowserDrawerValue.HIDDEN) {
                            onDismissRequest()
                        }
                    }
                    dragDistancePx = 0f
                },
            )

        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .width(drawerWidth)
                    .height(drawerHeight)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (offsetFraction.value * drawerHeightPx).roundToInt(),
                        )
                    },
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            color = MaterialTheme.colorScheme.surface,
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
                            .height(28.dp)
                            .then(dragModifier)
                            .clickable(role = Role.Button) {
                                val targetValue =
                                    if (drawerValue == WebSessionBrowserDrawerValue.EXPANDED) {
                                        WebSessionBrowserDrawerValue.PARTIAL
                                    } else {
                                        WebSessionBrowserDrawerValue.EXPANDED
                                    }
                                drawerValue = targetValue
                                scope.launch {
                                    offsetFraction.stop()
                                    offsetFraction.animateTo(
                                        targetValue.offsetFraction(partialOffsetFraction),
                                        BrowserDrawerAnimationSpec,
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
                            .weight(1f)
                            .navigationBarsPadding()
                            .imePadding(),
                    content = content,
                )
            }
        }
    }
}

private fun WebSessionBrowserDrawerValue.offsetFraction(partialOffsetFraction: Float): Float =
    when (this) {
        WebSessionBrowserDrawerValue.HIDDEN -> 1f
        WebSessionBrowserDrawerValue.PARTIAL -> partialOffsetFraction
        WebSessionBrowserDrawerValue.EXPANDED -> 0f
    }

private fun nearestDrawerValue(
    fraction: Float,
    partialOffsetFraction: Float,
): WebSessionBrowserDrawerValue =
    WebSessionBrowserDrawerValue.entries.minBy { value ->
        abs(fraction - value.offsetFraction(partialOffsetFraction))
    }
