package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import java.text.DateFormat
import java.util.Date

@Composable
fun FileManagerSearchHistoryDrawer(records: List<FileManagerSearchRecord>, error: String?, onDismiss: () -> Unit,
    onOpen: (FileManagerSearchRecord) -> Unit, onDelete: (String?) -> Unit,
) {
    var selected by remember { mutableStateOf<FileManagerSearchRecord?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    FileManagerBrowseDrawer("最近搜索", "保存最近 20 次搜索，每次最多 1000 项结果", { onDismiss(); selected?.let(onOpen) }) { dismiss ->
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (records.isEmpty()) Text("暂无搜索记录")
        else TextButton({ confirmClear = true }) { Text("清空搜索记录") }
        records.forEach { record ->
            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.clickable { selected = record; dismiss() }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(record.query.ifBlank { "按高级条件搜索" }, style = MaterialTheme.typography.titleSmall)
                    Text(fileManagerLocationLabel(record.location), style = MaterialTheme.typography.bodySmall)
                    Text("${historyTime(record.time)} · ${record.status} · ${record.total} 项", style = MaterialTheme.typography.bodySmall)
                    TextButton({ onDelete(record.id) }) { Text("删除记录") }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("清空搜索记录？") },
        text = { Text("仅删除保存的搜索记录与结果，不删除文件。") },
        confirmButton = { TextButton({ onDelete(null); confirmClear = false }) { Text("清空") } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("取消") } })
}

@Composable
fun FileManagerTaskDrawer(initialRecent: Boolean, records: List<FileManagerTaskRecord>, transfer: FileManagerTransferState,
    action: FileManagerActionState?, writing: Boolean, conflict: Boolean, error: String?, onDismiss: () -> Unit,
    onTransfer: () -> Unit, onDelete: (String?) -> Unit, onOpenLocation: (FileManagerLocation) -> Unit,
) {
    var recent by remember { mutableStateOf(initialRecent) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    FileManagerBrowseDrawer("文件任务", "", { onDismiss(); afterClose?.invoke() }) { dismiss ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(!recent, { recent = false }, label = { Text("进行中") })
            FilterChip(recent, { recent = true }, label = { Text("最近任务") })
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!recent) {
            if (transfer.running) {
                Text(if (transfer.move) "移动 · ${transfer.total} 项" else "复制 · ${transfer.total} 项", style = MaterialTheme.typography.titleMedium)
                transfer.source?.let { Text("来源：${fileManagerLocationLabel(it)}") }
                transfer.destination?.let { Text("目标：${fileManagerLocationLabel(it)}") }
                Text(if (conflict) "需要处理同名冲突" else "已处理 ${transfer.results.size}/${transfer.total} · ${transfer.currentName.orEmpty()}")
                LinearProgressIndicator(progress = { transfer.results.size.toFloat() / transfer.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                Button({ afterClose = onTransfer; dismiss() }) { Text("查看进度与处理") }
            } else if (writing) {
                Text("文件操作进行中")
                action?.let { Text("${it.currentName.orEmpty()} · 已处理 ${it.results.size}/${it.files.size}") }
                Text("关闭面板可返回当前操作详情。", style = MaterialTheme.typography.bodySmall)
            } else Text("暂无进行中的任务")
        } else {
            val finished = records.filter { it.finishedAt != null }
            if (finished.isEmpty()) Text("暂无最近任务")
            else TextButton({ confirmClear = true }) { Text("清空已结束的任务记录") }
            finished.forEach { record ->
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${record.title} · ${record.total} 项", Modifier.fillMaxWidth().clickable { expanded = if (expanded == record.id) null else record.id }, style = MaterialTheme.typography.titleSmall)
                        Text("${historyTime(record.time)} · ${record.status}", style = MaterialTheme.typography.bodySmall)
                        Text(fileManagerLocationLabel(record.source), style = MaterialTheme.typography.bodySmall)
                        record.destination?.let { Text("→ ${fileManagerLocationLabel(it)}", style = MaterialTheme.typography.bodySmall) }
                        TextButton({ expanded = if (expanded == record.id) null else record.id }) { Text(if (expanded == record.id) "收起结果" else "查看结果") }
                        if (expanded == record.id) {
                            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(record.results) { result ->
                                    Column {
                                        Text("${result.name} · ${outcomeLabel(result.outcome)}", style = MaterialTheme.typography.bodyMedium)
                                        result.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        result.destination?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                            }
                            if (record.results.size < record.resultCount) Text("保存 ${record.results.size}/${record.resultCount} 项详情；摘要状态按完整批次计算。")
                            if (record.status == "结果待确认") Text("请核对来源与目标；不会自动重新执行。")
                            TextButton({ afterClose = { onOpenLocation(record.source) }; dismiss() }, enabled = !writing) { Text("打开来源位置") }
                            record.destination?.let { target -> TextButton({ afterClose = { onOpenLocation(target) }; dismiss() }, enabled = !writing) { Text("打开目标位置") } }
                            TextButton({ onDelete(record.id) }) { Text("删除记录") }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("清空任务记录？") },
        text = { Text("不删除文件、不撤销操作，也不取消进行中的任务。") },
        confirmButton = { TextButton({ onDelete(null); confirmClear = false }) { Text("清空") } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("取消") } })
}

private fun historyTime(time: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))
private fun outcomeLabel(outcome: FileManagerTransferOutcome) = when (outcome) {
    FileManagerTransferOutcome.COMPLETED -> "完成"; FileManagerTransferOutcome.FAILED -> "失败"
    FileManagerTransferOutcome.COPIED_SOURCE_RETAINED -> "已复制，来源保留"; FileManagerTransferOutcome.SKIPPED -> "已跳过"
    FileManagerTransferOutcome.NOT_STARTED -> "未执行"; FileManagerTransferOutcome.UNKNOWN -> "结果待确认"
}
