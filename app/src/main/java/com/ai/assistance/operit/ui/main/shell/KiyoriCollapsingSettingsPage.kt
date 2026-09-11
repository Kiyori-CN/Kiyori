package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kiyori.design.theme.KiyoriSettingsTheme
import com.kiyori.design.theme.KiyoriUiShapes
import com.kiyori.design.theme.LocalKiyoriSettingsColors

internal enum class KiyoriSettingsNavigationIcon {
    BACK,
    MENU,
}

internal data class KiyoriCollapsingSettingsHeaderFrame(
    val contentHeightDp: Float,
    val titleStartDp: Float,
    val titleTopDp: Float,
    val titleFontSizeSp: Float,
)

internal const val KIYORI_SETTINGS_HEADER_COLLAPSE_DISTANCE_DP = 72

internal fun calculateKiyoriSettingsHeaderCollapseProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    collapseDistancePx: Float,
): Float {
    require(firstVisibleItemIndex >= 0) { "firstVisibleItemIndex must not be negative" }
    require(firstVisibleItemScrollOffset >= 0) {
        "firstVisibleItemScrollOffset must not be negative"
    }
    require(collapseDistancePx > 0f) { "collapseDistancePx must be positive" }
    return if (firstVisibleItemIndex > 0) {
        1f
    } else {
        (firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
    }
}

internal fun calculateKiyoriSettingsHeaderFrame(
    collapseProgress: Float,
): KiyoriCollapsingSettingsHeaderFrame {
    require(collapseProgress in 0f..1f) { "collapseProgress must be within 0..1" }
    return KiyoriCollapsingSettingsHeaderFrame(
        contentHeightDp = lerpFloat(128f, 56f, collapseProgress),
        titleStartDp = lerpFloat(32f, 56f, collapseProgress),
        titleTopDp = lerpFloat(60f, 16f, collapseProgress),
        titleFontSizeSp = lerpFloat(26f, 20f, collapseProgress),
    )
}

internal fun calculateKiyoriSettingsHeaderTitleEndPadding(
    headerActionWidth: Dp?,
): Dp {
    require(headerActionWidth == null || headerActionWidth > 0.dp) {
        "headerActionWidth must be positive when an action is present"
    }
    return if (headerActionWidth == null) 16.dp else headerActionWidth + 16.dp
}

@Composable
internal fun KiyoriCollapsingSettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: KiyoriSettingsNavigationIcon = KiyoriSettingsNavigationIcon.BACK,
    navigationIconVisible: Boolean = true,
    collapseOnScroll: Boolean = true,
    headerAction: (@Composable () -> Unit)? = null,
    headerActionWidth: Dp = 48.dp,
    content: LazyListScope.() -> Unit,
) {
    KiyoriSettingsTheme {
        KiyoriCollapsingSettingsPageContent(
            title = title,
            onBack = onBack,
            navigationIcon = navigationIcon,
            navigationIconVisible = navigationIconVisible,
            collapseOnScroll = collapseOnScroll,
            headerAction = headerAction,
            headerActionWidth = headerActionWidth,
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
private fun KiyoriCollapsingSettingsPageContent(
    title: String,
    onBack: () -> Unit,
    navigationIcon: KiyoriSettingsNavigationIcon,
    navigationIconVisible: Boolean,
    collapseOnScroll: Boolean,
    headerAction: (@Composable () -> Unit)?,
    headerActionWidth: Dp,
    modifier: Modifier,
    content: LazyListScope.() -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val collapseDistancePx = with(density) {
        KIYORI_SETTINGS_HEADER_COLLAPSE_DISTANCE_DP.dp.toPx()
    }
    val listState = rememberLazyListState()
    val collapseProgress by
        remember(listState, collapseDistancePx, collapseOnScroll) {
            derivedStateOf {
                if (collapseOnScroll) {
                    calculateKiyoriSettingsHeaderCollapseProgress(
                        firstVisibleItemIndex = listState.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                        collapseDistancePx = collapseDistancePx,
                    )
                } else {
                    1f
                }
            }
        }
    val headerFrame = calculateKiyoriSettingsHeaderFrame(collapseProgress)
    val expandedHeaderFrame = calculateKiyoriSettingsHeaderFrame(if (collapseOnScroll) 0f else 1f)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.pageBackground),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "kiyori_settings_collapsing_header_space") {
                Spacer(
                    modifier =
                        Modifier.height(
                            statusBarHeight + expandedHeaderFrame.contentHeightDp.dp,
                        ),
                )
            }
            content()
            item(key = "kiyori_settings_bottom_space") {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        KiyoriCollapsingSettingsHeader(
            title = title,
            onBack = onBack,
            navigationIcon = navigationIcon,
            navigationIconVisible = navigationIconVisible,
            headerAction = headerAction,
            headerActionWidth = headerActionWidth,
            statusBarHeight = statusBarHeight,
            frame = headerFrame,
            modifier = Modifier.zIndex(1f),
        )
    }
}

@Composable
internal fun KiyoriSettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = KiyoriUiShapes.card,
        colors =
            CardDefaults.cardColors(
                containerColor = LocalKiyoriSettingsColors.current.cardBackground,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun KiyoriCollapsingSettingsHeader(
    title: String,
    onBack: () -> Unit,
    navigationIcon: KiyoriSettingsNavigationIcon,
    headerAction: (@Composable () -> Unit)?,
    headerActionWidth: Dp,
    statusBarHeight: androidx.compose.ui.unit.Dp,
    frame: KiyoriCollapsingSettingsHeaderFrame,
    modifier: Modifier = Modifier,
    navigationIconVisible: Boolean = true,
) {
    val colors = LocalKiyoriSettingsColors.current
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(statusBarHeight + frame.contentHeightDp.dp)
                .background(colors.pageBackground)
                .clipToBounds(),
    ) {
        if (navigationIconVisible) {
            IconButton(
                onClick = onBack,
                modifier =
                    Modifier
                        .offset(x = 8.dp, y = statusBarHeight + 4.dp)
                        .size(48.dp),
            ) {
                Icon(
                    imageVector =
                        when (navigationIcon) {
                            KiyoriSettingsNavigationIcon.BACK ->
                                Icons.AutoMirrored.Filled.ArrowBack
                            KiyoriSettingsNavigationIcon.MENU -> Icons.Default.Menu
                        },
                    contentDescription =
                        when (navigationIcon) {
                            KiyoriSettingsNavigationIcon.BACK -> "返回"
                            KiyoriSettingsNavigationIcon.MENU -> "菜单"
                        },
                    tint = colors.primaryText,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = title,
            fontSize = frame.titleFontSizeSp.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primaryText,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start =
                            if (navigationIconVisible) {
                                frame.titleStartDp.dp
                            } else {
                                16.dp
                            },
                        end =
                            calculateKiyoriSettingsHeaderTitleEndPadding(
                                headerActionWidth.takeIf { headerAction != null },
                            ),
                    )
                    .offset(y = statusBarHeight + frame.titleTopDp.dp)
                    .semantics { heading() },
        )
        if (headerAction != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = statusBarHeight + 4.dp)
                        .size(width = headerActionWidth, height = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                headerAction()
            }
        }
    }
}

private fun lerpFloat(
    start: Float,
    end: Float,
    fraction: Float,
): Float = start + (end - start) * fraction
