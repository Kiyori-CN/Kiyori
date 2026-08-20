package com.ai.assistance.operit.ui.main.shell

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.kiyori.design.theme.KiyoriSettingsHomeIconPalette
import com.kiyori.design.theme.KiyoriSettingsTheme
import com.kiyori.design.theme.LocalKiyoriSettingsColors
import com.kiyori.design.theme.resolveKiyoriSettingsThemeShortcutIconColor
import com.kiyori.design.theme.resolveSettingsHomeIconColors
import kotlinx.coroutines.launch

internal data class KiyoriSettingsHomeEntry(
    val title: String,
    val icon: ImageVector,
    val iconPalette: KiyoriSettingsHomeIconPalette,
    val action: KiyoriSettingsHomeAction = KiyoriSettingsHomeAction.NONE,
)

internal enum class KiyoriSettingsHomeAction {
    NONE,
    OPEN_ACCOUNT_CONNECTIONS,
    OPEN_AI_ASSISTANT,
    OPEN_BROWSER_SETTINGS,
    OPEN_DOWNLOAD_SETTINGS,
    OPEN_FILE_MANAGER,
    OPEN_PLAYER_SETTINGS,
    OPEN_APPEARANCE_SETTINGS,
    OPEN_DATA_SETTINGS,
    OPEN_MORE_FEATURES,
}

