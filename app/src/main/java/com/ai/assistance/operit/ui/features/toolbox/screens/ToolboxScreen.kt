package com.ai.assistance.operit.ui.features.toolbox.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.toolbox.screens.apppermissions.AppPermissionsScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.ffmpegtoolbox.FFmpegToolboxScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.FileManagerScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.logcat.LogcatScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.shellexecutor.ShellExecutorScreen
import com.ai.assistance.operit.terminal.main.TerminalScreen as TerminalViewScreen
// import com.ai.assistance.operit.ui.features.toolbox.screens.terminalconfig.TerminalAutoConfigScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.UIDebuggerScreen
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.rememberTerminalEnv
import com.ai.assistance.operit.ui.main.navigation.LocalAppNavigationModel
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.NavigationSurface
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.kiyoriSemanticToneForStableId
import com.kiyori.design.theme.KiyoriUiShapes

data class Tool(
        val id: String,
        val name: String,
        val icon: ImageVector,
        val description: String? = null,
        val onClick: () -> Unit
)

/** 工具箱屏幕，展示可用的各种工具 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun ToolboxScreen(
        navController: NavController,
        onNavigationEntrySelected: (NavigationEntrySpec) -> Unit
) {
        val navigationModel = LocalAppNavigationModel.current
        val toolboxEntries =
                remember(navigationModel) {
                        navigationModel
                                ?.navigationEntries
                                .orEmpty()
                                .filter { entry ->
                                        entry.surface == NavigationSurface.TOOLBOX
                                }
                }
        val tools =
                remember(toolboxEntries, onNavigationEntrySelected) {
                        toolboxEntries.map { entry ->
                                Tool(
                                        id = entry.entryId,
                                        name = entry.title,
                                        icon = entry.icon,
                                        description = entry.description,
                                        onClick = {
                                                onNavigationEntrySelected(entry)
                                        }
                                )
                        }
                }

        Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                ) {
                        items(
                                items = tools,
                                key = { tool -> tool.id }
                        ) { tool ->
                                ToolCard(tool = tool)
                        }
                }
        }
}

/** 工具项卡片 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCard(tool: Tool) {
        val tone = remember(tool.id) { kiyoriSemanticToneForStableId(tool.id) }

        Card(
                onClick = tool.onClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 156.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                elevation =
                        CardDefaults.cardElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 1.dp
                        ),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = KiyoriUiShapes.card
        ) {
                // 卡片内容
                Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        KiyoriSemanticIconBadge(
                                imageVector = tool.icon,
                                tone = tone,
                                contentDescription = null,
                                containerSize = 48.dp,
                                iconSize = 24.dp,
                                shape = CircleShape,
                        )

                        Text(
                                text = tool.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                minLines = 1,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                        )

                        tool.description?.takeIf { it.isNotBlank() }?.let { description ->
                                Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 2.dp),
                                        minLines = 1,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                )
                        }
                }
        }
}


/** 显示文件管理器工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        FileManagerScreen(
                                onBack = { navController.popBackStack() }
                        )
                }
        }
}

/** 显示终端工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalToolScreen(navController: NavController, forceShowSetup: Boolean = false) {
        val context = LocalContext.current
        val terminalManager = remember { TerminalManager.getInstance(context) }
        val terminalEnv = rememberTerminalEnv(terminalManager = terminalManager, forceShowSetup = forceShowSetup)
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        TerminalViewScreen(
                                env = terminalEnv,
                                onClose = { navController.popBackStack() },
                        )
                }
        }
}

/** 显示终端自动配置工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalAutoConfigToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        // TODO: 需要重构以适配新的终端架构
                        // TerminalAutoConfigScreen(navController = navController)
                        Text(
                            text = stringResource(R.string.tool_terminal_auto_config_under_construction),
                            modifier = Modifier.padding(16.dp)
                        )
                }
        }
}

/** 显示应用权限管理工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPermissionsToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        AppPermissionsScreen(navController = navController)
                }
        }
}

/** 显示UI调试工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIDebuggerToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        UIDebuggerScreen(navController = navController)
                }
        }
}

/** 显示FFmpeg工具箱屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFmpegToolboxToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        FFmpegToolboxScreen(navController = navController)
                }
        }
}

/** 显示Shell命令执行器屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellExecutorToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        ShellExecutorScreen(navController = navController)
                }
        }
}

/** 显示日志查看器屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        LogcatScreen(navController = navController)
                }
        }
}

/** 显示工具测试屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolTesterToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        com.ai.assistance.operit.ui.features.toolbox.screens.tooltester
                                .ToolTesterScreen(navController = navController)
                }
        }
}

/** 显示默认助手设置引导屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultAssistantGuideToolScreen(navController: NavController) {
        com.ai.assistance.operit.ui.features.toolbox.screens.defaultassistant
                .DefaultAssistantGuideScreen(navController = navController)
}
