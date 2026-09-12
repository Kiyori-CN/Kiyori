package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.preferences.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerHistoryStore
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.design.theme.KiyoriSettingsTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal const val KIYORI_FILE_MANAGER_SETTINGS_PAGE_TITLE = "文件管理器"

/** 状态使用稳定枚举，调整显示文案不会改变操作分派或丢失恢复中的面板。 */
internal enum class FileSettingsPanel(val title: String) {
    LEFT_PATH("左列启动目录"), RIGHT_PATH("右列启动目录"),
    DENSITY("显示密度"), FILENAME_LINES("文件名行数"),
    SORT_MODE("初始排序依据"), SORT_DIRECTION("初始排序方向"), REFRESH("自动刷新间隔"),
    HIDDEN_ENTRIES("手动隐藏清单"), HIDDEN_LOCATIONS("隐藏的本地入口"),
    RECYCLE_HELP("回收与恢复说明"), CLEAR_SEARCH("清空搜索记录"),
    CLEAR_TASKS("清空已结束的任务记录"), RESET_BROWSING("恢复浏览默认设置"),
    RESET_DRAWER("恢复默认侧栏布局"),
}

internal fun fileManagerSortLabel(mode: FileManagerSortMode): String = when (mode) {
    FileManagerSortMode.NAME -> "名称"
    FileManagerSortMode.SIZE -> "大小"
    FileManagerSortMode.MODIFIED -> "修改时间"
    FileManagerSortMode.FORMAT -> "文件格式"
}

internal fun fileManagerSortDirectionLabel(mode: FileManagerSortMode, descending: Boolean): String = when (mode) {
    FileManagerSortMode.NAME, FileManagerSortMode.FORMAT -> if (descending) "降序" else "升序"
    FileManagerSortMode.SIZE -> if (descending) "从大到小" else "从小到大"
    FileManagerSortMode.MODIFIED -> if (descending) "从新到旧" else "从旧到新"
}

internal fun fileManagerDensityLabel(size: Float): String = when (size) {
    0.8f -> "紧凑"
    1f -> "标准"
    1.2f -> "宽松"
    else -> "自定义 · ${(size * 100).toInt()}%"
}