internal enum class KiyoriSettingsQuickTheme {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

internal const val KIYORI_SETTINGS_THEME_MENU_WIDTH_DP = 156

internal fun resolveKiyoriSettingsQuickTheme(
    useSystemTheme: Boolean,
    themeMode: String,
): KiyoriSettingsQuickTheme =
    if (useSystemTheme) {
        KiyoriSettingsQuickTheme.FOLLOW_SYSTEM
    } else {
        when (themeMode) {
            UserPreferencesManager.THEME_MODE_LIGHT -> KiyoriSettingsQuickTheme.LIGHT
            UserPreferencesManager.THEME_MODE_DARK -> KiyoriSettingsQuickTheme.DARK
            else -> error("Unsupported theme mode: $themeMode")
        }
    }

internal fun resolveKiyoriSettingsEffectiveDarkTheme(
    useSystemTheme: Boolean,
    themeMode: String,
    systemDarkTheme: Boolean,
): Boolean =
    if (useSystemTheme) {
        systemDarkTheme
    } else {
        when (themeMode) {
            UserPreferencesManager.THEME_MODE_LIGHT -> false
            UserPreferencesManager.THEME_MODE_DARK -> true
            else -> error("Unsupported theme mode: $themeMode")
        }
    }

internal val kiyoriSettingsHomeGroups =
    listOf(
        listOf(
            KiyoriSettingsHomeEntry(
                "我的账号",
                Icons.Default.AccountCircle,
                KiyoriSettingsHomeIconPalette.ACCOUNT_CONNECTION,
                KiyoriSettingsHomeAction.OPEN_ACCOUNT_CONNECTIONS,
            ),
            KiyoriSettingsHomeEntry(
                "AI助手",
                Icons.Outlined.SmartToy,
                KiyoriSettingsHomeIconPalette.AI_ASSISTANT,
                KiyoriSettingsHomeAction.OPEN_AI_ASSISTANT,
            ),
            KiyoriSettingsHomeEntry(
                "小程序",
                Icons.Default.Apps,
                KiyoriSettingsHomeIconPalette.MINI_APP,
            ),
        ),
        listOf(
            KiyoriSettingsHomeEntry(
                KIYORI_BROWSER_SETTINGS_PAGE_TITLE,
                Icons.Default.Language,
                KiyoriSettingsHomeIconPalette.BROWSER,
                KiyoriSettingsHomeAction.OPEN_BROWSER_SETTINGS,
            ),
            KiyoriSettingsHomeEntry(
                KIYORI_DOWNLOAD_SETTINGS_PAGE_TITLE,
                Icons.Default.Download,
                KiyoriSettingsHomeIconPalette.DOWNLOADS,
                KiyoriSettingsHomeAction.OPEN_DOWNLOAD_SETTINGS,
            ),
            KiyoriSettingsHomeEntry(
                "文件管理器",
                Icons.Default.Folder,
                KiyoriSettingsHomeIconPalette.FILE_MANAGER,
                KiyoriSettingsHomeAction.OPEN_FILE_MANAGER,
            ),
        ),
        listOf(
            KiyoriSettingsHomeEntry(
                KIYORI_PLAYER_SETTINGS_PAGE_TITLE,
                Icons.Default.PlayCircle,
                KiyoriSettingsHomeIconPalette.VIDEO_PLAYER,
                KiyoriSettingsHomeAction.OPEN_PLAYER_SETTINGS,
            ),
            KiyoriSettingsHomeEntry(
                "音乐播放器",
                Icons.Default.Audiotrack,
                KiyoriSettingsHomeIconPalette.MUSIC_PLAYER,
            ),
            KiyoriSettingsHomeEntry(
                "文档阅读器",
                Icons.AutoMirrored.Filled.MenuBook,
                KiyoriSettingsHomeIconPalette.DOCUMENT_READER,
            ),
        ),
        listOf(
            KiyoriSettingsHomeEntry(
                "界面定制",
                Icons.Default.Palette,
                KiyoriSettingsHomeIconPalette.APPEARANCE,
                KiyoriSettingsHomeAction.OPEN_APPEARANCE_SETTINGS,
            ),
            KiyoriSettingsHomeEntry(
                "数据备份",
                Icons.Default.Backup,
                KiyoriSettingsHomeIconPalette.DATA_BACKUP,
                KiyoriSettingsHomeAction.OPEN_DATA_SETTINGS,
            ),
            KiyoriSettingsHomeEntry(
                "更多功能",
                Icons.Default.Widgets,
                KiyoriSettingsHomeIconPalette.MORE_FEATURES,
                KiyoriSettingsHomeAction.OPEN_MORE_FEATURES,
            ),
        ),
    )

@Composable
internal fun KiyoriSettingsHomePage(
    onOpenAccountConnections: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenDataSettings: () -> Unit,
    onOpenMoreFeatures: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    BackHandler(enabled = onBack != null) {
        onBack?.invoke()
    }
    KiyoriSettingsTheme {
        val context = LocalContext.current
        val preferencesManager =
            remember(context) { UserPreferencesManager.getInstance(context.applicationContext) }
        val useSystemTheme by preferencesManager.useSystemTheme.collectAsState(initial = false)
        val themeMode by
            preferencesManager.themeMode.collectAsState(
                initial = UserPreferencesManager.THEME_MODE_LIGHT,
            )
        val systemDarkTheme = isSystemInDarkTheme()
        val effectiveDarkTheme =
            resolveKiyoriSettingsEffectiveDarkTheme(
                useSystemTheme = useSystemTheme,
                themeMode = themeMode,
                systemDarkTheme = systemDarkTheme,
            )
        val selectedQuickTheme =
            resolveKiyoriSettingsQuickTheme(
                useSystemTheme = useSystemTheme,
                themeMode = themeMode,
            )
        val scope = rememberCoroutineScope()
        val colors = LocalKiyoriSettingsColors.current
        LazyColumn(
            modifier = modifier.fillMaxSize().background(colors.pageBackground),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                KiyoriSettingsHomeHeader(
                    effectiveDarkTheme = effectiveDarkTheme,
                    selectedQuickTheme = selectedQuickTheme,
                    onBack = onBack,
                    onSelectQuickTheme = { selection ->
                        scope.launch {
                            when (selection) {
                                KiyoriSettingsQuickTheme.FOLLOW_SYSTEM ->
                                    preferencesManager.saveThemeSettings(useSystemTheme = true)
                                KiyoriSettingsQuickTheme.LIGHT ->
                                    preferencesManager.saveThemeSettings(
                                        themeMode = UserPreferencesManager.THEME_MODE_LIGHT,
                                        useSystemTheme = false,
                                    )
                                KiyoriSettingsQuickTheme.DARK ->
                                    preferencesManager.saveThemeSettings(
                                        themeMode = UserPreferencesManager.THEME_MODE_DARK,
                                        useSystemTheme = false,
                                    )
                            }
                        }
                    },
                )
            }
            itemsIndexed(kiyoriSettingsHomeGroups) { _, group ->
                KiyoriSettingsHomeGroupCard(
                    entries = group,
                    onOpenAccountConnections = onOpenAccountConnections,
                    onOpenAiAssistant = onOpenAiAssistant,
                    onOpenBrowserSettings = onOpenBrowserSettings,
                    onOpenDownloadSettings = onOpenDownloadSettings,
                    onOpenFileManager = onOpenFileManager,
                    onOpenPlayerSettings = onOpenPlayerSettings,
                    onOpenAppearanceSettings = onOpenAppearanceSettings,
                    onOpenDataSettings = onOpenDataSettings,
                    onOpenMoreFeatures = onOpenMoreFeatures,
                )
            }
            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun KiyoriSettingsHomeHeader(
    effectiveDarkTheme: Boolean,
    selectedQuickTheme: KiyoriSettingsQuickTheme,
    onBack: (() -> Unit)?,
    onSelectQuickTheme: (KiyoriSettingsQuickTheme) -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    var showThemeMenu by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.pageBackground)
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack == null) {
            Spacer(modifier = Modifier.width(10.dp))
        } else {
            KiyoriSettingsHomeBackAction(onBack)
        }
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
        KiyoriSettingsThemeShortcut(
            effectiveDarkTheme = effectiveDarkTheme,
            selectedQuickTheme = selectedQuickTheme,
            expanded = showThemeMenu,
            onExpandedChange = { showThemeMenu = it },
            onSelect = { selection ->
                showThemeMenu = false
                onSelectQuickTheme(selection)
            },
        )
    }
}

