package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.kiyori.design.theme.KiyoriUiShapes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.rememberLocal
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/**
 * 文件夹展开状态的持久化数据类（与 MemoryFolderSelectionDialog 共享）
 */
@Serializable
data class FolderExpandedState(
    val expandedPaths: Set<String> = emptySet()
)

/**
 * Memory-space selector and metadata controls. Memory contents stay in the existing ObjectBox
 * database keyed by the stable space id.
 */
@Composable
private fun ProfileSelector(
    profileList: List<String>,
    profileNameMap: Map<String, String>,
    selectedProfileId: String,
    onProfileSelected: (String) -> Unit,
    onMemorySpaceCreate: (String) -> Unit,
    onMemorySpaceRename: (String, String) -> Unit,
    onMemorySpaceDelete: (String) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf("") }
    val selectedProfileName = profileNameMap[selectedProfileId] ?: selectedProfileId

    var manageExpanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = KiyoriUiShapes.control) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(stringResource(R.string.memory_space_select), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(selectedProfileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                profileList.forEach { profileId ->
                    DropdownMenuItem(text = { Text(profileNameMap[profileId] ?: profileId, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = { if (profileId == selectedProfileId) Icon(Icons.Default.Check, null) },
                        onClick = { onProfileSelected(profileId); expanded = false })
                }
            }
        }
        Box {
            IconButton(onClick = { manageExpanded = true }, enabled = enabled) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.library_manage_spaces)) }
            DropdownMenu(expanded = manageExpanded, onDismissRequest = { manageExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.memory_space_create)) }, leadingIcon = { Icon(Icons.Outlined.Add, null) },
                    onClick = { manageExpanded = false; editedName = ""; showCreateDialog = true })
                DropdownMenuItem(text = { Text(stringResource(R.string.memory_space_rename)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { manageExpanded = false; editedName = selectedProfileName; showRenameDialog = true })
                if (selectedProfileId != "default") DropdownMenuItem(text = { Text(stringResource(R.string.memory_space_delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { manageExpanded = false; showDeleteDialog = true })
            }
        }
    }

    if (showCreateDialog || showRenameDialog) {
        val creating = showCreateDialog
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                showRenameDialog = false
                editedName = ""
            },
            title = {
                Text(stringResource(if (creating) R.string.memory_space_create else R.string.memory_space_rename))
            },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text(stringResource(R.string.memory_space_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = editedName.trim()
                        if (creating) onMemorySpaceCreate(name)
                        else onMemorySpaceRename(selectedProfileId, name)
                        showCreateDialog = false
                        showRenameDialog = false
                        editedName = ""
                    },
                    enabled = editedName.isNotBlank()
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    showRenameDialog = false
                    editedName = ""
                }) { Text(stringResource(R.string.cancel_action)) }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.memory_space_delete)) },
            text = { Text(stringResource(R.string.memory_space_delete_warning, selectedProfileName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onMemorySpaceDelete(selectedProfileId)
                        showDeleteDialog = false
                    }
                ) { Text(stringResource(R.string.confirm_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }
}

/**
 * 文件夹树节点数据结构
 */
data class FolderNode(
    val name: String,
    val fullPath: String,
    val children: MutableList<FolderNode> = mutableListOf(),
    var isExpanded: Boolean = true
)

/**
 * 左侧文件夹导航组件
 * @param folderPaths 所有文件夹路径列表（扁平结构，如 ["工作/项目A", "工作/项目B", "生活"]）
 * @param selectedFolderPath 当前选中的文件夹路径
 * @param onFolderSelected 文件夹选中回调
 * @param onFolderRename 重命名文件夹回调
 * @param onFolderDelete 删除文件夹回调
 * @param onFolderCreate 创建文件夹回调
 */
@Composable
fun FolderNavigator(
    folderPaths: List<String>,
    selectedFolderPath: String,
    onFolderSelected: (String) -> Unit,
    onFolderRename: ((String, String) -> Unit)? = null,
    onFolderDelete: ((String) -> Unit)? = null,
    onFolderCreate: ((String) -> Unit)? = null,
    isBusy: Boolean = false,
    error: String? = null,
    // New parameters for profile selection
    profileList: List<String>,
    profileNameMap: Map<String, String>,
    selectedProfileId: String,
    onProfileSelected: (String) -> Unit,
    onMemorySpaceCreate: (String) -> Unit,
    onMemorySpaceRename: (String, String) -> Unit,
    onMemorySpaceDelete: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 构建文件夹树
    val rootNode = remember(folderPaths) {
        buildFolderTree(folderPaths)
    }

    // 使用 rememberLocal 持久化展开状态（默认为空，即全部折叠）
    var expandedState by rememberLocal(
        key = "folder_navigator_expanded_state_$selectedProfileId",
        defaultValue = FolderExpandedState(),
        serializer = serializer()
    )
    
    // 对话框状态
    var showCreateDialog by remember(selectedProfileId) { mutableStateOf(false) }
    var showRenameDialog by remember(selectedProfileId) { mutableStateOf(false) }
    var showDeleteDialog by remember(selectedProfileId) { mutableStateOf(false) }
    var contextMenuFolder by remember { mutableStateOf<String?>(null) }

    Surface(modifier = modifier.fillMaxWidth().fillMaxHeight(0.78f), color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.library_location), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismissRequest) { Icon(Icons.Outlined.Close, stringResource(R.string.memory_close)) }
            }
            Box(Modifier.fillMaxWidth().height(4.dp)) { if (isBusy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            // 空间和目录操作跟随列表滚动，横屏/大字体时仍给目录留下可用空间。
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    error?.let { Text(it, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    ProfileSelector(profileList, profileNameMap, selectedProfileId, onProfileSelected,
                        onMemorySpaceCreate, onMemorySpaceRename, onMemorySpaceDelete, enabled = !isBusy)
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.folder_navigator_folder), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showCreateDialog = true }, enabled = !isBusy) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.foldernav_new_folder))
                        }
                    }
                    FolderItem(name = stringResource(R.string.folder_navigator_all), fullPath = "", level = 0,
                        isSelected = selectedFolderPath.isEmpty(), isExpanded = false, hasChildren = false,
                        onToggleExpand = {}, onClick = { onFolderSelected("") }, onLongClick = null)
                }
                if (rootNode.children.isEmpty()) item {
                    Text(stringResource(R.string.library_no_folders), Modifier.padding(horizontal = 24.dp, vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                renderFolderTree(nodes = rootNode.children, level = 0, selectedPath = selectedFolderPath,
                    expandedPaths = expandedState.expandedPaths,
                    onToggleExpand = { path ->
                        expandedState = if (path in expandedState.expandedPaths) expandedState.copy(expandedPaths = expandedState.expandedPaths - path)
                            else expandedState.copy(expandedPaths = expandedState.expandedPaths + path)
                    }, onFolderSelected = onFolderSelected, onFolderLongClick = { contextMenuFolder = it })
            }
        }
    }

    // 对话框
    if (showCreateDialog) {
        FolderCreateDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { newPath ->
                onFolderCreate?.invoke(com.ai.assistance.operit.data.repository.MemoryRepository.normalizeFolderPath(newPath) ?: newPath)
                showCreateDialog = false
            }
        )
    }
    
    if (showRenameDialog && contextMenuFolder != null) {
        FolderRenameDialog(
            currentPath = contextMenuFolder!!,
            onDismiss = {
                showRenameDialog = false
                contextMenuFolder = null
            },
            onRename = { newPath ->
                onFolderRename?.invoke(contextMenuFolder!!, com.ai.assistance.operit.data.repository.MemoryRepository.normalizeFolderPath(newPath) ?: newPath)
                showRenameDialog = false
                contextMenuFolder = null
            }
        )
    }
    
    if (showDeleteDialog && contextMenuFolder != null) {
        FolderDeleteDialog(
            folderPath = contextMenuFolder!!,
            onDismiss = {
                showDeleteDialog = false
                contextMenuFolder = null
            },
            onConfirm = {
                onFolderDelete?.invoke(contextMenuFolder!!)
                showDeleteDialog = false
                contextMenuFolder = null
            }
        )
    }
    
    // 右键菜单（不与其他对话框同时显示）
    if (contextMenuFolder != null && !showRenameDialog && !showDeleteDialog) {
        FolderContextMenu(
            folderPath = contextMenuFolder!!,
            onDismiss = { contextMenuFolder = null },
            onRename = {
                showRenameDialog = true
            },
            onDelete = {
                showDeleteDialog = true
            }
        )
    }
}

