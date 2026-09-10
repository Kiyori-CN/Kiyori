package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import android.os.Environment
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.websession.browser.chrome.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriUiShapes
import androidx.compose.foundation.clickable
import com.ai.assistance.operit.ui.components.KiyoriModalBottomDrawer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FileManagerTopBar(
    currentPath: String, environmentLabel: String,
    folderCount: Int, fileCount: Int, selectedCount: Int, storageLabel: String,
    isSearching: Boolean, showHiddenFiles: Boolean, sortMode: FileManagerSortMode,
    onExitFileManager: () -> Unit, onPathClick: () -> Unit, onOpenStorageDrawer: () -> Unit,
    onShowSearchDialog: () -> Unit, onSelectAll: () -> Unit,
    onClearSelection: () -> Unit, onToggleHiddenFiles: () -> Unit,
    onSelectSort: (FileManagerSortMode) -> Unit, onOpenLinux: () -> Unit,
    onNew: () -> Unit, onExitSearch: () -> Unit, canCreate: Boolean,
    sortDescending: Boolean, onToggleSortDirection: () -> Unit,
    onInvertSelection: () -> Unit,
    filterLabel: String = "",
    clipboardCount: Int, onPaste: () -> Unit, onClearClipboard: () -> Unit,
    hasTask: Boolean, onShowTask: () -> Unit,
) {
    var showOptions by remember { mutableStateOf(false) }
    var afterOptionsClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExitFileManager) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回文件管理首页") }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text("文件管理", style = MaterialTheme.typography.titleMedium)
                Text(environmentLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalIconButton(onClick = onShowSearchDialog) {
                BadgedBox(badge = { if (filterLabel.isNotEmpty()) Badge() }) {
                    Icon(Icons.Rounded.Search, if (filterLabel.isEmpty()) "搜索当前位置" else "搜索；当前列表已定位 $filterLabel")
                }
            }
            IconButton(onClick = { showOptions = true }) {
                BadgedBox(badge = { if (clipboardCount > 0 || hasTask) Badge() }) {
                    Icon(Icons.Rounded.Tune, "浏览选项与文件任务")
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenStorageDrawer, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(Icons.Rounded.Storage, "切换存储位置", tint = MaterialTheme.colorScheme.primary)
                }
                Surface(onClick = onPathClick, color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(vertical = 4.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(currentPath, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        FileManagerFittedText(
                            text = buildString {
                                append("文件夹: $folderCount 文件: $fileCount")
                                if (selectedCount > 0) append(" 已选: $selectedCount")
                                if (storageLabel.isNotBlank()) append(" $storageLabel")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
    if (showOptions) KiyoriModalBottomDrawer(onDismissRequest = {
        showOptions = false
        afterOptionsClose?.invoke()
        afterOptionsClose = null
    }) { dismissDrawer ->
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("浏览选项", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = dismissDrawer) { Icon(Icons.Rounded.Close, "关闭浏览选项") }
            }
            Text("点击打开 · 左右滑动选择 · 长按操作", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (storageLabel.isNotBlank()) Text(storageLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FileManagerOptionGroup("排序", Icons.AutoMirrored.Filled.Sort, KiyoriSemanticTone.BLUE) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileManagerSortMode.entries.forEach { mode ->
                        FilterChip(selected = mode == sortMode, onClick = { onSelectSort(mode) }, label = { Text(when (mode) {
                            FileManagerSortMode.NAME -> "名称"
                            FileManagerSortMode.SIZE -> "大小"
                            FileManagerSortMode.MODIFIED -> "修改时间"
                        }) })
                    }
                }
                TextButton(onClick = onToggleSortDirection) {
                    Icon(if (sortDescending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(if (sortDescending) "降序排列" else "升序排列")
                }
            }
            Surface(shape = KiyoriUiShapes.card) {
                Column {
                    ListItem(headlineContent = { Text("显示隐藏项目") }, supportingContent = { Text("名称以点开头的文件和文件夹") },
                        leadingContent = { FileManagerIconBadge(Icons.Rounded.Visibility, KiyoriSemanticTone.CYAN, 36.dp) },
                        trailingContent = { Switch(checked = showHiddenFiles, onCheckedChange = null) },
                        modifier = Modifier.clickable(onClick = onToggleHiddenFiles))
                    HorizontalDivider(Modifier.padding(start = 68.dp))
                    FileManagerActionRow("全选可见项目", icon = Icons.Rounded.SelectAll, tone = KiyoriSemanticTone.GREEN) {
                        afterOptionsClose = onSelectAll; dismissDrawer()
                    }
                    FileManagerActionRow("清空当前选择", icon = Icons.Rounded.Deselect, tone = KiyoriSemanticTone.BLUE, enabled = selectedCount > 0) {
                        afterOptionsClose = onClearSelection; dismissDrawer()
                    }
                    FileManagerActionRow("粘贴到当前目录（$clipboardCount 项）", icon = Icons.Rounded.ContentPaste, tone = KiyoriSemanticTone.BLUE, enabled = clipboardCount > 0 && canCreate) {
                        afterOptionsClose = onPaste; dismissDrawer()
                    }
                    FileManagerActionRow("清空剪贴板", icon = Icons.Rounded.Clear, tone = KiyoriSemanticTone.ORANGE, enabled = clipboardCount > 0) {
                        afterOptionsClose = onClearClipboard; dismissDrawer()
                    }
                    FileManagerActionRow("传输任务", icon = Icons.Rounded.SwapHoriz, tone = KiyoriSemanticTone.CYAN, enabled = hasTask) {
                        afterOptionsClose = onShowTask; dismissDrawer()
                    }
                    FileManagerActionRow("反向选择", icon = Icons.Rounded.Checklist, tone = KiyoriSemanticTone.PURPLE) {
                        afterOptionsClose = onInvertSelection; dismissDrawer()
                    }
                }
            }
            Surface(shape = KiyoriUiShapes.card) {
                Column {
                    FileManagerActionRow("新建文件或文件夹", icon = Icons.Rounded.CreateNewFolder, tone = KiyoriSemanticTone.ORANGE, enabled = canCreate) {
                        afterOptionsClose = onNew; dismissDrawer()
                    }
                    FileManagerActionRow("浏览 Linux 文件", icon = Icons.Rounded.Terminal, tone = KiyoriSemanticTone.CYAN) {
                        afterOptionsClose = onOpenLinux; dismissDrawer()
                    }
                }
            }
        }
    }
}

@Composable
private fun FileManagerOptionGroup(title: String, icon: ImageVector, tone: KiyoriSemanticTone, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = KiyoriUiShapes.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FileManagerIconBadge(icon, tone, 32.dp)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun FileManagerBottomBar(
    canGoBack: Boolean, canGoForward: Boolean, activePane: FileManagerPane,
    onBack: () -> Unit, onForward: () -> Unit, onNew: () -> Unit,
    onMirrorPath: () -> Unit, onOpenMenu: () -> Unit, canCreate: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(
            start = WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP.dp,
            top = WEB_SESSION_BROWSER_BOTTOM_TOP_PADDING_DP.dp,
            end = WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP.dp,
            bottom = WEB_SESSION_BROWSER_BOTTOM_BOTTOM_PADDING_DP.dp,
        ), verticalAlignment = Alignment.CenterVertically) {
            BrowserBottomBarAction(R.drawable.ic_kiyori_browser_bottom_back, "后退", onBack, canGoBack)
            BrowserBottomBarAction(R.drawable.ic_kiyori_browser_bottom_forward, "前进", onForward, canGoForward)
            BrowserBottomBarSlot("新建", onNew, canCreate) {
                Icon(Icons.Rounded.Add, null, Modifier.size(WEB_SESSION_BROWSER_BOTTOM_ICON_SIZE_DP.dp))
            }
            BrowserBottomBarSlot(if (activePane == FileManagerPane.LEFT) "将左栏位置同步到右栏" else "将右栏位置同步到左栏", onMirrorPath) {
                // 同一 SyncAlt 图标分上下半部着色，保持原有箭头轮廓及位置。
                val ink = LocalContentColor.current
                val upper = if (activePane == FileManagerPane.LEFT) ink else ink.copy(alpha = 0.38f)
                val lower = if (activePane == FileManagerPane.RIGHT) ink else ink.copy(alpha = 0.38f)
                Box(Modifier.size(WEB_SESSION_BROWSER_BOTTOM_ICON_SIZE_DP.dp)) {
                    Icon(Icons.Rounded.SyncAlt, null, Modifier.fillMaxSize().drawWithContent {
                        clipRect(bottom = size.height / 2f) { this@drawWithContent.drawContent() }
                    }, tint = upper)
                    Icon(Icons.Rounded.SyncAlt, null, Modifier.fillMaxSize().drawWithContent {
                        clipRect(top = size.height / 2f) { this@drawWithContent.drawContent() }
                    }, tint = lower)
                }
            }
            BrowserBottomBarAction(R.drawable.ic_kiyori_tool_toolbox, "菜单", onOpenMenu)
        }
    }
}

data class FileManagerStorageEntry(
    val title: String,
    val path: String,

    val environment: String? = null,
    val subtitle: String? = null,
    val bookmarkUri: String? = null,
    val fileBookmark: com.ai.assistance.operit.data.preferences.ApiPreferences.FileBookmark? = null,
    val category: String = "本地",
    val network: com.ai.assistance.operit.core.tools.defaultTool.standard.NetworkStorageProfile? = null,
)

@Composable
fun FileManagerStorageDrawer(
    entries: List<FileManagerStorageEntry>,
    onSelect: (FileManagerStorageEntry) -> Unit,
    onAddBookmark: () -> Unit,
    onDeleteBookmark: (FileManagerStorageEntry) -> Unit,
    onDismiss: () -> Unit,
    onRecycleBin: () -> Unit,
    currentPath: String,
    currentEnvironment: String?,
    onAddNetwork: () -> Unit = {},
    onAddNetworkGroup: () -> Unit = {},
    networkGroups: List<String> = emptyList(),
    onEditNetwork: (com.ai.assistance.operit.core.tools.defaultTool.standard.NetworkStorageProfile) -> Unit = {},
) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kiyori", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Box {
                IconButton(onClick = { showAdd = true }) { Icon(Icons.Rounded.MoreVert, "添加存储或分组") }
                DropdownMenu(expanded = showAdd, onDismissRequest = { showAdd = false }) {
                    DropdownMenuItem(text = { Text("本地存储") }, onClick = { showAdd = false; onAddBookmark() })
                    DropdownMenuItem(text = { Text("网络存储") }, onClick = { showAdd = false; onAddNetwork() })
                    DropdownMenuItem(text = { Text("网络分组") }, onClick = { showAdd = false; onAddNetworkGroup() })
                }
            }
        }
        listOf("本地", "网络", "书签", "工作区", "工具").forEach { category ->
            var expanded by androidx.compose.runtime.saveable.rememberSaveable(category) { mutableStateOf(true) }
            ListItem(
                headlineContent = { Text(category, style = MaterialTheme.typography.titleSmall) },
                trailingContent = { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "收起$category" else "展开$category") },
                modifier = Modifier.clickable { expanded = !expanded },
            )
            if (expanded) {
                val items = entries.filter { it.category == category }
                items.filter { category != "网络" }.forEach { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title, style = MaterialTheme.typography.bodyMedium) },
                        supportingContent = { entry.subtitle?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                        leadingContent = { Icon(when (category) {
                            "网络" -> Icons.Rounded.Cloud
                            "书签" -> Icons.Rounded.Bookmark
                            "工作区" -> Icons.Rounded.Workspaces
                            else -> if (entry.environment == "linux") Icons.Rounded.Terminal else Icons.Rounded.Folder
                        }, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = {
                            if (entry.bookmarkUri != null || entry.fileBookmark != null) IconButton(onClick = { onDeleteBookmark(entry) }) {
                                Icon(Icons.Rounded.Close, "移除${entry.title}", Modifier.size(18.dp))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = if (entry.path == currentPath && entry.environment == currentEnvironment)
                            MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface),
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp).clickable { onSelect(entry) },
                    )
                }
                if (category == "网络") {
                    (listOf("") + networkGroups + items.mapNotNull { it.network?.group }).distinct().forEach { group ->
                        var groupExpanded by androidx.compose.runtime.saveable.rememberSaveable(group) { mutableStateOf(true) }
                        val grouped = items.filter { it.network?.group == group }
                        if (group.isNotEmpty()) ListItem(
                            headlineContent = { Text(group, style = MaterialTheme.typography.labelLarge) },
                            trailingContent = { Icon(if (groupExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) },
                            modifier = Modifier.padding(start = 20.dp).clickable { groupExpanded = !groupExpanded },
                        )
                        if (groupExpanded) grouped.forEach { entry -> ListItem(
                            headlineContent = { Text(entry.title, style = MaterialTheme.typography.bodyMedium) },
                            supportingContent = { Text(entry.subtitle.orEmpty(), style = MaterialTheme.typography.bodySmall) },
                            leadingContent = { Icon(Icons.Rounded.Cloud, null) },
                            trailingContent = { Row {
                                IconButton(onClick = { entry.network?.let(onEditNetwork) }) { Icon(Icons.Rounded.Edit, "编辑${entry.title}", Modifier.size(18.dp)) }
                                IconButton(onClick = { onDeleteBookmark(entry) }) { Icon(Icons.Rounded.Close, "移除${entry.title}", Modifier.size(18.dp)) }
                            } },
                            modifier = Modifier.padding(start = 20.dp).clickable { onSelect(entry) },
                        ) }
                    }
                    if (items.isEmpty()) TextButton(onClick = onAddNetwork, modifier = Modifier.padding(start = 16.dp)) { Text("添加网络存储") }
                }
                if (category == "书签" && items.isEmpty()) Text("长按项目 → 加书签", Modifier.padding(start = 28.dp, bottom = 12.dp), style = MaterialTheme.typography.bodySmall)
                if (category == "工具") ListItem(
                    headlineContent = { Text("回收站") }, leadingContent = { Icon(Icons.Rounded.RestoreFromTrash, null) },
                    modifier = Modifier.padding(start = 12.dp).clickable(onClick = onRecycleBin),
                )
            }
        }
    }
}

fun defaultFileManagerStorageEntries(workspacePath: String): List<FileManagerStorageEntry> = listOf(
    FileManagerStorageEntry("根目录", "/", subtitle = "访问范围由系统权限决定"),
    FileManagerStorageEntry("内部存储", Environment.getExternalStorageDirectory().absolutePath),
    FileManagerStorageEntry("Linux", "/", environment = "linux"),
    FileManagerStorageEntry("默认工作区", workspacePath, category = "工作区"),
)
