package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.preferences.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerHistoryStore
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import kotlinx.coroutines.launch

internal fun fileManagerSortLabel(mode: FileManagerSortMode): String = when (mode) {
    FileManagerSortMode.NAME -> "名称"; FileManagerSortMode.SIZE -> "大小"
    FileManagerSortMode.MODIFIED -> "修改时间"; FileManagerSortMode.FORMAT -> "文件格式"
}

/** 只消费现有偏好与历史所有者；路径表单及清理操作必须确认，取消不保存草稿。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KiyoriFileManagerSettingsPage(onBack: () -> Unit, onOpenPermissions: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember(context) { FileManagerPreferences.getInstance(context) }
    val settings by preferences.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun open(title: String, value: String = "") { input = value; error = null; dialog = title }
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text("文件管理器设置", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
    }, snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { FileSettingsSection("启动位置", "仅在新建会话时使用；从设置返回继续浏览原位置。", KiyoriSemanticTone.BLUE) {
                FileSettingRow("左列启动目录", settings.leftStartPath.ifBlank { "内部存储" }) { open("左列启动目录", settings.leftStartPath) }
                FileSettingRow("右列启动目录", settings.rightStartPath.ifBlank { "内部存储" }) { open("右列启动目录", settings.rightStartPath) }
            } }
            item { FileSettingsSection("列表显示", "立即应用到左右两列。", KiyoriSemanticTone.PURPLE) {
                val size = when (settings.itemSize) { 0.8f -> "紧凑"; 1f -> "标准"; 1.2f -> "宽松"; else -> "自定义" }
                FileSettingRow("显示密度", "$size · 同时调整文字、图标与行高") { open("显示密度") }
                FileSettingRow("文件名行数", "最多 ${settings.filenameLines} 行，超出部分省略") { open("文件名行数") }
                FileSettingRow("时间显示到秒", "关闭后显示到分钟，保留完整年月日", settings.showSeconds) { preferences.update { it.copy(showSeconds = !it.showSeconds) } }
                FileSettingRow("统计文件夹大小", "统计可见目录；大型文件夹会增加存储读取", settings.showDirectorySizes) { preferences.update { it.copy(showDirectorySizes = !it.showDirectorySizes) } }
                FileSettingRow("隐藏项目的显示方式", "分别控制系统隐藏项与手动隐藏项") { open("隐藏项目的显示方式") }
                FileSettingRow("管理手动隐藏清单", "${settings.manuallyHiddenFiles.size} 个项目 · 取消隐藏不会修改文件") { open("管理手动隐藏清单") }
            } }
            item { FileSettingsSection("浏览与刷新", "初始排列用于新会话，当前列可从工具栏单独排序。", KiyoriSemanticTone.CYAN) {
                FileSettingRow("初始排列", "${fileManagerSortLabel(settings.sortMode)} · ${if (settings.sortDescending) "降序" else "升序"} · 文件夹优先") { open("初始排列") }
                FileSettingRow("自动刷新间隔", if (settings.refreshIntervalSeconds == 0) "关闭 · 仍可手动刷新" else "前台每 ${settings.refreshIntervalSeconds} 秒检查；网络存储至少间隔 30 秒") { open("自动刷新间隔") }
            } }
            item { FileSettingsSection("侧栏与书签", "长按入口可编辑或排序；根目录与内部存储固定保留。", KiyoriSemanticTone.ORANGE) {
                FileSettingRow("显示书签分类", "隐藏分类不会删除书签或使桌面快捷方式失效", settings.showBookmarks) { preferences.update { it.copy(showBookmarks = !it.showBookmarks) } }
                FileSettingRow("显示工作区分类", "只控制侧栏，不改变 AI 对话的工作区绑定", settings.showWorkspaces) { preferences.update { it.copy(showWorkspaces = !it.showWorkspaces) } }
                FileSettingRow("新书签放在顶部", "只影响以后添加的书签", settings.newBookmarksOnTop) { preferences.update { it.copy(newBookmarksOnTop = !it.newBookmarksOnTop) } }
                FileSettingRow("恢复隐藏的本地入口", "${settings.drawerHidden.size} 个入口被隐藏 · 保留名称与授权") { open("恢复隐藏的本地入口") }
                FileSettingRow("恢复默认侧栏布局", "恢复分类显示、内置入口及排列；保留自建入口") { open("恢复默认侧栏布局") }
            } }
            item { FileSettingsSection("存储与回收", "文件访问遵循系统授权；删除文件始终先确认。", KiyoriSemanticTone.GREEN) {
                FileSettingRow("管理访问权限", "查看存储、Shizuku 与 Root 的授权状态") { onOpenPermissions() }
                FileSettingRow("回收与恢复说明", "本地删除先移至回收站，永久删除需再次确认") { open("回收与恢复说明") }
            } }
            item { FileSettingsSection("记录与隐私", "历史仅保存在本机；清理记录不会删除文件或停止任务。", KiyoriSemanticTone.PINK) {
                FileSettingRow("清空搜索记录", "删除已保存的搜索条件和结果") { open("清空搜索记录") }
                FileSettingRow("清空已结束的任务记录", "保留正在执行的任务") { open("清空已结束的任务记录") }
                FileSettingRow("恢复浏览默认设置", "重置启动、显示、排序与刷新；保留书签和隐藏清单") { open("恢复浏览默认设置") }
            } }
        }
    }
    val title = dialog
    if (title != null) {
        val isPath = title == "左列启动目录" || title == "右列启动目录"
        val confirm = isPath || title in listOf("清空搜索记录", "清空已结束的任务记录", "恢复浏览默认设置", "恢复默认侧栏布局")
        AlertDialog(onDismissRequest = { if (!busy) dialog = null }, title = { Text(title) }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (title) {
                    "左列启动目录", "右列启动目录" -> OutlinedTextField(input, { input = it; error = null }, Modifier.fillMaxWidth(),
                        label = { Text("本地绝对目录路径") }, placeholder = { Text("例如 /storage/emulated/0/Download") },
                        isError = error != null, supportingText = { Text("留空使用内部存储。下次新建会话生效；目录不可访问时显示错误。") })
                    "显示密度" -> listOf("紧凑" to 0.8f, "标准" to 1f, "宽松" to 1.2f).forEach { (label, value) ->
                        FileSettingChoice(label, settings.itemSize == value) { preferences.update { it.copy(itemSize = value) }; dialog = null }
                    }
                    "文件名行数" -> (1..6).forEach { value -> FileSettingChoice("$value 行", settings.filenameLines == value) {
                        preferences.update { it.copy(filenameLines = value) }; dialog = null
                    } }
                    "隐藏项目的显示方式" -> {
                        FileSettingRow("显示系统隐藏项", "名称以点开头的文件和文件夹", settings.showHiddenFiles) { preferences.update { it.copy(showHiddenFiles = !it.showHiddenFiles) } }
                        FileSettingRow("显示手动隐藏项", "临时显示清单中的项目，保留清单", settings.showManuallyHiddenFiles) { preferences.update { it.copy(showManuallyHiddenFiles = !it.showManuallyHiddenFiles) } }
                    }
                    "管理手动隐藏清单" -> {
                        if (settings.manuallyHiddenFiles.isEmpty()) Text("没有手动隐藏的项目。可在文件列表选择项目后，从菜单中隐藏。")
                        settings.manuallyHiddenFiles.forEach { entry -> FileSettingRow(entry.path, "${entry.environment ?: "本地"} · 点按取消隐藏") {
                            preferences.update { it.copy(manuallyHiddenFiles = it.manuallyHiddenFiles - entry) }
                        } }
                    }
                    "初始排列" -> {
                        FileManagerSortMode.entries.forEach { mode -> FileSettingChoice(fileManagerSortLabel(mode), settings.sortMode == mode) { preferences.update { it.copy(sortMode = mode) } } }
                        HorizontalDivider()
                        listOf(false, true).forEach { descending -> FileSettingChoice(if (descending) "降序" else "升序", settings.sortDescending == descending) { preferences.update { it.copy(sortDescending = descending) } } }
                    }
                    "自动刷新间隔" -> listOf(0, 3, 10, 30).forEach { seconds -> FileSettingChoice(if (seconds == 0) "关闭" else "$seconds 秒", settings.refreshIntervalSeconds == seconds) {
                        preferences.update { it.copy(refreshIntervalSeconds = seconds) }; dialog = null
                    } }
                    "恢复隐藏的本地入口" -> {
                        if (settings.drawerHidden.isEmpty()) Text("没有隐藏的本地入口。")
                        settings.drawerHidden.forEach { id -> FileSettingRow(settings.drawerNames[id] ?: if (id.contains("linux")) "Linux" else "本地存储入口", "点按恢复到侧栏") {
                            preferences.update { it.copy(drawerHidden = it.drawerHidden - id) }
                        } }
                    }
                    "恢复默认侧栏布局" -> Text("恢复侧栏分类与内置入口，清除自定义顺序和入口隐藏状态。保留自建书签、网络配置、工作区及入口名称；已删除的授权目录需要重新添加。")
                    "回收与恢复说明" -> {
                        Text("本地文件的“删除”先移到源存储卷的回收目录，保留原始位置。回收失败会显示原因，不会自动改为永久删除。")
                        Text("从侧栏“工具 → 回收站”恢复项目。原位置已有同名文件时停止恢复，不覆盖现有文件。")
                        Text("永久删除必须单独确认，无法从回收站恢复。共享存储的回收内容需手动清理；内部及旧应用专属目录的回收内容可能随清除数据或卸载删除。")
                        Text("网络和授权目录的操作能力由存储后端决定，当前未支持的操作会明确提示。")
                    }
                    "清空搜索记录" -> Text("清空本机保存的搜索条件与结果列表？正在显示的目录和实际文件不会改变。")
                    "清空已结束的任务记录" -> Text("清空已结束任务的操作记录？正在执行的任务和实际文件会保留。")
                    "恢复浏览默认设置" -> Text("左右启动目录恢复为内部存储；标准密度、文件名最多 4 行、时间显示到秒、开启目录大小统计、每 3 秒刷新，按名称升序。显示系统隐藏项，不显示手动隐藏项。侧栏入口、隐藏清单和历史记录会保留。")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (!confirm) { dialog = null; return@TextButton }
                if (isPath) {
                    val path = input.trim()
                    if (path.isNotEmpty() && !validFileManagerStartPath(path)) { error = "请输入以 / 开头的路径，不使用 .、.. 或控制字符"; return@TextButton }
                    preferences.update { if (title == "左列启动目录") it.copy(leftStartPath = path) else it.copy(rightStartPath = path) }
                    dialog = null
                } else {
                    busy = true
                    scope.launch {
                        try {
                            when (title) {
                                "清空搜索记录" -> FileManagerHistoryStore.getInstance(context).removeSearch(null)
                                "清空已结束的任务记录" -> FileManagerHistoryStore.getInstance(context).removeTask(null)
                                "恢复默认侧栏布局" -> preferences.update { it.copy(drawerOrder = emptyList(), drawerHidden = emptySet(), drawerRemoved = emptySet(), showBookmarks = true, showWorkspaces = true) }
                                "恢复浏览默认设置" -> preferences.update { FileManagerSettings(manuallyHiddenFiles = it.manuallyHiddenFiles,
                                    drawerOrder = it.drawerOrder, drawerHidden = it.drawerHidden, drawerRemoved = it.drawerRemoved, drawerNames = it.drawerNames,
                                    defaultWorkspacePath = it.defaultWorkspacePath, showBookmarks = it.showBookmarks, showWorkspaces = it.showWorkspaces, newBookmarksOnTop = it.newBookmarksOnTop) }
                            }
                            dialog = null
                            busy = false
                            snackbar.showSnackbar("已完成")
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (failure: Exception) {
                            error = "操作失败，请重试"
                            com.ai.assistance.operit.util.AppLogger.e("FileManagerSettings", "清理或重置失败", failure)
                        } finally { busy = false }
                    }
                }
            }) { Text(if (busy) "处理中…" else if (isPath) "保存" else if (confirm) "确认" else "完成") }
        }, dismissButton = { if (confirm) TextButton(enabled = !busy, onClick = { dialog = null }) { Text("取消") } })
    }
}

@Composable
private fun FileSettingsSection(title: String, description: String, tone: KiyoriSemanticTone, content: @Composable ColumnScope.() -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = tone.resolveColors().icon, style = MaterialTheme.typography.titleSmall)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        content()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FileSettingRow(title: String, description: String, checked: Boolean? = null, onClick: () -> Unit) {
    val interaction = if (checked == null) Modifier.clickable(role = Role.Button, onClick = onClick)
        else Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { onClick() })
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).then(interaction)
        .padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(16.dp))
        if (checked != null) Switch(checked, onCheckedChange = null)
        else Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FileSettingChoice(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null); Spacer(Modifier.width(12.dp)); Text(title)
    }
}
