package com.ai.assistance.operit.ui.features.packages.dialogs

import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.kiyori.design.theme.KiyoriUiShapes
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.javascript.JsToolManager
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptExecutionDialog(
        packageName: String,
        tool: PackageTool,
        packageManager: PackageManager,
        initialResult: ToolResult?,
        onExecuted: (ToolResult) -> Unit,
        onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scriptText by remember(tool) { mutableStateOf(tool.script) }
    var paramValues by remember(tool) { mutableStateOf(tool.parameters.associate { it.name to "" }) }
    var executing by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    val requestClose: () -> Unit = { if (executing) showCloseConfirm = true else onDismiss() }
    // 父页也保存最近一条结果；回传的同一结果不能再次重置当前流的完整列表。
    var executionResults by remember {
        mutableStateOf(initialResult?.let { listOf(it) } ?: emptyList())
    }

    Dialog(onDismissRequest = requestClose) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
            shape = KiyoriUiShapes.dialog,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                // 紧凑的标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val fullToolId = "${packageName}:${tool.name}"
                        Text(
                            text = stringResource(R.string.script_execution),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "ID: $fullToolId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // 脚本编辑器
                    Text(
                        text = stringResource(R.string.script_code),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    ) {
                        TextField(
                            readOnly = executing,
                            value = scriptText,
                            onValueChange = { newValue -> scriptText = newValue },
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            placeholder = {
                                Text(
                                    stringResource(R.string.script_code_placeholder),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        )
                    }

                    // 参数输入
                    if (tool.parameters.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.script_params_config),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        tool.parameters.forEach { param ->
                            OutlinedTextField(
                                readOnly = executing,
                                value = paramValues[param.name] ?: "",
                                onValueChange = { value ->
                                    paramValues = paramValues.toMutableMap().apply {
                                        put(param.name, value)
                                    }
                                },
                                label = {
                                    Text("${param.name}${if (param.required) " *" else ""}")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text(param.description.resolve(context)) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    // 执行结果
                    if (executionResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.execution_result),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(executionResults) { result ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (result.success) 
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else 
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (result.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (result.success) result.result.toString() else stringResource(R.string.script_error, result.error ?: ""),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = requestClose) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    FilledTonalButton(
                        onClick = {
                            if (!executing) {
                                // UI 线程冻结此次输入；后台执行不能读取用户后来输入的新版本。
                                val scriptSnapshot = scriptText
                                val parameterSnapshot = paramValues.toMap()
                                executing = true
                                executionResults = emptyList()
                                scope.launch {
                                    try {
                                        val missingParams = tool.parameters.filter { it.required }
                                            .map { it.name }.filter { parameterSnapshot[it].isNullOrBlank() }
                                        if (missingParams.isNotEmpty()) {
                                            val result = ToolResult(
                                                toolName = "${packageName}:${tool.name}", success = false,
                                                result = StringResultData(""),
                                                error = context.getString(R.string.script_missing_params, missingParams.joinToString(", ")),
                                            )
                                            executionResults = listOf(result)
                                            onExecuted(result)
                                        } else {
                                            val aiTool = AITool(
                                                name = "${packageName}:${tool.name}",
                                                parameters = parameterSnapshot.map { (name, value) -> ToolParameter(name, value) },
                                            )
                                            JsToolManager.getInstance(context, packageManager)
                                                .executeScript(scriptSnapshot, aiTool)
                                                .flowOn(Dispatchers.IO)
                                                .collect { result ->
                                                    executionResults = executionResults + result
                                                    onExecuted(result)
                                                }
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        AppLogger.e("ScriptExecutionDialog", "Script execution failed")
                                        val result = ToolResult(
                                            toolName = "${packageName}:${tool.name}", success = false,
                                            result = StringResultData(""),
                                            error = context.getString(R.string.pkg_script_execution_failed),
                                        )
                                        executionResults = executionResults + result
                                        onExecuted(result)
                                    } finally {
                                        executing = false
                                    }
                                }
                            }
                        },
                        enabled = !executing
                    ) {
                        if (executing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.script_executing))
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.script_execute))
                        }
                    }
                }
            }
        }
    }
    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text(stringResource(R.string.pkg_script_close_title)) },
            text = { Text(stringResource(R.string.pkg_script_close_message)) },
            confirmButton = {
                TextButton(onClick = { showCloseConfirm = false; onDismiss() }) {
                    Text(stringResource(R.string.pkg_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) { Text(stringResource(R.string.pkg_cancel)) }
            },
        )
    }

}
