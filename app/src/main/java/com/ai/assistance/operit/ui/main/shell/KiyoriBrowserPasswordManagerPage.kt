package com.ai.assistance.operit.ui.main.shell

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy

import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserCredentialVaultSnapshot
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserSavedCredential
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserSavedCredentialSummary
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSettings
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.design.theme.LocalKiyoriSettingsColors
import com.kiyori.design.theme.KiyoriUiShapes
import java.util.Locale
import kotlinx.coroutines.launch

private const val BROWSER_PASSWORD_MANAGER_TAG = "BrowserPasswordManager"

@Composable
internal fun KiyoriBrowserPasswordManagerPage(
    settings: WebSessionBrowserSettings,
    vaultState: BrowserCredentialVaultSnapshot,
    onBack: () -> Unit,
    onSetPasswordSavingEnabled: (Boolean) -> Unit,
    onLoadCredential: suspend (String) -> BrowserSavedCredential?,
    onUpdateCredential: suspend (String, String, String) -> Unit,
    onDeleteCredential: suspend (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCredentialId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCredential by remember { mutableStateOf<BrowserSavedCredential?>(null) }
    var editCredential by remember { mutableStateOf<BrowserSavedCredential?>(null) }
    var deleteCredential by remember { mutableStateOf<BrowserSavedCredential?>(null) }
    val filteredCredentials =
        remember(vaultState.credentials, searchQuery) {
            filterBrowserCredentialSummaries(vaultState.credentials, searchQuery)
        }

    LaunchedEffect(selectedCredentialId, vaultState.credentials) {
        val credentialId = selectedCredentialId
        if (credentialId == null) {
            selectedCredential = null
            return@LaunchedEffect
        }
        try {
            selectedCredential = onLoadCredential(credentialId)
            if (selectedCredential == null) {
                selectedCredentialId = null
            }
        } catch (error: Exception) {
            AppLogger.e(
                BROWSER_PASSWORD_MANAGER_TAG,
                "Unable to reveal saved browser credential",
                error,
            )
            selectedCredentialId = null
            Toast.makeText(context, "网站密码读取失败", Toast.LENGTH_SHORT).show()
        }
    }

    KiyoriCollapsingSettingsPage(
        title = "网站密码管理",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            KiyoriSettingsGroupSection(
                title = "保存与填充",
                description = "凭据仅保存在本机普通窗口；无痕窗口不会读取、保存或自动填充",
            ) {
                KiyoriSettingsRow(
                    title = "自动保存网站密码",
                    description = "提交包含一个密码字段的登录表单时，安全保存或更新该网站账号",
                    kind = KiyoriSettingsRowKind.TOGGLE,
                    checked = settings.websitePasswordSavingEnabled,
                    enabled =
                        vaultState.isAvailable ||
                            settings.websitePasswordSavingEnabled,
                    onClick = {
                        onSetPasswordSavingEnabled(
                            !settings.websitePasswordSavingEnabled,
                        )
                    },
                )
                KiyoriSettingsDivider()
                BrowserPasswordSecurityNotice(
                    isLoading = vaultState.isLoading,
                    isAvailable = vaultState.isAvailable,
                    errorMessage = vaultState.errorMessage,
                )
            }
        }
        item {
            KiyoriSettingsGroupSection(
                title = "已保存的网站",
                description =
                    when {
                        vaultState.isLoading -> "正在解锁本机密码保险库"
                        vaultState.isAvailable ->
                            "共 ${vaultState.credentials.size} 项；按网站或账号搜索，点击条目查看详情"
                        else -> "密码保险库当前不可用，已保存内容不会以明文方式打开"
                    },
            ) {
                when {
                    vaultState.isLoading -> {
                        BrowserCredentialLoadingState()
                    }
                    vaultState.isAvailable -> {
                        BrowserCredentialSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                        )
                        KiyoriSettingsDivider()
                        if (filteredCredentials.isEmpty()) {
                            BrowserCredentialEmptyState(hasQuery = searchQuery.isNotBlank())
                        } else {
                            filteredCredentials.forEachIndexed { index, credential ->
                                KiyoriSettingsRow(
                                    title = credential.host,
                                    description = credential.username,
                                    kind = KiyoriSettingsRowKind.NAVIGATION,
                                    value = "查看",
                                    onClick = { selectedCredentialId = credential.id },
                                )
                                if (index != filteredCredentials.lastIndex) {
                                    KiyoriSettingsDivider()
                                }
                            }
                        }
                    }
                    else -> {
                        BrowserCredentialUnavailableState(
                            message =
                                requireNotNull(vaultState.errorMessage) {
                                    "Unavailable browser credential vault must expose an error message"
                                },
                        )
                    }
                }
            }
        }
    }

    selectedCredential?.let { credential ->
        BrowserCredentialDetailDialog(
            credential = credential,
            onDismiss = { selectedCredentialId = null },
            onCopyUsername = {
                context.copySensitiveBrowserCredential(
                    label = "网站账号",
                    text = credential.username,
                )
                Toast.makeText(context, "账号已复制", Toast.LENGTH_SHORT).show()
            },
            onCopyPassword = {
                context.copySensitiveBrowserCredential(
                    label = "网站密码",
                    text = credential.password,
                )
                Toast.makeText(context, "密码已复制", Toast.LENGTH_SHORT).show()
            },
            onEdit = {
                editCredential = credential
                selectedCredentialId = null
            },
            onDelete = {
                deleteCredential = credential
                selectedCredentialId = null
            },
        )
    }

    editCredential?.let { credential ->
        BrowserCredentialEditDialog(
            credential = credential,
            onDismiss = { editCredential = null },
            onSave = { username, password ->
                scope.launch {
                    try {
                        onUpdateCredential(credential.id, username, password)
                        editCredential = null
                        Toast.makeText(context, "网站密码已更新", Toast.LENGTH_SHORT).show()
                    } catch (error: Exception) {
                        AppLogger.e(
                            BROWSER_PASSWORD_MANAGER_TAG,
                            "Unable to update saved browser credential",
                            error,
                        )
                        Toast.makeText(
                            context,
                            "网站密码更新失败",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }

    deleteCredential?.let { credential ->
        AlertDialog(
            onDismissRequest = { deleteCredential = null },
            title = { Text("删除网站密码？") },
            text = {
                Text(
                    "将删除 ${credential.origin} 中账号 ${credential.username} 的保存记录。该操作不会退出网站登录状态。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val deleted = onDeleteCredential(credential.id)
                                deleteCredential = null
                                Toast.makeText(
                                    context,
                                    if (deleted) "网站密码已删除" else "该记录已不存在",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } catch (error: Exception) {
                                AppLogger.e(
                                    BROWSER_PASSWORD_MANAGER_TAG,
                                    "Unable to delete saved browser credential",
                                    error,
                                )
                                Toast.makeText(
                                    context,
                                    "网站密码删除失败",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text(
                        text = "删除",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCredential = null }) {
                    Text("取消")
                }
            },
            shape = KiyoriUiShapes.dialog,
        )
    }
}

internal fun filterBrowserCredentialSummaries(
    credentials: List<BrowserSavedCredentialSummary>,
    query: String,
): List<BrowserSavedCredentialSummary> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isEmpty()) {
        return credentials
    }
    return credentials.filter { credential ->
        credential.host.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            credential.origin.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            credential.pageUrl.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            credential.username.lowercase(Locale.ROOT).contains(normalizedQuery)
    }
}

@Composable
private fun BrowserPasswordSecurityNotice(
    isLoading: Boolean,
    isAvailable: Boolean,
    errorMessage: String?,
) {
    val colors = LocalKiyoriSettingsColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp)) {
        Text(
            text =
                when {
                    isLoading -> "正在解锁"
                    isAvailable -> "本机加密"
                    else -> "保险库不可用"
                },
            color =
                if (isLoading || isAvailable) {
                    colors.primaryText
                } else {
                    MaterialTheme.colorScheme.error
                },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text =
                when {
                    isLoading -> "正在从 Android Keystore 解锁本机凭据，请稍候。"
                    isAvailable ->
                        "账号与密码使用 Android Keystore 加密，不进入当前备份；卸载应用会同时删除密钥和凭据。"
                    else ->
                        requireNotNull(errorMessage) {
                            "Unavailable browser credential vault must expose an error message"
                        }
                },
            color = colors.secondaryText,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun BrowserCredentialLoadingState() {
    val colors = LocalKiyoriSettingsColors.current
    Text(
        text = "正在解锁本机凭据…",
        color = colors.secondaryText,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
    )
}

@Composable
private fun BrowserCredentialSearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        label = { Text("搜索网站或账号") },
        colors = kiyoriSettingsOutlinedTextFieldColors(),
    )
}

@Composable
private fun BrowserCredentialEmptyState(hasQuery: Boolean) {
    val colors = LocalKiyoriSettingsColors.current
    Text(
        text =
            if (hasQuery) {
                "没有匹配的网站或账号"
            } else {
                "暂无已保存的网站密码。开启自动保存后，在普通窗口提交登录表单即可建立记录。"
            },
        color = colors.secondaryText,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
    )
}

@Composable
private fun BrowserCredentialUnavailableState(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
    )
}

@Composable
private fun BrowserCredentialDetailDialog(
    credential: BrowserSavedCredential,
    onDismiss: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showPassword by remember(credential.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = credential.origin,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = credential.pageUrl,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BrowserCredentialValueCard(
                    label = "账号",
                    value = credential.username,
                    onCopy = onCopyUsername,
                )
                BrowserCredentialValueCard(
                    label = "密码",
                    value =
                        if (showPassword) {
                            credential.password
                        } else {
                            "•".repeat(credential.password.length.coerceIn(8, 16))
                        },
                    onCopy = onCopyPassword,
                    trailingAction = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector =
                                    if (showPassword) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                contentDescription =
                                    if (showPassword) "隐藏密码" else "显示密码",
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onEdit) {
                    Text("编辑")
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        shape = KiyoriUiShapes.dialog,
    )
}

@Composable
private fun BrowserCredentialValueCard(
    label: String,
    value: String,
    onCopy: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    RoundedCornerShape(14.dp),
                )
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        trailingAction?.invoke()
        IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "复制$label",
            )
        }
    }
}

