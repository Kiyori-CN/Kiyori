package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriSettingsTheme
import com.kiyori.design.theme.LocalKiyoriSettingsColors
import com.kiyori.design.theme.resolveColors

internal data class KiyoriSettingsHomeEntry(
    val title: String,
    val icon: ImageVector,
    val iconTone: KiyoriSemanticTone,
    val action: KiyoriSettingsHomeAction = KiyoriSettingsHomeAction.NONE,
)

internal enum class KiyoriSettingsHomeAction {
    NONE,
    OPEN_ACCOUNT_CONNECTIONS,
    OPEN_AI_ASSISTANT,
    OPEN_SPEECH_SERVICES,
    OPEN_BROWSER_SETTINGS,
    OPEN_DOWNLOAD_SETTINGS,
    OPEN_PLAYER_SETTINGS,
    OPEN_APPEARANCE_SETTINGS,
    OPEN_DATA_SETTINGS,
}

internal val kiyoriSettingsHomeGroups =
    listOf(
        listOf(
            KiyoriSettingsHomeEntry(
                "账号与连接",
                Icons.Default.AccountCircle,
                KiyoriSemanticTone.GREEN,
                KiyoriSettingsHomeAction.OPEN_ACCOUNT_CONNECTIONS,
            ),
            KiyoriSettingsHomeEntry(
                "AI 助手",
                Icons.Outlined.SmartToy,
                KiyoriSemanticTone.PURPLE,
                KiyoriSettingsHomeAction.OPEN_AI_ASSISTANT,
            ),
            KiyoriSettingsHomeEntry(
                "语音服务",
                Icons.Default.RecordVoiceOver,
                KiyoriSemanticTone.CYAN,
                KiyoriSettingsHomeAction.OPEN_SPEECH_SERVICES,
            ),
            KiyoriSettingsHomeEntry(
                "小程序管理",
                Icons.Default.Apps,
                KiyoriSemanticTone.BLUE,
            ),
        ),
        listOf(
            KiyoriSettingsHomeEntry(
                "网页浏览器",
                Icons.Default.Language,
                KiyoriSemanticTone.BLUE,
                KiyoriSettingsHomeAction.OPEN_BROWSER_SETTINGS,
            ),
            KiyoriSettingsHomeEntry(
                "视频播放器",
                Icons.Default.PlayCircle,
                KiyoriSemanticTone.RED,
                KiyoriSettingsHomeAction.OPEN_PLAYER_SETTINGS,
            ),
            KiyoriSettingsHomeEntry(
                "音乐播放器",
                Icons.Default.Audiotrack,
                KiyoriSemanticTone.PURPLE,
            ),
            KiyoriSettingsHomeEntry(
                "小说阅读器",
                Icons.AutoMirrored.Filled.MenuBook,
                KiyoriSemanticTone.ORANGE,
            ),
        ),
        listOf(
            KiyoriSettingsHomeEntry(
                "文件下载器",
                Icons.Default.Download,
                KiyoriSemanticTone.RED,
                KiyoriSettingsHomeAction.OPEN_DOWNLOAD_SETTINGS,
            ),
            KiyoriSettingsHomeEntry(
                "文件管理器",
                Icons.Default.Folder,
                KiyoriSemanticTone.GREEN,
            ),
            KiyoriSettingsHomeEntry(
                "广告拦截器",
                Icons.Default.Block,
                KiyoriSemanticTone.RED,
            ),
            KiyoriSettingsHomeEntry(
                "日志记录器",
                Icons.Default.BugReport,
                KiyoriSemanticTone.BLUE,
            ),
        ),
        listOf(
            KiyoriSettingsHomeEntry(
                "界面定制",
                Icons.Default.Palette,
                KiyoriSemanticTone.PINK,
                KiyoriSettingsHomeAction.OPEN_APPEARANCE_SETTINGS,
            ),
            KiyoriSettingsHomeEntry(
                "数据备份与同步",
                Icons.Default.Backup,
                KiyoriSemanticTone.CYAN,
                KiyoriSettingsHomeAction.OPEN_DATA_SETTINGS,
            ),
            KiyoriSettingsHomeEntry(
                "开发手册与模式",
                Icons.AutoMirrored.Filled.MenuBook,
                KiyoriSemanticTone.ORANGE,
            ),
            KiyoriSettingsHomeEntry(
                "更多功能",
                Icons.Default.Widgets,
                KiyoriSemanticTone.BLUE,
            ),
        ),
    )

