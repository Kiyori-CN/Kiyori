package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add

import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.memory.screens.memoryCategoryLabel
import com.ai.assistance.operit.data.model.MemoryLibraryPolicy
import com.ai.assistance.operit.data.model.Memory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMemoryDialog(
    memory: Memory?,
    allFolderPaths: List<String>,
    isSaving: Boolean = false,
    error: String? = null,
    initialFolderPath: String = "",
    onDismiss: () -> Unit,
    onSave: (
        memory: Memory?,
        title: String,
        content: String,
        contentType: String,
        source: String,
        credibility: Float,
        importance: Float,
        folderPath: String,
        tags: List<String>,
        category: String
    ) -> Unit
) {
    val currentLocale = LocalConfiguration.current.locales[0]
    val defaultFolder = initialFolderPath
    val scrollState = rememberScrollState()
    var category by rememberSaveable(memory?.id) { mutableStateOf(memory?.let(MemoryLibraryPolicy::category) ?: "other") }
    var title by rememberSaveable(memory?.id) { mutableStateOf(memory?.title ?: "") }
    var content by rememberSaveable(memory?.id) { mutableStateOf(memory?.content ?: "") }
    var contentType by rememberSaveable(memory?.id) { mutableStateOf(memory?.contentType ?: "text/plain") }
    var source by rememberSaveable(memory?.id) { mutableStateOf(memory?.source ?: "user_input") }
    var credibility by rememberSaveable(memory?.id) { mutableStateOf(memory?.credibility ?: 0.8f) }
    var importance by rememberSaveable(memory?.id) { mutableStateOf(memory?.importance ?: 0.5f) }
    var folderPath by rememberSaveable(memory?.id) { mutableStateOf(memory?.folderPath ?: defaultFolder) }
    var tags by rememberSaveable(memory?.id) { mutableStateOf(memory?.tags?.map { it.name } ?: emptyList<String>()) }
    
    var showDiscard by remember { mutableStateOf(false) }
    val dirty = title != (memory?.title ?: "") || content != (memory?.content ?: "") ||
        contentType != (memory?.contentType ?: "text/plain") || source != (memory?.source ?: "user_input") ||
        category != (memory?.let(MemoryLibraryPolicy::category) ?: "other") ||
        folderPath != (memory?.folderPath ?: defaultFolder) || credibility != (memory?.credibility ?: 0.8f) ||
        importance != (memory?.importance ?: 0.5f) || tags != (memory?.tags?.map { it.name } ?: emptyList<String>())
    val requestDismiss = { if (!isSaving) { if (dirty) showDiscard = true else onDismiss() } }
    if (showDiscard) {
        AlertDialog(
        onDismissRequest = { showDiscard = false },
        title = { Text(stringResource(R.string.library_discard_title)) },
        text = { Text(stringResource(R.string.library_discard_message)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_discard)) } },
        dismissButton = { TextButton(onClick = { showDiscard = false }) { Text(stringResource(R.string.library_keep_editing)) } }
        )
        return
    }

    Dialog(
        onDismissRequest = requestDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .fillMaxHeight(0.9f)
                    .imePadding(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = if (memory == null) {
                            stringResource(R.string.memory_create_new)
                        } else {
                            stringResource(R.string.memory_edit_memory)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
                    )
                    HorizontalDivider()

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(24.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(stringResource(R.string.memory_title)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text(stringResource(R.string.memory_content)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 200.dp),
                            enabled = memory?.isDocumentNode != true
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        var categoryExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { categoryExpanded = true }) { Text(memoryCategoryLabel(category)) }
                            DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                                MemoryLibraryPolicy.categories.forEach { value ->
                                    DropdownMenuItem(text = { Text(memoryCategoryLabel(value)) }, onClick = { category = value; categoryExpanded = false })
                                }
                            }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        FolderSelector(
                            allFolderPaths = allFolderPaths,
                            selectedPath = folderPath,
                            onPathSelected = { folderPath = it }
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        TagsEditor(tags = tags, onTagsChanged = { tags = it })
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = source,
                            onValueChange = { source = it },
                            label = { Text(stringResource(R.string.memory_source)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("${stringResource(R.string.memory_credibility)}: ${String.format(currentLocale, "%.2f", credibility)}")
                        Slider(
                            value = credibility,
                            onValueChange = { credibility = it },
                            valueRange = 0f..1f
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("${stringResource(R.string.memory_importance)}: ${String.format(currentLocale, "%.2f", importance)}")
                        Slider(
                            value = importance,
                            onValueChange = { importance = it },
                            valueRange = 0f..1f
                        )
                    }

                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = requestDismiss, enabled = !isSaving) {
                            Text(stringResource(R.string.memory_cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            enabled = !isSaving && title.isNotBlank() && content.isNotBlank(),
                            onClick = {
                                onSave(
                                    memory,
                                    title,
                                    content,
                                    contentType,
                                    source,
                                    credibility,
                                    importance,
                                    folderPath,
                                    tags.toList(),
                                    category
                                )
                            }
                        ) {
                            Text(stringResource(if (isSaving) R.string.library_saving else R.string.memory_save))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSelector(
    allFolderPaths: List<String>,
    isSaving: Boolean = false,
    error: String? = null,
    selectedPath: String,
    onPathSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedPath,
            onValueChange = onPathSelected,
            readOnly = false,
            label = { Text(stringResource(R.string.memory_folder_label2)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            allFolderPaths.forEach { path ->
                DropdownMenuItem(
                    text = { Text(path) },
                    onClick = {
                        onPathSelected(path)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(
    tags: List<String>,
    onTagsChanged: (List<String>) -> Unit
) {
    var newTagText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column {
        Text(stringResource(R.string.memory_tags), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { /* Not used */ },
                    label = { Text(tag) },
                    trailingIcon = {
                        IconButton(
                            onClick = { onTagsChanged(tags - tag) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = "Remove tag")
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newTagText,
            onValueChange = { newTagText = it },
            placeholder = { Text(stringResource(R.string.memory_add_tag_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (newTagText.isNotBlank() && newTagText !in tags) {
                    onTagsChanged(tags + newTagText)
                    newTagText = ""
                }
                keyboardController?.hide()
            }),
            trailingIcon = {
                IconButton(onClick = {
                    if (newTagText.isNotBlank() && newTagText !in tags) {
                        onTagsChanged(tags + newTagText)
                        newTagText = ""
                    }
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add tag")
                }
            }
        )
    }
}