@Composable
private fun BrowserCredentialEditDialog(
    credential: BrowserSavedCredential,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var username by remember(credential.id) { mutableStateOf(credential.username) }
    var password by remember(credential.id) { mutableStateOf(credential.password) }
    var showPassword by remember(credential.id) { mutableStateOf(false) }
    val canSave =
        username.trim().isNotEmpty() &&
            username.length <= 512 &&
            password.isNotEmpty() &&
            password.length <= 4_096

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "编辑网站账号",
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 280.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = credential.origin,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { value ->
                        if (value.length <= 512) {
                            username = value
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("账号") },
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { value ->
                        if (value.length <= 4_096) {
                            password = value
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("密码") },
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation =
                        if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector =
                                    if (showPassword) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                contentDescription =
                                    if (showPassword) "隐藏密码" else "显示密码",
                            )
                        }
                    },
                    keyboardOptions =
                        KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(username.trim(), password) },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
            shape = KiyoriUiShapes.dialog,
    )
}

private fun Context.copySensitiveBrowserCredential(
    label: String,
    text: String,
) {
    val clipboard =
        requireNotNull(getSystemService(ClipboardManager::class.java)) {
            "ClipboardManager system service is unavailable"
        }
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras =
            PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
    }
    clipboard.setPrimaryClip(clip)
}
