package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.completion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme.EditorTheme

/** 光标坐标已处于窗口空间，不能再次叠加 Popup 父锚点；下方不足时放到上方。 */
internal fun completionPopupPosition(cursor: IntOffset, window: IntSize, popup: IntSize, viewportBottom: Int): IntOffset {
    val bottom = minOf(window.height, viewportBottom).coerceAtLeast(0)
    val maxY = (bottom - popup.height).coerceAtLeast(0)
    val y = if (cursor.y.toLong() + popup.height <= bottom) cursor.y else cursor.y - popup.height
    return IntOffset(cursor.x.coerceIn(0, (window.width - popup.width).coerceAtLeast(0)), y.coerceIn(0, maxY))
}

/**
 * 代码补全弹出组件
 */
@Composable
fun CompletionPopup(
    completionItems: List<CompletionItem>,
    theme: EditorTheme,
    onItemSelected: (CompletionItem) -> Unit,
    onDismissRequest: () -> Unit,
    offset: IntOffset = IntOffset.Zero,
    viewportBottom: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    if (completionItems.isEmpty()) return
    
    val listState = rememberLazyListState()
    LaunchedEffect(completionItems) { listState.scrollToItem(0) }
    val positionProvider = remember(offset, viewportBottom) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
                completionPopupPosition(offset, windowSize, popupContentSize, viewportBottom)
        }
    }
    
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = false, // 设置为false，避免抢占焦点
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = true
        ) 
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 12.dp,
            color = theme.gutterBackground,
            contentColor = theme.textColor,
            border = BorderStroke(1.dp, theme.gutterBorder),
            modifier = modifier
                .width(260.dp)
                .heightIn(max = 220.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(completionItems) { item ->
                    CompletionItemRow(item, theme, onItemSelected)
                }
            }
        }
    }
}

/**
 * 补全项行组件
 */
@Composable
fun CompletionItemRow(
    item: CompletionItem,
    theme: EditorTheme,
    onItemSelected: (CompletionItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onItemSelected(item) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Icon(
            imageVector = when (item.kind) {
                CompletionItemKind.KEYWORD -> Icons.Default.Code
                CompletionItemKind.FUNCTION -> Icons.Default.Functions
                CompletionItemKind.METHOD -> Icons.Default.PlayArrow
                CompletionItemKind.VARIABLE -> Icons.Default.DataObject
                CompletionItemKind.CLASS -> Icons.Default.Class
                CompletionItemKind.PROPERTY -> Icons.AutoMirrored.Filled.Label
                CompletionItemKind.SNIPPET -> Icons.Default.ContentCopy
                else -> Icons.Default.TextFields
            },
            contentDescription = null,
            tint = when (item.kind) {
                CompletionItemKind.KEYWORD -> theme.keywordColor
                CompletionItemKind.FUNCTION, CompletionItemKind.METHOD -> theme.processingColor
                CompletionItemKind.VARIABLE -> theme.attributeColor
                CompletionItemKind.CLASS -> theme.typeColor
                CompletionItemKind.PROPERTY -> theme.attributeColor
                else -> theme.lineNumberColor
            },
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp)) // 减小间隔
        
        // 标签
        Column {
            Text(
                text = item.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = theme.textColor
            )
            
            // 详情信息
            if (item.detail != null) {
                Text(
                    text = item.detail,
                    fontSize = 12.sp,
                    color = theme.lineNumberColor
                )
            }
        }
    }
}
