package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import com.kiyori.design.theme.KiyoriUiShapes
import com.kiyori.design.theme.KiyoriSemanticTone
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.FolderOpen
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerPane
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerPaneState

@Composable
fun FileManagerDualPane(
    left: FileManagerPaneState,
    right: FileManagerPaneState,
    activePane: FileManagerPane,
    leftListState: LazyListState,
    rightListState: LazyListState,
    itemSize: Float,
    leftSelectedFiles: List<FileItem>,
    rightSelectedFiles: List<FileItem>,
    leftSelectionMode: Boolean,
    rightSelectionMode: Boolean,
    onPaneClick: (FileManagerPane) -> Unit,
    onRetry: (FileManagerPane) -> Unit,
    onItemClick: (FileManagerPane, FileItem) -> Unit,
    onItemLongClick: (FileManagerPane, FileItem) -> Unit,
    onItemSwipeRight: (FileManagerPane, FileItem) -> Unit,
    onItemToggleSelection: (FileManagerPane, FileItem) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        FileManagerPaneColumn(
            pane = FileManagerPane.LEFT,
            state = left,
            activePane = activePane,
            listState = leftListState,
            itemSize = itemSize,
            selectedFiles = leftSelectedFiles,
            selectionMode = leftSelectionMode,
            onPaneClick = onPaneClick,
            onRetry = onRetry,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onItemSwipeRight = onItemSwipeRight,
            onItemToggleSelection = onItemToggleSelection,
            modifier = Modifier.weight(1f),
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FileManagerPaneColumn(
            pane = FileManagerPane.RIGHT,
            state = right,
            activePane = activePane,
            listState = rightListState,
            itemSize = itemSize,
            selectedFiles = rightSelectedFiles,
            selectionMode = rightSelectionMode,
            onPaneClick = onPaneClick,
            onRetry = onRetry,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onItemSwipeRight = onItemSwipeRight,
            onItemToggleSelection = onItemToggleSelection,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FileManagerPaneColumn(
    pane: FileManagerPane,
    state: FileManagerPaneState,
    activePane: FileManagerPane,
    listState: LazyListState,
    itemSize: Float,
    selectedFiles: List<FileItem>,
    selectionMode: Boolean,
    onPaneClick: (FileManagerPane) -> Unit,
    onRetry: (FileManagerPane) -> Unit,
    onItemClick: (FileManagerPane, FileItem) -> Unit,
    onItemLongClick: (FileManagerPane, FileItem) -> Unit,
    onItemSwipeRight: (FileManagerPane, FileItem) -> Unit,
    onItemToggleSelection: (FileManagerPane, FileItem) -> Unit,
    modifier: Modifier,
) {
    val isActive = pane == activePane
    val latestActive by rememberUpdatedState(isActive)
    val latestPaneClick by rememberUpdatedState(onPaneClick)
    val selectedNames = remember(selectedFiles) { selectedFiles.map { it.name }.toSet() }
    Surface(
        modifier = modifier
            .fillMaxHeight().clipToBounds()
            .semantics { stateDescription = (if (pane == FileManagerPane.LEFT) "左栏" else "右栏") + if (isActive) "，当前操作栏" else "" }
            .drawWithContent {
                drawContent()
                // 仅非活动栏轻微下沉，使用中性渐隐内阴影，不绘制彩色边框或占用内容宽度。
                if (!isActive) {
                    val depth = 6.dp.toPx().coerceAtMost(size.minDimension / 2)
                    val shadow = Color.Black.copy(alpha = 0.065f)
                    drawRect(Brush.horizontalGradient(listOf(shadow, Color.Transparent), endX = depth),
                        size = Size(depth, size.height))
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, shadow), startX = size.width - depth, endX = size.width),
                        topLeft = Offset(size.width - depth, 0f), size = Size(depth, size.height))
                    drawRect(Brush.verticalGradient(listOf(shadow, Color.Transparent), endY = depth),
                        size = Size(size.width, depth))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, shadow), startY = size.height - depth, endY = size.height),
                        topLeft = Offset(0f, size.height - depth), size = Size(size.width, depth))
                }
            }
            // 在子项处理点击/滑动前记录按下，保证轻触或滑动栏位空白区也能切换焦点。
            .pointerInput(pane) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (!latestActive) latestPaneClick(pane)
                }
            },
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when {
                    state.error != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = state.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                TextButton(onClick = { onRetry(pane) }) { Text("重新读取") }
                            }
                        }
                    }
                    else -> {
                        // 未完成的目录读取不是空目录；导航中保持表面，避免闪过图标与加载提示。
                        if (!state.isLoading && state.files.none { it.name != ".." }) {
                            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                FileManagerIconBadge(Icons.Rounded.FolderOpen, KiyoriSemanticTone.ORANGE, 56.dp)
                                Text(if (state.filterQuery.isNotEmpty()) "没有匹配的项目" else if (state.environment == "recycle") "回收站为空" else "此目录为空",
                                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                                if (state.filterQuery.isNotEmpty()) Text("打开顶栏搜索可调整或清除定位筛选", style = MaterialTheme.typography.bodySmall)
                                else if (state.environment == "recycle") Text("移至回收站的项目会显示在这里，可恢复或永久删除。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            items(state.files, key = { file -> "${state.environment}:${state.path}:${file.name}" }, contentType = { "file" }) { file ->
                                FileListItem(
                                    file = file,
                                    isSelected = file.name in selectedNames,
                                    selectionMode = selectionMode,
                                    onItemClick = { onItemClick(pane, file) },
                                    onItemLongClick = { onItemLongClick(pane, file) },
                                    onSwipeRight = { onItemSwipeRight(pane, file) },
                                    onToggleSelection = { onItemToggleSelection(pane, file) },
                                    itemSize = itemSize,
                                )
                            }
                        }
                    }
                }
                if (!state.isLoading && state.error == null) {
                    androidx.compose.runtime.key(state.environment, state.path, state.filterQuery) {
                        FileManagerScrollIndicator(listState, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
