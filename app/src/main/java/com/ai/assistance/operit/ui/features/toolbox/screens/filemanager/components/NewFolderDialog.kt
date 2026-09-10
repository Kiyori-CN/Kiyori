package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerNameError

/** 单一提交入口；失败保留输入，布局随字体增长。 */
@Composable
fun FileManagerNewEntryDialog(
    showDialog: Boolean,
    entryName: String,
    onEntryNameChange: (String) -> Unit,
    onCreateFile: () -> Unit,
    onCreateFolder: () -> Unit,
    onDismiss: () -> Unit,
    isCreating: Boolean,
    error: String?,
    unknown: Boolean,
) {
    if (!showDialog) return
    var folder by rememberSaveable { mutableStateOf(true) }
    val validation = fileManagerNameError(entryName)
    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("创建新项目") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = folder, onClick = { folder = true }, label = { Text("文件夹") }, enabled = !isCreating && !unknown)
                    FilterChip(selected = !folder, onClick = { folder = false }, label = { Text("空白文件") }, enabled = !isCreating && !unknown)
                }
                OutlinedTextField(
                    value = entryName,
                    onValueChange = onEntryNameChange,
                    enabled = !isCreating && !unknown,
                    label = { Text(if (folder) "文件夹名称" else "文件名称（包含扩展名）") },
                    supportingText = { Text(error ?: validation ?: if (folder) "在当前目录创建" else "按输入名称创建，不自动添加扩展名") },
                    isError = error != null || (entryName.isNotEmpty() && validation != null),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isCreating && !unknown && validation == null,
                onClick = { if (folder) onCreateFolder() else onCreateFile() },
            ) { Text(if (isCreating) "正在创建…" else "创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isCreating) { Text(if (unknown) "关闭并检查" else "取消") } },
    )
}
