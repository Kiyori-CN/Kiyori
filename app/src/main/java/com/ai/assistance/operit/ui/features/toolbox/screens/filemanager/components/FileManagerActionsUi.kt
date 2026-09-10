package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.formatFileSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileManagerDestinationDialog(draft: FileManagerTransferDraft?, onBrowse: () -> Unit, onOther: () -> Unit, onDismiss: () -> Unit) {
    if (draft == null) return
    val verb = if (draft.move) "移动" else "复制"
    AlertDialog(onDismissRequest = onDismiss, title = { Text("$verb · 选择目标") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${draft.files.size} 项 · ${draft.source.environment ?: "手机存储"}")
            SelectionContainer { Text(draft.source.path, style = MaterialTheme.typography.bodySmall) }
            Text("${draft.files.take(3).joinToString("、") { it.name }}${if (draft.files.size > 3) "…" else ""}")
            OutlinedButton(onClick = onOther, modifier = Modifier.fillMaxWidth()) {
                Column { Text("使用另一位置"); Text("${draft.other.environment ?: "手机存储"} · ${draft.other.path}", style = MaterialTheme.typography.bodySmall) }
            }
            Text("选择“浏览目标目录”后，可轻触左右栏、返回或进入文件夹，到达目标后在浏览选项中点击“粘贴到当前目录”。")
            if (draft.move) Text("当前支持同一文件系统内移动；跨文件系统会明确失败并保留源项目。", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(onClick = onBrowse) { Text("浏览目标目录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun FileManagerActionDialog(
    state: FileManagerActionState?, canWrite: Boolean, onDismiss: () -> Unit,
    onNameChange: (String) -> Unit, onConfirm: () -> Unit, onInspect: (Boolean) -> Unit,
    onCopyText: (String) -> Unit,
) {
    if (state == null) return
    val destructive = state.kind == FileManagerActionKind.DELETE
    val write = destructive || state.kind == FileManagerActionKind.ZIP || state.kind == FileManagerActionKind.EXTRACT
    val title = when (state.kind) {
        FileManagerActionKind.DELETE -> "移至回收站"
        FileManagerActionKind.ZIP -> if (state.shareAfter) "压缩并分享" else "压缩为 ZIP"
        FileManagerActionKind.EXTRACT -> "解压 ZIP"
        FileManagerActionKind.PROPERTIES -> "属性"
        FileManagerActionKind.TOOLS -> "文件工具"
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (state.completed) "$title · 已完成" else title) },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(state.file.name, style = MaterialTheme.typography.titleSmall)
            SelectionContainer { Text(fileManagerJoinPath(state.location.path, state.file.name), style = MaterialTheme.typography.bodySmall) }
            if (state.loading || state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.running) Text("正在执行，请等待结果；完成前请保留此页面。")
            state.inspection?.let { info ->
                Text("${if (info.directory) "文件夹" else "文件"} · ${formatFileSize(info.bytes)}（${info.bytes} 字节）")
                if (info.directory) Text("${info.files} 个文件 · ${(info.directories - 1).coerceAtLeast(0)} 个子文件夹")
                Text("修改时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(info.modified))}", style = MaterialTheme.typography.bodySmall)
                Text("读取：${if (info.readable) "允许" else "不可用"} · 写入：${if (info.writable) "允许" else "不可用"}", style = MaterialTheme.typography.bodySmall)
                if (!write) {
                    if (!info.directory) OutlinedButton(onClick = { onInspect(true) }, enabled = !state.loading) { Text("计算 SHA-256") }
                    info.sha256?.let { hash -> SelectionContainer { Text("SHA-256\n$hash", style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = { onCopyText(hash) }) { Text("复制 SHA-256") } }
                }
            }
            if (state.kind == FileManagerActionKind.TOOLS) {
                OutlinedButton(onClick = { onCopyText(state.file.name) }) { Text("复制名称") }
                OutlinedButton(onClick = { onCopyText(fileManagerJoinPath(state.location.path, state.file.name)) }) { Text("复制完整路径") }
            }
            if ((state.kind == FileManagerActionKind.ZIP || state.kind == FileManagerActionKind.EXTRACT) && !state.completed) {
                OutlinedTextField(state.outputName, onNameChange, label = { Text(if (state.kind == FileManagerActionKind.EXTRACT) "目标文件夹名称" else "压缩包名称") }, singleLine = true,
                    enabled = !state.running && !state.unknown, isError = fileManagerNameError(state.outputName) != null)
                Text(if (state.kind == FileManagerActionKind.EXTRACT) "解压到当前目录的新文件夹，同名不覆盖。支持未加密 ZIP，最多 100000 项、8 GiB。" else "保存在当前目录，包含所选项目本身；同名不覆盖。", style = MaterialTheme.typography.bodySmall)
            }
            if (destructive && !state.completed) Text("将此项目及全部内容移至回收站，可在存储抽屉中恢复。同名不覆盖；跨文件系统会失败并保留原文件，不会自动永久删除。", color = MaterialTheme.colorScheme.error)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.stagingPath?.let { SelectionContainer { Text("需检查的位置：\n$it") } }
            if (state.error != null && !state.unknown && !state.running && !state.loading) TextButton(onClick = { onInspect(false) }) { Text("重新检查项目") }
        } },
        confirmButton = {
            if (write && !state.completed) Button(onClick = onConfirm,
                enabled = canWrite && !state.loading && !state.running && !state.unknown && state.inspection != null && state.error == null &&
                    (destructive || fileManagerNameError(state.outputName) == null),
                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()) {
                Text(if (destructive) "移至回收站" else if (state.kind == FileManagerActionKind.EXTRACT) "开始解压" else "开始压缩")
            } else TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = { if (write && !state.completed) TextButton(onClick = onDismiss, enabled = !state.running) { Text("取消") } },
    )
}
