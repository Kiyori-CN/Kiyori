package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.defaultTool.standard.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FileManagerRecycleBinDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<RecycledFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<RecycledFile?>(null) }
    suspend fun reload() { entries = withContext(Dispatchers.IO) { fileRecycleRoots(context).flatMap(LocalFileRecycleBin::list).sortedByDescending { it.deletedAt } } }
    LaunchedEffect(Unit) {
        try { reload() } catch (failure: Exception) { error = "无法读取回收站：${failure.message}" }
        finally { loading = false }
    }
    fun operate(record: RecycledFile, delete: Boolean) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (delete) LocalFileRecycleBin.delete(record, NativeNoReplaceCommit::commit)
                    else LocalFileRecycleBin.restore(record, NativeNoReplaceCommit::commit)
                }
                reload()
            } catch (failure: Exception) { error = "操作未完成：${failure.message}" }
            finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("回收站") }, text = {
        Column {
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!loading && entries.isEmpty() && error == null) Text("回收站为空")
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(entries, key = { "${it.root}/${it.id}" }) { entry ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(java.io.File(entry.originalPath).name, style = MaterialTheme.typography.titleSmall)
                        Text(entry.originalPath, style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(enabled = !busy, onClick = { operate(entry, false) }) { Text("恢复") }
                            TextButton(enabled = !busy, onClick = { pendingDelete = entry }) { Text("永久删除") }
                        }
                    }
                }
            }
            Text("恢复到原位置，同名不覆盖。回收内容保留在应用存储中，卸载应用会删除。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("关闭") } })
    pendingDelete?.let { entry -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("永久删除") },
        text = { Text("永久删除 ${java.io.File(entry.originalPath).name}，此操作无法撤销。") },
        confirmButton = { TextButton(onClick = { pendingDelete = null; operate(entry, true) }) { Text("永久删除") } },
        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }) }
}
