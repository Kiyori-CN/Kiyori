package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsWorkspacePage
import com.kiyori.platform.storage.KiyoriArtifactStoragePolicy
import kotlinx.coroutines.launch

@Composable
fun ArtifactStorageSettingsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val initial = remember(context) { KiyoriArtifactStoragePolicy.roots(context) }
    var androidRoot by remember { mutableStateOf(initial.android) }
    var linuxRoot by remember { mutableStateOf(initial.linux) }
    KiyoriSettingsWorkspacePage(
        title = "AI 产物保存位置",
        onBack = onBackPressed,
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("未明确指定路径时，AI 生成、导出、下载和脚手架文件会使用这里的默认位置。")
            OutlinedTextField(
                value = androidRoot,
                onValueChange = { androidRoot = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Android 根目录（相对 Download）") },
                supportingText = { Text("例如：Kiyori/workspace。不要填写 / 开头的绝对路径或 ..") },
                singleLine = true,
            )
            OutlinedTextField(
                value = linuxRoot,
                onValueChange = { linuxRoot = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ubuntu 根目录（绝对路径）") },
                supportingText = { Text("例如：/workspace。不要使用 ..") },
                singleLine = true,
            )
            Button(
                onClick = {
                    runCatching { KiyoriArtifactStoragePolicy.setRoots(context, androidRoot, linuxRoot) }
                        .onSuccess { scope.launch { snackbar.showSnackbar("已保存产物保存位置") } }
                        .onFailure { scope.launch { snackbar.showSnackbar(it.message ?: "路径无效") } }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
            Button(
                onClick = {
                    KiyoriArtifactStoragePolicy.reset(context)
                    val defaults = KiyoriArtifactStoragePolicy.roots(context)
                    androidRoot = defaults.android
                    linuxRoot = defaults.linux
                    scope.launch { snackbar.showSnackbar("已恢复默认位置") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("恢复默认") }
        }
    }
}