@Composable
internal fun KiyoriSettingsHomePage(
    onOpenAccountConnections: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenSpeechServices: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenDataSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KiyoriSettingsTheme {
        val colors = LocalKiyoriSettingsColors.current
        LazyColumn(
            modifier = modifier.fillMaxSize().background(colors.pageBackground),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                KiyoriSettingsHomeHeader(
                    onOpenAppearanceSettings = onOpenAppearanceSettings,
                )
            }
            itemsIndexed(kiyoriSettingsHomeGroups) { _, group ->
                KiyoriSettingsHomeGroupCard(
                    entries = group,
                    onOpenAccountConnections = onOpenAccountConnections,
                    onOpenAiAssistant = onOpenAiAssistant,
                    onOpenSpeechServices = onOpenSpeechServices,
                    onOpenBrowserSettings = onOpenBrowserSettings,
                    onOpenDownloadSettings = onOpenDownloadSettings,
                    onOpenPlayerSettings = onOpenPlayerSettings,
                    onOpenAppearanceSettings = onOpenAppearanceSettings,
                    onOpenDataSettings = onOpenDataSettings,
                )
            }
            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun KiyoriSettingsHomeHeader(
    onOpenAppearanceSettings: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.pageBackground)
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "设置",
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primaryText,
            modifier = Modifier.weight(1f),
        )
        KiyoriSettingsHeaderAction(R.drawable.ic_kiyori_settings_header_top_search, "搜索")
        KiyoriSettingsHeaderAction(R.drawable.ic_kiyori_settings_header_scan, "扫描")
        KiyoriSettingsHeaderAction(R.drawable.ic_kiyori_settings_header_top_refresh, "刷新")
        KiyoriSettingsHeaderAction(
            iconResId = R.drawable.ic_kiyori_settings_header_sun,
            contentDescription = "外观",
            onClick = onOpenAppearanceSettings,
        )
    }
}

@Composable
private fun KiyoriSettingsHeaderAction(
    iconResId: Int,
    contentDescription: String,
    onClick: (() -> Unit)? = null,
) {
    val interactionModifier =
        if (onClick == null) {
            Modifier.alpha(0.48f).semantics { disabled() }
        } else {
            Modifier.clickable(onClick = onClick)
        }
    Box(
        modifier = Modifier.size(36.dp).then(interactionModifier).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = contentDescription,
            tint = LocalKiyoriSettingsColors.current.secondaryText,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun KiyoriSettingsHomeGroupCard(
    entries: List<KiyoriSettingsHomeEntry>,
    onOpenAccountConnections: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenSpeechServices: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenDataSettings: () -> Unit,
) {
    KiyoriSettingsGroupCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            KiyoriSettingsHomeRow(
                entry = entry,
                onOpenAccountConnections = onOpenAccountConnections,
                onOpenAiAssistant = onOpenAiAssistant,
                onOpenSpeechServices = onOpenSpeechServices,
                onOpenBrowserSettings = onOpenBrowserSettings,
                onOpenDownloadSettings = onOpenDownloadSettings,
                onOpenPlayerSettings = onOpenPlayerSettings,
                onOpenAppearanceSettings = onOpenAppearanceSettings,
                onOpenDataSettings = onOpenDataSettings,
            )
            if (index != entries.lastIndex) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(0.6.dp)
                            .background(LocalKiyoriSettingsColors.current.divider),
                )
            }
        }
    }
}

@Composable
private fun KiyoriSettingsHomeRow(
    entry: KiyoriSettingsHomeEntry,
    onOpenAccountConnections: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenSpeechServices: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenDataSettings: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    val iconColors = entry.iconTone.resolveColors()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    when (entry.action) {
                        KiyoriSettingsHomeAction.NONE -> Unit
                        KiyoriSettingsHomeAction.OPEN_ACCOUNT_CONNECTIONS ->
                            onOpenAccountConnections()
                        KiyoriSettingsHomeAction.OPEN_AI_ASSISTANT -> onOpenAiAssistant()
                        KiyoriSettingsHomeAction.OPEN_SPEECH_SERVICES ->
                            onOpenSpeechServices()
                        KiyoriSettingsHomeAction.OPEN_BROWSER_SETTINGS -> onOpenBrowserSettings()
                        KiyoriSettingsHomeAction.OPEN_DOWNLOAD_SETTINGS -> onOpenDownloadSettings()
                        KiyoriSettingsHomeAction.OPEN_PLAYER_SETTINGS -> onOpenPlayerSettings()
                        KiyoriSettingsHomeAction.OPEN_APPEARANCE_SETTINGS ->
                            onOpenAppearanceSettings()
                        KiyoriSettingsHomeAction.OPEN_DATA_SETTINGS -> onOpenDataSettings()
                    }
                }
                .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .background(iconColors.container, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = entry.title,
                tint = iconColors.icon,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(modifier = Modifier.width(13.dp))
        Text(
            text = entry.title,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Medium,
            color = colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.mutedIcon,
            modifier = Modifier.size(18.dp),
        )
    }
}
