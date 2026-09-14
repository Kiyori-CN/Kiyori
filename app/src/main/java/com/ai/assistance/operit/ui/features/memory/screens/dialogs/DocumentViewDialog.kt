package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.DocumentChunk
import com.kiyori.design.theme.KiyoriUiShapes

@Composable
fun DocumentViewDialog(
    memoryTitle: String, onTitleChange: (String) -> Unit,
    chunks: List<DocumentChunk>, chunkStates: Map<Long, String>, onChunkChange: (Long, String) -> Unit,
    searchQuery: String, onSearchQueryChange: (String) -> Unit, onPerformSearch: () -> Unit,
    onDismiss: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit,
    onArchive: () -> Unit, archived: Boolean, isDirty: Boolean,
    isSaving: Boolean = false, error: String? = null, folderPath: String = ""
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val dismiss = { if (!isSaving) { if (isDirty) confirmDiscard = true else onDismiss() } }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 680.dp).fillMaxWidth().fillMaxHeight(0.92f).imePadding(), shape = KiyoriUiShapes.dialog) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.library_knowledge), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !isSaving) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.library_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            // 归档会关闭详情，因此必须先保存或放弃当前草稿。
                            DropdownMenuItem(text = { Text(stringResource(if (archived) R.string.library_restore else R.string.library_archive)) }, enabled = !isDirty,
                                onClick = { menu = false; onArchive() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.memory_delete_document), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                    IconButton(onClick = dismiss, enabled = !isSaving) { Icon(Icons.Outlined.Close, stringResource(R.string.memory_close)) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        OutlinedTextField(value = memoryTitle, onValueChange = onTitleChange, label = { Text(stringResource(R.string.memory_document_title)) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !isSaving, shape = KiyoriUiShapes.field)
                    }
                    if (folderPath.isNotBlank()) item { Text(folderPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    item {
                        OutlinedTextField(value = searchQuery, onValueChange = onSearchQueryChange, placeholder = { Text(stringResource(R.string.library_saved_document_search), style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !isSaving, shape = KiyoriUiShapes.field,
                            trailingIcon = { IconButton(onClick = { keyboard?.hide(); onPerformSearch() }, enabled = !isSaving) { Icon(Icons.Outlined.Search, stringResource(R.string.library_search_action)) } },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onPerformSearch() }))
                    }
                    if (chunks.isEmpty()) item { Text(stringResource(R.string.document_no_content_found), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(chunks, key = { it.id }) { chunk ->
                        OutlinedTextField(value = chunkStates[chunk.id] ?: chunk.content, onValueChange = { onChunkChange(chunk.id, it) },
                            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.document_block_label, chunk.chunkIndex + 1)) },
                            enabled = !isSaving, shape = KiyoriUiShapes.field)
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = dismiss, enabled = !isSaving) { Text(stringResource(R.string.memory_close)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onSave, enabled = !isSaving && memoryTitle.isNotBlank() && isDirty) {
                        Text(stringResource(if (isSaving) R.string.library_saving else R.string.memory_save_all))
                    }
                }
            }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text(stringResource(R.string.library_discard_title)) },
        text = { Text(stringResource(R.string.library_discard_message)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.library_keep_editing)) } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.memory_delete_document)) },
        text = { Text(stringResource(R.string.library_delete_confirm)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !isSaving) { Text(stringResource(R.string.memory_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.memory_cancel)) } })
}
