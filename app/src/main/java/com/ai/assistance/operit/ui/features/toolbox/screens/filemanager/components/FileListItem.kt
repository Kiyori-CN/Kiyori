package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.getFileIcon
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.getFileIconColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DisplayMode {
    SINGLE_COLUMN,
    TWO_COLUMNS,
    THREE_COLUMNS,
}

private val unselectedFileRowColor = Color(0xFFFAFAFA)
private val selectedFileRowColor = Color(0xFF7DBEDC)

/**
 * MT 风格的文件项：行本身是连续的浅色带状区域，水平滑动负责多选。
 * 点击和长按仍由 combinedClickable 处理，三类手势不会改变既有打开/菜单入口。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileItem,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    onSwipeRight: () -> Unit = {},
    itemSize: Float = 1f,
    displayMode: DisplayMode = DisplayMode.SINGLE_COLUMN,
    compact: Boolean = false,
) {
    val isCompactTwoColumn = compact && displayMode == DisplayMode.TWO_COLUMNS
    val baseHeight = if (isCompactTwoColumn) 40.dp else 72.dp
    val baseIconSize = if (isCompactTwoColumn) 28.dp else {
        when (displayMode) {
            DisplayMode.SINGLE_COLUMN -> 40.dp
            DisplayMode.TWO_COLUMNS -> 36.dp
            DisplayMode.THREE_COLUMNS -> 32.dp
        }
    }
    val basePadding = if (isCompactTwoColumn) 5.dp else {
        when (displayMode) {
            DisplayMode.SINGLE_COLUMN -> 12.dp
            DisplayMode.TWO_COLUMNS -> 8.dp
            DisplayMode.THREE_COLUMNS -> 6.dp
        }
    }
    val baseSpacing = if (isCompactTwoColumn) 4.dp else {
        when (displayMode) {
            DisplayMode.SINGLE_COLUMN -> 8.dp
            DisplayMode.TWO_COLUMNS -> 6.dp
            DisplayMode.THREE_COLUMNS -> 4.dp
        }
    }
    val baseTextSize = if (isCompactTwoColumn) 14.sp else {
        when (displayMode) {
            DisplayMode.SINGLE_COLUMN -> 16.sp
            DisplayMode.TWO_COLUMNS -> 14.sp
            DisplayMode.THREE_COLUMNS -> 12.sp
        }
    }
    val titleLineHeight = if (isCompactTwoColumn) 17.sp else MaterialTheme.typography.bodyLarge.lineHeight
    val metadataLineHeight = if (isCompactTwoColumn) 12.sp else MaterialTheme.typography.bodySmall.lineHeight
    val metadataTextSize = if (isCompactTwoColumn) 10.sp else baseTextSize * 0.82f
    val dateLabel = formatDate(file)
    val hasMetadata = file.name != ".." && dateLabel.isNotBlank()
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(baseHeight * itemSize)
            .graphicsLayer { translationX = dragOffset }
            .pointerInput(file.name) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        // 紧凑双栏最多只移动一个图标距离，继续拖动保持边界不再位移。
                        val maxOffset = if (isCompactTwoColumn) {
                            (baseIconSize * itemSize).toPx()
                        } else {
                            160.dp.toPx()
                        }
                        dragOffset = (dragOffset + dragAmount).coerceIn(-maxOffset, maxOffset)
                        change.consume()
                    },
                    onDragEnd = {
                        val selectionThreshold = if (isCompactTwoColumn) {
                            (baseIconSize * itemSize).toPx() * 0.55f
                        } else {
                            48.dp.toPx()
                        }
                        // 只有从左向右的正向滑动建立选择，反向滑动只恢复原位。
                        if (dragOffset >= selectionThreshold) onSwipeRight()
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                )
            }
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick,
            ),
        color = if (isSelected) selectedFileRowColor else unselectedFileRowColor,
        contentColor = Color.Black,
        shape = RectangleShape,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = basePadding * itemSize, vertical = 4.dp * itemSize),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(baseIconSize * itemSize),
                color = getFileIconColor(file),
                contentColor = Color.White,
                shape = RoundedCornerShape(if (isCompactTwoColumn) 4.dp else 8.dp),
                tonalElevation = 0.dp,
            ) {
                Icon(
                    imageVector = getFileIcon(file),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(baseIconSize * 0.2f * itemSize),
                )
            }

            Spacer(modifier = Modifier.width(baseSpacing * itemSize))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = baseTextSize * itemSize,
                        lineHeight = titleLineHeight * itemSize,
                    ),
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasMetadata) {
                    Spacer(modifier = Modifier.height(2.dp * itemSize))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = metadataTextSize * itemSize,
                                lineHeight = metadataLineHeight * itemSize,
                            ),
                            color = Color(0xFF757575),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!file.isDirectory) {
                            Spacer(modifier = Modifier.width(6.dp * itemSize))
                            Text(
                                text = formatFileSize(file.size),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = metadataTextSize * itemSize,
                                    lineHeight = metadataLineHeight * itemSize,
                                ),
                                color = Color(0xFF757575),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String = when {
    size <= 0 -> "0B"
    size < 1024 -> "${size}B"
    size < 1024 * 1024 -> "${size / 1024}KB"
    size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)}MB"
    else -> "${size / (1024 * 1024 * 1024)}GB"
}

private fun formatDate(file: FileItem): String {
    if (file.lastModified > 0) {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(file.lastModified))
    }
    val raw = file.lastModifiedLabel.trim()
    if (raw.isBlank()) return ""
    val parsed = listOf(
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
    ).asSequence().mapNotNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                if (pattern.endsWith("'Z'")) timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(raw)
        }.getOrNull()
    }.firstOrNull()
    return parsed?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it)
    } ?: raw
}
