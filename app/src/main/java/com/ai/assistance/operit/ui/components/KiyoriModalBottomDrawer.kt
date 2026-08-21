package com.ai.assistance.operit.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * 全应用模态底部抽屉宿主。
 *
 * 业务调用方只持有“是否挂载”和自己的内容状态；关闭请求先交给共享三态抽屉完成 Hidden
 * 动画，再通知调用方卸载。否则直接清除业务状态会跳过浏览器同款退出动画，并再次分裂手势 owner。
 */
@Composable
internal fun KiyoriModalBottomDrawer(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    partialVisibleFraction: Float? = null,
    content: @Composable ColumnScope.(dismissDrawer: () -> Unit) -> Unit,
) {
    var isVisible by remember { mutableStateOf(true) }
    val dismissDrawer = { isVisible = false }

    BackHandler(enabled = isVisible, onBack = dismissDrawer)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val resolvedPartialVisibleFraction =
            partialVisibleFraction
                ?: resolveKiyoriBottomDrawerPartialFraction(
                    widthDp = maxWidth.value,
                    heightDp = maxHeight.value,
                )
        KiyoriDraggableBottomDrawer(
            isVisible = isVisible,
            partialVisibleFraction = resolvedPartialVisibleFraction,
            onDismissRequest = dismissDrawer,
            onHidden = onDismissRequest,
        ) {
            content(dismissDrawer)
        }
    }
}
