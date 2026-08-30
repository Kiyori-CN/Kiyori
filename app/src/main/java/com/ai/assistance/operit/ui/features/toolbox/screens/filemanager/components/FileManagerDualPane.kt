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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.zIndex
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
    isMultiSelectMode: Boolean,
    selectedFiles: List<FileItem>,
    selectedFile: FileItem?,
    onPaneClick: (FileManagerPane) -> Unit,
    onItemClick: (FileManagerPane, FileItem) -> Unit,
    onItemLongClick: (FileManagerPane, FileItem) -> Unit,
    onItemSwipeRight: (FileManagerPane, FileItem) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Start,
    ) {
        FileManagerPaneColumn(
            pane = FileManagerPane.LEFT,
            state = left,
            activePane = activePane,
            listState = leftListState,
            itemSize = itemSize,
            isMultiSelectMode = isMultiSelectMode,
            selectedFiles = selectedFiles,
            selectedFile = selectedFile,
            onPaneClick = onPaneClick,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onItemSwipeRight = onItemSwipeRight,
            modifier = Modifier.weight(1f),
        )
        FileManagerPaneColumn(
            pane = FileManagerPane.RIGHT,
            state = right,
            activePane = activePane,
            listState = rightListState,
            itemSize = itemSize,
            isMultiSelectMode = isMultiSelectMode,
            selectedFiles = selectedFiles,
            selectedFile = selectedFile,
            onPaneClick = onPaneClick,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onItemSwipeRight = onItemSwipeRight,
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
    isMultiSelectMode: Boolean,
    selectedFiles: List<FileItem>,
    selectedFile: FileItem?,
    onPaneClick: (FileManagerPane) -> Unit,
    onItemClick: (FileManagerPane, FileItem) -> Unit,
    onItemLongClick: (FileManagerPane, FileItem) -> Unit,
    onItemSwipeRight: (FileManagerPane, FileItem) -> Unit,
    modifier: Modifier,
) {
    val isActive = pane == activePane
    Surface(
        modifier = modifier
            .fillMaxHeight()
            // 阴影和 zIndex 表示当前栏位于另一栏上方，同时保留两栏的完整点击区域。
            .zIndex(if (isActive) 1f else 0f)
            .shadow(
                // 顶/底/中间三条边都需要清晰的层级分隔，避免当前栏与另一栏融成一整片。
                elevation = if (isActive) 8.dp else 0.dp,
                shape = RectangleShape,
                clip = false,
            )
            // 在子项处理点击/滑动前记录按下，保证轻触或滑动栏位空白区也能切换焦点。
            .pointerInput(pane) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPaneClick(pane)
                }
            },
        color = androidx.compose.ui.graphics.Color(0xFFFAFAFA),
        tonalElevation = 0.dp,
    ) {
        when {
            state.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
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
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (state.isLoading && state.files.isEmpty()) {
                        item {
                            Text(
                                text = "...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            )
                        }
                    }
                    items(state.files, key = { file -> "${state.path}:${file.name}" }) { file ->
                        FileListItem(
                            file = file,
                            isSelected = pane == activePane && if (isMultiSelectMode) {
                                selectedFiles.contains(file)
                            } else {
                                selectedFile == file
                            },
                            onItemClick = { onItemClick(pane, file) },
                            onItemLongClick = { onItemLongClick(pane, file) },
                            onSwipeRight = { onItemSwipeRight(pane, file) },
                            itemSize = itemSize,
                            displayMode = DisplayMode.TWO_COLUMNS,
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}
