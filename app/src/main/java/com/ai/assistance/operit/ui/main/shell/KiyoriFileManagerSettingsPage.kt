package com.ai.assistance.operit.ui.main.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.preferences.FileManagerPreferences
import com.ai.assistance.operit.data.preferences.FileManagerSortMode

internal fun fileManagerSortLabel(mode: FileManagerSortMode): String = when (mode) {
    FileManagerSortMode.NAME -> "名称"
    FileManagerSortMode.SIZE -> "大小"
    FileManagerSortMode.MODIFIED -> "修改时间"
    FileManagerSortMode.FORMAT -> "文件格式"
}

@Composable
internal fun KiyoriFileManagerSettingsPage(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember(context) { FileManagerPreferences.getInstance(context) }
    val settings by preferences.state.collectAsState()
    var selection by remember { mutableStateOf<KiyoriSettingsSelection?>(null) }
    var showRecycleRules by remember { mutableStateOf(false) }
    KiyoriCollapsingSettingsPage(title = "文件管理器", onBack = onBack, modifier = modifier) {
        item {
            KiyoriSettingsGroupSection("文件列表", "隐藏项目与大小立即应用到两栏；默认排序用于新会话。") {
                KiyoriSettingsRow("显示隐藏项目", "显示名称以点开头的文件和文件夹", KiyoriSettingsRowKind.TOGGLE,
                    checked = settings.showHiddenFiles, onClick = {
                        preferences.update { it.copy(showHiddenFiles = !it.showHiddenFiles) }
                    })
                KiyoriSettingsDivider()
                KiyoriSettingsRow("默认排序方式", "文件夹始终优先显示", KiyoriSettingsRowKind.NAVIGATION,
                    value = fileManagerSortLabel(settings.sortMode), onClick = {
                        selection = KiyoriSettingsSelection("默认排序方式", fileManagerSortLabel(settings.sortMode),
                            FileManagerSortMode.entries.map { mode ->
                                KiyoriSettingsSelectionOption(fileManagerSortLabel(mode), selected = mode == settings.sortMode,
                                    onSelect = { preferences.update {
                                        if (it.sortMode == mode) it else it.copy(sortMode = mode, sortDescending = mode != FileManagerSortMode.NAME)
                                    } })
                            })
                    })
                KiyoriSettingsDivider()
                KiyoriSettingsRow("默认排序方向", "只调整文件夹组内和文件组内的顺序", KiyoriSettingsRowKind.NAVIGATION,
                    value = if (settings.sortDescending) "降序" else "升序", onClick = {
                        selection = KiyoriSettingsSelection("默认排序方向", if (settings.sortDescending) "降序" else "升序",
                            listOf(false, true).map { descending ->
                                KiyoriSettingsSelectionOption(if (descending) "降序" else "升序",
                                    description = when (settings.sortMode) {
                                        FileManagerSortMode.NAME -> if (descending) "名称从后到前" else "名称从前到后"
                                        FileManagerSortMode.SIZE -> if (descending) "大文件在前" else "小文件在前"
                                        FileManagerSortMode.FORMAT -> if (descending) "扩展名从后到前" else "扩展名从前到后"
                                        FileManagerSortMode.MODIFIED -> if (descending) "最近修改在前" else "较早修改在前"
                                    }, selected = descending == settings.sortDescending,
                                    onSelect = { preferences.update { it.copy(sortDescending = descending) } })
                            })
                    })
                KiyoriSettingsDivider()
                val sizes = listOf("紧凑" to 0.8f, "标准" to 1f, "宽松" to 1.2f)
                val sizeLabel = sizes.firstOrNull { it.second == settings.itemSize }?.first ?: "自定义"
                KiyoriSettingsRow("列表大小", "同时调整项目高度、图标和文字大小", KiyoriSettingsRowKind.NAVIGATION,
                    value = sizeLabel, onClick = {
                        selection = KiyoriSettingsSelection("列表大小", sizeLabel, sizes.map { (label, size) ->
                            KiyoriSettingsSelectionOption(label, selected = size == settings.itemSize,
                                onSelect = { preferences.update { it.copy(itemSize = size) } })
                        })
                    })
            }
        }
        item {
            KiyoriSettingsGroupSection("存储访问", "普通存储、Shizuku 和 Root 的可访问范围由系统权限决定。") {
                KiyoriSettingsRow("权限管理", "查看存储与 Shizuku 授权状态", KiyoriSettingsRowKind.NAVIGATION,
                    onClick = onOpenPermissions)
            }
        }
        item {
            KiyoriSettingsGroupSection("回收站", "在文件管理器左侧抽屉打开回收站，可恢复项目或永久删除。共享存储的回收内容不会随卸载自动清除。") {
                KiyoriSettingsRow("删除与恢复规则", "移至回收站保留原始位置；永久删除需要再次确认", KiyoriSettingsRowKind.NAVIGATION,
                    onClick = { showRecycleRules = true })
            }
        }
    }
    selection?.let { value ->
        KiyoriSettingsSelectionSheet(value, onDismiss = { selection = null }, onSelect = {
            it.onSelect()
            selection = null
        })
    }
    if (showRecycleRules) AlertDialog(
        onDismissRequest = { showRecycleRules = false },
        title = { Text("删除与恢复规则") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("移至回收站：保留原始位置，存放于源存储卷的回收目录；失败时显示具体结果。")
                Text("恢复项目：恢复到原始位置，遇到同名内容时不会覆盖。")
                Text("永久删除：确认后无法从回收站恢复，请先检查所选项目。")
                Text("共享存储中的回收内容需要手动清理；应用内部及旧应用专属目录中的回收内容仍可能随清除应用数据或卸载而删除。")
            }
        },
        confirmButton = { TextButton(onClick = { showRecycleRules = false }) { Text("知道了") } },
    )
}
