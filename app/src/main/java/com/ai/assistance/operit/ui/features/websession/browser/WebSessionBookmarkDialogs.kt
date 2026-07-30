package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmarkDraft
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.ai.assistance.operit.ui.theme.KiyoriSemanticTone

internal data class WebSessionBookmarkFolderOption(
    val id: Long,
    val title: String,
    val depth: Int,
)

@Composable
internal fun WebSessionBookmarkEditorDialog(
    title: String,
    initialDraft: WebSessionBookmarkDraft,
    folderOptions: List<WebSessionBookmarkFolderOption>,
    onDismiss: () -> Unit,
    onConfirm: (WebSessionBookmarkDraft) -> Unit,
) {
    var bookmarkTitle by remember(initialDraft) { mutableStateOf(initialDraft.title) }
    var bookmarkUrl by remember(initialDraft) { mutableStateOf(initialDraft.url) }
    var bookmarkIconUrl by remember(initialDraft) { mutableStateOf(initialDraft.iconUrl) }
    var selectedFolderId by remember(initialDraft) { mutableStateOf(initialDraft.folderId) }
    var folderText by remember(initialDraft, folderOptions) {
        mutableStateOf(folderOptions.firstOrNull { it.id == initialDraft.folderId }?.title ?: "/")
    }
    var folderTextEdited by remember(initialDraft) { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }

    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 300.dp, max = 380.dp).heightIn(max = 620.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrowserBookmarkDialogTitle(
                    title = title,
                    icon = Icons.Filled.Bookmark,
                    tone = KiyoriSemanticTone.BLUE,
                )
                OutlinedTextField(
                    value = bookmarkTitle,
                    onValueChange = { bookmarkTitle = it },
                    label = { Text("网站名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                OutlinedTextField(
                    value = bookmarkUrl,
                    onValueChange = { bookmarkUrl = it },
                    label = { Text("网址") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                OutlinedTextField(
                    value = bookmarkIconUrl,
                    onValueChange = { bookmarkIconUrl = it },
                    label = { Text("网址图标") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                OutlinedTextField(
                    value = folderText,
                    onValueChange = {
                        folderText = it
                        selectedFolderId = null
                        folderTextEdited = true
                    },
                    label = { Text("书签文件夹") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showFolderPicker = true }) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择书签文件夹")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        onClick = {
                            val normalizedFolder = folderText.trim()
                            val newFolderTitle =
                                if (
                                    folderTextEdited &&
                                        normalizedFolder.isNotBlank() &&
                                        normalizedFolder != "/"
                                ) {
                                    normalizedFolder
                                } else {
                                    ""
                                }
                            onConfirm(
                                WebSessionBookmarkDraft(
                                    title = bookmarkTitle.trim(),
                                    url = bookmarkUrl.trim(),
                                    iconUrl = bookmarkIconUrl.trim(),
                                    folderId = if (newFolderTitle.isBlank()) selectedFolderId else null,
                                    newFolderTitle = newFolderTitle,
                                ),
                            )
                        },
                    ) {
                        Text("确认")
                    }
                }
            }
        }
    }

    if (showFolderPicker) {
        WebSessionBookmarkFolderPickerDialog(
            options = folderOptions,
            onDismiss = { showFolderPicker = false },
            onSelect = { option ->
                selectedFolderId = option?.id
                folderText = option?.title ?: "/"
                folderTextEdited = false
                showFolderPicker = false
            },
        )
    }
}

@Composable
private fun WebSessionBookmarkFolderPickerDialog(
    options: List<WebSessionBookmarkFolderOption>,
    onDismiss: () -> Unit,
    onSelect: (WebSessionBookmarkFolderOption?) -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 300.dp, max = 380.dp).heightIn(max = 520.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                BrowserBookmarkDialogTitle(
                    title = "选择书签文件夹",
                    icon = Icons.Filled.Folder,
                    tone = KiyoriSemanticTone.PURPLE,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                    item(key = "root") {
                        WebSessionBookmarkPickerRow(title = "/", depth = 0) { onSelect(null) }
                    }
                    items(options, key = { option -> option.id }) { option ->
                        WebSessionBookmarkPickerRow(option.title, option.depth) { onSelect(option) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebSessionBookmarkPickerRow(
    title: String,
    depth: Int,
    onClick: () -> Unit,
) {
    Column {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(WebSessionBrowserPopupItemHeight)
                    .clickable(onClick = onClick)
                    .padding(start = (20 + depth * 24).dp, end = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = (20 + depth * 24).dp, end = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
    }
}

@Composable
internal fun WebSessionBookmarkTextDialog(
    title: String,
    initialValue: String,
    confirmText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 300.dp, max = 380.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                BrowserBookmarkDialogTitle(
                    title = title,
                    icon = Icons.Filled.Bookmark,
                    tone = KiyoriSemanticTone.PURPLE,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (title == "书签导入") 5 else 1,
                    maxLines = if (title == "书签导入") 10 else 1,
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = { onConfirm(value.trim()) }) { Text(confirmText) }
                }
            }
        }
    }
}

@Composable
internal fun WebSessionBookmarkOptionsDialog(
    title: String,
    options: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 300.dp, max = 360.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                BrowserBookmarkDialogTitle(
                    title = title,
                    icon = Icons.Filled.Tune,
                    tone = KiyoriSemanticTone.ORANGE,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                options.forEach { option ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(WebSessionBrowserPopupItemHeight)
                                .clickable { onSelect(option) }
                                .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
internal fun WebSessionBookmarkConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 300.dp, max = 380.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                BrowserBookmarkDialogTitle(
                    title = title,
                    icon = Icons.Filled.Warning,
                    tone = KiyoriSemanticTone.RED,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = onConfirm) { Text("确定") }
                }
            }
        }
    }
}

@Composable
private fun BrowserBookmarkDialogTitle(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: KiyoriSemanticTone,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KiyoriSemanticIconBadge(
            imageVector = icon,
            tone = tone,
            contentDescription = null,
            containerSize = 34.dp,
            iconSize = 18.dp,
            shape = RoundedCornerShape(10.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
