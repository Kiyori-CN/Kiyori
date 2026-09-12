package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import com.kiyori.design.theme.kiyoriSurfaceColors

import com.kiyori.design.theme.KiyoriSurfaceTokens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ContentCopy

import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.ui.features.websession.browser.chrome.*
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuTone
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuIconBadge
import com.ai.assistance.operit.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.formatFileSize
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriUiShapes
import com.kiyori.design.theme.KiyoriBrowserTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileContextMenu(
    showMenu: Boolean, onDismissRequest: () -> Unit, contextMenuFile: FileItem?,
    fullPath: String, environmentLabel: String, onExit: () -> Unit, onSettings: () -> Unit, onSelect: () -> Unit, onOpen: () -> Unit,
    onCopy: () -> Unit, onRename: () -> Unit, writing: Boolean,
    onMove: () -> Unit, onDelete: () -> Unit, onTools: () -> Unit, onZip: () -> Unit,
    onProperties: () -> Unit, onShare: () -> Unit, onBookmark: () -> Unit,
    onExtract: () -> Unit, onWorkspace: () -> Unit, onInvertSelection: () -> Unit, allSelected: Boolean,
    localActions: Boolean, selectionCount: Int,
    onClearSelection: () -> Unit, onSelectAll: () -> Unit,
    onPaste: () -> Unit, canPaste: Boolean, onShowTask: () -> Unit, hasTask: Boolean,
    sourceIsLeft: Boolean = true, recycleBin: Boolean = false, onRestore: () -> Unit = {},
    canSelect: Boolean = true, allArchives: Boolean = false,
) {
    if (!showMenu) return
    val hasItem = contextMenuFile != null && selectionCount > 0
    val singleItem = hasItem && selectionCount <= 1
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    fun close(action: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        visible = false
        scope.launch {
            delay(140)
            latestDismiss()
            action?.invoke()
        }
    }
    LaunchedEffect(Unit) { visible = true }
    val actions = listOf(
        MenuAction(if (recycleBin) "恢复" else "复制", if (recycleBin) Icons.Rounded.Restore else Icons.Outlined.ContentCopy, WebSessionBrowserMenuTone.ADD_BOOKMARK, !writing && hasItem && (localActions || recycleBin), if (recycleBin) onRestore else onCopy),
        MenuAction("移动", Icons.AutoMirrored.Rounded.DriveFileMove, WebSessionBrowserMenuTone.BOOKMARKS, !writing && localActions && hasItem && !recycleBin, onMove),
        MenuAction("粘贴", Icons.Outlined.ContentPaste, WebSessionBrowserMenuTone.HISTORY, canPaste, onPaste),
        MenuAction(if (recycleBin) "永久删除" else "删除", Icons.Rounded.DeleteOutline, WebSessionBrowserMenuTone.AD_MARKING, !writing && (localActions || recycleBin) && hasItem, onDelete),
        MenuAction(if (selectionCount > 1) "批量改名" else "重命名", Icons.Rounded.DriveFileRenameOutline, WebSessionBrowserMenuTone.PLUGINS, !writing && localActions && hasItem && !recycleBin, onRename),
        MenuAction("压缩", Icons.Rounded.FolderZip, WebSessionBrowserMenuTone.USER_AGENT, !writing && localActions && hasItem, onZip),
        MenuAction("解压", Icons.Rounded.Unarchive, WebSessionBrowserMenuTone.FLOATING_SNIFFER, !writing && localActions && hasItem && allArchives, onExtract),
        MenuAction("分享", Icons.Outlined.Share, WebSessionBrowserMenuTone.NETWORK_LOG, !writing && localActions && hasItem, onShare),
        MenuAction("属性", Icons.Rounded.Info, WebSessionBrowserMenuTone.DIAGNOSTICS, !writing && hasItem && (localActions || recycleBin), onProperties),
        MenuAction("工具箱", Icons.Rounded.Build, WebSessionBrowserMenuTone.TOOLBOX, !writing, onTools),
        MenuAction(if (allSelected) "全不选" else "全选", Icons.Rounded.SelectAll, WebSessionBrowserMenuTone.INCOGNITO, canSelect, if (allSelected) onClearSelection else onSelectAll),
        MenuAction("反选", Icons.Rounded.FlipToBack, WebSessionBrowserMenuTone.READER_MODE, canSelect, onInvertSelection),
        MenuAction("打开方式", Icons.AutoMirrored.Outlined.OpenInNew, WebSessionBrowserMenuTone.PAGE_SOURCE, singleItem && localActions && contextMenuFile?.isDirectory == false, onOpen),
        MenuAction("加书签", Icons.Rounded.BookmarkAdd, WebSessionBrowserMenuTone.DOWNLOADS, singleItem && !recycleBin, onBookmark),
        MenuAction("加工作区", Icons.Rounded.CreateNewFolder, WebSessionBrowserMenuTone.SITE_CONFIG, singleItem && !recycleBin && contextMenuFile?.isDirectory == true, onWorkspace),
    )
    // 复用浏览器菜单几何和动画：无展开锚点，手势只滚动内容，不能把菜单上拉为全屏。
    KiyoriBrowserTheme {
        Dialog(onDismissRequest = { close() }, properties = DialogProperties(
            usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false,
            // 全屏遮罩覆盖状态栏，避免窗口避让后露出顶部亮色条。
            decorFitsSystemWindows = false,
        )) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            // 遮罩由菜单动画唯一绘制，避免 Dialog 再叠一层 dim 导致比浏览器菜单更暗。
            SideEffect { window?.setDimAmount(0f) }
            BackHandler { close() }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                AnimatedVisibility(visible, enter = fadeIn(tween(140)), exit = fadeOut(tween(120))) {
                    Box(Modifier.fillMaxSize().background(kiyoriSurfaceColors().modalScrim).clickable { close() })
                }
                val maximumHeight = maxHeight * 0.85f
                AnimatedVisibility(visible, Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 4 },
                    exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { it / 4 }) {
                    Surface(shape = KiyoriUiShapes.sheet, color = kiyoriSurfaceColors().sheet,
                        shadowElevation = KiyoriSurfaceTokens.sheetElevation, modifier = Modifier.fillMaxWidth().heightIn(max = maximumHeight)) {
                        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                            .padding(start = WEB_SESSION_BROWSER_MENU_START_PADDING_DP.dp, top = WEB_SESSION_BROWSER_MENU_TOP_PADDING_DP.dp,
                                end = WEB_SESSION_BROWSER_MENU_END_PADDING_DP.dp, bottom = WEB_SESSION_BROWSER_MENU_BOTTOM_PADDING_DP.dp),
                            verticalArrangement = Arrangement.spacedBy(WEB_SESSION_BROWSER_MENU_ROW_SPACING_DP.dp)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                Text("${if (sourceIsLeft) "左栏" else "右栏"} · ${if (recycleBin) "回收站" else environmentLabel} · ${if (hasItem) "${selectionCount.coerceAtLeast(1)} 项" else "未选择项目"}", style = MaterialTheme.typography.labelMedium)
                                Text(fullPath, maxLines = 1, overflow = TextOverflow.StartEllipsis, style = MaterialTheme.typography.bodySmall)
                                if (selectionCount > 1 && !recycleBin) Text("打开方式、添加书签和工作区需单选", style = MaterialTheme.typography.labelSmall)
                                if (!localActions && !recycleBin) Text("此位置支持浏览与收藏；文件写操作尚未接入", style = MaterialTheme.typography.labelSmall)
                            }
                            actions.chunked(5).forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { action ->
                                        Surface(onClick = { close(action.action) }, enabled = action.enabled && !closing,
                                            color = kiyoriSurfaceColors().sheet, shape = KiyoriUiShapes.control,
                                            modifier = Modifier.weight(1f).alpha(if (action.enabled) 1f else 0.38f)) {
                                            Column(Modifier.padding(horizontal = WEB_SESSION_BROWSER_MENU_CELL_HORIZONTAL_PADDING_DP.dp,
                                                vertical = WEB_SESSION_BROWSER_MENU_CELL_VERTICAL_PADDING_DP.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                WebSessionBrowserMenuIconBadge(imageVector = action.icon, tone = action.tone, contentDescription = null, containerSize = WEB_SESSION_BROWSER_MENU_ICON_CONTAINER_SIZE_DP.dp, iconSize = WEB_SESSION_BROWSER_MENU_ICON_SIZE_DP.dp)
                                                Spacer(Modifier.height(WEB_SESSION_BROWSER_MENU_ICON_LABEL_SPACING_DP.dp))
                                                Text(action.label, Modifier.fillMaxWidth().height(WEB_SESSION_BROWSER_MENU_LABEL_HEIGHT_DP.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = WEB_SESSION_BROWSER_MENU_LABEL_SIZE_SP.sp, lineHeight = 12.sp, maxLines = 2,
                                                    textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(
                                    start = WEB_SESSION_BROWSER_MENU_BOTTOM_ROW_HORIZONTAL_PADDING_DP.dp,
                                    top = WEB_SESSION_BROWSER_MENU_BOTTOM_ROW_TOP_PADDING_DP.dp,
                                    end = WEB_SESSION_BROWSER_MENU_BOTTOM_ROW_HORIZONTAL_PADDING_DP.dp,
                                ), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.weight(WEB_SESSION_BROWSER_MENU_BOTTOM_SIDE_SLOT_WEIGHT.toFloat()), contentAlignment = Alignment.Center) {
                                    BottomMenuAction("关闭", R.drawable.ic_kiyori_tool_power, { close(onExit) }, WebSessionBrowserMenuTone.EXIT_BROWSER)
                                }
                                Box(Modifier.weight(WEB_SESSION_BROWSER_MENU_BOTTOM_CENTER_SLOT_WEIGHT.toFloat()), contentAlignment = Alignment.Center) {
                                    BottomMenuAction("收起", R.drawable.ic_kiyori_tool_collapse, { close() }, WebSessionBrowserMenuTone.COLLAPSE)
                                }
                                Box(Modifier.weight(WEB_SESSION_BROWSER_MENU_BOTTOM_SIDE_SLOT_WEIGHT.toFloat()), contentAlignment = Alignment.Center) {
                                    BottomMenuAction("设置", R.drawable.ic_kiyori_tool_settings, { close(onSettings) }, WebSessionBrowserMenuTone.SETTINGS)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class MenuAction(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tone: WebSessionBrowserMenuTone, val enabled: Boolean, val action: () -> Unit)
