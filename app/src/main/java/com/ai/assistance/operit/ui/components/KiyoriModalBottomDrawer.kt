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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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

    // 选择器可能由 LazyColumn.item、Card 或滚动 Column 直接调用。若把 fillMaxSize 的抽屉
    // 留在调用点，它会参与父布局测量，产生空白占位并可能被滚动容器裁剪；Dialog 才是这里的
    // 全屏模态层边界。业务状态仍由调用方持有，Dialog 只负责把共享三态抽屉放到独立窗口层。
    Dialog(
        onDismissRequest = dismissDrawer,
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                // 遮罩必须延伸到状态栏，否则独立 Dialog 窗口会在顶部留下未遮盖的亮色条。
                // 内容安全区继续由共享抽屉处理，不能靠窗口整体避让系统栏。
                decorFitsSystemWindows = false,
            ),
    ) {
        KiyoriDialogOwnsScrim()
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
}
