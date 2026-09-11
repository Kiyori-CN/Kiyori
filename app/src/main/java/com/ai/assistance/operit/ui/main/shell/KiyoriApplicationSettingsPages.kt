package com.ai.assistance.operit.ui.main.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.features.github.GitHubLoginWebViewDialog
import com.ai.assistance.operit.ui.main.components.LocalKiyoriEmbeddedSettingsNavigation
import com.kiyori.design.theme.KiyoriSemanticTone
import kotlinx.coroutines.launch

internal enum class KiyoriAiAssistantSettingsAction {
    OPEN_MODEL_CONFIG,
    OPEN_FUNCTIONAL_CONFIG,
    OPEN_MODEL_PROMPTS,
    OPEN_USER_PREFERENCES,
    OPEN_AVATAR_SETTINGS,
    OPEN_WAIFU_MODE,
    OPEN_TEXT_TO_SPEECH_SETTINGS,
    OPEN_SPEECH_TO_TEXT_SETTINGS,
    OPEN_VOICE_WAKEUP_SETTINGS,
    OPEN_CONTEXT_SUMMARY,
    OPEN_TOOL_PERMISSIONS,
    OPEN_TOKEN_USAGE,
    OPEN_EXTERNAL_HTTP_CHAT,
    OPEN_ARTIFACT_STORAGE,
}

internal data class KiyoriAiAssistantSettingsEntrySpec(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val iconTone: KiyoriSemanticTone,
    val action: KiyoriAiAssistantSettingsAction,
)

internal data class KiyoriAiAssistantSettingsGroupSpec(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val entries: List<KiyoriAiAssistantSettingsEntrySpec>,
)

internal data class KiyoriNavigationSettingsEntrySpec<T>(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTone: KiyoriSemanticTone,
    val action: T,
)

internal data class KiyoriNavigationSettingsGroupSpec<T>(
    val title: String,
    val description: String,
    val entries: List<KiyoriNavigationSettingsEntrySpec<T>>,
)

