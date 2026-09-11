package com.ai.assistance.operit.ui.features.packages.screens.mcp.components

import com.kiyori.design.theme.kiyoriSurfaceColors

import com.kiyori.design.theme.KiyoriSurfaceTokens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.kiyori.design.theme.KiyoriUiShapes

/**
 * 环境变量管理对话框
 *
 * 用于添加、编辑和删除MCP插件的环境变量
 *
 * @param environmentVariables 当前的环境变量
 * @param onDismiss 关闭对话框的回调
 * @param onConfirm 提交环境变量的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPEnvironmentVariablesDialog(
        environmentVariables: Map<String, String>,
        onDismiss: () -> Unit,
        onConfirm: (Map<String, String>) -> Unit
) {
    val envVarsList = remember { mutableStateListOf<Pair<String, String>>().apply { addAll(environmentVariables.toList()) } }

    // 新变量的键和值
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
                shape = KiyoriUiShapes.dialog,
                color = kiyoriSurfaceColors().popup,
                tonalElevation = KiyoriSurfaceTokens.flatElevation,
                shadowElevation = KiyoriSurfaceTokens.popupElevation,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth()) {
                Text(text = stringResource(R.string.mcp_manage_env_variables), style = MaterialTheme.typography.headlineSmall)

                Spacer(modifier = Modifier.height(16.dp))

                // 环境变量列表
                if (envVarsList.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp).fillMaxWidth()) {
                        items(items = envVarsList, key = { it.first }) { (key, value) ->
                            Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = key, style = MaterialTheme.typography.bodyMedium)
                                    OutlinedTextField(
                                        value = value,
                                        onValueChange = { updated ->
                                            val index = envVarsList.indexOfFirst { it.first == key }
                                            if (index >= 0) envVarsList[index] = key to updated
                                        },
                                        label = { Text(stringResource(R.string.mcp_var_value)) },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                IconButton(onClick = { envVarsList.remove(Pair(key, value)) }) {
                                    Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.delete)
                                    )
                                }
                            }
                            if (key != envVarsList.last().first) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 添加新环境变量
                OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text(stringResource(R.string.mcp_var_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                        value = newValue,
                        visualTransformation = PasswordVisualTransformation(),
                        onValueChange = { newValue = it },
                        label = { Text(stringResource(R.string.mcp_var_value)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                        enabled = newKey.trim().matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && envVarsList.none { it.first == newKey.trim() } && '\u0000' !in newValue,
                        onClick = {
                            if (newKey.isNotBlank()) {
                                envVarsList.add(Pair(newKey.trim(), newValue))
                                newKey = ""
                                newValue = ""
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = stringResource(R.string.add))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.mcp_add_var))
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (newKey.isNotEmpty() || newValue.isNotEmpty()) Text(stringResource(R.string.mcp_env_pending), style = MaterialTheme.typography.bodySmall)

                // 按钮行
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                            enabled = newKey.isEmpty() && newValue.isEmpty() && envVarsList.none { '\u0000' in it.second },
                            onClick = { onConfirm(envVarsList.associate { it.first to it.second }) }
                    ) { Text(stringResource(R.string.mcp_confirm)) }
                }
            }
        }
    }
}
