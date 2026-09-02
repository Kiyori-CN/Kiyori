package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import android.view.WindowManager
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerPane

private val fileMenuTextColor = Color(0xFF111111)
private val fileMenuDisabledColor = Color(0xFFB0B0B0)
private val fileMenuAccentColor = Color(0xFF42A5F5)

/**
 * MT 风格的居中长按菜单。按钮暂时只保留视觉和点击目标，业务动作在后续增量接回。
 */
@Composable
fun FileContextMenu(
    showMenu: Boolean,
    onDismissRequest: () -> Unit,
    contextMenuFile: FileItem?,
    sourcePane: FileManagerPane,
    leftPath: String,
    rightPath: String,
    leftEnvironment: String?,
    rightEnvironment: String?,
) {
    if (!showMenu || contextMenuFile == null) return

    val isFolder = contextMenuFile.isDirectory
    val sourcePath = if (sourcePane == FileManagerPane.LEFT) leftPath else rightPath
    val targetPath = if (sourcePane == FileManagerPane.LEFT) rightPath else leftPath
    val sourceEnvironment = if (sourcePane == FileManagerPane.LEFT) leftEnvironment else rightEnvironment
    val targetEnvironment = if (sourcePane == FileManagerPane.LEFT) rightEnvironment else leftEnvironment
    val moveEnabled = !isFolder || sourcePath != targetPath || sourceEnvironment != targetEnvironment
    val copyMoveArrow = if (sourcePane == FileManagerPane.LEFT) "->" else "<-"
    val copyMoveArrowBeforeLabel = sourcePane == FileManagerPane.RIGHT
    // 更新后的同尺寸截图显示 Kiyori 菜单整体比 MT 低 65 px；用 dp 固化目标设备上的窗口偏移。
    val menuVerticalOffsetPx = with(LocalDensity.current) {
        (-18.5).dp.roundToPx()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0f)
                window.setGravity(Gravity.CENTER)
                window.attributes = window.attributes.apply { y = menuVerticalOffsetPx }
            }
        }
        Surface(
            modifier = Modifier.width(320.dp).height(269.dp),
            shape = RoundedCornerShape(3.dp),
            color = Color(0xFFFAFAFA),
            contentColor = fileMenuTextColor,
            shadowElevation = 8.dp,
        ) {
            Column {
                Box(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "带",
                            modifier = Modifier.padding(start = 10.dp),
                            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                            color = Color(0xFF666666),
                        )
                        Text(
                            text = " • ",
                            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                            color = fileMenuAccentColor,
                        )
                        Text(
                            text = "的菜单表示可以长按触发单窗口操作",
                            modifier = Modifier.weight(1f),
                            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                            color = Color(0xFF666666),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable(onClick = onDismissRequest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = Color(0xFF666666),
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = Color(0xFFE0E0E0))
                Column(modifier = Modifier.fillMaxWidth()) {
                    FileContextMenuRow(
                        left = {
                            FileContextMenuAction(
                                icon = Icons.Default.ContentCopy,
                                label = "复制",
                                arrow = copyMoveArrow,
                                arrowBeforeLabel = copyMoveArrowBeforeLabel,
                            )
                        },
                        right = {
                            FileContextMenuAction(
                                icon = Icons.Default.ContentCut,
                                label = "移动",
                                arrow = copyMoveArrow,
                                arrowBeforeLabel = copyMoveArrowBeforeLabel,
                                enabled = moveEnabled,
                            )
                        },
                    )
                    FileContextMenuRow(
                        left = { FileContextMenuAction(Icons.Default.Delete, "删除") },
                        right = { FileContextMenuAction(Icons.Default.Edit, "重命名") },
                    )
                    FileContextMenuRow(
                        left = { FileContextMenuAction(Icons.Default.Build, "工具") },
                        right = { FileContextMenuAction(Icons.Default.FileDownload, "压缩") },
                    )
                    FileContextMenuRow(
                        left = { FileContextMenuAction(Icons.Default.Error, "属性") },
                        right = { FileContextMenuAction(Icons.Default.Share, "分享", enabled = !isFolder) },
                    )
                    FileContextMenuRow(
                        left = { FileContextMenuAction(Icons.Default.Done, "打开方式…", enabled = !isFolder) },
                        right = { FileContextMenuAction(Icons.Default.CollectionsBookmark, "添加书签") },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileContextMenuRow(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { left() }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { right() }
    }
}

@Composable
private fun FileContextMenuAction(
    icon: ImageVector,
    label: String,
    arrow: String? = null,
    arrowBeforeLabel: Boolean = false,
    enabled: Boolean = true,
) {
    val tint = if (enabled) fileMenuTextColor else fileMenuDisabledColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(enabled = enabled) {}
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        if (arrow != null && arrowBeforeLabel) {
            Text(
                text = arrow,
                style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp),
                color = tint,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp),
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        if (arrow != null && !arrowBeforeLabel) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = arrow,
                style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp),
                color = tint,
                maxLines = 1,
            )
        }
        if (arrow != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .background(
                        color = if (enabled) fileMenuAccentColor else fileMenuDisabledColor,
                        shape = CircleShape,
                    ),
            )
        }
    }
}
