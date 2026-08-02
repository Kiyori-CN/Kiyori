package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
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
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.features.github.GitHubLoginWebViewDialog
import com.ai.assistance.operit.ui.main.components.LocalKiyoriEmbeddedSettingsNavigation
import com.kiyori.design.theme.KiyoriSemanticTone
import kotlinx.coroutines.launch

internal enum class KiyoriAiAssistantSettingsAction {
    OPEN_USER_PREFERENCES,
    OPEN_MODEL_CONFIG,
    OPEN_FUNCTIONAL_CONFIG,
    OPEN_MODEL_PROMPTS,
    OPEN_PERSONA_GENERATION,
    OPEN_WAIFU_MODE,
    OPEN_CONTEXT_SUMMARY,
    OPEN_TOOL_PERMISSIONS,
    OPEN_TOKEN_USAGE,
    OPEN_EXTERNAL_HTTP_CHAT,
}

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
        KiyoriNavigationSettingsGroupSpec(
            title = "模型与服务",
            description = "配置对话模型、API 连接和各项 AI 功能使用的专属模型",
            entries =
                listOf(
                    KiyoriNavigationSettingsEntrySpec(
                        title = "模型与 API",
                        description = "管理模型提供方、API、模型名称和生成参数",
                        icon = Icons.Default.Settings,
                        iconTone = KiyoriSemanticTone.BLUE,
                        action = KiyoriAiAssistantSettingsAction.OPEN_MODEL_CONFIG,
                    ),
                    KiyoriNavigationSettingsEntrySpec(
                        title = "功能模型",
                        description = "为对话、总结、记忆库等功能指定独立模型",
                        icon = Icons.Default.Tune,
                        iconTone = KiyoriSemanticTone.CYAN,
                        action = KiyoriAiAssistantSettingsAction.OPEN_FUNCTIONAL_CONFIG,
                    ),
                ),
        ),
        KiyoriNavigationSettingsGroupSpec(
            title = "对话与角色",
            description = "管理发送给 AI 的用户信息、系统提示词和角色表达方式",
            entries =
                listOf(
                    KiyoriNavigationSettingsEntrySpec(
                        title = "用户偏好",
                        description = "编辑会作为用户上下文发送给 AI 的 user.md",
                        icon = Icons.Default.Person,
                        iconTone = KiyoriSemanticTone.GREEN,
                        action = KiyoriAiAssistantSettingsAction.OPEN_USER_PREFERENCES,
                    ),
                    KiyoriNavigationSettingsEntrySpec(
                        title = "提示词",
                        description = "配置系统提示词、模型提示词和功能提示词模板",
                        icon = Icons.AutoMirrored.Filled.Chat,
                        iconTone = KiyoriSemanticTone.PURPLE,
                        action = KiyoriAiAssistantSettingsAction.OPEN_MODEL_PROMPTS,
                    ),
                    KiyoriNavigationSettingsEntrySpec(
                        title = "人设卡生成",
                        description = "使用现有模型和提示词生成可复用的人设卡",
                        icon = Icons.Default.Badge,
                        iconTone = KiyoriSemanticTone.PINK,
                        action = KiyoriAiAssistantSettingsAction.OPEN_PERSONA_GENERATION,
                    ),
                    KiyoriNavigationSettingsEntrySpec(
                        title = "分句回复",
                        description = "配置 AI 回复分句发送和角色化表达模式",
                        icon = Icons.Default.Forum,
                        iconTone = KiyoriSemanticTone.PURPLE,
                        action = KiyoriAiAssistantSettingsAction.OPEN_WAIFU_MODE,
                    ),
                ),
        ),
        KiyoriNavigationSettingsGroupSpec(
            title = "上下文与安全",
            description = "控制上下文总结策略和 AI 调用工具时的授权规则",
            entries =
                listOf(
                    KiyoriNavigationSettingsEntrySpec(
                        title = "上下文与总结",
                        description = "管理上下文长度、自动总结和历史媒体保留策略",
                        icon = Icons.Default.History,
                        iconTone = KiyoriSemanticTone.ORANGE,
                        action = KiyoriAiAssistantSettingsAction.OPEN_CONTEXT_SUMMARY,
                    ),
                    KiyoriNavigationSettingsEntrySpec(
                        title = "AI 工具授权",
                        description = "设置工具调用为允许、询问或禁止",
                        icon = Icons.Default.Security,
                        iconTone = KiyoriSemanticTone.RED,
                        action = KiyoriAiAssistantSettingsAction.OPEN_TOOL_PERMISSIONS,
                    ),
                ),
        ),
        KiyoriNavigationSettingsGroupSpec(
            title = "使用与连接",
            description = "查看 AI 使用成本并管理面向外部应用的对话接口",
            entries =
                listOf(
                    KiyoriNavigationSettingsEntrySpec(
                        title = "Token 使用统计",
                        description = "查看模型 Token 消耗、费用和自定义定价",
                        icon = Icons.Default.BarChart,
                        iconTone = KiyoriSemanticTone.BLUE,
                        action = KiyoriAiAssistantSettingsAction.OPEN_TOKEN_USAGE,
                    ),
                    KiyoriNavigationSettingsEntrySpec(
                        title = "外部 HTTP 对话",
                        description = "管理本地 HTTP 对话接口、端口和访问令牌",
                        icon = Icons.Default.Cloud,
                        iconTone = KiyoriSemanticTone.CYAN,
                        action = KiyoriAiAssistantSettingsAction.OPEN_EXTERNAL_HTTP_CHAT,
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
    KiyoriNavigationSettingsPage(
        title = "AI 助手",
        groups = kiyoriAiAssistantSettingsGroups,
        onAction = onAction,
        modifier = modifier,
    )
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
        title = "数据备份与同步",
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
        title = "账号与连接",
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