/**
 * 递归渲染文件夹树
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.renderFolderTree(
    nodes: List<FolderNode>,
    level: Int,
    selectedPath: String,
    expandedPaths: Set<String>,
    onToggleExpand: (String) -> Unit,
    onFolderSelected: (String) -> Unit,
    onFolderLongClick: (String) -> Unit
) {
    nodes.forEach { node ->
        val isExpanded = node.fullPath in expandedPaths
        val hasChildren = node.children.isNotEmpty()

        item(key = node.fullPath) {
            FolderItem(
                name = node.name,
                fullPath = node.fullPath,
                level = level,
                isSelected = selectedPath == node.fullPath,
                isExpanded = isExpanded,
                hasChildren = hasChildren,
                onToggleExpand = { onToggleExpand(node.fullPath) },
                onClick = { onFolderSelected(node.fullPath) },
                onLongClick = { onFolderLongClick(node.fullPath) },
                modifier = Modifier.animateItem(placementSpec = tween(durationMillis = 300))
            )
        }

        // 如果展开且有子节点，递归渲染
        if (isExpanded && hasChildren) {
            renderFolderTree(
                nodes = node.children,
                level = level + 1,
                selectedPath = selectedPath,
                expandedPaths = expandedPaths,
                onToggleExpand = onToggleExpand,
                onFolderSelected = onFolderSelected,
                onFolderLongClick = onFolderLongClick
            )
        }
    }
}

/**
 * 单个文件夹项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderItem(
    name: String,
    fullPath: String,
    level: Int,
    isSelected: Boolean,
    isExpanded: Boolean,
    hasChildren: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    } else {
        Color.Transparent
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    // 展开箭头的旋转动画
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "expand_rotation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(KiyoriUiShapes.control)
            .semantics { selected = isSelected }
            .background(backgroundColor)
            .then(
                if (onLongClick != null && fullPath != stringResource(R.string.memory_uncategorized)) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable { onClick() }
                }
            )
            .heightIn(min = 52.dp)
            .padding(start = (16 + level.coerceAtMost(5) * 12).dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 展开/折叠图标
        if (hasChildren) {
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) stringResource(R.string.memory_collapse) else stringResource(R.string.memory_expand),
                    tint = textColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotationAngle)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            // 叶子行省去箭头槽，减少无效缩进。
        }

        // 文件夹图标
        Icon(
            imageVector = if (hasChildren && isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 文件夹名称
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (onLongClick != null && fullPath != stringResource(R.string.memory_uncategorized)) IconButton(onClick = onLongClick) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.library_folder_actions, name), Modifier.size(20.dp))
        }
    }
}

/**
 * 从扁平路径列表构建文件夹树
 * @param paths 路径列表，如 ["工作/项目A", "工作/项目B", "生活/健康"]
 * @return 根节点（虚拟根节点，不显示）
 */
