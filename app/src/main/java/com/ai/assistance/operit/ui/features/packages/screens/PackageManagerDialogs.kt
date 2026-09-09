package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.PackageManager

@Composable
fun PackageLoadErrorsDialog(
    errorInfos: List<PackageManager.PackageLoadErrorInfo>,
    onDeleteSource: suspend (String) -> Boolean,
    onSourceDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    val deletedSources = remember { mutableStateListOf<String>() }
    val dismiss: () -> Unit = { if (!deleting) onDismiss() }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(text = stringResource(R.string.error_occurred_simple)) },
        text = {
            SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
            ) {
                errorInfos.forEach { errorInfo ->
                    Text(
                        text = errorInfo.packageName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    errorInfo.sourcePath?.let { sourcePath ->
                        Text(
                            text = sourcePath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = errorInfo.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (errorInfo.isExternalSource && errorInfo.sourcePath != null && errorInfo.sourcePath !in deletedSources) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            enabled = !deleting,
                            onClick = { deleteFailed = false; deleteTarget = errorInfo.sourcePath }
                        ) {
                            Text(text = stringResource(R.string.package_conflict_delete_source))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            }
        },
        confirmButton = {
            TextButton(onClick = dismiss, enabled = !deleting) {
                Text(text = stringResource(R.string.ok))
            }
        }
    )
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteTarget = null },
            title = { Text(stringResource(R.string.pkg_confirm_delete)) },
            text = {
                SelectionContainer {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.pkg_source_delete_warning, target))
                        if (deleteFailed) Text(stringResource(R.string.pkg_details_delete_failed), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        if (!deleting) {
                            deleting = true
                            deleteFailed = false
                            scope.launch {
                                try {
                                    if (onDeleteSource(target)) {
                                        deletedSources.add(target)
                                        deleteTarget = null
                                        onSourceDeleted()
                                    } else deleteFailed = true
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    deleteFailed = true
                                } finally {
                                    deleting = false
                                }
                            }
                        }
                    },
                ) { Text(stringResource(if (deleting) R.string.pkg_details_deleting else R.string.pkg_delete)) }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { deleteTarget = null }) { Text(stringResource(R.string.pkg_cancel)) }
            },
        )
    }

}
