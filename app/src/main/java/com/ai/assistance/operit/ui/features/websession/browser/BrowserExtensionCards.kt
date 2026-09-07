package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.websession.extension.InstalledBrowserExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun BrowserExtensionCards(
    items: List<InstalledBrowserExtension>,
    coordinator: BrowserPresentationCoordinator,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedSourcePath by remember { mutableStateOf<String?>(null) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val diagnostics by coordinator.browserExtensionDiagnostics.collectAsState()
    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: failure.javaClass.simpleName }
            finally { busy = false }
        }
    }
    items.forEach { item ->
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().clickable {
                selectedId = item.id
                selectedSourcePath = item.bundle.files.keys.sorted().firstOrNull()
            }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(item.bundle.manifest.name, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.browser_extension_custom_version, item.bundle.manifest.version),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.bundle.manifest.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                Switch(checked = item.enabled, enabled = !busy, onCheckedChange = { enabled ->
                    perform { coordinator.setBrowserExtensionEnabled(item.id, item.revision, enabled) }
                })
            }
        }
    }
    val selected = items.firstOrNull { it.id == selectedId }
    if (selected != null && deleteId == null && error == null) {
        val sourceEntry = selected.bundle.files[selectedSourcePath]?.let { selectedSourcePath.orEmpty() to it }
            ?: selected.bundle.files.toSortedMap().entries.firstOrNull()?.let { it.key to it.value }
        AlertDialog(
            onDismissRequest = { selectedId = null; sourceMenuExpanded = false },
            title = { Text(selected.bundle.manifest.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selected.id)
                    Text(stringResource(R.string.browser_extension_reload_note))
                    Text(selected.bundle.manifest.content_scripts.flatMap { it.matches }.distinct().joinToString("\n"))
                    diagnostics.filter { it.extensionId == selected.id && it.revision == selected.revision }.takeLast(10).forEach {
                        Text("${it.state} · ${it.sessionId}\n${it.detail}", style = MaterialTheme.typography.bodySmall)
                    }
                    selected.bundle.manifest.action?.let { action ->
                        TextButton(enabled = selected.enabled && !busy, onClick = {
                            perform { coordinator.invokeBrowserExtensionAction(selected.id) }
                        }) { Text(action.default_title) }
                    }
                    TextButton(enabled = !busy, onClick = { deleteId = selected.id }) {
                        Text(stringResource(R.string.browser_extension_delete))
                    }
                    sourceEntry?.let { (path, source) ->
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { sourceMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(path) }
                            DropdownMenu(
                                expanded = sourceMenuExpanded,
                                onDismissRequest = { sourceMenuExpanded = false },
                            ) {
                                selected.bundle.files.keys.sorted().forEach { sourcePath ->
                                    DropdownMenuItem(
                                        text = { Text(sourcePath) },
                                        onClick = {
                                            selectedSourcePath = sourcePath
                                            sourceMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        SelectionContainer {
                            Column {
                                Text(source.take(16000), style = MaterialTheme.typography.bodySmall)
                                if (source.length > 16000) Text(stringResource(R.string.browser_extension_source_truncated))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedId = null; sourceMenuExpanded = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
    val deleting = items.firstOrNull { it.id == deleteId }
    if (deleting != null && error == null) AlertDialog(
        onDismissRequest = { deleteId = null },
        title = { Text(stringResource(R.string.browser_extension_delete)) },
        text = { Text(stringResource(R.string.browser_extension_delete_confirm, deleting.bundle.manifest.name)) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            perform { coordinator.deleteBrowserExtension(deleting.id, deleting.revision); deleteId = null; selectedId = null }
        }) { Text(stringResource(R.string.browser_extension_delete)) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(android.R.string.cancel)) } },
    )
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(android.R.string.ok)) } }) }
}
