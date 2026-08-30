package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

@Composable
fun FileManagerTopBar(
    currentPath: String,
    folderCount: Int,
    fileCount: Int,
    storageLabel: String,
    isSearching: Boolean,
    onExitFileManager: () -> Unit,
    onPathClick: () -> Unit,
    onOpenStorageDrawer: () -> Unit,
    onRefresh: () -> Unit,
    onShowSearchDialog: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleHiddenFiles: () -> Unit,
    onSelectSort: () -> Unit,
    onOpenLinux: () -> Unit,
    onNewFolder: () -> Unit,
    onExitSearch: () -> Unit,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    val chromeColor = MaterialTheme.colorScheme.inverseSurface
    val chromeContentColor = MaterialTheme.colorScheme.inverseOnSurface
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = chromeColor,
        contentColor = chromeContentColor,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onExitFileManager,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "退出文件管理器",
                        tint = chromeContentColor,
                    )
                }
                IconButton(
                    onClick = onOpenStorageDrawer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "打开存储位置",
                        tint = chromeContentColor,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onPathClick)
                        .padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = currentPath,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = chromeContentColor,
                    )
                    Text(
                        text = "文件夹: $folderCount  文件: $fileCount  $storageLabel",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = chromeContentColor.copy(alpha = 0.76f),
                    )
                }
                IconButton(
                    onClick = { overflowExpanded = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多文件管理操作",
                        tint = chromeContentColor,
                    )
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                ) {
                    FileManagerMenuItem(Icons.Default.Refresh, stringResource(R.string.refresh)) {
                        overflowExpanded = false
                        onRefresh()
                    }
                    FileManagerMenuItem(Icons.Default.Search, stringResource(R.string.search)) {
                        overflowExpanded = false
                        onShowSearchDialog()
                    }
                    FileManagerMenuItem(Icons.Default.SelectAll, "全选") {
                        overflowExpanded = false
                        onSelectAll()
                    }
                    FileManagerMenuItem(Icons.Default.VisibilityOff, stringResource(R.string.file_manager_show_hidden)) {
                        overflowExpanded = false
                        onToggleHiddenFiles()
                    }
                    FileManagerMenuItem(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.file_manager_sort)) {
                        overflowExpanded = false
                        onSelectSort()
                    }
                    FileManagerMenuItem(Icons.Default.Terminal, "Linux") {
                        overflowExpanded = false
                        onOpenLinux()
                    }
                    FileManagerMenuItem(Icons.Default.CreateNewFolder, stringResource(R.string.file_manager_new)) {
                        overflowExpanded = false
                        onNewFolder()
                    }
                }
            }
            if (isSearching) {
                HorizontalDivider(color = chromeContentColor.copy(alpha = 0.16f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = chromeContentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.searching),
                        style = MaterialTheme.typography.bodyMedium,
                        color = chromeContentColor,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onExitSearch, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = chromeContentColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileManagerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
fun FileManagerBottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNew: () -> Unit,
    onMirrorPath: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val bottomBarIconColor = Color.Black
    val disabledBottomBarIconColor = Color(0xFFBDBDBD)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        contentColor = Color.Black,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "活动窗格后退",
                    tint = if (canGoBack) bottomBarIconColor else disabledBottomBarIconColor,
                )
            }
            IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "活动窗格前进",
                    tint = if (canGoForward) bottomBarIconColor else disabledBottomBarIconColor,
                )
            }
            IconButton(onClick = onNew, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Add, contentDescription = "新建", tint = bottomBarIconColor)
            }
            IconButton(onClick = onMirrorPath, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = "同步活动路径到另一栏",
                    tint = bottomBarIconColor,
                )
            }
            IconButton(onClick = onNavigateUp, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = stringResource(R.string.file_manager_navigate_up),
                    tint = bottomBarIconColor,
                )
            }
        }
    }
}

data class FileManagerStorageEntry(
    val title: String,
    val path: String,
    val environment: String? = null,
    val subtitle: String? = null,
    val bookmarkUri: String? = null,
)

@Composable
fun FileManagerStorageDrawer(
    entries: List<FileManagerStorageEntry>,
    onSelect: (FileManagerStorageEntry) -> Unit,
    onAddBookmark: () -> Unit,
    onDeleteBookmark: (FileManagerStorageEntry) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.86f),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.statusBarsPadding().navigationBarsPadding().padding(vertical = 16.dp),
        ) {
            Text(
                text = "存储位置",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(entry.title)
                            entry.subtitle?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    trailingIcon = if (entry.bookmarkUri != null) {
                        {
                            IconButton(onClick = { onDeleteBookmark(entry) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除 ${entry.title}",
                                )
                            }
                        }
                    } else {
                        null
                    },
                    onClick = { onSelect(entry) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DropdownMenuItem(
                text = { Text("添加本地存储") },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                onClick = onAddBookmark,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

fun defaultFileManagerStorageEntries(workspacePath: String): List<FileManagerStorageEntry> =
    listOf(
        FileManagerStorageEntry(
            title = "手机存储",
            path = Environment.getExternalStorageDirectory().absolutePath,
            subtitle = Environment.getExternalStorageDirectory().absolutePath,
        ),
        FileManagerStorageEntry("Linux", "/", environment = "linux", subtitle = "Linux 文件系统"),
        FileManagerStorageEntry("根目录", "/", subtitle = "设备文件系统"),
        FileManagerStorageEntry("工作区", workspacePath, subtitle = workspacePath),
    )
