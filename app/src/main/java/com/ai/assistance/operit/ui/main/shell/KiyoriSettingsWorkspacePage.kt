package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kiyori.design.theme.KiyoriSettingsTheme
import com.kiyori.design.theme.LocalKiyoriSettingsColors

internal const val KIYORI_SETTINGS_WORKSPACE_TOP_BAR_HEIGHT_DP = 56
internal const val KIYORI_SETTINGS_WORKSPACE_HORIZONTAL_PADDING_DP = 16
internal const val KIYORI_SETTINGS_WORKSPACE_VERTICAL_PADDING_DP = 12
internal const val KIYORI_SETTINGS_WORKSPACE_COLLAPSE_DISTANCE_DP =
    KIYORI_SETTINGS_HEADER_COLLAPSE_DISTANCE_DP

internal fun calculateKiyoriSettingsWorkspaceHeaderOffset(
    currentOffsetPx: Float,
    availableDeltaY: Float,
    collapseDistancePx: Float,
): Float {
    require(currentOffsetPx >= 0f) { "currentOffsetPx must not be negative" }
    require(collapseDistancePx > 0f) { "collapseDistancePx must be positive" }
    return (currentOffsetPx - availableDeltaY).coerceIn(0f, collapseDistancePx)
}

/**
 * 长表单、编辑器、统计和多标签页面使用的设置工作台。
 *
 * 页面继续持有自己的滚动与输入状态；工作台通过嵌套滚动读取真实的纵向滚动增量，复用
 * KiyoriCollapsingSettingsPage 的标题帧。这样不会再出现固定顶栏，也不需要给每个页面套第二个列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KiyoriSettingsWorkspacePage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: KiyoriSettingsNavigationIcon = KiyoriSettingsNavigationIcon.BACK,
    snackbarHostState: SnackbarHostState? = null,
    headerAction: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    KiyoriSettingsTheme {
        val colors = LocalKiyoriSettingsColors.current
        val density = LocalDensity.current
        val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val collapseDistancePx = with(density) {
            KIYORI_SETTINGS_WORKSPACE_COLLAPSE_DISTANCE_DP.dp.toPx()
        }
        var headerOffsetPx by remember { mutableFloatStateOf(0f) }
        val nestedScrollConnection = remember(collapseDistancePx) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y >= 0f) return Offset.Zero
                    val previousOffset = headerOffsetPx
                    val nextOffset = calculateKiyoriSettingsWorkspaceHeaderOffset(
                        currentOffsetPx = previousOffset,
                        availableDeltaY = available.y,
                        collapseDistancePx = collapseDistancePx,
                    )
                    headerOffsetPx = nextOffset
                    return Offset(x = 0f, y = previousOffset - nextOffset)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y <= 0f) return Offset.Zero
                    val previousOffset = headerOffsetPx
                    val nextOffset = calculateKiyoriSettingsWorkspaceHeaderOffset(
                        currentOffsetPx = previousOffset,
                        availableDeltaY = available.y,
                        collapseDistancePx = collapseDistancePx,
                    )
                    headerOffsetPx = nextOffset
                    return Offset(x = 0f, y = previousOffset - nextOffset)
                }
            }
        }
        val collapseProgress by remember(headerOffsetPx, collapseDistancePx) {
            derivedStateOf {
                (headerOffsetPx / collapseDistancePx).coerceIn(0f, 1f)
            }
        }
        val headerFrame = calculateKiyoriSettingsHeaderFrame(collapseProgress)
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(colors.pageBackground)
                    .nestedScroll(nestedScrollConnection),
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {},
                snackbarHost = {
                    snackbarHostState?.let { state ->
                        SnackbarHost(hostState = state)
                    }
                },
                floatingActionButton = {
                    floatingActionButton?.invoke()
                },
                containerColor = colors.pageBackground,
                contentColor = colors.primaryText,
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
            ) { innerPadding ->
                content(
                    PaddingValues(
                        top = statusBarHeight + headerFrame.contentHeightDp.dp,
                        bottom = innerPadding.calculateBottomPadding(),
                    ),
                )
            }
            KiyoriCollapsingSettingsHeader(
                title = title,
                onBack = onBack,
                navigationIcon = navigationIcon,
                headerAction = headerAction,
                headerActionWidth = 48.dp,
                statusBarHeight = statusBarHeight,
                frame = headerFrame,
                modifier = Modifier.zIndex(1f),
            )
        }
    }
}
