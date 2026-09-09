package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.kiyori.design.theme.KiyoriUiShapes

/**
 * 应用日志导出屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val viewModel: LogcatViewModel = viewModel(factory = LogcatViewModel.Factory(context.applicationContext))
    val isCurrentScreen = LocalIsCurrentScreen.current
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    val isSaving by viewModel.isSaving.collectAsState()
    val isClearing by viewModel.isClearing.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()

    CustomScaffold(
        snackbarHost = {
            if (isCurrentScreen && !showClearConfirm) saveResult?.let {
                Snackbar(modifier = Modifier.padding(16.dp), action = {
                    TextButton(onClick = viewModel::dismissResult) { Text(stringResource(R.string.close)) }
                }) {
                    SelectionContainer { Text(it) }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                shape = KiyoriUiShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.logcat_management),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.logcat_description),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.saveLogsToFile() },
                        enabled = !isSaving && !isClearing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.logcat_save_to_file))
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.dismissResult(); showClearConfirm = true },
                        enabled = !isSaving && !isClearing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.logcat_clear_current))
                    }
                }
            }
        }
    }
    if (isCurrentScreen && showClearConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isClearing) showClearConfirm = false },
            title = { Text(stringResource(R.string.logcat_clear_current)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.logcat_clear_current_confirm))
                    saveResult?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (isClearing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearLogs { showClearConfirm = false } }, enabled = !isClearing && !isSaving) {
                    Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }, enabled = !isClearing) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

}