private fun buildFolderTree(paths: List<String>): FolderNode {
    val root = FolderNode("", "")

    paths.forEach { path ->
        if (path.isBlank()) return@forEach

        val parts = path.split("/").filter { it.isNotBlank() }
        var currentNode = root
        var currentPath = ""

        parts.forEachIndexed { index, part ->
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"

            // 查找是否已有此节点
            var childNode = currentNode.children.find { it.name == part }
            if (childNode == null) {
                // 创建新节点
                childNode = FolderNode(
                    name = part,
                    fullPath = currentPath,
                    isExpanded = false // 默认收起
                )
                currentNode.children.add(childNode)
            }
            currentNode = childNode
        }
    }

    fun sort(node: FolderNode) {
        node.children.sortBy { it.name.lowercase() }
        node.children.forEach(::sort)
    }
    sort(root)
    return root
}

/**
 * 文件夹右键菜单
 */
@Composable
private fun FolderContextMenu(
    folderPath: String,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_folder_operations)) },
        text = {
            Column {
                Text("${stringResource(R.string.memory_folder_label)}: $folderPath", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onRename()
                    // 不调用 onDismiss()，让菜单自动隐藏
                }) {
                    Text(stringResource(R.string.memory_rename_folder))
                }
                TextButton(onClick = {
                    onDelete()
                    // 不调用 onDismiss()，让菜单自动隐藏
                }) {
                    Text(stringResource(R.string.memory_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.memory_cancel))
            }
        }
    )
}

/**
 * 创建文件夹对话框
 */
@Composable
private fun FolderCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_create_folder)) },
        text = {
            Column {
                Text(stringResource(R.string.memory_folder_path_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.memory_folder_path_label)) },
                    placeholder = { Text(stringResource(R.string.memory_folder_path_example)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (folderName.isNotBlank()) onCreate(folderName.trim()) },
                enabled = folderName.isNotBlank()
            ) {
                Text(stringResource(R.string.memory_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.memory_cancel))
            }
        }
    )
}

/**
 * 重命名文件夹对话框
 */
@Composable
private fun FolderRenameDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember(currentPath) { mutableStateOf(currentPath) }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_rename_folder)) },
        text = {
            Column {
                Text("${stringResource(R.string.memory_current_path)}: $currentPath", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.memory_new_path)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (newName.isNotBlank() && newName != currentPath) onRename(newName.trim()) },
                enabled = newName.isNotBlank() && newName != currentPath
            ) {
                Text(stringResource(R.string.memory_rename_folder))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.memory_cancel))
            }
        }
    )
}

/**
 * 删除文件夹确认对话框
 */
@Composable
private fun FolderDeleteDialog(
    folderPath: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_confirm_delete_folder)) },
        text = {
            Column {
                Text(stringResource(R.string.memory_confirm_delete_folder_message), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${stringResource(R.string.memory_folder_label)}: $folderPath", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.memory_delete_folder_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.memory_confirm_delete_action), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.memory_cancel))
            }
        }
    )
}
