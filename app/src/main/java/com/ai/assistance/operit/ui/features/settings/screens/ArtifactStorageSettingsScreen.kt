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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsWorkspacePage
import com.kiyori.platform.storage.KiyoriArtifactStoragePolicy
import com.ai.assistance.operit.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun ArtifactStorageSettingsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val initial = remember(context) {
        runCatching { KiyoriArtifactStoragePolicy.roots(context) }
            .getOrElse { KiyoriArtifactStoragePolicy.savedInputs(context) }
    }
    var androidRoot by rememberSaveable { mutableStateOf(initial.android) }
    var linuxRoot by rememberSaveable { mutableStateOf(initial.linux) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    fun save(android: String, linux: String) {
        saving = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { KiyoriArtifactStoragePolicy.setRoots(context, android, linux) }
                val saved = KiyoriArtifactStoragePolicy.roots(context)
                androidRoot = saved.android
                linuxRoot = saved.linux
                snackbar.showSnackbar(context.getString(R.string.artifact_storage_saved))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = context.getString(R.string.artifact_storage_invalid) + "\n" + failure.message.orEmpty()
            } finally {
                saving = false
            }
        }
    }
    KiyoriSettingsWorkspacePage(
        title = stringResource(R.string.kiyori_ai_settings_artifact_storage),
        onBack = onBackPressed,
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.artifact_storage_help))
            OutlinedTextField(
                value = androidRoot,
                onValueChange = { androidRoot = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.artifact_storage_android_label)) },
                supportingText = { Text(stringResource(R.string.artifact_storage_android_help)) },
                enabled = !saving,
                singleLine = true,
            )
            OutlinedTextField(
                value = linuxRoot,
                onValueChange = { linuxRoot = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.artifact_storage_linux_label)) },
                supportingText = { Text(stringResource(R.string.artifact_storage_linux_help)) },
                enabled = !saving,
                singleLine = true,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(stringResource(R.string.artifact_storage_boundary), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { save(androidRoot, linuxRoot) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.artifact_storage_save)) }
            Button(
                onClick = {
                    val defaults = KiyoriArtifactStoragePolicy.normalizeRoots(null, null)
                    save(defaults.android, defaults.linux)
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.artifact_storage_reset)) }
        }
    }
}
