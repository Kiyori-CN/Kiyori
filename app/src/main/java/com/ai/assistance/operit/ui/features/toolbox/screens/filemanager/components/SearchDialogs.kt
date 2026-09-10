package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.rounded.Search
import com.kiyori.design.theme.KiyoriSemanticTone
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.getFileIcon
import com.kiyori.design.theme.KiyoriUiShapes

import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.text.input.KeyboardType
import com.ai.assistance.operit.ui.components.KiyoriModalBottomDrawer
import com.ai.assistance.operit.core.tools.FileSearchNameMode
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.formatFileSize

/** 一个入口配置当前目录与递归搜索；高级条件始终经过同一状态所有者和后端校验。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchDialog(showDialog: Boolean, searchQuery: String, onQueryChange: (String) -> Unit,
    form: FileManagerSearchForm, onFormChange: (FileManagerSearchForm) -> Unit,
    location: String, activeFilter: String, onClearFilter: () -> Unit,
    onSearch: () -> Unit, onDismiss: () -> Unit,
) {
    if (!showDialog) return
    var advanced by remember { mutableStateOf(form.hasAdvancedFilters) }
    var submitAfterClose by remember { mutableStateOf(false) }
    val validation = remember(searchQuery, form) { runCatching { form.options(searchQuery) }.exceptionOrNull()?.message }
    KiyoriModalBottomDrawer(onDismissRequest = { onDismiss(); if (submitAfterClose) onSearch() }) { dismiss ->
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileManagerIconBadge(Icons.Rounded.Search, KiyoriSemanticTone.BLUE, 36.dp)
                Text("搜索文件", Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = dismiss) { Icon(Icons.Default.Close, "关闭搜索") }
            }
            Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = searchQuery, onValueChange = onQueryChange,
                label = { Text("文件或文件夹名称") }, placeholder = { Text(when (form.nameMode) {
                    FileSearchNameMode.CONTAINS -> "名称包含，可留空仅按高级条件查找"
                    FileSearchNameMode.GLOB -> "例如 *.pdf、照片?.jpg"
                    FileSearchNameMode.REGEX -> "例如 (?i)report.*\\.txt$"
                }) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = KiyoriUiShapes.field,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (validation == null) { submitAfterClose = true; dismiss() } }))
            SearchCheckRow("搜索子目录", form.recursive) { onFormChange(form.copy(recursive = it)) }
            Text(if (form.recursive) "搜索当前位置及其所有可访问的子目录" else "仅搜索当前位置的直接项目", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (activeFilter.isNotEmpty()) TextButton(onClick = onClearFilter) { Text("清除当前列表定位筛选：$activeFilter") }
            OutlinedButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                Text("高级功能${if (form.hasAdvancedFilters) " · 已设置" else ""}", Modifier.weight(1f).padding(horizontal = 8.dp))
                Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            if (advanced) {
                Text("按文件大小过滤", style = MaterialTheme.typography.titleSmall)
                SearchChoice(form.sizePreset.label, FileSearchSizePreset.entries.map { it.label }) { index ->
                    onFormChange(form.copy(sizePreset = FileSearchSizePreset.entries[index]))
                }
                if (form.sizePreset == FileSearchSizePreset.CUSTOM) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(form.minimumMiB, { onFormChange(form.copy(minimumMiB = it)) }, Modifier.weight(1f),
                        label = { Text("最小 MiB") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(form.maximumMiB, { onFormChange(form.copy(maximumMiB = it)) }, Modifier.weight(1f),
                        label = { Text("最大 MiB") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Text("按修改时间过滤", style = MaterialTheme.typography.titleSmall)
                val days = listOf(0, 1, 7, 30, 365, -1)
                val timeLabels = listOf("任意时间", "最近 24 小时", "最近 7 天", "最近 30 天", "最近 365 天", "自定义日期")
                SearchChoice(timeLabels[days.indexOf(form.modifiedDays).coerceAtLeast(0)], timeLabels) { onFormChange(form.copy(modifiedDays = days[it])) }
                if (form.modifiedDays < 0) {
                    OutlinedTextField(form.modifiedFrom, { onFormChange(form.copy(modifiedFrom = it)) }, Modifier.fillMaxWidth(), label = { Text("起始日期（yyyy-MM-dd）") }, singleLine = true)
                    OutlinedTextField(form.modifiedTo, { onFormChange(form.copy(modifiedTo = it)) }, Modifier.fillMaxWidth(), label = { Text("结束日期（包含当天）") }, placeholder = { Text("yyyy-MM-dd，留空不限") }, singleLine = true)
                }
                OutlinedTextField(form.content, { onFormChange(form.copy(content = it)) }, Modifier.fillMaxWidth(),
                    label = { Text("文件中包含内容") }, placeholder = { Text("留空不读取文件内容") }, maxLines = 3)
                Text("内容搜索支持 4 MiB 内的 UTF-8 文本；大小与内容条件仅匹配文件。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SearchCheckRow("区分大小写", form.caseSensitive) { onFormChange(form.copy(caseSensitive = it)) }
                SearchCheckRow("正则表达式", form.nameMode == FileSearchNameMode.REGEX) {
                    onFormChange(form.copy(nameMode = if (it) FileSearchNameMode.REGEX else FileSearchNameMode.CONTAINS))
                }
                if (form.nameMode == FileSearchNameMode.REGEX) Text("正则同时应用于名称和内容；按匹配片段查找。", style = MaterialTheme.typography.bodySmall)
                else SearchCheckRow("名称使用通配符（*、?）", form.nameMode == FileSearchNameMode.GLOB) {
                    onFormChange(form.copy(nameMode = if (it) FileSearchNameMode.GLOB else FileSearchNameMode.CONTAINS))
                }
                SearchCheckRow("包含隐藏文件和文件夹", form.includeHidden) { onFormChange(form.copy(includeHidden = it)) }
                TextButton(onClick = { onFormChange(FileManagerSearchForm(recursive = form.recursive)) }) { Text("重置高级条件") }
            }
            validation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(onClick = { submitAfterClose = true; dismiss() }, enabled = validation == null, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("开始搜索")
            }
        }
    }
}

@Composable
private fun SearchCheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null); Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SearchChoice(label: String, values: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            values.forEachIndexed { index, value -> DropdownMenuItem(text = { Text(value) }, onClick = { expanded = false; onSelect(index) }) }
        }
    }
}

/**
 * 搜索结果对话框
 */
