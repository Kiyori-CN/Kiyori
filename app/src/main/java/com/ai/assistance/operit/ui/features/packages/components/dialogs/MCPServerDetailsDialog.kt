package com.ai.assistance.operit.ui.features.packages.components.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.ui.features.packages.components.dialogs.actions.MCPServerDetailsActions
import com.ai.assistance.operit.ui.features.packages.components.dialogs.content.MCPServerConfigContent
import com.ai.assistance.operit.ui.features.packages.components.dialogs.content.MCPServerDetailsContent
import com.ai.assistance.operit.ui.features.packages.components.dialogs.header.MCPServerDetailsHeader
import com.ai.assistance.operit.ui.features.packages.components.dialogs.tabs.MCPServerDetailsTabs
import com.ai.assistance.operit.data.mcp.MCPLocalServer

/**
 * A dialog that displays detailed information about an MCP server.
 *
 * @param server The MCP server to display details for
 * @param onDismiss Callback to be invoked when the dialog is dismissed
 * @param onInstall Callback to be invoked when the install button is clicked
 * @param onUninstall Callback to be invoked when the uninstall button is clicked
 * @param installedPath 已安装插件的路径，如果未安装则为null
 * @param pluginConfig 插件配置，只在已安装的插件中提供
 * @param onSaveConfig 保存配置回调
 * @param onUpdateConfig 更新配置回调
 * @param mdFontSize Markdown内容的字体大小
 */
@Composable
fun MCPServerDetailsDialog(
        server: MCPLocalServer.PluginMetadata,
        onDismiss: () -> Unit,
        onInstall: (MCPLocalServer.PluginMetadata) -> Unit,
        onUninstall: (MCPLocalServer.PluginMetadata) -> Unit,
        installedPath: String? = null,
        pluginConfig: String = "",
        onSaveConfig: suspend (String) -> Boolean = { false },
        mdFontSize: Float = 14f
) {
    val isInstalled = server.isInstalled
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    // 添加标签页状态
    var selectedTabIndex by remember { mutableStateOf(0) }

    // 本地编辑的配置内容
    var localPluginConfig by remember { mutableStateOf(pluginConfig) }

    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveResult by remember { mutableStateOf<Boolean?>(null) }
    var savedConfig by remember { mutableStateOf(pluginConfig) }
    var discardRequested by remember { mutableStateOf(false) }
    var uninstallRequested by remember { mutableStateOf(false) }
    val requestDismiss: () -> Unit = {
        if (!saving) {
            if (localPluginConfig != savedConfig) discardRequested = true else onDismiss()
        }
    }
    Dialog(onDismissRequest = requestDismiss) {
        Surface(
                modifier =
                        Modifier.fillMaxWidth(0.95f) // Take 95% of the screen width
                                .fillMaxHeight(
                                        0.7f
                                ) // Take 70% of the screen height (reduced from 0.85f)
                                .heightIn(
                                        min = minOf(400.dp, screenHeight * 0.7f),
                                        max = screenHeight * 0.7f // Reduced maximum height
                                ) // Responsive height
                                .padding(vertical = 8.dp), // Reduced vertical padding
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                    // Header (Logo, title, badges, etc.)
                    MCPServerDetailsHeader(server = server, onDismiss = requestDismiss)

                    // Tabs if installed
                    if (isInstalled) {
                        MCPServerDetailsTabs(
                                selectedTabIndex = selectedTabIndex,
                                onTabSelected = { selectedTabIndex = it }
                        )
                    }

                    // Content based on selected tab - Note the paddingBottom to make room for
                    // actions
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .weight(1f)
                    ) {
                        if (!isInstalled || selectedTabIndex == 0) {
                            // Details tab
                            MCPServerDetailsContent(
                                    server = server,
                                    modifier = Modifier.fillMaxSize(), // Fill the available space
                                    mdFontSize = mdFontSize.sp // Pass the font size parameter
                            )
                        } else {
                            // Config tab
                            MCPServerConfigContent(
                                    localPluginConfig = localPluginConfig,
                                    onConfigChanged = { localPluginConfig = it },
                                    installedPath = installedPath,
                                    saving = saving,
                                    saveResult = saveResult,
                                    onSaveConfig = {
                                        if (!saving) {
                                            val draft = localPluginConfig
                                            saving = true
                                            saveResult = null
                                            scope.launch {
                                                try {
                                                    val saved = onSaveConfig(draft)
                                                    saveResult = saved
                                                    if (saved) savedConfig = draft
                                                } catch (cancelled: CancellationException) { throw cancelled }
                                                catch (_: Exception) { saveResult = false }
                                                finally { saving = false }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize() // Fill the available space
                            )
                        }
                    }

                // 按钮参与实际布局测量，避免大字体下覆盖正文。
                Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 3.dp, // Slightly elevated
                        shadowElevation = 4.dp // Add shadow for visual separation
                ) {
                    MCPServerDetailsActions(
                            server = server,
                            isInstalled = isInstalled,
                            enabled = !saving,
                            onInstall = onInstall,
                            onUninstall = { if (!saving) uninstallRequested = true }
                    )
                }
            }
        }
    }
    if (discardRequested || uninstallRequested) {
        AlertDialog(
            onDismissRequest = { discardRequested = false; uninstallRequested = false },
            title = { Text(stringResource(if (uninstallRequested) R.string.uninstall else R.string.mcp_unsaved_config)) },
            text = { Text(if (uninstallRequested) server.name else stringResource(R.string.mcp_discard_config)) },
            confirmButton = {
                TextButton(onClick = {
                    if (uninstallRequested) onUninstall(server) else onDismiss()
                    discardRequested = false
                    uninstallRequested = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { discardRequested = false; uninstallRequested = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }


}
