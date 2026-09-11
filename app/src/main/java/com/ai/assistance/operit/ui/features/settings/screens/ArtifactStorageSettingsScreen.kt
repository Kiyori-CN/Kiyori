package com.ai.assistance.operit.ui.features.settings.screens

import android.os.Environment
import android.provider.DocumentsContract
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.main.components.LocalSetScreenSoftInputMode
import com.ai.assistance.operit.ui.main.components.LocalSetUseScreenImePadding
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsGroupSection
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsWorkspacePage
import com.kiyori.platform.storage.ArtifactPathRules
import com.kiyori.platform.storage.KiyoriArtifactStoragePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION")
@Composable
fun ArtifactStorageSettingsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val observableResources = LocalResources.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val isCurrentScreen = LocalIsCurrentScreen.current
    val setSoftInputMode = LocalSetScreenSoftInputMode.current
    val setUseImePadding = LocalSetUseScreenImePadding.current
    val defaults = remember { KiyoriArtifactStoragePolicy.normalizeRoots(null, null) }
    // 分别读取以保留可修复的非法旧值；不存在的键仍显示真正默认值。
    val initial = remember(context) {
        val raw = KiyoriArtifactStoragePolicy.savedInputs(context)
        KiyoriArtifactStoragePolicy.Roots(
            runCatching { KiyoriArtifactStoragePolicy.root(context, "android") }.getOrElse { raw.android },
            runCatching { KiyoriArtifactStoragePolicy.root(context, "linux") }.getOrElse { raw.linux },
        )
    }
    var savedAndroid by rememberSaveable { mutableStateOf(initial.android) }
    var savedLinux by rememberSaveable { mutableStateOf(initial.linux) }
    var androidRoot by rememberSaveable { mutableStateOf(initial.android) }
    var linuxRoot by rememberSaveable { mutableStateOf(initial.linux) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }
    var probeResult by remember { mutableStateOf<String?>(null) }
    val externalRoot = remember { Environment.getExternalStorageDirectory().absolutePath }
    val androidValidation = remember(androidRoot) { runCatching { ArtifactPathRules.androidRoot(androidRoot, externalRoot) } }
    val linuxValidation = remember(linuxRoot) { runCatching { ArtifactPathRules.linuxRoot(linuxRoot) } }
    val dirty = androidRoot != savedAndroid || linuxRoot != savedLinux
    val valid = androidValidation.isSuccess && linuxValidation.isSuccess

    SideEffect {
        if (isCurrentScreen) {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setUseImePadding(true)
        }
    }
    fun back() {
        if (busy) return
        if (dirty) showLeaveDialog = true else onBackPressed()
    }
    BackHandler(enabled = isCurrentScreen, onBack = ::back)
    fun save(leave: Boolean = false) {
        if (busy || !valid) return
        focus.clearFocus()
        busy = true
        error = null
        scope.launch {
            try {
                val saved = KiyoriArtifactStoragePolicy.Roots(androidValidation.getOrThrow(), linuxValidation.getOrThrow())
                // commit 已开始后应完成持久化，不能因导航中途留下半次操作。
                withContext(NonCancellable + Dispatchers.IO) { KiyoriArtifactStoragePolicy.setRoots(context, saved.android, saved.linux) }
                savedAndroid = saved.android
                savedLinux = saved.linux
                androidRoot = saved.android
                linuxRoot = saved.linux
                busy = false
                showLeaveDialog = false
                if (leave) onBackPressed() else scope.launch { snackbar.showSnackbar(observableResources.getString(R.string.artifact_storage_saved)) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                AppLogger.e("ArtifactStorageSettings", "Unable to save artifact directories", failure)
                error = observableResources.getString(R.string.artifact_storage_invalid)
            } finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                androidRoot = ArtifactPathRules.primaryTreeRoot(uri.authority, DocumentsContract.getTreeDocumentId(uri), externalRoot)
                probeResult = null
                error = null
            } catch (failure: Exception) { error = observableResources.getString(R.string.artifact_storage_picker_error) }
        }
    }
    KiyoriSettingsWorkspacePage(
        title = stringResource(R.string.kiyori_ai_settings_artifact_storage), onBack = ::back, snackbarHostState = snackbar,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.artifact_storage_help), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
                KiyoriSettingsGroupSection(stringResource(R.string.artifact_storage_android_label), stringResource(R.string.artifact_storage_android_help)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArtifactPathField(androidRoot, { androidRoot = it; error = null; probeResult = null }, !busy, androidValidation.isFailure, R.string.artifact_storage_android_invalid)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(enabled = !busy, onClick = {
                                focus.clearFocus()
                                try { picker.launch(null) } catch (failure: Exception) { error = observableResources.getString(R.string.artifact_storage_picker_error) }
                            }) {
                                Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.artifact_storage_choose))
                            }
                            TextButton(enabled = !busy && androidValidation.isSuccess, onClick = {
                                busy = true
                                error = null
                                val path = androidValidation.getOrThrow()
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { KiyoriArtifactStoragePolicy.checkAndroidWrite(path) }
                                        probeResult = observableResources.getString(R.string.artifact_storage_writable)
                                    } catch (cancelled: CancellationException) { throw cancelled
                                    } catch (failure: Exception) {
                                        AppLogger.e("ArtifactStorageSettings", "Directory write check failed", failure)
                                        probeResult = observableResources.getString(R.string.artifact_storage_not_writable)
                                    } finally { busy = false }
                                }
                            }) { Text(stringResource(R.string.artifact_storage_check)) }
                        }
                        Text(probeResult ?: stringResource(R.string.artifact_storage_check_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidValidation.getOrNull()?.let { ArtifactPathPreview("$it/office/…/report.pdf\n$it/bilibili/…\n$it/browser/…") }
                    }
                }
                KiyoriSettingsGroupSection(stringResource(R.string.artifact_storage_linux_label), stringResource(R.string.artifact_storage_linux_help)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArtifactPathField(linuxRoot, { linuxRoot = it; error = null }, !busy, linuxValidation.isFailure, R.string.artifact_storage_linux_invalid)
                        linuxValidation.getOrNull()?.let { ArtifactPathPreview("$it/code-runner/…\n$it/office/…/report.pdf") }
                    }
                }
                Text(stringResource(R.string.artifact_storage_boundary), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error) }
            }
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(stringResource(if (dirty) R.string.artifact_storage_unsaved else R.string.artifact_storage_current), style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !busy && (androidRoot != defaults.android || linuxRoot != defaults.linux), onClick = {
                            androidRoot = defaults.android; linuxRoot = defaults.linux; error = null; probeResult = null
                        }) { Text(stringResource(R.string.artifact_storage_reset)) }
                        Button(onClick = { save() }, enabled = !busy && dirty && valid, modifier = Modifier.weight(1f)) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(stringResource(R.string.artifact_storage_save))
                        }
                    }
                }
            }
        }
    }
    if (showLeaveDialog && isCurrentScreen) {
        AlertDialog(
            onDismissRequest = { if (!busy) showLeaveDialog = false }, title = { Text(stringResource(R.string.save_changes_question)) },
            text = { Text(stringResource(R.string.artifact_storage_leave_help)) },
            confirmButton = { TextButton(enabled = !busy && valid, onClick = { save(leave = true) }) { Text(stringResource(R.string.artifact_storage_save)) } },
            dismissButton = {
                Row {
                    TextButton(enabled = !busy, onClick = { showLeaveDialog = false; onBackPressed() }) { Text(stringResource(R.string.artifact_storage_discard)) }
                    TextButton(enabled = !busy, onClick = { showLeaveDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun ArtifactPathField(value: String, onChange: (String) -> Unit, enabled: Boolean, invalid: Boolean, errorRes: Int) {
    val clipboard = LocalClipboardManager.current
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true, isError = invalid,
        label = { Text(stringResource(R.string.artifact_storage_path)) },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
        supportingText = if (invalid) ({ Text(stringResource(errorRes)) }) else null,
        trailingIcon = {
            IconButton(enabled = enabled && value.isNotBlank(), onClick = { clipboard.setText(AnnotatedString(value)) }) {
                Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy_path))
            }
        },
    )
}

@Composable
private fun ArtifactPathPreview(paths: String) {
    Text(stringResource(R.string.artifact_storage_preview), style = MaterialTheme.typography.labelMedium)
    SelectionContainer { Text(paths, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
