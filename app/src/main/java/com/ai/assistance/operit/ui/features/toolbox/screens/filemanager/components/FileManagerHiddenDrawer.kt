package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.preferences.FileManagerHiddenEntry
import com.ai.assistance.operit.ui.components.KiyoriModalBottomDrawer
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerViewModel

/** 手动隐藏只改变应用内可见性；编辑清单不会重命名、移动或删除真实文件。 */
@Composable
internal fun FileManagerHiddenDrawer(model: FileManagerViewModel, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<FileManagerHiddenEntry?>(null) }
    var path by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    KiyoriModalBottomDrawer(onDismissRequest = onDismiss) { dismiss ->
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("隐藏文件", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "关闭隐藏文件") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("控制双栏中的项目显示。手动隐藏仅在 Kiyori 生效，不改变文件，也不提供加密保护。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { HiddenSwitch("显示系统隐藏项", "名称以点开头的文件与文件夹", model.showHiddenFiles, model::setShowSystemHidden) }
            item { HiddenSwitch("显示手动隐藏项", "临时查看清单中的项目，清单仍保留", model.showManuallyHiddenFiles, model::setShowManuallyHidden) }
            item {
                val count = model.selectedFiles.size
                Button(onClick = { model.hideSelectedFiles(); notice = "已隐藏 $count 项，可在下方恢复显示" },
                    enabled = count > 0 && !model.isWriting && model.activePaneState.environment != "recycle",
                    modifier = Modifier.fillMaxWidth()) { Text(if (count > 0) "隐藏所选 $count 项" else "先在列表中选择项目") }
                if (model.activePaneState.environment == "recycle") Text("回收站不隐藏项目，便于找回文件。", style = MaterialTheme.typography.bodySmall)
                notice?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall) }
            }
            item { Text("手动隐藏清单 · ${model.manuallyHiddenFiles.size}", style = MaterialTheme.typography.titleSmall) }
            if (model.manuallyHiddenFiles.isEmpty()) item {
                Text("暂无手动隐藏项。返回列表选择文件或文件夹后，在这里隐藏。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(model.manuallyHiddenFiles.sortedWith(compareBy({ it.environment.orEmpty() }, { it.path })), key = { "${it.environment.orEmpty().length}:${it.environment.orEmpty()}${it.path}" }) { entry ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(entry.path.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(entry.path, style = MaterialTheme.typography.bodySmall)
                        Text(when (entry.environment) { null -> "手机存储"; "linux" -> "Ubuntu"; else -> entry.environment },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton({ editing = entry; path = entry.path; error = null }) { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("编辑路径") }
                            TextButton({ model.removeHiddenEntry(entry) }) { Icon(Icons.Outlined.Visibility, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("取消隐藏") }
                        }
                    }
                }
            }
        }
        editing?.let { original ->
            AlertDialog(onDismissRequest = { editing = null }, title = { Text("编辑隐藏路径") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("只修改隐藏清单。文件已移动时，可更新为它的新路径。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(path, { path = it; error = null }, Modifier.fillMaxWidth(), label = { Text("完整路径") },
                        isError = error != null, supportingText = { error?.let { Text(it) } }, minLines = 2, maxLines = 5)
                } },
                confirmButton = { TextButton({
                    try { model.editHiddenEntry(original, path); editing = null }
                    catch (failure: IllegalArgumentException) { error = failure.message }
                }) { Text("保存") } },
                dismissButton = { TextButton({ editing = null }) { Text("取消") } })
        }
    }
}

@Composable
private fun HiddenSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null)
    }
}