internal val kiyoriAiAssistantSettingsGroups =
    listOf(
        KiyoriAiAssistantSettingsGroupSpec(
            titleRes = R.string.kiyori_ai_settings_group_model_generation,
            descriptionRes = R.string.kiyori_ai_settings_group_model_generation_desc,
            entries =
                listOf(
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_model_api,
                        descriptionRes = R.string.kiyori_ai_settings_model_api_desc,
                        icon = Icons.Outlined.Settings,
                        iconTone = KiyoriSemanticTone.BLUE,
                        action = KiyoriAiAssistantSettingsAction.OPEN_MODEL_CONFIG,
                    ),
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_function_models,
                        descriptionRes = R.string.kiyori_ai_settings_function_models_desc,
                        icon = Icons.Outlined.Tune,
                        iconTone = KiyoriSemanticTone.CYAN,
                        action = KiyoriAiAssistantSettingsAction.OPEN_FUNCTIONAL_CONFIG,
                    ),
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_prompts_roles,
                        descriptionRes = R.string.kiyori_ai_settings_prompts_roles_desc,
                        icon = Icons.AutoMirrored.Filled.Chat,
                        iconTone = KiyoriSemanticTone.PURPLE,
                        action = KiyoriAiAssistantSettingsAction.OPEN_MODEL_PROMPTS,
                    ),
                ),
        ),
        KiyoriAiAssistantSettingsGroupSpec(
            titleRes = R.string.kiyori_ai_settings_group_personalization,
            descriptionRes = R.string.kiyori_ai_settings_group_personalization_desc,
            entries =
                listOf(
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_user_profile,
                        descriptionRes = R.string.kiyori_ai_settings_user_profile_desc,
                        icon = Icons.Default.Person,
                        iconTone = KiyoriSemanticTone.GREEN,
                        action = KiyoriAiAssistantSettingsAction.OPEN_USER_PREFERENCES,
                    ),
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_avatar,
                        descriptionRes = R.string.kiyori_ai_settings_avatar_desc,
                        icon = Icons.Default.Face,
                        iconTone = KiyoriSemanticTone.PINK,
                        action = KiyoriAiAssistantSettingsAction.OPEN_AVATAR_SETTINGS,
                    ),
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_reply_expression,
                        descriptionRes = R.string.kiyori_ai_settings_reply_expression_desc,
                        icon = Icons.Default.Forum,
                        iconTone = KiyoriSemanticTone.CYAN,
                        action = KiyoriAiAssistantSettingsAction.OPEN_WAIFU_MODE,
                    ),
                ),
        ),
        KiyoriAiAssistantSettingsGroupSpec(
            titleRes = R.string.kiyori_ai_settings_group_voice,
            descriptionRes = R.string.kiyori_ai_settings_group_voice_desc,
            entries =
                listOf(
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_tts,
                        descriptionRes = R.string.kiyori_ai_settings_tts_desc,
                        icon = Icons.Default.RecordVoiceOver,
                        iconTone = KiyoriSemanticTone.ORANGE,
                        action = KiyoriAiAssistantSettingsAction.OPEN_TEXT_TO_SPEECH_SETTINGS,
                    ),
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_stt,
                        descriptionRes = R.string.kiyori_ai_settings_stt_desc,
                        icon = Icons.Default.Mic,
                        iconTone = KiyoriSemanticTone.CYAN,
                        action = KiyoriAiAssistantSettingsAction.OPEN_SPEECH_TO_TEXT_SETTINGS,
                    ),
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_voice_wakeup,
                        descriptionRes = R.string.kiyori_ai_settings_voice_wakeup_desc,
                        icon = Icons.Default.Mic,
                        iconTone = KiyoriSemanticTone.PURPLE,
                        action = KiyoriAiAssistantSettingsAction.OPEN_VOICE_WAKEUP_SETTINGS,
                    ),
                ),
        ),
        KiyoriAiAssistantSettingsGroupSpec(
            titleRes = R.string.kiyori_ai_settings_group_context_tools,
            descriptionRes = R.string.kiyori_ai_settings_group_context_tools_desc,
            entries =
                listOf(
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_context_summary,
                        descriptionRes = R.string.kiyori_ai_settings_context_summary_desc,
                        icon = Icons.Default.History,
                        iconTone = KiyoriSemanticTone.ORANGE,
                        action = KiyoriAiAssistantSettingsAction.OPEN_CONTEXT_SUMMARY,
                    ),
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_tool_permissions,
                        descriptionRes = R.string.kiyori_ai_settings_tool_permissions_desc,
                        icon = Icons.Default.Security,
                        iconTone = KiyoriSemanticTone.RED,
                        action = KiyoriAiAssistantSettingsAction.OPEN_TOOL_PERMISSIONS,
                    ),
                ),
        ),
        KiyoriAiAssistantSettingsGroupSpec(
            titleRes = R.string.kiyori_ai_settings_group_service_usage,
            descriptionRes = R.string.kiyori_ai_settings_group_service_usage_desc,
            entries =
                listOf(
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_usage_cost,
                        descriptionRes = R.string.kiyori_ai_settings_usage_cost_desc,
                        icon = Icons.Default.BarChart,
                        iconTone = KiyoriSemanticTone.BLUE,
                        action = KiyoriAiAssistantSettingsAction.OPEN_TOKEN_USAGE,
                    ),
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_lan_automation,
                        descriptionRes = R.string.kiyori_ai_settings_lan_automation_desc,
                        icon = Icons.Default.Cloud,
                        iconTone = KiyoriSemanticTone.CYAN,
                        action = KiyoriAiAssistantSettingsAction.OPEN_EXTERNAL_HTTP_CHAT,
                    ),
                    KiyoriAiAssistantSettingsEntrySpec(
                        titleRes = R.string.kiyori_ai_settings_artifact_storage,
                        descriptionRes = R.string.kiyori_ai_settings_artifact_storage_desc,
                        icon = Icons.Default.Dashboard,
                        iconTone = KiyoriSemanticTone.GREEN,
                        action = KiyoriAiAssistantSettingsAction.OPEN_ARTIFACT_STORAGE,
                    ),
                ),
        ),
    )

