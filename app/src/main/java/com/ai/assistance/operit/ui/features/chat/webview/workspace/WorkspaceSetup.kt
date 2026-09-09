package com.ai.assistance.operit.ui.features.chat.webview.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.webview.createAndGetDefaultWorkspace
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.ui.features.chat.webview.createWorkspaceFromTemplate
import com.ai.assistance.operit.ui.features.chat.webview.getWorkspacePath
import androidx.activity.compose.BackHandler
import java.io.File
import kotlinx.coroutines.*

/**
 * VSCode风格的工作区设置组件
 * 用于初始绑定工作区
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceSetup(chatId: String, isVisible: Boolean = true, onBindWorkspace: suspend (String, String?) -> Unit) {
    val context = LocalContext.current
    val existingWorkspace = remember(chatId) { File(getWorkspacePath(context, chatId)) }
    var showFileBrowser by remember { mutableStateOf(false) }
    var showProjectTypeDialog by remember { mutableStateOf(false) }
    var projectTypeDialogError by remember { mutableStateOf<String?>(null) }
    var isImportingToolPkgTemplate by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    val packageManager = remember { PackageManager.getInstance(context, toolHandler) }
    var toolPkgWorkspaceTemplates by remember { mutableStateOf<List<PackageManager.ToolPkgWorkspaceTemplate>>(emptyList()) }

    LaunchedEffect(Unit) {
        toolPkgWorkspaceTemplates = packageManager.getToolPkgWorkspaceTemplates(context)
    }

    fun createAndBind(create: () -> File) {
        if (isImportingToolPkgTemplate) return
        projectTypeDialogError = null
        isImportingToolPkgTemplate = true
        scope.launch {
            try {
                val directory = withContext(Dispatchers.IO) { create() }
                onBindWorkspace(directory.absolutePath, null)
                showProjectTypeDialog = false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                com.ai.assistance.operit.util.AppLogger.e("WorkspaceSetup", "Failed to create workspace", error)
                projectTypeDialogError = if (existingWorkspace.exists()) {
                    context.getString(R.string.workspace_existing_preserved)
                } else context.getString(R.string.workspace_template_import_failed)
            } finally {
                isImportingToolPkgTemplate = false
            }
        }
    }

    fun bindBuiltInWorkspace(projectType: String?) {
        createAndBind { createAndGetDefaultWorkspace(context, chatId, projectType) }
    }

    fun importToolPkgWorkspaceTemplate(template: PackageManager.ToolPkgWorkspaceTemplate) {
        createAndBind {
            createWorkspaceFromTemplate(context, chatId) { directory ->
                packageManager.importToolPkgWorkspaceTemplate(
                    containerPackageName = template.containerPackageName,
                    templateId = template.templateId,
                    destinationDir = directory,
                ).getOrThrow()
            }
        }
    }

    BackHandler(enabled = isVisible && showFileBrowser) { showFileBrowser = false }
    if (isVisible && showFileBrowser) {
        FileBrowser(
            initialPath = context.filesDir.absolutePath, // 默认应用内部目录
            onBindWorkspace = { path, env -> onBindWorkspace(path, env) },
            onCancel = { showFileBrowser = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // 移除点击时的涟漪效果
                    enabled = true,
                    onClick = {}
                ) // 添加点击拦截
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isVisible && showProjectTypeDialog) {
                AlertDialog(
                    onDismissRequest = {
                        if (!isImportingToolPkgTemplate) {
                            showProjectTypeDialog = false
                            projectTypeDialogError = null
                        }
                    },
                    title = {
                        Text(
                            text = context.getString(R.string.workspace_select_language_type_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = context.getString(R.string.workspace_select_language_type_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (existingWorkspace.isDirectory) {
                                OutlinedButton(
                                    enabled = !isImportingToolPkgTemplate,
                                    onClick = { createAndBind { existingWorkspace } },
                                ) { Text(context.getString(R.string.workspace_rebind_existing)) }
                            }
                            projectTypeDialogError?.let { error ->
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (isImportingToolPkgTemplate) {
                                Text(
                                    text = context.getString(R.string.workspace_template_importing),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            ProjectTypeCard(
                                enabled = !isImportingToolPkgTemplate,
                                icon = Icons.Default.CreateNewFolder,
                                title = context.getString(R.string.workspace_project_type_blank_title),
                                description = context.getString(R.string.workspace_project_type_blank_description),
                                onClick = {
                                    bindBuiltInWorkspace("blank")
                                }
                            )
                            
                            // Office 项目卡片
                            ProjectTypeCard(
                                enabled = !isImportingToolPkgTemplate,
                                icon = Icons.Default.Description,
                                title = context.getString(R.string.workspace_project_type_office_title),
                                description = context.getString(R.string.workspace_project_type_office_description),
                                onClick = {
                                    bindBuiltInWorkspace("office")
                                }
                            )
                            
                            // Web 项目卡片
                            ProjectTypeCard(
                                enabled = !isImportingToolPkgTemplate,
                                icon = Icons.Default.Language,
                                title = context.getString(R.string.workspace_project_type_web_title),
                                description = context.getString(R.string.workspace_project_type_web_description),
                                onClick = {
                                    bindBuiltInWorkspace(null)
                                }
                            )

                            // Android 项目卡片
                            ProjectTypeCard(
                                enabled = !isImportingToolPkgTemplate,
                                icon = Icons.Default.PhoneAndroid,
                                title = context.getString(R.string.workspace_project_type_android_title),
                                description = context.getString(R.string.workspace_project_type_android_description),
                                onClick = {
                                    bindBuiltInWorkspace("android")
                                }
                            )

                            // Flutter 项目卡片
                            ProjectTypeCard(
                                enabled = !isImportingToolPkgTemplate,
                                icon = Icons.Default.Widgets,
                                title = context.getString(R.string.workspace_project_type_flutter_title),
                                description = context.getString(R.string.workspace_project_type_flutter_description),
                                onClick = {
                                    bindBuiltInWorkspace("flutter")
                                }
                            )
                             
                            // Node.js 项目卡片
                            ProjectTypeCard(
                                enabled = !isImportingToolPkgTemplate,
                                icon = Icons.Default.Terminal,
                                title = context.getString(R.string.workspace_project_type_node_title),
                                description = context.getString(R.string.workspace_project_type_node_description),
                                onClick = {
                                    bindBuiltInWorkspace("node")
                                }
                            )
                            
                            // TypeScript 项目卡片
                            ProjectTypeCard(
                                enabled = !isImportingToolPkgTemplate,
                                icon = Icons.Default.Code,
                                title = context.getString(R.string.workspace_project_type_typescript_title),
                                description = context.getString(R.string.workspace_project_type_typescript_description),
                                onClick = {
                                    bindBuiltInWorkspace("typescript")
                                }
                            )
                            
                            // Python 项目卡片
                            ProjectTypeCard(
                                enabled = !isImportingToolPkgTemplate,
                                icon = Icons.Default.Code,
                                title = context.getString(R.string.workspace_project_type_python_title),
                                description = context.getString(R.string.workspace_project_type_python_description),
                                onClick = {
                                    bindBuiltInWorkspace("python")
                                }
                            )
                            
                            // Java 项目卡片
                            ProjectTypeCard(
                                enabled = !isImportingToolPkgTemplate,
                                icon = Icons.Default.Settings,
                                title = context.getString(R.string.workspace_project_type_java_title),
                                description = context.getString(R.string.workspace_project_type_java_description),
                                onClick = {
                                    bindBuiltInWorkspace("java")
                                }
                            )
                            
                            // Go 项目卡片
                            ProjectTypeCard(
                                enabled = !isImportingToolPkgTemplate,
                                icon = Icons.Default.Build,
                                title = context.getString(R.string.workspace_project_type_go_title),
                                description = context.getString(R.string.workspace_project_type_go_description),
                                onClick = {
                                    bindBuiltInWorkspace("go")
                                }
                            )

                            if (toolPkgWorkspaceTemplates.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = context.getString(R.string.workspace_project_type_toolpkg_section),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                toolPkgWorkspaceTemplates.forEach { template ->
                                    ProjectTypeCard(
                                        enabled = !isImportingToolPkgTemplate,
                                        icon = Icons.Default.Extension,
                                        title = template.displayName,
                                        description =
                                            buildString {
                                                append(template.containerPackageName)
                                                if (template.description.isNotBlank()) {
                                                    append(" · ")
                                                    append(template.description)
                                                }
                                            },
                                        onClick = {
                                            importToolPkgWorkspaceTemplate(template)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showProjectTypeDialog = false
                                projectTypeDialogError = null
                            },
                            enabled = !isImportingToolPkgTemplate
                        ) {
                            Text(context.getString(R.string.cancel))
                        }
                    }
                )
            }

            // VSCode风格的图标
            Icon(
                imageVector = Icons.Default.Widgets, // 使用更通用的图标
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = context.getString(R.string.setup_workspace),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = context.getString(R.string.workspace_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // VSCode风格的选项卡
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WorkspaceOption(
                    icon = Icons.Default.CreateNewFolder,
                    title = context.getString(R.string.create_default_workspace),
                    description = context.getString(R.string.create_new_workspace_in_app),
                    onClick = {
                        projectTypeDialogError = null
                        showProjectTypeDialog = true
                    }
                )
                
                WorkspaceOption(
                    icon = Icons.Default.FolderOpen,
                    title = context.getString(R.string.select_existing_workspace),
                    description = context.getString(R.string.select_folder_from_device),
                    onClick = { showFileBrowser = true }
                )
            }
        }
    }
}

/**
 * 项目类型卡片组件（IDE风格）
 */
@Composable
fun ProjectTypeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 图标
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // 文字内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 箭头指示
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 工作区选项卡组件
 */
@Composable
fun WorkspaceOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp) // 调整大小
            .heightIn(min = 160.dp)
            .clip(RoundedCornerShape(12.dp)) // 更圆的角
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp, // 移除阴影
            pressedElevation = 0.dp
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
} 
