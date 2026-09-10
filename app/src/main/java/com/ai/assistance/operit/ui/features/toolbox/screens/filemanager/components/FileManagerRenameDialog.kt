package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerRenameState
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerNameError

@Composable
fun FileManagerRenameDialog(state: FileManagerRenameState, onChange: (String) -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val request = state.request ?: return
    val validation = fileManagerNameError(state.newName)
    AlertDialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        title = { Text("重命名项目") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${request.location.environment ?: "手机"} · ${request.location.path}", style = MaterialTheme.typography.bodySmall)
                Text("原名称：${request.name}")
                OutlinedTextField(value = state.newName, onValueChange = onChange,
                    label = { Text("新名称（保留需要的扩展名）") }, singleLine = true,
                    enabled = !state.running && !state.unknown,
                    isError = state.error != null || validation != null,
                    supportingText = { Text(state.error ?: validation ?: "已有同名项目不会被替换") },
                    modifier = Modifier.fillMaxWidth())
                if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !state.running && !state.unknown && validation == null && state.newName != request.name) {
                Text(if (state.running) "正在重命名…" else "重命名")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.running) { Text(if (state.unknown) "关闭并检查目录" else "取消") }
        },
    )
}