internal enum class KiyoriAppearanceSettingsAction {
    OPEN_LANGUAGE,
    OPEN_THEME,
    OPEN_GLOBAL_DISPLAY,
    OPEN_LAYOUT_ADJUSTMENT,
}

internal val kiyoriAppearanceSettingsGroups =
    listOf(
        KiyoriNavigationSettingsGroupSpec(
            title = "界面与语言",
            description = "调整 Kiyori 的界面语言、主题、全局显示和 AI 对话布局",
            entries =
                listOf(
                    KiyoriNavigationSettingsEntrySpec(
                        title = "语言",
                        description = "切换应用界面使用的显示语言",
                        icon = Icons.Default.Language,
                        iconTone = KiyoriSemanticTone.BLUE,
                        action = KiyoriAppearanceSettingsAction.OPEN_LANGUAGE,
                    ),
                    KiyoriNavigationSettingsEntrySpec(
                        title = "主题与外观",
                        description = "配置固定亮暗主题，并个性化 AI 对话背景、消息、头像和输入区",
                        icon = Icons.Default.Palette,
                        iconTone = KiyoriSemanticTone.PINK,
                        action = KiyoriAppearanceSettingsAction.OPEN_THEME,
                    ),
                    KiyoriNavigationSettingsEntrySpec(
                        title = "全局显示",
                        description = "管理独立于角色卡和主题的全局显示行为",
                        icon = Icons.Default.Visibility,
                        iconTone = KiyoriSemanticTone.GREEN,
                        action = KiyoriAppearanceSettingsAction.OPEN_GLOBAL_DISPLAY,
                    ),
                    KiyoriNavigationSettingsEntrySpec(
                        title = "布局调整",
                        description = "调整 AI 对话输入区、设置栏和内容布局",
                        icon = Icons.Default.Dashboard,
                        iconTone = KiyoriSemanticTone.ORANGE,
                        action = KiyoriAppearanceSettingsAction.OPEN_LAYOUT_ADJUSTMENT,
                    ),
                ),
        ),
    )

internal enum class KiyoriDataSettingsAction {
    OPEN_BACKUP,
    OPEN_CHAT_HISTORY,
}

internal const val KIYORI_ACCOUNT_SETTINGS_PAGE_TITLE = "我的账号"
internal const val KIYORI_DATA_SETTINGS_PAGE_TITLE = "数据备份"

internal val kiyoriDataSettingsGroups =
    listOf(
        KiyoriNavigationSettingsGroupSpec(
            title = "聊天与记忆数据",
            description = "备份、恢复和整理 AI 对话、记忆库及相关数据",
            entries =
                listOf(
                    KiyoriNavigationSettingsEntrySpec(
                        title = "数据备份与恢复",
                        description = "导入、导出或删除聊天记录和记忆库数据",
                        icon = Icons.Default.Backup,
                        iconTone = KiyoriSemanticTone.GREEN,
                        action = KiyoriDataSettingsAction.OPEN_BACKUP,
                    ),
                    KiyoriNavigationSettingsEntrySpec(
                        title = "聊天历史管理",
                        description = "查看统计并批量整理、导入或导出聊天记录",
                        icon = Icons.Default.History,
                        iconTone = KiyoriSemanticTone.BLUE,
                        action = KiyoriDataSettingsAction.OPEN_CHAT_HISTORY,
                    ),
                ),
        ),
    )

