package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import com.kiyori.design.theme.KiyoriUiShapes
import com.kiyori.design.theme.KiyoriSemanticTone
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.ai.assistance.operit.ui.components.KiyoriModalBottomDrawer

private fun locationLabel(location: FileManagerLocation): String = fileManagerLocationLabel(location)

@Composable
fun FileManagerClipboardBar(count: Int, move: Boolean, canPaste: Boolean, writing: Boolean,
    onPaste: () -> Unit, onClear: () -> Unit) {
    if (count == 0) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("待${if (move) "移动" else "复制"} $count 项", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onPaste, enabled = canPaste) { Text("粘贴到此处") }
            IconButton(onClick = onClear, enabled = !writing) { Icon(Icons.Rounded.Close, "取消待粘贴项目", Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun FileManagerCopyConfirmation(request: FileManagerCopyRequest?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (request == null) return
    val verb = if (request.moveRequested) "移动" else "复制"
    val requestError = fileManagerTransferError(request)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$verb ${request.files.size} 个项目") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FileManagerLocationCard("来源", request.source)
                FileManagerLocationCard("目标", request.destination)
                Text(request.files.take(5).joinToString("\n") { it.name } +
                    if (request.files.size > 5) "\n另有 ${request.files.size - 5} 项" else "")
                Text(if (request.moveRequested) "同卷移动成功后原位置不再保留。同名会暂停询问，跨文件系统会失败并保留源项目。" else "保留源项目。同名项目会暂停询问，不自动替换或合并。",
                    style = MaterialTheme.typography.bodySmall)
                requestError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = requestError == null) { Text("开始$verb") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (requestError == null) "取消" else "重新选择目录") } },
    )
}

@Composable
fun FileManagerCopyConflictDialog(
    conflict: FileManagerCopyConflict?,
    destination: FileManagerLocation?,
    directory: Boolean,
    onResolve: (String?) -> Unit,
    onStop: () -> Unit,
    move: Boolean = false,
) {
    if (conflict == null) return
    var newName by rememberSaveable(conflict) { mutableStateOf(fileManagerCopyName(conflict.destinationName, directory)) }
    val nameError = fileManagerNameError(newName)
    AlertDialog(
        // Back/点击外部停止剩余批次，不默认为覆盖或跳过。
        onDismissRequest = onStop,
        title = { Text("目标已有同名项目") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(conflict.destinationName)
                destination?.let { Text(locationLabel(it), style = MaterialTheme.typography.bodySmall) }
                Text("可以跳过此项，或为目标项目指定名称。已有项目保持不变。")
                OutlinedTextField(value = newName, onValueChange = { newName = it },
                    label = { Text("目标名称") }, singleLine = true, isError = nameError != null,
                    supportingText = { nameError?.let { Text(it) } }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onStop) { Text("停止后续项目") }
            }
        },
        confirmButton = {
            Button(onClick = { onResolve(newName) }, enabled = nameError == null && newName != conflict.destinationName) {
                Text(if (move) "改名后移动" else "保留两份")
            }
        },
        dismissButton = { TextButton(onClick = { onResolve(null) }) { Text("跳过此项") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerTransferDetails(
    visible: Boolean,
    state: FileManagerTransferState,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    onOpenDestination: () -> Unit,
) {
    if (!visible || state.total == 0) return
    KiyoriModalBottomDrawer(onDismissRequest = onDismiss) { dismissDrawer ->
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.running) (if (state.move) "正在移动" else "正在复制") else (if (state.move) "移动结果" else "复制结果"), style = MaterialTheme.typography.titleLarge)
            state.destination?.let {
                Text(locationLabel(it), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (state.running) {
                LinearProgressIndicator(progress = { state.results.size.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                Text(if (state.stopRequested) "正在等待当前项目完成，随后停止" else
                    "已处理 ${state.results.size}/${state.total} · ${state.currentName ?: "准备中"}")
                TextButton(onClick = onStop, enabled = !state.stopRequested) { Text("完成当前项后停止") }
            } else {
                val completed = state.results.count { it.outcome == FileManagerTransferOutcome.COMPLETED }
                val skipped = state.results.count { it.outcome == FileManagerTransferOutcome.SKIPPED }
                val notStarted = state.results.count { it.outcome == FileManagerTransferOutcome.NOT_STARTED }
                val attention = state.results.size - completed - skipped - notStarted
                Text("完成 $completed · 跳过 $skipped · 未执行 $notStarted · 需检查 $attention")
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.results, key = { it.name }) { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                        val label = when (item.outcome) {
                            FileManagerTransferOutcome.COMPLETED -> "已完成"
                            FileManagerTransferOutcome.FAILED -> "失败"
                            FileManagerTransferOutcome.COPIED_SOURCE_RETAINED -> "已复制，源仍保留"
                            FileManagerTransferOutcome.SKIPPED -> "已跳过"
                            FileManagerTransferOutcome.NOT_STARTED -> "未执行"
                            FileManagerTransferOutcome.UNKNOWN -> "结果未确认"
                        }
                        Text(label + (item.message?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                        SelectionContainer {
                            Column {
                                item.destination?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                item.stagingPath?.let { Text("需检查暂存位置：$it", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            if (!state.running && state.results.any { it.outcome == FileManagerTransferOutcome.COMPLETED })
                OutlinedButton(onClick = onOpenDestination, modifier = Modifier.fillMaxWidth()) { Text("查看目标目录") }
            TextButton(onClick = dismissDrawer) { Text(if (state.running) "继续浏览" else "关闭") }
        }
    }
}