@Composable
fun SearchResultsDialog(
    showDialog: Boolean,
    searchResults: List<FileItem>,
    onNavigateToFileDirectory: (String) -> Unit,
    onDismiss: () -> Unit,
    isSearching: Boolean,
    error: String?,
    summary: String = "",
    limitations: List<String> = emptyList(),
    onEditSearch: () -> Unit = {},
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = KiyoriUiShapes.dialog,
            title = { 
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("搜索结果 · ${searchResults.size}", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
            },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodySmall)
                limitations.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (isSearching) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("正在搜索，关闭可取消")
                    }
                } else if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                } else if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (limitations.isEmpty()) "未找到匹配项目，请尝试其他条件或位置" else "本次已检查范围内没有匹配项目，请查看上方限制并调整条件")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(searchResults, key = { it.fullPath ?: it.name }) { file ->
                            Surface(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { 
                                        file.fullPath?.let { path -> onNavigateToFileDirectory(path) }
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = KiyoriUiShapes.control
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FileManagerFileBadge(file, 36.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = file.fullPath ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis
                                        )
                                        if (file.lastModified > 0) Text(
                                            (if (file.isDirectory) "文件夹" else formatFileSize(file.size)) + " · " +
                                                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified)),
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                }
            },
            dismissButton = { if (!isSearching) TextButton(onClick = onEditSearch) { Text("修改条件") } },
            confirmButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (isSearching) "取消搜索" else "关闭")
                }
            }
        )
    }
}
