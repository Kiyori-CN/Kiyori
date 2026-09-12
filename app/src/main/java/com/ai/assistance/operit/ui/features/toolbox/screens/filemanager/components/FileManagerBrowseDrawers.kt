package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.ai.assistance.operit.ui.components.KiyoriModalBottomDrawer
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*

@Composable
internal fun FileManagerBrowseDrawer(title: String, location: String, onDismiss: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    KiyoriModalBottomDrawer(onDismissRequest = onDismiss) { dismiss ->
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "关闭") }
            }
            if (location.isNotEmpty()) Text(location, style = MaterialTheme.typography.bodySmall)
            content(dismiss)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileManagerFilterDrawer(pane: FileManagerPane, state: FileManagerPaneState, onDismiss: () -> Unit,
    onApply: (FileManagerFilterDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(state.filterDraft) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmed by remember { mutableStateOf<FileManagerFilterDraft?>(null) }
    FileManagerBrowseDrawer("过滤 · ${paneLabel(pane)}", fileManagerLocationLabel(FileManagerLocation(state.path, state.environment)),
        onDismiss = { onDismiss(); confirmed?.let(onApply) }) { dismiss ->
        Text("确认后只更新${paneLabel(pane)}；不同条件同时满足，多种格式任选其一。", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text("文件名包含") }, singleLine = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FileManagerItemType.entries.forEach { type -> FilterChip(draft.type == type, { draft = draft.copy(type = type) }, label = { Text(type.label) }) }
        }
        Text("文件格式", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("jpg", "png", "mp4", "mp3", "pdf", "zip", "apk").forEach { format ->
                val selected = draft.formats.split(',', '，', ';', '；', ' ').filter { it.isNotBlank() }.toSet()
                FilterChip(format in selected, { draft = draft.copy(formats = (if (format in selected) selected - format else selected + format).joinToString(", ")) }, label = { Text(format.uppercase()) })
            }
        }
        OutlinedTextField(draft.formats, { draft = draft.copy(formats = it) }, Modifier.fillMaxWidth(),
            label = { Text("扩展名，逗号分隔") }, placeholder = { Text("例如 pdf, tar.gz；留空不限") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(draft.noExtension, { draft = draft.copy(noExtension = it) }); Text("包含无扩展名文件")
        }
        Text("文件大小", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FileManagerSizeUnit.entries.forEach { unit -> FilterChip(draft.unit == unit, { draft = draft.copy(unit = unit) }, label = { Text(unit.name) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft.minimum, { draft = draft.copy(minimum = it) }, Modifier.weight(1f), label = { Text("最小值") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(draft.maximum, { draft = draft.copy(maximum = it) }, Modifier.weight(1f), label = { Text("最大值") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        Text("修改时间", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "不限", 1 to "一天", 7 to "七天", 30 to "三十天", -1 to "自定义").forEach { (days, label) ->
                FilterChip(draft.days == days, { draft = draft.copy(days = days) }, label = { Text(label) })
            }
        }
        if (draft.days < 0) {
            OutlinedTextField(draft.from, { draft = draft.copy(from = it) }, Modifier.fillMaxWidth(), label = { Text("起始日期 yyyy-MM-dd") }, singleLine = true)
            OutlinedTextField(draft.to, { draft = draft.copy(to = it) }, Modifier.fillMaxWidth(), label = { Text("结束日期 yyyy-MM-dd（含当天）") }, singleLine = true)
        }
        Text("大小与格式仅匹配文件；时间不可用的项目不匹配时间条件。留空表示不限。", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { confirmed = FileManagerFilterDraft(); dismiss() }, modifier = Modifier.weight(1f)) { Text("清除过滤") }
            Button(onClick = {
                try { draft.compile(); confirmed = draft; dismiss() }
                catch (failure: IllegalArgumentException) { error = failure.message }
            }, modifier = Modifier.weight(1f)) { Text("应用过滤") }
        }
    }
}

internal fun paneLabel(pane: FileManagerPane) = if (pane == FileManagerPane.LEFT) "左列" else "右列"
internal fun sortLabel(mode: FileManagerSortMode) = when (mode) {
    FileManagerSortMode.NAME -> "名称"; FileManagerSortMode.SIZE -> "大小"
    FileManagerSortMode.MODIFIED -> "修改时间"; FileManagerSortMode.FORMAT -> "文件格式"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileManagerSortDrawer(pane: FileManagerPane, state: FileManagerPaneState, onDismiss: () -> Unit,
    onApply: (FileManagerSortMode, Boolean) -> Unit,
) {
    var mode by remember { mutableStateOf(state.sortMode) }
    var descending by remember { mutableStateOf(state.sortDescending) }
    var apply by remember { mutableStateOf(false) }
    FileManagerBrowseDrawer("排序 · ${paneLabel(pane)}", fileManagerLocationLabel(FileManagerLocation(state.path, state.environment)),
        { onDismiss(); if (apply) onApply(mode, descending) }) { dismiss ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FileManagerSortMode.entries.forEach { item -> FilterChip(mode == item, { mode = item }, label = { Text(sortLabel(item)) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!descending, { descending = false }, label = { Text(if (mode == FileManagerSortMode.MODIFIED) "最早在前" else "升序") })
            FilterChip(descending, { descending = true }, label = { Text(if (mode == FileManagerSortMode.MODIFIED) "最新在前" else "降序") })
        }
        Text("目录始终优先；仅更改${paneLabel(pane)}的排列，另一列保持不变。")
        Button({ apply = true; dismiss() }, Modifier.fillMaxWidth()) { Text("应用排序") }
    }
}
