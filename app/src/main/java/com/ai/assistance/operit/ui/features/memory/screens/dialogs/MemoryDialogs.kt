package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.data.model.CloudEmbeddingConfig
import com.ai.assistance.operit.data.model.MemoryLibraryPolicy
import com.ai.assistance.operit.ui.features.memory.screens.memoryCategoryLabel
import com.ai.assistance.operit.ui.features.memory.screens.memoryKindLabel
import com.ai.assistance.operit.ui.features.memory.screens.memorySourceLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.Memory
import com.kiyori.capability.ai.memory.MemoryGraphEdge
import androidx.compose.foundation.text.selection.SelectionContainer
import java.text.SimpleDateFormat
import com.kiyori.design.theme.KiyoriUiShapes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryInfoDialog(
    memory: Memory, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    onArchive: () -> Unit, isSaving: Boolean, error: String?, cloudConfig: CloudEmbeddingConfig
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var metadata by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("yyyy-MM-dd HH:mm", locale) }
    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 600.dp).fillMaxWidth().fillMaxHeight(0.9f), shape = KiyoriUiShapes.dialog) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(memoryKindLabel(MemoryLibraryPolicy.kind(memory)), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !isSaving) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.library_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(if (memory.archived) R.string.library_restore else R.string.library_archive)) }, onClick = { menu = false; onArchive() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.memory_delete), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                    IconButton(onClick = onDismiss, enabled = !isSaving) { Icon(Icons.Outlined.Close, stringResource(R.string.memory_close)) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SelectionContainer { Text(memory.title, style = MaterialTheme.typography.headlineSmall) }
                    Text(listOf(memoryCategoryLabel(MemoryLibraryPolicy.category(memory)), memorySourceLabel(memory.source), dateFormat.format(memory.updatedAt)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (memory.archived) Text(stringResource(R.string.library_archive_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    SelectionContainer { Text(memory.content, style = MaterialTheme.typography.bodyLarge) }
                    if (memory.tags.isNotEmpty()) Text(memory.tags.joinToString("  ") { "#${it.name}" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    TextButton(onClick = { metadata = !metadata }, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.library_details)); Spacer(Modifier.width(8.dp))
                        Icon(if (metadata) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    }
                    if (metadata) {
                        val indexed = remember(memory, cloudConfig) { MemoryLibraryPolicy.compatible(memory.embedding, memory.embeddingModelKey, memory.embeddingContentHash, cloudConfig, "${memory.title}\n${memory.content}") }
                        Text(stringResource(if (!cloudConfig.enabled) R.string.library_index_local else if (indexed) R.string.library_index_ready else R.string.library_index_pending), style = MaterialTheme.typography.bodySmall)
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${stringResource(R.string.memory_folder)}: ${memory.folderPath.orEmpty().ifBlank { stringResource(R.string.library_folder_root) }}", style = MaterialTheme.typography.bodySmall)
                                Text("${stringResource(R.string.memory_source)}: ${memory.source}", style = MaterialTheme.typography.bodySmall)
                                Text("${stringResource(R.string.memory_uuid)}: ${memory.uuid}", style = MaterialTheme.typography.bodySmall)
                                Text("${stringResource(R.string.memory_created_at)}: ${dateFormat.format(memory.createdAt)}", style = MaterialTheme.typography.bodySmall)
                                Text("${stringResource(R.string.memory_importance)}: ${String.format(locale, "%.2f", memory.importance)}", style = MaterialTheme.typography.bodySmall)
                                Text("${stringResource(R.string.memory_credibility)}: ${String.format(locale, "%.2f", memory.credibility)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onEdit, enabled = !isSaving) { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.memory_edit)) }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.memory_delete)) },
        text = { Text(stringResource(R.string.library_delete_confirm)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !isSaving) { Text(stringResource(R.string.memory_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.memory_cancel)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EdgeInfoDialog(
    edge: MemoryGraphEdge,
    graph: com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sourceNode = graph.nodes.find { it.id == edge.sourceId }
    val targetNode = graph.nodes.find { it.id == edge.targetId }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = KiyoriUiShapes.dialog,
        title = { Text(stringResource(R.string.memory_link_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 端点不在当前图谱里说明范围变了，不是“未分类”；文案必须说明真实原因。
                Text("${stringResource(R.string.memory_from)}: ${sourceNode?.label ?: stringResource(R.string.library_link_node_missing)}")
                Text("${stringResource(R.string.memory_to)}: ${targetNode?.label ?: stringResource(R.string.library_link_node_missing)}")
                HorizontalDivider()
                Text("${stringResource(R.string.memory_type)}: ${edge.label}")
                Text("${stringResource(R.string.memory_weight)}: ${edge.weight}")
            }
        },
        confirmButton = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.Center
            ) {
                Button(onClick = onEdit, shape = KiyoriUiShapes.control) { Text(stringResource(R.string.memory_edit)) }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = KiyoriUiShapes.control,
                ) { Text(stringResource(R.string.memory_delete)) }
                OutlinedButton(onClick = onDismiss, shape = KiyoriUiShapes.control) { Text(stringResource(R.string.memory_close)) }
            }
        }
    )
}

@Composable
fun EditEdgeDialog(
    edge: MemoryGraphEdge,
    onDismiss: () -> Unit,
    onSave: (type: String, weight: Float, description: String) -> Unit
) {
    var type by remember { mutableStateOf(edge.label ?: "related") }
    var weight by remember { mutableStateOf(edge.weight.toString()) }
    var description by remember { mutableStateOf("") } // 假设需要编辑description

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = KiyoriUiShapes.dialog,
        title = { Text(stringResource(R.string.memory_edit_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text(stringResource(R.string.memory_type)) }, shape = KiyoriUiShapes.field)
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text(stringResource(R.string.memory_weight)) }, shape = KiyoriUiShapes.field)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.memory_description)) }, shape = KiyoriUiShapes.field)
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(type, weight.toFloatOrNull() ?: 1.0f, description)
            }, shape = KiyoriUiShapes.control) { Text(stringResource(R.string.memory_save)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, shape = KiyoriUiShapes.control) { Text(stringResource(R.string.memory_cancel)) } }
    )
}

@Composable
fun LinkMemoryDialog(
    sourceNodeLabel: String,
    targetNodeLabel: String,
    onDismiss: () -> Unit,
    onLink: (type: String, weight: Float, description: String) -> Unit
) {
    var type by remember { mutableStateOf("related") }
    var weight by remember { mutableStateOf("1.0") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = KiyoriUiShapes.dialog,
        title = { Text(stringResource(R.string.memory_link_nodes, sourceNodeLabel, targetNodeLabel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text(stringResource(R.string.memory_type)) }
                    , shape = KiyoriUiShapes.field
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text(stringResource(R.string.memory_weight)) }
                    , shape = KiyoriUiShapes.field
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.memory_description)) }
                    , shape = KiyoriUiShapes.field
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weight.toFloatOrNull() ?: 1.0f
                    onLink(type, w, description)
                }
            , shape = KiyoriUiShapes.control) { Text(stringResource(R.string.memory_create_link)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, shape = KiyoriUiShapes.control) { Text(stringResource(R.string.memory_cancel)) } }
    )
}

@Composable
fun BatchDeleteConfirmDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = KiyoriUiShapes.dialog,
        title = { Text(stringResource(R.string.confirm_delete)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.memory_delete_confirmation, selectedCount),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.memory_delete_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = KiyoriUiShapes.control,
            ) {
                Text(stringResource(R.string.confirm_delete))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = KiyoriUiShapes.control) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