/** 页面仅投影两个既有存储；不接管浏览位置、权限或正在执行的任务。 */
@Composable
internal fun KiyoriFileManagerSettingsPage(onBack: () -> Unit, onOpenPermissions: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember(context) { FileManagerPreferences.getInstance(context) }
    val historyStore = remember(context) { FileManagerHistoryStore.getInstance(context) }
    val settings by preferences.state.collectAsState()
    val history by historyStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var panel by rememberSaveable { mutableStateOf<FileSettingsPanel?>(null) }
    var busy by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var historyReady by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf(false) }
    var historyAttempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(historyStore, historyAttempt) {
        historyReady = false
        historyError = false
        try {
            historyStore.load()
            historyReady = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            historyError = true
            AppLogger.e("FileManagerSettings", "读取文件历史失败", failure)
        }
    }
    fun open(target: FileSettingsPanel) { if (!busy) { operationError = null; panel = target } }
    fun complete(message: String) {
        panel = null
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) }
    }
    val endedTasks = history.tasks.count { it.finishedAt != null }
    val runningTasks = history.tasks.size - endedTasks

    KiyoriSettingsTheme {
        Box(modifier.fillMaxSize()) {
            KiyoriCollapsingSettingsPage(title = KIYORI_FILE_MANAGER_SETTINGS_PAGE_TITLE, onBack = onBack) {
                item(key = "startup") {
                    KiyoriSettingsGroupSection("启动位置", "用于下次新建文件会话；从设置返回时继续浏览原位置。") {
                        FileSettingsRow("左列启动目录", settings.leftStartPath.ifBlank { "默认打开手机内部存储" }, value = "编辑") { open(FileSettingsPanel.LEFT_PATH) }
                        KiyoriSettingsDivider()
                        FileSettingsRow("右列启动目录", settings.rightStartPath.ifBlank { "默认打开手机内部存储" }, value = "编辑") { open(FileSettingsPanel.RIGHT_PATH) }
                    }
                }
                item(key = "display") {
                    KiyoriSettingsGroupSection("列表显示", "显示偏好立即应用到左右两列。") {
                        FileSettingsRow("显示密度", "同时调整文字、图标与行高", value = fileManagerDensityLabel(settings.itemSize)) { open(FileSettingsPanel.DENSITY) }
                        KiyoriSettingsDivider()
                        FileSettingsRow("文件名行数", "超过上限的名称省略显示", value = "最多 ${settings.filenameLines} 行") { open(FileSettingsPanel.FILENAME_LINES) }
                        KiyoriSettingsDivider()
                        FileSettingsRow("时间显示到秒", "关闭后显示到分钟，始终保留完整年月日", checked = settings.showSeconds) { preferences.update { it.copy(showSeconds = !it.showSeconds) } }
                        KiyoriSettingsDivider()
                        FileSettingsRow("统计文件夹大小", "关闭可减少目录扫描；大型目录统计需要更多时间", checked = settings.showDirectorySizes) { preferences.update { it.copy(showDirectorySizes = !it.showDirectorySizes) } }
                    }
                }
                item(key = "hidden") {
                    KiyoriSettingsGroupSection("隐藏项目", "只改变 Kiyori 中的可见性，不修改文件，也不提供加密保护。") {
                        FileSettingsRow("显示系统隐藏项", "名称以点开头的文件和文件夹", checked = settings.showHiddenFiles) { preferences.update { it.copy(showHiddenFiles = !it.showHiddenFiles) } }
                        KiyoriSettingsDivider()
                        FileSettingsRow("显示手动隐藏项", "临时查看隐藏清单中的项目，清单仍保留", checked = settings.showManuallyHiddenFiles) { preferences.update { it.copy(showManuallyHiddenFiles = !it.showManuallyHiddenFiles) } }
                        KiyoriSettingsDivider()
                        FileSettingsRow("手动隐藏清单", "按路径或存储环境查找，取消单项或全部隐藏", value = "${settings.manuallyHiddenFiles.size} 项") { open(FileSettingsPanel.HIDDEN_ENTRIES) }
                    }
                }
                item(key = "browsing") {
                    KiyoriSettingsGroupSection("排序与刷新", "初始排序用于新会话；当前列仍可从文件管理工具栏单独排序。") {
                        FileSettingsRow("初始排序依据", "文件夹始终优先排列", value = fileManagerSortLabel(settings.sortMode)) { open(FileSettingsPanel.SORT_MODE) }
                        KiyoriSettingsDivider()
                        FileSettingsRow("初始排序方向", "对文件夹和文件分别排序", value = fileManagerSortDirectionLabel(settings.sortMode, settings.sortDescending)) { open(FileSettingsPanel.SORT_DIRECTION) }
                        KiyoriSettingsDivider()
                        FileSettingsRow("自动刷新间隔", "仅在文件管理器前台检查；网络存储至少间隔 30 秒", value = if (settings.refreshIntervalSeconds == 0) "关闭" else "${settings.refreshIntervalSeconds} 秒") { open(FileSettingsPanel.REFRESH) }
                    }
                }
                item(key = "drawer") {
                    KiyoriSettingsGroupSection("侧栏与书签", "长按侧栏入口可管理；根目录与内部存储固定保留。") {
                        FileSettingsRow("显示书签分类", "关闭不会删除书签或使桌面快捷方式失效", checked = settings.showBookmarks) { preferences.update { it.copy(showBookmarks = !it.showBookmarks) } }
                        KiyoriSettingsDivider()
                        FileSettingsRow("显示工作区分类", "只控制侧栏，不改变 AI 对话绑定的工作区", checked = settings.showWorkspaces) { preferences.update { it.copy(showWorkspaces = !it.showWorkspaces) } }
                        KiyoriSettingsDivider()
                        FileSettingsRow("新书签放在顶部", "只影响以后添加的书签", checked = settings.newBookmarksOnTop) { preferences.update { it.copy(newBookmarksOnTop = !it.newBookmarksOnTop) } }
                        KiyoriSettingsDivider()
                        FileSettingsRow("隐藏的本地入口", "逐项恢复到侧栏，保留名称与授权", value = "${settings.drawerHidden.size} 项") { open(FileSettingsPanel.HIDDEN_LOCATIONS) }
                        KiyoriSettingsDivider()
                        FileSettingsRow("恢复默认侧栏布局", "恢复分类、内置入口与顺序，保留自建入口") { open(FileSettingsPanel.RESET_DRAWER) }
                    }
                }
                item(key = "storage") {
                    KiyoriSettingsGroupSection("存储与回收", "文件访问遵循系统授权；删除与永久删除分别确认。") {
                        FileSettingsRow("管理访问权限", "查看存储、Shizuku 与 Root 的授权状态", onClick = onOpenPermissions)
                        KiyoriSettingsDivider()
                        FileSettingsRow("回收与恢复说明", "了解回收位置、同名冲突与永久删除") { open(FileSettingsPanel.RECYCLE_HELP) }
                    }
                }
                item(key = "history") {
                    KiyoriSettingsGroupSection("记录与重置", "历史仅保存在本机；清理不会删除实际文件或停止任务。") {
                        if (!historyReady) {
                            FileSettingsRow(if (historyError) "历史记录读取失败" else "正在读取历史记录",
                                if (historyError) "点按重试，原记录未被清空" else "读取完成后显示可清理数量",
                                enabled = historyError) { historyAttempt++ }
                            KiyoriSettingsDivider()
                        }
                        FileSettingsRow("清空搜索记录", "删除已保存的搜索条件与结果", value = if (historyReady) "${history.searches.size} 条" else "未读取",
                            enabled = historyReady && history.searches.isNotEmpty()) { open(FileSettingsPanel.CLEAR_SEARCH) }
                        KiyoriSettingsDivider()
                        FileSettingsRow("清空已结束的任务记录", if (runningTasks > 0) "保留 $runningTasks 条进行中的任务记录" else "保留实际文件，不影响后续任务",
                            value = if (historyReady) "$endedTasks 条" else "未读取", enabled = historyReady && endedTasks > 0) { open(FileSettingsPanel.CLEAR_TASKS) }
                        KiyoriSettingsDivider()
                        FileSettingsRow("恢复浏览默认设置", "重置启动、显示、排序与刷新；保留侧栏、清单和历史") { open(FileSettingsPanel.RESET_BROWSING) }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
        }
        panel?.let { current ->
            when (current) {
                FileSettingsPanel.LEFT_PATH, FileSettingsPanel.RIGHT_PATH -> FileSettingsPathDialog(
                    panel = current, settings = settings, onDismiss = { panel = null },
                    onSave = { path ->
                        preferences.update { if (current == FileSettingsPanel.LEFT_PATH) it.copy(leftStartPath = path) else it.copy(rightStartPath = path) }
                        complete("启动目录已保存，下次新建文件会话生效")
                    },
                )
                FileSettingsPanel.DENSITY, FileSettingsPanel.FILENAME_LINES, FileSettingsPanel.SORT_MODE,
                FileSettingsPanel.SORT_DIRECTION, FileSettingsPanel.REFRESH -> KiyoriSettingsSelectionSheet(
                    selection = fileSettingsSelection(current, settings, preferences),
                    onDismiss = { panel = null }, onSelect = { it.onSelect() },
                )
                FileSettingsPanel.HIDDEN_ENTRIES, FileSettingsPanel.HIDDEN_LOCATIONS -> FileSettingsHiddenSheet(
                    locations = current == FileSettingsPanel.HIDDEN_LOCATIONS,
                    settings = settings, preferences = preferences, onDismiss = { panel = null },
                )
                FileSettingsPanel.RECYCLE_HELP -> FileSettingsHelpDialog(onDismiss = { panel = null })
                else -> FileSettingsConfirmation(
                    panel = current, settings = settings, searchCount = history.searches.size, endedTasks = endedTasks,
                    busy = busy, error = operationError, onDismiss = { if (!busy) panel = null },
                    onConfirm = {
                        if (!busy) {
                            busy = true
                            operationError = null
                            scope.launch {
                                var success = false
                                try {
                                    when (current) {
                                        FileSettingsPanel.CLEAR_SEARCH -> historyStore.removeSearch(null)
                                        FileSettingsPanel.CLEAR_TASKS -> historyStore.removeTask(null)
                                        FileSettingsPanel.RESET_BROWSING -> preferences.update { it.resetBrowsing() }
                                        FileSettingsPanel.RESET_DRAWER -> preferences.update { it.resetDrawerLayout() }
                                        else -> error("Unsupported confirmation: $current")
                                    }
                                    success = true
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) {
                                    operationError = "操作未完成，请重试。原文件不受影响。"
                                    AppLogger.e("FileManagerSettings", "清理或重置失败", failure)
                                } finally { busy = false }
                                // 写入状态先结束，避免旧 Snackbar 的挂起回调清除下一次操作的 busy。
                                if (success) complete(when (current) {
                                    FileSettingsPanel.CLEAR_SEARCH -> "搜索记录已清空"
                                    FileSettingsPanel.CLEAR_TASKS -> "已结束的任务记录已清空"
                                    FileSettingsPanel.RESET_DRAWER -> "默认侧栏布局已恢复"
                                    else -> "浏览默认设置已恢复；启动位置与排序在新会话生效"
                                })
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun FileSettingsRow(title: String, description: String, value: String? = null, checked: Boolean? = null,
    enabled: Boolean = true, onClick: () -> Unit,
) = KiyoriSettingsRow(
    title = title, description = description, value = value,
    kind = if (checked == null) KiyoriSettingsRowKind.NAVIGATION else KiyoriSettingsRowKind.TOGGLE,
    checked = checked == true, enabled = enabled, onClick = onClick,
)

private fun fileSettingsSelection(panel: FileSettingsPanel, settings: FileManagerSettings, preferences: FileManagerPreferences): KiyoriSettingsSelection {
    val options = when (panel) {
        FileSettingsPanel.DENSITY -> listOf(0.8f, 1f, 1.2f).let { sizes ->
            if (settings.itemSize in sizes) sizes else (sizes + settings.itemSize).sorted()
        }.map { size -> KiyoriSettingsSelectionOption(fileManagerDensityLabel(size),
            "文字、图标与行高缩放为 ${(size * 100).toInt()}%", settings.itemSize == size) { preferences.update { it.copy(itemSize = size) } } }
        FileSettingsPanel.FILENAME_LINES -> (1..6).map { lines -> KiyoriSettingsSelectionOption("最多 $lines 行",
            if (lines == 1) "列表更紧凑，长名称省略显示" else "显示更多名称内容，行高随内容增长", settings.filenameLines == lines) { preferences.update { it.copy(filenameLines = lines) } } }
        FileSettingsPanel.SORT_MODE -> FileManagerSortMode.entries.map { mode -> KiyoriSettingsSelectionOption(fileManagerSortLabel(mode),
            "文件夹优先；下次新建文件会话生效", settings.sortMode == mode) { preferences.update { it.copy(sortMode = mode) } } }
        FileSettingsPanel.SORT_DIRECTION -> listOf(false, true).map { descending -> KiyoriSettingsSelectionOption(fileManagerSortDirectionLabel(settings.sortMode, descending),
            "下次新建文件会话生效", settings.sortDescending == descending) { preferences.update { it.copy(sortDescending = descending) } } }
        FileSettingsPanel.REFRESH -> listOf(0, 3, 10, 30).map { seconds -> KiyoriSettingsSelectionOption(if (seconds == 0) "关闭" else "$seconds 秒",
            if (seconds == 0) "仍可从工具栏或下拉手动刷新" else "仅前台检查；网络存储至少间隔 30 秒", settings.refreshIntervalSeconds == seconds) { preferences.update { it.copy(refreshIntervalSeconds = seconds) } } }
        else -> error("Unsupported selection: $panel")
    }
    return KiyoriSettingsSelection(panel.title, options.first { it.selected }.label, options)
}

@Composable
private fun FileSettingsPathDialog(panel: FileSettingsPanel, settings: FileManagerSettings, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(panel) { mutableStateOf(if (panel == FileSettingsPanel.LEFT_PATH) settings.leftStartPath else settings.rightStartPath) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(panel.title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("留空使用手机内部存储。只在下次新建文件会话时生效，不会移动文件。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value, { value = it; error = null }, Modifier.fillMaxWidth(), singleLine = false,
                label = { Text("绝对目录路径") }, placeholder = { Text("例如 /storage/emulated/0/Download") },
                isError = error != null, supportingText = { Text(error ?: "不使用 .、.. 或控制字符") })
        }
    }, confirmButton = { TextButton(onClick = {
        val clean = value.trim().trimEnd('/').ifEmpty { "" }
        if (clean.isNotEmpty() && !validFileManagerStartPath(clean)) error = "请输入以 / 开头的有效路径"
        else onSave(clean)
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun FileSettingsHiddenSheet(locations: Boolean, settings: FileManagerSettings, preferences: FileManagerPreferences, onDismiss: () -> Unit) {
    val entries = if (locations) settings.drawerHidden.toList().sorted() else settings.manuallyHiddenFiles.map { it.path }.sorted()
    var query by remember { mutableStateOf("") }
    val visible = entries.filter { it.contains(query.trim(), ignoreCase = true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (locations) "隐藏的本地入口" else "手动隐藏清单") }, text = {
        Column(Modifier.heightIn(max = 440.dp)) {
            Text("${entries.size} 项 · 只改变侧栏或列表显示，不修改文件", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true, label = { Text("筛选路径或名称") })
            if (visible.isEmpty()) Text(if (entries.isEmpty()) "暂无记录" else "没有匹配项", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false)) {
                items(visible) { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry, Modifier.weight(1f), maxLines = 2)
                        TextButton(onClick = { preferences.update { current -> if (locations) current.copy(drawerHidden = current.drawerHidden - entry) else current.copy(manuallyHiddenFiles = current.manuallyHiddenFiles.filterNot { it.path == entry }.toSet()) } }) { Text("恢复") }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
}

@Composable
private fun FileSettingsHelpDialog(onDismiss: () -> Unit) = AlertDialog(onDismissRequest = onDismiss, title = { Text("回收与恢复说明") }, text = {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("本地删除会先移到源存储卷的回收目录，保留原始位置；回收失败会显示原因，不会静默改为永久删除。")
        Text("恢复时若原位置已有同名文件会停止操作，不覆盖现有文件。永久删除需再次确认，且无法恢复。")
        Text("网络和授权目录的能力由存储后端决定，未支持的操作会明确提示。")
    }
}, confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } })

@Composable
private fun FileSettingsConfirmation(panel: FileSettingsPanel, settings: FileManagerSettings, searchCount: Int, endedTasks: Int,
    busy: Boolean, error: String?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val message = when (panel) {
        FileSettingsPanel.CLEAR_SEARCH -> "将清空 $searchCount 条搜索记录。当前目录和实际文件不会改变。"
        FileSettingsPanel.CLEAR_TASKS -> "将清空 $endedTasks 条已结束任务记录，进行中的任务和实际文件会保留。"
        FileSettingsPanel.RESET_DRAWER -> "将恢复分类、内置入口和顺序，保留自建入口、别名、授权和书签。"
        else -> "将恢复启动位置、显示、排序和刷新偏好；侧栏、隐藏清单与历史记录会保留。"
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(panel.title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(message); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    }, confirmButton = { TextButton(enabled = !busy, onClick = onConfirm) { Text(if (busy) "处理中…" else "确认") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } })
}
