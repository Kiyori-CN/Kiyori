package com.ai.assistance.operit.ui.features.toolbox.screens.shellexecutor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiyori.design.theme.KiyoriUiShapes
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import java.text.SimpleDateFormat
import java.util.*
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors

/** 命令执行记录数据类 */
data class CommandRecord(
    val command: String,
    val result: AndroidShellExecutor.CommandResult,
    val timestamp: Long = System.currentTimeMillis()
)

/** 预设命令分类 */
enum class CommandCategory(val stringResId: Int) {
    SYSTEM(R.string.shell_executor_category_system),
    FILE(R.string.shell_executor_category_file),
    NETWORK(R.string.shell_executor_category_network),
    HARDWARE(R.string.shell_executor_category_hardware),
    PACKAGE(R.string.shell_executor_category_package)
}

@Composable
fun CommandCategory.getDisplayName(): String {
    return stringResource(stringResId)
}

/** 预设命令数据类 */
data class PresetCommand(
    val name: String,
    val command: String,
    val description: String,
    val category: CommandCategory,
    val icon: ImageVector
)

/**
 * Shell命令执行器屏幕
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShellExecutorScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val isCurrentScreen = LocalIsCurrentScreen.current
    val state: ShellExecutorViewModel = viewModel { ShellExecutorViewModel(context.applicationContext) }
    val focusManager = LocalFocusManager.current
    
    // 创建命令管理器
    val commandManager = remember { ShellCommandManager(context) }
    
    var commandInput by state::commandInput
    val isExecuting = state.isExecuting
    val commandHistory = state.commandHistory
    val errorMessage = state.errorMessage
    var showPresets by rememberSaveable { mutableStateOf(false) }
    var showClearHistory by rememberSaveable { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    val suggestionsList = remember(commandInput, commandHistory) {
        commandHistory.map { it.command }.filter { it.startsWith(commandInput, ignoreCase = true) }.take(5)
    }
    val presetCommands = remember(commandManager) { commandManager.getPresetCommands() }

    fun executeCommand(command: String) {
        if (isExecuting || command.isBlank()) return
        focusManager.clearFocus()
        showSuggestions = false
        state.executeCommand(command)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部区域
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.tool_shell_executor),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.tool_shell_executor_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 命令输入区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = commandInput,
                            onValueChange = { commandInput = it; showSuggestions = it.isNotBlank() },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.shell_executor_input_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Terminal,
                                    contentDescription = stringResource(R.string.shell_executor_command),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (commandInput.isNotEmpty()) {
                                    IconButton(onClick = { commandInput = "" }) {
                                        Icon(
                                            Icons.Outlined.Clear,
                                            contentDescription = stringResource(R.string.shell_executor_clear)
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = { executeCommand(commandInput) }
                            ),
                            singleLine = true,
                            shape = KiyoriUiShapes.field,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        
                        // 命令建议下拉菜单
                        if (showSuggestions && commandInput.isNotEmpty() && suggestionsList.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shadowElevation = 4.dp,
                                modifier = Modifier.fillMaxWidth()
                                    .align(Alignment.BottomStart)
                                    .offset(y = 56.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    suggestionsList.forEach { suggestion ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    commandInput = suggestion
                                                    showSuggestions = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = suggestion,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        
                                        if (suggestion != suggestionsList.last()) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 执行按钮
                    FilledTonalButton(
                        onClick = { executeCommand(commandInput) },
                        enabled = commandInput.trim().isNotEmpty() && !isExecuting,
                        shape = CircleShape,
                        modifier = Modifier.height(56.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.shell_executor_execute)
                            )
                        }
                    }
                }

                // 工具栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 清除历史按钮
                    if (commandHistory.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                showClearHistory = true
                            },
                            enabled = !isExecuting,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(R.string.shell_executor_clear_history),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.shell_executor_clear_history))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // 预设命令切换按钮
                    TextButton(
                        onClick = { showPresets = !showPresets },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(if (showPresets) stringResource(R.string.shell_executor_hide_presets) else stringResource(R.string.shell_executor_show_presets))
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showPresets) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 状态指示器
                if (isExecuting) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.shell_executor_executing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 预设命令区域
        AnimatedVisibility(
            visible = showPresets,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                val presetsByCategory = remember(presetCommands) {
                    presetCommands.groupBy { it.category }
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.shell_executor_common_commands),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 按分类显示预设命令
                    presetsByCategory.forEach { (category, commands) ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = category.getDisplayName(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 2
                            ) {
                                commands.forEach { presetCommand ->
                                    PresetCommandChip(
                                        presetCommand = presetCommand,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            commandInput = presetCommand.command
                                            showPresets = false
                                        }
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // 结果区域
        Box(modifier = Modifier.weight(1f)) {
            if (commandHistory.isEmpty()) {
                // 空状态
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.shell_executor_input_command_hint),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.shell_executor_view_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = { showPresets = !showPresets }) {
                        Text(stringResource(R.string.shell_executor_view_presets))
                    }
                }
            } else {
                // 命令历史记录
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(items = commandHistory, key = { it.command }) { record ->
                        CommandResultCard(
                            record = record,
                            onReExecute = { executeCommand(record.command) },
                            canExecute = !isExecuting
                        )
                    }

                    // 底部空间
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (isCurrentScreen && showClearHistory) {
        AlertDialog(
            onDismissRequest = { showClearHistory = false },
            title = { Text(stringResource(R.string.shell_executor_clear_history)) },
            text = { Text(stringResource(R.string.shell_executor_clear_history_confirm)) },
            confirmButton = { TextButton(onClick = {
                state.clearHistory()
                showClearHistory = false
            }, enabled = !isExecuting) { Text(stringResource(R.string.shell_executor_confirm)) } },
            dismissButton = { TextButton(onClick = { showClearHistory = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // 错误提示
    if (isCurrentScreen && errorMessage != null) {
        AlertDialog(
            onDismissRequest = { state.clearError() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.shell_executor_error_title))
                }
            },
            text = { Text(
                context.getString(R.string.shell_executor_execute_failed, errorMessage),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) },
            confirmButton = {
                TextButton(
                    onClick = { state.clearError() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text(stringResource(R.string.shell_executor_confirm)) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

/** 预设命令芯片 */
@Composable
fun PresetCommandChip(presetCommand: PresetCommand, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable { onClick() },
        shape = KiyoriUiShapes.control,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = presetCommand.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = presetCommand.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 命令结果卡片 */
@Composable
fun CommandResultCard(record: CommandRecord, onReExecute: () -> Unit = {}, canExecute: Boolean = true) {
    val successColors = KiyoriSemanticTone.GREEN.resolveColors()
    val errorColors = KiyoriSemanticTone.RED.resolveColors()
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()) }
    val formattedDate = remember(record) { dateFormatter.format(Date(record.timestamp)) }
    
    var expanded by rememberSaveable(record.command, record.timestamp) { mutableStateOf(false) }
    val backgroundColor = MaterialTheme.colorScheme.surface
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 命令和时间信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.command,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 状态指示
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (record.result.success) successColors.icon
                            else errorColors.icon
                        )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 展开/收起按钮
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.shell_executor_collapse) else stringResource(R.string.shell_executor_expand)
                    )
                }
            }

            // 命令输出结果
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // 标准输出
                    Text(
                        text = stringResource(R.string.shell_executor_command),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ShellOutputText(record.command)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (record.result.stdout.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.shell_executor_stdout),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            ShellOutputText(record.result.stdout)
                        }
                    }

                    // 标准错误
                    if (record.result.stderr.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.shell_executor_stderr),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            ShellOutputText(record.result.stderr, MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }

                    // 退出代码
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.shell_executor_exit_code, record.result.exitCode.toString()),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (record.result.exitCode == 0)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // 重新执行按钮
                        TextButton(
                            onClick = onReExecute,
                            enabled = canExecute,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.shell_executor_re_execute),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.shell_executor_re_execute),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 长输出在有界区域内滚动，保留完整内容与文本选择，避免一张历史卡占满页面。 */
@Composable
private fun ShellOutputText(text: String, color: Color = LocalContentColor.current) {
    SelectionContainer {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()).padding(12.dp)
        )
    }
}