@Composable
private fun KiyoriSettingsThemeShortcut(
    effectiveDarkTheme: Boolean,
    selectedQuickTheme: KiyoriSettingsQuickTheme,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (KiyoriSettingsQuickTheme) -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    val shortcutColor = resolveKiyoriSettingsThemeShortcutIconColor(effectiveDarkTheme)
    Box {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clickable { onExpandedChange(!expanded) }
                    .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector =
                    if (effectiveDarkTheme) {
                        Icons.Default.DarkMode
                    } else {
                        Icons.Default.LightMode
                    },
                contentDescription = if (effectiveDarkTheme) "深色主题" else "浅色主题",
                tint = shortcutColor,
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier =
                Modifier
                    .width(KIYORI_SETTINGS_THEME_MENU_WIDTH_DP.dp)
                    .background(colors.cardBackground),
        ) {
            KiyoriSettingsQuickTheme.entries.forEach { option ->
                val selected = option == selectedQuickTheme
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = colors.primaryText,
                            fontSize = 14.sp,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint =
                                if (selected) {
                                    shortcutColor
                                } else {
                                    colors.secondaryText
                                },
                            modifier = Modifier.size(19.dp),
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "当前主题",
                                tint = shortcutColor,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun KiyoriSettingsHomeBackAction(onBack: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            tint = LocalKiyoriSettingsColors.current.secondaryText,
            modifier = Modifier.size(22.dp),
        )
    }
}

private val KiyoriSettingsQuickTheme.label: String
    get() =
        when (this) {
            KiyoriSettingsQuickTheme.FOLLOW_SYSTEM -> "跟随系统"
            KiyoriSettingsQuickTheme.LIGHT -> "浅色模式"
            KiyoriSettingsQuickTheme.DARK -> "深色模式"
        }

private val KiyoriSettingsQuickTheme.icon: ImageVector
    get() =
        when (this) {
            KiyoriSettingsQuickTheme.FOLLOW_SYSTEM -> Icons.Default.BrightnessAuto
            KiyoriSettingsQuickTheme.LIGHT -> Icons.Default.LightMode
            KiyoriSettingsQuickTheme.DARK -> Icons.Default.DarkMode
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
    onOpenBrowserSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenDataSettings: () -> Unit,
    onOpenMoreFeatures: () -> Unit,
) {
    KiyoriSettingsGroupCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            KiyoriSettingsHomeRow(
                entry = entry,
                onOpenAccountConnections = onOpenAccountConnections,
                onOpenAiAssistant = onOpenAiAssistant,
                onOpenBrowserSettings = onOpenBrowserSettings,
                onOpenDownloadSettings = onOpenDownloadSettings,
                onOpenFileManager = onOpenFileManager,
                onOpenPlayerSettings = onOpenPlayerSettings,
                onOpenAppearanceSettings = onOpenAppearanceSettings,
                onOpenDataSettings = onOpenDataSettings,
                onOpenMoreFeatures = onOpenMoreFeatures,
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
    onOpenBrowserSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenDataSettings: () -> Unit,
    onOpenMoreFeatures: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    val iconColors = entry.iconPalette.resolveSettingsHomeIconColors()
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
                        KiyoriSettingsHomeAction.OPEN_BROWSER_SETTINGS -> onOpenBrowserSettings()
                        KiyoriSettingsHomeAction.OPEN_DOWNLOAD_SETTINGS -> onOpenDownloadSettings()
                        KiyoriSettingsHomeAction.OPEN_FILE_MANAGER -> onOpenFileManager()
                        KiyoriSettingsHomeAction.OPEN_PLAYER_SETTINGS -> onOpenPlayerSettings()
                        KiyoriSettingsHomeAction.OPEN_APPEARANCE_SETTINGS ->
                            onOpenAppearanceSettings()
                        KiyoriSettingsHomeAction.OPEN_DATA_SETTINGS -> onOpenDataSettings()
                        KiyoriSettingsHomeAction.OPEN_MORE_FEATURES -> onOpenMoreFeatures()
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
