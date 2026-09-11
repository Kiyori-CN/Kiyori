package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import android.os.Environment
import android.os.StatFs
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.websession.browser.chrome.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward

import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kiyori.design.theme.KiyoriBrowserTheme
import com.kiyori.design.theme.KIYORI_SECONDARY_BAR_CONTENT_HEIGHT_DP
import com.kiyori.design.theme.KIYORI_SECONDARY_BAR_VERTICAL_PADDING_DP
import com.kiyori.design.theme.KIYORI_DRAWER_HEADER_TOP_SPACING_DP
import com.kiyori.design.theme.KIYORI_DRAWER_HEADER_VERTICAL_PADDING_DP
import com.kiyori.design.theme.KIYORI_DRAWER_HEADER_HORIZONTAL_PADDING_DP
import com.ai.assistance.operit.ui.components.KiyoriDrawerTitle
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriUiShapes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import com.ai.assistance.operit.ui.components.KiyoriModalBottomDrawer
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FileManagerTopBar(
    currentPath: String,
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
    // 与 AI 使用相同的 TopAppBar 默认高度和状态栏消费方式；栏颜色隔离于设置页灰底。
    KiyoriBrowserTheme {
        Column(Modifier.fillMaxWidth()) {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text("文件管理", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onExitFileManager) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回文件管理首页") }
                },
                actions = {
                    IconButton(onClick = onShowSearchDialog) {
                        BadgedBox(badge = { if (filterLabel.isNotEmpty()) Badge() }) {
                            Icon(Icons.Outlined.Search, if (filterLabel.isEmpty()) "搜索当前位置" else "搜索；当前列表已定位 $filterLabel")
                        }
                    }
                    IconButton(onClick = { showOptions = true }) {
                        BadgedBox(badge = { if (clipboardCount > 0 || hasTask) Badge() }) {
                            Icon(Icons.Outlined.Tune, "浏览选项与文件任务")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            // 与 AI 第二栏一致：浅灰半透明层叠在白底上，36dp 内容加上下各 2dp。
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .padding(vertical = KIYORI_SECONDARY_BAR_VERTICAL_PADDING_DP.dp)
                    .heightIn(min = KIYORI_SECONDARY_BAR_CONTENT_HEIGHT_DP.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenStorageDrawer, modifier = Modifier.padding(start = 4.dp).requiredSize(KIYORI_SECONDARY_BAR_CONTENT_HEIGHT_DP.dp)) {
                        Icon(Icons.Rounded.Storage, "切换存储位置", tint = MaterialTheme.colorScheme.primary)
                    }
                    // 点击区域随紧凑栏高布局，避免 clickable Surface 的默认最小高度将第二栏撑回 48dp。
                    Column(Modifier.weight(1f).heightIn(min = KIYORI_SECONDARY_BAR_CONTENT_HEIGHT_DP.dp)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onPathClick)
                        .padding(horizontal = 4.dp), verticalArrangement = Arrangement.Center) {
                        LeadingEllipsisPath(currentPath, MaterialTheme.typography.labelMedium)
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
                IconButton(onClick = dismissDrawer) { Icon(Icons.Outlined.Close, "关闭浏览选项") }
            }
            Text("点击打开 · 左右滑动选择 · 长按操作", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (storageLabel.isNotBlank()) Text(storageLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FileManagerOptionGroup("排序", Icons.AutoMirrored.Outlined.Sort, KiyoriSemanticTone.BLUE) {
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
                    FileManagerActionRow("粘贴到当前目录（$clipboardCount 项）", icon = Icons.Outlined.ContentPaste, tone = KiyoriSemanticTone.BLUE, enabled = clipboardCount > 0 && canCreate) {
                        afterOptionsClose = onPaste; dismissDrawer()
                    }
                    FileManagerActionRow("清空剪贴板", icon = Icons.Outlined.Clear, tone = KiyoriSemanticTone.ORANGE, enabled = clipboardCount > 0) {
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
    KiyoriBrowserTheme {
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
                    Icon(Icons.Outlined.Add, null, Modifier.size(WEB_SESSION_BROWSER_BOTTOM_ICON_SIZE_DP.dp))
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
    Column(Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        .padding(end = 8.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(KIYORI_DRAWER_HEADER_TOP_SPACING_DP.dp))
        KiyoriBrowserTheme {
            Row(Modifier.fillMaxWidth().padding(
                start = KIYORI_DRAWER_HEADER_HORIZONTAL_PADDING_DP.dp, end = 4.dp,
                top = KIYORI_DRAWER_HEADER_VERTICAL_PADDING_DP.dp, bottom = KIYORI_DRAWER_HEADER_VERTICAL_PADDING_DP.dp,
            ), verticalAlignment = Alignment.Top) {
                KiyoriDrawerTitle(Modifier.weight(1f))
                // 操作按钮保留完整触摸区，但不撑高标题行、改变标题的顶部基线。
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { showAdd = true }, modifier = Modifier.requiredSize(48.dp)) { Icon(Icons.Outlined.MoreVert, "添加存储或分组") }
                    DropdownMenu(expanded = showAdd, onDismissRequest = { showAdd = false }) {
                        DropdownMenuItem(text = { Text("本地存储") }, onClick = { showAdd = false; onAddBookmark() })
                        DropdownMenuItem(text = { Text("网络存储") }, onClick = { showAdd = false; onAddNetwork() })
                        DropdownMenuItem(text = { Text("网络分组") }, onClick = { showAdd = false; onAddNetworkGroup() })
                    }
                }
            }
        }
        listOf("本地", "网络", "书签", "工作区", "工具").forEach { category ->
            var expanded by androidx.compose.runtime.saveable.rememberSaveable(category) { mutableStateOf(true) }
            CompactStorageRow(
                headlineContent = { Text(category, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                trailingContent = { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "收起$category" else "展开$category") },
                modifier = Modifier.clickable { expanded = !expanded },
            )
            if (expanded) {
                val items = entries.filter { it.category == category }
                items.filter { category != "网络" }.forEach { entry ->
                    CompactStorageRow(
                        headlineContent = { Text(entry.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            LeadingEllipsisPath(entry.subtitle ?: entry.path)
                            if (category == "本地" && entry.environment.isNullOrBlank()) StorageUsageIndicator(entry.path)
                        },
                        leadingContent = { Icon(when (category) {
                            "网络" -> Icons.Rounded.Cloud
                            "书签" -> Icons.Rounded.Bookmark
                            "工作区" -> Icons.Rounded.Workspaces
                            else -> if (entry.environment == "linux") Icons.Rounded.Terminal else Icons.Rounded.Folder
                        }, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = {
                            if (entry.bookmarkUri != null || entry.fileBookmark != null) IconButton(onClick = { onDeleteBookmark(entry) }) {
                                Icon(Icons.Outlined.Close, "移除${entry.title}", Modifier.size(18.dp))
                            }
                        },
                        selected = entry.path == currentPath && entry.environment.orEmpty().removePrefix("android") == currentEnvironment.orEmpty().removePrefix("android"),
                            modifier = Modifier.padding(start = 12.dp, end = 8.dp).clickable { onSelect(entry) },
                        )
                }
                if (category == "网络") {
                    (listOf("") + networkGroups + items.mapNotNull { it.network?.group }).distinct().forEach { group ->
                        var groupExpanded by androidx.compose.runtime.saveable.rememberSaveable(group) { mutableStateOf(true) }
                        val grouped = items.filter { it.network?.group == group }
                        if (group.isNotEmpty()) CompactStorageRow(
                            headlineContent = { Text(group, style = MaterialTheme.typography.labelLarge) },
                            trailingContent = { Icon(if (groupExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) },
                            modifier = Modifier.padding(start = 20.dp).clickable { groupExpanded = !groupExpanded },
                        )
                        if (groupExpanded) grouped.forEach { entry -> CompactStorageRow(
                            headlineContent = { Text(entry.title, fontSize = 12.sp) },
                            supportingContent = { LeadingEllipsisPath(entry.subtitle.orEmpty(), MaterialTheme.typography.bodySmall) },
                            leadingContent = { Icon(Icons.Rounded.Cloud, null) },
                            trailingContent = { Row {
                                IconButton(onClick = { entry.network?.let(onEditNetwork) }) { Icon(Icons.Outlined.Edit, "编辑${entry.title}", Modifier.size(18.dp)) }
                                IconButton(onClick = { onDeleteBookmark(entry) }) { Icon(Icons.Outlined.Close, "移除${entry.title}", Modifier.size(18.dp)) }
                            } },
                            modifier = Modifier.padding(start = 20.dp).clickable { onSelect(entry) },
                        ) }
                    }
                    if (items.isEmpty()) TextButton(onClick = onAddNetwork, modifier = Modifier.padding(start = 16.dp)) { Text("添加网络存储") }
                }
                if (category == "书签" && items.isEmpty()) Text("长按项目 → 加书签", Modifier.padding(start = 28.dp, bottom = 12.dp), style = MaterialTheme.typography.bodySmall)
                if (category == "工具") CompactStorageRow(
                    headlineContent = { Text("回收站", fontSize = 12.sp) }, leadingContent = { Icon(Icons.Rounded.RestoreFromTrash, null) },
                    supportingContent = { Text("恢复或彻底删除", fontSize = 10.sp) },
                    modifier = Modifier.padding(start = 12.dp).clickable(onClick = onRecycleBin),
                )
            }
        }
    }
}

/** 路径从左侧省略，保留末级目录，便于区分相同名称的存储位置。 */
@Composable
private fun LeadingEllipsisPath(path: String, style: TextStyle = LocalTextStyle.current) {
    // 交给文本布局按真实可用宽度省略，避免固定字符数截断、宽字体溢出或拆断 Unicode。
    Text(path, modifier = Modifier.fillMaxWidth(), style = style, maxLines = 1, softWrap = false,
        overflow = TextOverflow.StartEllipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 不使用 Material ListItem 的多行最小高度，视觉紧凑，同时保留完整行的 48dp 触摸区。 */
@Composable
private fun CompactStorageRow(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
) {
    Row(modifier.fillMaxWidth()
        .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface)
        .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (leadingContent != null) Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leadingContent() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            headlineContent()
            if (supportingContent != null) ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp)) { supportingContent() }
        }
        trailingContent?.invoke()
    }
}

fun defaultFileManagerStorageEntries(workspacePath: String): List<FileManagerStorageEntry> = listOf(
    FileManagerStorageEntry("根目录", "/", subtitle = "/"),
    FileManagerStorageEntry("内部存储", Environment.getExternalStorageDirectory().absolutePath, subtitle = Environment.getExternalStorageDirectory().absolutePath),
    FileManagerStorageEntry("Linux", "/", environment = "linux"),
    FileManagerStorageEntry("默认工作区", workspacePath, category = "工作区"),
)

@Composable
private fun StorageUsageIndicator(path: String) {
    val usage by produceState<Pair<Long, Long>?>(null, path) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { StatFs(path).let { it.totalBytes - it.availableBytes to it.totalBytes } }.getOrNull()
        }
    }
    usage?.let { (used, total) ->
        val fraction = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("已用 ${compactStorageSize(used)} / ${compactStorageSize(total)} · ${(fraction * 100).toInt()}%", fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth().height(3.dp))
        }
    } ?: Text("容量暂不可用", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun compactStorageSize(bytes: Long): String {
    val units = arrayOf("B", "K", "M", "G", "T")
    var value = bytes.toDouble(); var index = 0
    while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }
    return if (index == 0) "${value.toLong()}B" else String.format(java.util.Locale.US, "%.2f%s", value, units[index])
}