@Composable
internal fun KiyoriAiAssistantSettingsPage(
    onAction: (KiyoriAiAssistantSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigation = LocalKiyoriEmbeddedSettingsNavigation.current
    KiyoriCollapsingSettingsPage(
        title = stringResource(R.string.kiyori_ai_settings_title),
        onBack = navigation.onClick,
        navigationIcon = navigation.icon,
        modifier = modifier,
    ) {
        items(
            items = kiyoriAiAssistantSettingsGroups,
            key = KiyoriAiAssistantSettingsGroupSpec::titleRes,
        ) { group ->
            KiyoriSettingsGroupSection(
                title = stringResource(group.titleRes),
                description = stringResource(group.descriptionRes),
            ) {
                group.entries.forEachIndexed { index, entry ->
                    KiyoriSettingsRow(
                        title = stringResource(entry.titleRes),
                        description = stringResource(entry.descriptionRes),
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = entry.icon,
                        iconTone = entry.iconTone,
                        onClick = { onAction(entry.action) },
                    )
                    if (index != group.entries.lastIndex) {
                        KiyoriSettingsDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun KiyoriAppearanceSettingsPage(
    onAction: (KiyoriAppearanceSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    KiyoriNavigationSettingsPage(
        title = "界面定制",
        groups = kiyoriAppearanceSettingsGroups,
        onAction = onAction,
        modifier = modifier,
    )
}

@Composable
internal fun KiyoriDataSettingsPage(
    onAction: (KiyoriDataSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    KiyoriNavigationSettingsPage(
        title = KIYORI_DATA_SETTINGS_PAGE_TITLE,
        groups = kiyoriDataSettingsGroups,
        onAction = onAction,
        modifier = modifier,
    )
}

@Composable
internal fun KiyoriAccountConnectionsSettingsPage(
    modifier: Modifier = Modifier,
) {
    val navigation = LocalKiyoriEmbeddedSettingsNavigation.current
    val context = LocalContext.current
    val githubAuth = remember(context) { GitHubAuthPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()
    val isLoggedIn by githubAuth.isLoggedInFlow.collectAsState(initial = false)
    val userInfo by githubAuth.userInfoFlow.collectAsState(initial = null)
    var showGitHubLogin by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val githubUser = userInfo
    val githubAccountLabel =
        when {
            !isLoggedIn -> "未登录"
            githubUser == null -> "读取中"
            else -> "@${githubUser.login}"
        }

    KiyoriCollapsingSettingsPage(
        title = KIYORI_ACCOUNT_SETTINGS_PAGE_TITLE,
        onBack = navigation.onClick,
        navigationIcon = navigation.icon,
        modifier = modifier,
    ) {
        item(key = "github_account") {
            KiyoriSettingsGroupSection(
                title = "GitHub 账号",
                description = "连接 GitHub 后可使用市场发布、管理和其他需要身份的功能",
            ) {
                KiyoriSettingsRow(
                    title = if (isLoggedIn) "GitHub 已连接" else "登录 GitHub",
                    description =
                        if (isLoggedIn) {
                            "当前账号 $githubAccountLabel，点击可退出登录"
                        } else {
                            "使用 GitHub 登录以启用需要账号身份的服务"
                        },
                    kind = KiyoriSettingsRowKind.NAVIGATION,
                    icon = Icons.Default.AccountCircle,
                    iconTone = KiyoriSemanticTone.GREEN,
                    value = githubAccountLabel,
                    onClick = {
                        if (isLoggedIn) {
                            showLogoutConfirm = true
                        } else {
                            showGitHubLogin = true
                        }
                    },
                )
            }
        }
    }

    if (showGitHubLogin) {
        GitHubLoginWebViewDialog(onDismissRequest = { showGitHubLogin = false })
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出 GitHub") },
            text = { Text("确定要断开当前 GitHub 账号连接吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        scope.launch { githubAuth.logout() }
                    },
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun <T> KiyoriNavigationSettingsPage(
    title: String,
    groups: List<KiyoriNavigationSettingsGroupSpec<T>>,
    onAction: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigation = LocalKiyoriEmbeddedSettingsNavigation.current
    KiyoriCollapsingSettingsPage(
        title = title,
        onBack = navigation.onClick,
        navigationIcon = navigation.icon,
        modifier = modifier,
    ) {
        items(groups, key = KiyoriNavigationSettingsGroupSpec<T>::title) { group ->
            KiyoriSettingsGroupSection(
                title = group.title,
                description = group.description,
            ) {
                group.entries.forEachIndexed { index, entry ->
                    KiyoriSettingsRow(
                        title = entry.title,
                        description = entry.description,
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = entry.icon,
                        iconTone = entry.iconTone,
                        onClick = { onAction(entry.action) },
                    )
                    if (index != group.entries.lastIndex) {
                        KiyoriSettingsDivider()
                    }
                }
            }
        }
    }
}
