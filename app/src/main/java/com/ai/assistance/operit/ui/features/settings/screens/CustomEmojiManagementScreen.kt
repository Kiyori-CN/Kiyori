package com.ai.assistance.operit.ui.features.settings.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CustomEmoji
import com.ai.assistance.operit.data.preferences.CustomEmojiPreferences
import com.ai.assistance.operit.ui.features.settings.viewmodels.CustomEmojiViewModel
import com.ai.assistance.operit.ui.main.shell.KIYORI_SETTINGS_FIELD_CORNER_RADIUS_DP
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsWorkspacePage
import com.ai.assistance.operit.ui.main.shell.kiyoriSettingsOutlinedTextFieldColors
import com.kiyori.design.theme.LocalKiyoriSettingsColors
import kotlinx.coroutines.launch

/**
 * 自定义表情管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomEmojiManagementScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsColors = LocalKiyoriSettingsColors.current
    val viewModel = remember { CustomEmojiViewModel(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val emojis by viewModel.emojisInCategory.collectAsState()
    val activeTargetName by viewModel.activeTargetName.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteEmojiDialog by remember { mutableStateOf<CustomEmoji?>(null) }
    var showImagePreview by remember { mutableStateOf<Uri?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    val canDeleteSelectedCategory = viewModel.isCustomCategory(selectedCategory)

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        // 系统文档选择器可浏览 OEM 相册界面隐藏的目录；仍只读取用户选中的 URI。
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addEmojis(selectedCategory, uris)
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSuccessMessage()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearErrorMessage()
        }
    }

    KiyoriSettingsWorkspacePage(
        title = stringResource(R.string.manage_custom_emoji),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { imagePickerLauncher.launch(arrayOf("image/*")) }
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_emoji))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 类别选择器
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = settingsColors.cardBackground
                ),
                border = BorderStroke(1.dp, settingsColors.divider),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.custom_emoji_bound_to_target),
                            style = MaterialTheme.typography.labelMedium,
                            color = settingsColors.secondaryText
                        )
                        Text(
                            text = activeTargetName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = settingsColors.primaryText
                        )
                    }
                }
            }

            CategorySelector(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelected = { viewModel.selectCategory(it) },
                modifier = Modifier.padding(16.dp)
            )
            
            // 分组管理和重置按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：分组管理按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 创建分组按钮
                    OutlinedButton(
                        onClick = { showCreateCategoryDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.create_group),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.create_group), fontSize = 12.sp)
                    }

                    // 删除分组按钮
                    OutlinedButton(
                        onClick = { showDeleteCategoryDialog = true },
                        enabled = canDeleteSelectedCategory,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete_group),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.delete_group), fontSize = 12.sp)
                    }
                }

                // 右侧：重置按钮
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = stringResource(R.string.reset_to_default),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.reset_to_default), fontSize = 12.sp)
                }
            }

            // 提示信息
            if (emojis.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.tap_button_add_emoji),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 表情网格
                EmojiGrid(
                    emojis = emojis,
                    onEmojiClick = { emoji ->
                        showImagePreview = viewModel.getEmojiUri(emoji)
                    },
                    onEmojiLongClick = { emoji ->
                        showDeleteEmojiDialog = emoji
                    },
                    getEmojiUri = { viewModel.getEmojiUri(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 加载指示器
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // 新建类别对话框
    if (showCreateCategoryDialog) {
        CreateCategoryDialog(
            onDismiss = { showCreateCategoryDialog = false },
            onCreate = { categoryName ->
                scope.launch {
                    if (viewModel.createCategory(categoryName)) {
                        showCreateCategoryDialog = false
                    }
                }
            }
        )
    }

    // 删除类别确认对话框
    if (showDeleteCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCategoryDialog = false },
            title = { Text(stringResource(R.string.delete_category)) },
            text = { Text(stringResource(R.string.confirm_delete_category, selectedCategory)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (canDeleteSelectedCategory) {
                            viewModel.deleteCategory(selectedCategory)
                            showDeleteCategoryDialog = false
                        }
                    },
                    enabled = canDeleteSelectedCategory,
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCategoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 删除表情确认对话框
    showDeleteEmojiDialog?.let { emoji ->
        AlertDialog(
            onDismissRequest = { showDeleteEmojiDialog = null },
            title = { Text(stringResource(R.string.delete_emoji)) },
            text = { Text(stringResource(R.string.confirm_delete_emoji)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEmoji(emoji.id)
                        showDeleteEmojiDialog = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteEmojiDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 重置确认对话框
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_to_default_emoji)) },
            text = { Text(stringResource(R.string.reset_emoji_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetToDefault()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm_reset_emoji))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 图片预览对话框
    showImagePreview?.let { uri ->
        Dialog(onDismissRequest = { showImagePreview = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(R.string.emoji_preview),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

}

/**
 * 类别选择器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCategory,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.select_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = kiyoriSettingsOutlinedTextFieldColors(),
            shape = RoundedCornerShape(KIYORI_SETTINGS_FIELD_CORNER_RADIUS_DP.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(category)
                            if (category !in CustomEmojiPreferences.BUILTIN_EMOTIONS) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = stringResource(R.string.custom),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 表情网格
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiGrid(
    emojis: List<CustomEmoji>,
    onEmojiClick: (CustomEmoji) -> Unit,
    onEmojiLongClick: (CustomEmoji) -> Unit,
    getEmojiUri: (CustomEmoji) -> Uri,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(emojis, key = { it.id }) { emoji ->
            EmojiCard(
                emoji = emoji,
                onClick = { onEmojiClick(emoji) },
                onLongClick = { onEmojiLongClick(emoji) },
                uri = getEmojiUri(emoji),
                showDeleteIcon = true
            )
        }
    }
}

/**
 * 表情卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCard(
    emoji: CustomEmoji,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    uri: Uri,
    showDeleteIcon: Boolean
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = uri,
                contentDescription = emoji.emotionCategory,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (showDeleteIcon) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 新建类别对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_category)) },
        text = {
            Column {
                Text(stringResource(R.string.category_name_hint))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = {
                        categoryName = it.lowercase()
                        isError = !it.matches(Regex("^[a-z0-9_]*$"))
                    },
                    label = { Text(stringResource(R.string.category_name)) },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(stringResource(R.string.invalid_category_name))
                        }
                    },
                    singleLine = true,
                    colors = kiyoriSettingsOutlinedTextFieldColors(),
                    shape = RoundedCornerShape(KIYORI_SETTINGS_FIELD_CORNER_RADIUS_DP.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(categoryName) },
                enabled = categoryName.isNotBlank() && !isError
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
