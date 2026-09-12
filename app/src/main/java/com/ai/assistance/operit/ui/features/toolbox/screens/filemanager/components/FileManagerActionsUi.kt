package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.formatFileSize
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun FileManagerLocationCard(label: String, location: FileManagerLocation) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Text(fileManagerLocationLabel(location), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun FileManagerDestinationDialog(draft: FileManagerTransferDraft?, onBrowse: () -> Unit, onOther: () -> Unit, onDismiss: () -> Unit) {
    if (draft == null) return
    val verb = if (draft.move) "移动" else "复制"
    val otherError = fileManagerTransferError(FileManagerCopyRequest(draft.files, draft.source, draft.other, draft.move))
    AlertDialog(onDismissRequest = onDismiss, title = { Text("$verb · 选择目标") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("已选 ${draft.files.size} 项", style = MaterialTheme.typography.titleSmall)
            FileManagerLocationCard("来源", draft.source)
            Text(draft.files.take(3).joinToString("、") { it.displayName } + if (draft.files.size > 3) "等" else "", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onOther, enabled = otherError == null, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("使用另一栏目录")
                    Text(fileManagerLocationLabel(draft.other), style = MaterialTheme.typography.bodySmall)
                }
            }
            otherError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("也可以浏览其他文件夹，再点击底部的“粘贴到此处”。切换左右栏不会改变已选来源。", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(onClick = onBrowse) { Text("浏览目标目录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun FileManagerActionDialog(
    state: FileManagerActionState?, canWrite: Boolean, onDismiss: () -> Unit,
    onNameChange: (String) -> Unit, onConfirm: () -> Unit, onInspect: (Boolean) -> Unit,
    onStop: () -> Unit, onOpenRecycleBin: () -> Unit, onCopyText: (String) -> Unit,
) {
    if (state == null) return
    val purge = state.kind == FileManagerActionKind.PURGE
    val write = state.kind !in setOf(FileManagerActionKind.PROPERTIES, FileManagerActionKind.TOOLS)
    val hasResults = state.results.isNotEmpty()
    val finished = hasResults && !state.running
    val title = when (state.kind) {
        FileManagerActionKind.DELETE -> "移至回收站"
        FileManagerActionKind.RESTORE -> "恢复到原位置"
        FileManagerActionKind.PURGE -> "永久删除"
        FileManagerActionKind.RENAME -> if (state.files.size > 1) "批量重命名" else "重命名"
        FileManagerActionKind.ZIP -> if (state.shareAfter) "压缩并分享" else "压缩为 ZIP"
        FileManagerActionKind.EXTRACT -> "解压 ZIP"
        FileManagerActionKind.PROPERTIES -> "属性"
        FileManagerActionKind.TOOLS -> "文件工具"
    }
    val nameInput = state.kind in setOf(FileManagerActionKind.RENAME, FileManagerActionKind.ZIP, FileManagerActionKind.EXTRACT) &&
        !(state.kind == FileManagerActionKind.EXTRACT && state.files.size > 1)
    val nameError = if (!nameInput) null else if (state.kind == FileManagerActionKind.RENAME)
        fileManagerBatchRenameError(state.files, state.outputName) else fileManagerNameError(state.outputName)
    val completedCount = state.results.count { it.outcome == FileManagerTransferOutcome.COMPLETED }
    val resultTitle = when {
        state.completed -> "操作已完成"
        state.unknown -> "部分结果需检查"
        state.stopRequested -> "操作已停止"
        completedCount > 0 -> "部分项目已完成"
        else -> "操作未完成"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = !state.running, dismissOnClickOutside = !state.running),
        icon = { Icon(when {
            state.completed -> Icons.Rounded.CheckCircleOutline
            finished -> Icons.Rounded.Info
            purge -> Icons.Rounded.DeleteForever
            state.kind == FileManagerActionKind.DELETE -> Icons.Rounded.DeleteOutline
            state.kind == FileManagerActionKind.RESTORE -> Icons.Rounded.Restore
            else -> Icons.Rounded.FolderOpen
        }, null, tint = if (purge) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
        title = { Text(if (finished) resultTitle else title) },
        text = { LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (finished) "$title · 完成 $completedCount/${if (state.kind == FileManagerActionKind.ZIP) 1 else state.files.size}" else "${state.files.size} 个项目" +
                        if (state.inspections.isNotEmpty()) " · ${formatFileSize(state.inspections.values.sumOf { it.bytes })}" else "", style = MaterialTheme.typography.titleSmall)
                    if (state.loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("正在检查项目及文件夹内容…", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.running) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("已处理 ${state.results.size}/${if (state.kind == FileManagerActionKind.ZIP) 1 else state.files.size} · ${state.currentName.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                        Text(if (state.stopRequested) "正在等待当前项完成，随后停止。" else "当前项目执行期间请等待结果。", style = MaterialTheme.typography.bodySmall)
                        if (state.files.size > 1 && state.kind != FileManagerActionKind.ZIP)
                            TextButton(onClick = onStop, enabled = !state.stopRequested) { Text("完成当前项后停止") }
                    }
                    if (!finished) {
                        val explanation = when (state.kind) {
                            FileManagerActionKind.DELETE -> "项目将从原位置移走，可从左侧存储抽屉的回收站恢复。回收内容仍占用空间。共享存储回收内容需手动清理，不会随卸载自动删除；应用内部及旧专属目录的回收内容会随清除应用数据丢失。"
                            FileManagerActionKind.PURGE -> "所选回收项目及其全部内容将被永久删除，无法恢复。"
                            FileManagerActionKind.RESTORE -> "恢复到各项目的原位置。已有同名项目不会被覆盖；原父目录不存在时会保留回收内容并说明原因。"
                            else -> null
                        }
                        explanation?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                            color = if (purge) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    state.error?.let { error ->
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                            SelectionContainer { Text(error, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                        if (!hasResults && !state.running && !state.loading && !state.unknown)
                            TextButton(onClick = { onInspect(false) }) { Text("重新检查") }
                    }
                    if (state.unknown) Text("请核对来源、目标或回收站中的实际内容，再决定下一步。此批次不会自动重试。", style = MaterialTheme.typography.bodySmall)
                    if (finished && state.kind == FileManagerActionKind.DELETE)
                        OutlinedButton(onClick = onOpenRecycleBin, modifier = Modifier.fillMaxWidth()) { Text("查看回收站") }
                }
            }
            if (!finished) {
                item { FileManagerLocationCard("来源目录", state.location) }
                items(state.files, key = { it.name }) { file ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(file.displayName, style = MaterialTheme.typography.titleSmall)
                        if (!write || state.kind == FileManagerActionKind.RESTORE) SelectionContainer {
                            Text(file.recycledOriginalPath ?: fileManagerJoinPath(state.location.path, file.name), style = MaterialTheme.typography.bodySmall)
                        }
                        state.inspections[file.name]?.let { info ->
                            Text("${if (info.directory) "文件夹" else "文件"} · ${formatFileSize(info.bytes)}" +
                                if (info.directory) " · ${info.files} 个文件，${(info.directories - 1).coerceAtLeast(0)} 个子文件夹" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!write) {
                                Text("修改时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(Date(info.modified))}", style = MaterialTheme.typography.bodySmall)
                                Text("读取：${if (info.readable) "允许" else "不可用"} · 写入：${if (info.writable) "允许" else "不可用"}", style = MaterialTheme.typography.bodySmall)
                                if (!info.directory && state.files.size == 1) OutlinedButton(onClick = { onInspect(true) }, enabled = !state.loading) { Text("计算 SHA-256") }
                                info.sha256?.let { hash ->
                                    SelectionContainer { Text(hash, style = MaterialTheme.typography.bodySmall) }
                                    TextButton(onClick = { onCopyText(hash) }) { Text("复制 SHA-256") }
                                }
                            }
                        }
                    }
                }
                if (nameInput && !hasResults) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(state.outputName, onNameChange, modifier = Modifier.fillMaxWidth(),
                            label = { Text(when (state.kind) { FileManagerActionKind.RENAME -> "名称模板"; FileManagerActionKind.EXTRACT -> "目标文件夹名称"; else -> "压缩包名称" }) },
                            singleLine = true, enabled = !state.running && !state.unknown, isError = nameError != null,
                            supportingText = { nameError?.let { Text(it) } })
                        Text(if (state.kind == FileManagerActionKind.RENAME) "{name} 原名称 · {ext} 扩展名 · {n} 序号；按当前顺序编号，不覆盖同名项目。"
                            else if (state.kind == FileManagerActionKind.EXTRACT) "解压到当前目录的新文件夹，同名不覆盖。支持未加密 ZIP，最多 100000 项、8 GiB。"
                            else "保存在当前目录，包含所选项目本身；同名不覆盖。", style = MaterialTheme.typography.bodySmall)
                        if (state.kind == FileManagerActionKind.RENAME && nameError == null)
                            state.files.take(5).forEachIndexed { index, file -> Text("${file.displayName} → ${fileManagerBatchRenameName(file, index, state.outputName)}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (state.kind == FileManagerActionKind.EXTRACT && state.files.size > 1) item { Text("分别解压到当前目录的“原文件名_extracted”文件夹，同名不覆盖。", style = MaterialTheme.typography.bodySmall) }
            }
            items(state.results) { result ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider()
                    Text(result.name, style = MaterialTheme.typography.titleSmall)
                    Text(when (result.outcome) {
                        FileManagerTransferOutcome.COMPLETED -> "已完成"
                        FileManagerTransferOutcome.UNKNOWN -> "结果需检查"
                        FileManagerTransferOutcome.NOT_STARTED -> "未执行"
                        FileManagerTransferOutcome.SKIPPED -> "已跳过"
                        else -> "失败"
                    }, style = MaterialTheme.typography.labelMedium,
                        color = if (result.outcome == FileManagerTransferOutcome.FAILED || result.outcome == FileManagerTransferOutcome.UNKNOWN) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Column {
                        result.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        result.destination?.let { Text("目标：$it", style = MaterialTheme.typography.bodySmall) }
                        result.stagingPath?.let { Text("需检查的位置：$it", style = MaterialTheme.typography.bodySmall) }
                    } }
                }
            }
            if (finished || state.error != null || state.kind == FileManagerActionKind.TOOLS) item {
                TextButton(onClick = { onCopyText(buildString {
                    appendLine(title); appendLine(fileManagerLocationLabel(state.location))
                    state.files.forEach { appendLine(it.recycledOriginalPath ?: fileManagerJoinPath(state.location.path, it.name)); it.fullPath?.let { path -> appendLine(path) } }
                    state.error?.let { appendLine(it) }
                    state.results.forEach { appendLine("${it.name}: ${it.outcome} ${it.message.orEmpty()}"); it.stagingPath?.let { path -> appendLine(path) } }
                }) }) { Text(if (finished || state.error != null) "复制操作详情" else "复制完整路径") }
            }
        } },
        confirmButton = {
            if (write && !finished) Button(onClick = onConfirm,
                enabled = canWrite && !state.loading && !state.running && !state.unknown && !hasResults && state.inspections.size == state.files.size && state.error == null && nameError == null,
                colors = if (purge) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()) {
                Text(if (state.running) "正在执行" else when (state.kind) {
                    FileManagerActionKind.DELETE -> "移至回收站"
                    FileManagerActionKind.PURGE -> "永久删除"
                    FileManagerActionKind.RESTORE -> "恢复"
                    FileManagerActionKind.RENAME -> "开始重命名"
                    FileManagerActionKind.EXTRACT -> "开始解压"
                    else -> "开始压缩"
                })
            } else TextButton(onClick = onDismiss) { Text(if (state.completed) "完成" else "关闭") }
        },
        dismissButton = { if (write && !finished) TextButton(onClick = onDismiss, enabled = !state.running) { Text("取消") } },
    )
}
