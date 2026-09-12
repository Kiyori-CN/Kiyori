package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.formatFileSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 图标独占左侧，名称最多四行，时间与大小共享右侧单行；触摸区域至少 48 dp。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileItem,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    onSwipeRight: () -> Unit = {},
    onToggleSelection: () -> Unit = onSwipeRight,
    itemSize: Float = 1f,
    selectionMode: Boolean = false,
) {
    val baseHeight = 48.dp
    val scale = itemSize.coerceIn(0.8f, 1.3f)
    val iconSize = 30.dp * scale
    val leftPadding = 5.dp
    val iconGap = 5.dp
    val maxDrag = leftPadding + iconSize
    val dateLabel = remember(file.lastModified, file.lastModifiedLabel) {
        if (file.lastModified > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified))
        else file.lastModifiedLabel.trim()
    }
    val latestSwipe by rememberUpdatedState(onSwipeRight)
    val latestLongClick by rememberUpdatedState(onItemLongClick)
    val latestClick by rememberUpdatedState(onItemClick)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var clickPending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var dragDistance by remember(file.name) { mutableFloatStateOf(0f) }
    var dragging by remember(file.name) { mutableStateOf(false) }
    val rowOffset by animateFloatAsState(dragDistance, animationSpec = if (dragging) snap() else spring(), label = "fileRowReturn")
    val showPress = (pressed || clickPending) && !dragging
    // 固定窗格裁切整行位移，图标、名称、元信息与背景同步移动，不侵入另一栏。
    Box(Modifier.fillMaxWidth().clipToBounds()) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = (baseHeight * scale).coerceAtLeast(48.dp))
            .graphicsLayer { translationX = rowOffset }
            .drawWithContent {
                drawContent()
                if (showPress) {
                    val depth = 4.dp.toPx().coerceAtMost(size.height / 2)
                    val shadow = Color.Black.copy(alpha = 0.12f)
                    drawRect(Brush.verticalGradient(listOf(shadow, Color.Transparent), endY = depth),
                        size = Size(size.width, depth))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, shadow), startY = size.height - depth, endY = size.height),
                        topLeft = Offset(0f, size.height - depth), size = Size(size.width, depth))
                }
            }
            .semantics {
                selected = isSelected
                if (file.name != "..") customActions = listOf(
                    CustomAccessibilityAction(if (isSelected) "取消选择" else "选择此项目") { onToggleSelection(); true },
                )
            }
            .pointerInput(file.name, maxDrag) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onHorizontalDrag = { change, amount ->
                        if (file.name != "..") {
                            dragDistance = (dragDistance + amount).coerceIn(-maxDrag.toPx(), maxDrag.toPx())
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        // 阈值使用 dp，左右完全一致；短横移和取消不改变选择，也不触发打开。
                        if (abs(dragDistance) >= maxDrag.toPx() * 0.65f) {
                            latestSwipe()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        dragDistance = 0f
                        dragging = false
                    },
                    onDragCancel = { dragDistance = 0f; dragging = false },
                )
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClickLabel = if (selectionMode && file.name != "..") "切换选择" else "打开",
                onClick = {
                    if (!clickPending) {
                        clickPending = true
                        scope.launch {
                            // 快速点击也保留一小段可见反馈；行离开组合时取消，防止迟到点击打开旧目录。
                            try { delay(80); latestClick() }
                            finally { clickPending = false }
                        }
                    }
                },
                onLongClickLabel = "文件操作",
                onLongClick = {
                    if (file.name != ".." && !clickPending) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestLongClick()
                    }
                },
            ),
        color = when {
            showPress && isSelected -> MaterialTheme.colorScheme.primaryContainer
            showPress -> MaterialTheme.colorScheme.surfaceContainerHighest
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            dragDistance != 0f -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surface
        },
        shape = RectangleShape,
    ) {
        Row(Modifier.padding(start = leftPadding, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FileManagerFileBadge(file, iconSize)
            Spacer(Modifier.width(iconGap))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(if (file.name == "..") "上一级" else file.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp * scale, lineHeight = 16.sp * scale),
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
                file.recycledOriginalPath?.let { original ->
                    Text(original, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.StartEllipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (file.recycleProblem != null) Text("回收记录需检查", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                if (file.name != "..") FileManagerFittedText(
                    text = listOfNotNull(dateLabel.ifBlank { "修改时间未知" },
                        if (!file.isDirectory) formatFileSize(file.size)
                        else file.directoryContentSize?.let(::formatFileSize)
                            ?: if (file.directorySizeUnavailable) "大小不可用" else "大小 —").joinToString(" "),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp * scale, lineHeight = 13.sp * scale),
                )
            }
        }
    }
    }
}
