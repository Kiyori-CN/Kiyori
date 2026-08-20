package com.ai.assistance.operit.ui.main.shell

import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadNetworkPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettings
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.FRESH_INSTALL_BROWSER_SETTINGS
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSettings
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.formatAutomaticFloatingMinimumDuration
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.formatWebTextZoomPercent
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.isSupportedBrowserHomeUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.parseAutomaticFloatingDurationSeconds
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuTone
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.screens.ScreenRouteRegistry
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriSettingsHomeIconPalette
import com.kiyori.integration.operit.navigation.AppRouteCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriSettingsPagesTest {
    @Test
    fun `shared settings header follows the reference start middle and pinned frames`() {
        assertEquals("网页浏览器", KIYORI_BROWSER_SETTINGS_PAGE_TITLE)
        assertEquals("文件下载器", KIYORI_DOWNLOAD_SETTINGS_PAGE_TITLE)
        assertEquals("视频播放器", KIYORI_PLAYER_SETTINGS_PAGE_TITLE)
        assertEquals("广告拦截器", KIYORI_AD_BLOCK_SETTINGS_PAGE_TITLE)
        assertEquals("更多功能", KIYORI_MORE_FEATURES_SETTINGS_PAGE_TITLE)
        assertEquals(156, KIYORI_SETTINGS_THEME_MENU_WIDTH_DP)
        assertEquals(
            0f,
            calculateKiyoriSettingsHeaderCollapseProgress(0, 0, 72f),
            0.0001f,
        )
        assertEquals(
            0.5f,
            calculateKiyoriSettingsHeaderCollapseProgress(0, 36, 72f),
            0.0001f,
        )
        assertEquals(
            1f,
            calculateKiyoriSettingsHeaderCollapseProgress(1, 0, 72f),
            0.0001f,
        )

        val expanded = calculateKiyoriSettingsHeaderFrame(0f)
        assertEquals(128f, expanded.contentHeightDp, 0.0001f)
        assertEquals(32f, expanded.titleStartDp, 0.0001f)
        assertEquals(60f, expanded.titleTopDp, 0.0001f)
        assertEquals(26f, expanded.titleFontSizeSp, 0.0001f)

        val middle = calculateKiyoriSettingsHeaderFrame(0.5f)
        assertEquals(92f, middle.contentHeightDp, 0.0001f)
        assertEquals(44f, middle.titleStartDp, 0.0001f)
        assertEquals(38f, middle.titleTopDp, 0.0001f)
        assertEquals(23f, middle.titleFontSizeSp, 0.0001f)

        val pinned = calculateKiyoriSettingsHeaderFrame(1f)
        assertEquals(56f, pinned.contentHeightDp, 0.0001f)
        assertEquals(56f, pinned.titleStartDp, 0.0001f)
        assertEquals(16f, pinned.titleTopDp, 0.0001f)
        assertEquals(20f, pinned.titleFontSizeSp, 0.0001f)
        assertEquals(16, KIYORI_SETTINGS_ROW_VERTICAL_PADDING_DP)
        assertEquals(26, KIYORI_SETTINGS_SELECTION_CORNER_RADIUS_DP)
        assertEquals(56, KIYORI_SETTINGS_SELECTION_OPTION_MIN_HEIGHT_DP)
        assertEquals(13, KIYORI_SETTINGS_SELECTION_OPTION_VERTICAL_PADDING_DP)
        assertEquals(21, KIYORI_SETTINGS_SELECTION_CHECK_ICON_SIZE_DP)
        assertEquals(17, KIYORI_SETTINGS_SELECTION_CANCEL_VERTICAL_PADDING_DP)
        assertEquals(14, KIYORI_SETTINGS_FIELD_CORNER_RADIUS_DP)
        assertEquals(18, KIYORI_SETTINGS_FIELD_HORIZONTAL_PADDING_DP)
    }

    @Test
    fun `settings home keeps four three-item groups and exposes real settings actions`() {
        assertEquals(
            listOf(3, 3, 3, 3),
            kiyoriSettingsHomeGroups.map { group -> group.size },
        )
        assertEquals(
            listOf(
                "我的账号",
                "AI助手",
                "小程序",
                "网页浏览器",
                "文件下载器",
                "文件管理器",
                "视频播放器",
                "音乐播放器",
                "文档阅读器",
                "界面定制",
                "数据备份",
                "更多功能",
            ),
            kiyoriSettingsHomeGroups.flatten().map(KiyoriSettingsHomeEntry::title),
        )
        assertEquals(
            listOf("我的账号"),
            kiyoriSettingsHomeGroups
                .flatten()
                .filter { entry ->
                    entry.action == KiyoriSettingsHomeAction.OPEN_ACCOUNT_CONNECTIONS
                }
                .map(KiyoriSettingsHomeEntry::title),
        )
        assertEquals(
            listOf("AI助手"),
            kiyoriSettingsHomeGroups
                .flatten()
                .filter { entry ->
                    entry.action == KiyoriSettingsHomeAction.OPEN_AI_ASSISTANT
                }
                .map(KiyoriSettingsHomeEntry::title),
        )
        assertEquals(
            listOf("小程序"),
            kiyoriSettingsHomeGroups
                .flatten()
                .filter { entry -> entry.title == "小程序" }
                .filter { entry -> entry.action == KiyoriSettingsHomeAction.NONE }
                .map(KiyoriSettingsHomeEntry::title),
        )
        assertEquals(
            listOf("网页浏览器"),
            kiyoriSettingsHomeGroups
                .flatten()
                .filter { entry ->
                    entry.action == KiyoriSettingsHomeAction.OPEN_BROWSER_SETTINGS
                }
                .map(KiyoriSettingsHomeEntry::title),
        )
        assertEquals(
            listOf("文件下载器"),
            kiyoriSettingsHomeGroups
                .flatten()
                .filter { entry ->
                    entry.action == KiyoriSettingsHomeAction.OPEN_DOWNLOAD_SETTINGS
                }
                .map(KiyoriSettingsHomeEntry::title),
        )
        assertEquals(
            listOf("视频播放器"),
            kiyoriSettingsHomeGroups
                .flatten()
                .filter { entry ->
                    entry.action == KiyoriSettingsHomeAction.OPEN_PLAYER_SETTINGS
                }
                .map(KiyoriSettingsHomeEntry::title),
        )
        assertTrue(
            kiyoriSettingsHomeGroups
                .flatten()
                .none { entry ->
                    entry.title in setOf("语音服务", "广告拦截器", "日志记录器", "开发手册")
                },
        )
        assertEquals(
            listOf("界面定制"),
            kiyoriSettingsHomeGroups
                .flatten()
                .filter { entry ->
                    entry.action == KiyoriSettingsHomeAction.OPEN_APPEARANCE_SETTINGS
                }
                .map(KiyoriSettingsHomeEntry::title),
        )
        assertEquals(
            listOf("数据备份"),
            kiyoriSettingsHomeGroups
                .flatten()
                .filter { entry ->
                    entry.action == KiyoriSettingsHomeAction.OPEN_DATA_SETTINGS
                }
                .map(KiyoriSettingsHomeEntry::title),
        )
        assertEquals(
            listOf("更多功能"),
            kiyoriSettingsHomeGroups
                .flatten()
                .filter { entry ->
                    entry.action == KiyoriSettingsHomeAction.OPEN_MORE_FEATURES
                }
                .map(KiyoriSettingsHomeEntry::title),
        )
    }

    @Test
    fun `more features exposes the original device permission owner as navigation`() {
        assertEquals(
            listOf("系统能力"),
            kiyoriMoreFeaturesSettingsGroups.map(KiyoriMoreFeaturesSettingsGroupSpec::title),
        )
        val permissionEntry =
            kiyoriMoreFeaturesSettingsGroups
                .flatMap(KiyoriMoreFeaturesSettingsGroupSpec::entries)
                .single()
        assertEquals("权限", permissionEntry.title)
        assertTrue(permissionEntry.description.contains("Shizuku"))
        assertTrue(permissionEntry.description.contains("无障碍"))
        assertTrue(permissionEntry.description.contains("Root"))
        assertEquals(
            KiyoriMoreFeaturesSettingsAction.OPEN_PERMISSIONS,
            permissionEntry.action,
        )
    }

    @Test
    fun `application settings roots keep AI application and data ownership separated`() {
        assertEquals("我的账号", KIYORI_ACCOUNT_SETTINGS_PAGE_TITLE)
        assertEquals("数据备份", KIYORI_DATA_SETTINGS_PAGE_TITLE)
        assertEquals("虚拟形象配置", KIYORI_AVATAR_SETTINGS_PAGE_TITLE)
        assertEquals("语音唤醒", KIYORI_VOICE_WAKEUP_SETTINGS_PAGE_TITLE)
        assertEquals(
            listOf(2, 2, 2, 4, 2, 2),
            kiyoriAiAssistantSettingsGroups.map { group -> group.entries.size },
        )
        assertEquals(
            listOf("助手体验", "语音与交互", "模型与服务", "对话与角色", "上下文与安全", "使用与连接"),
            kiyoriAiAssistantSettingsGroups.map { group -> group.title },
        )
        assertEquals(
            listOf(
                "虚拟形象配置",
                "语音唤醒",
                "文本转语音",
                "语音转文本",
                "模型与 API",
                "功能模型",
                "用户偏好",
                "提示词",
                "人设卡生成",
                "分句回复",
                "上下文与总结",
                "AI 工具授权",
                "Token 使用统计",
                "外部 HTTP 对话",
            ),
            kiyoriAiAssistantSettingsGroups
                .flatMap { group -> group.entries }
                .map { entry -> entry.title },
        )
        assertEquals(
            KiyoriAiAssistantSettingsAction.entries.toSet(),
            kiyoriAiAssistantSettingsGroups
                .flatMap { group -> group.entries }
                .map { entry -> entry.action }
                .toSet(),
        )
        assertEquals(
            listOf("语言", "主题与外观", "全局显示", "布局调整"),
            kiyoriAppearanceSettingsGroups
                .flatMap { group -> group.entries }
                .map { entry -> entry.title },
        )
        assertEquals(
            KiyoriAppearanceSettingsAction.entries.toSet(),
            kiyoriAppearanceSettingsGroups
                .flatMap { group -> group.entries }
                .map { entry -> entry.action }
                .toSet(),
        )
        assertEquals(
            listOf("数据备份与恢复", "聊天历史管理"),
            kiyoriDataSettingsGroups
                .flatMap { group -> group.entries }
                .map { entry -> entry.title },
        )
        assertEquals(
            KiyoriDataSettingsAction.entries.toSet(),
            kiyoriDataSettingsGroups
                .flatMap { group -> group.entries }
                .map { entry -> entry.action }
                .toSet(),
        )
        assertTrue(
            (kiyoriAiAssistantSettingsGroups +
                kiyoriAppearanceSettingsGroups +
                kiyoriDataSettingsGroups).all { group ->
                group.description.isNotBlank() &&
                    group.entries.all { entry -> entry.description.isNotBlank() } &&
                    group.entries.map { entry -> entry.iconTone }.toSet().size ==
                    group.entries.size
            },
        )
        assertEquals(
            KiyoriSemanticTone.entries.toSet(),
            (kiyoriAiAssistantSettingsGroups +
                kiyoriAppearanceSettingsGroups +
                kiyoriDataSettingsGroups)
                .flatMap { group -> group.entries }
                .map { entry -> entry.iconTone }
                .toSet(),
        )
        assertFalse(
            kiyoriAppearanceSettingsGroups
                .flatMap { group -> group.entries }
                .single { entry -> entry.action == KiyoriAppearanceSettingsAction.OPEN_THEME }
                .description
                .contains("全局配色"),
        )
    }

    @Test
    fun `settings quick theme resolves selection and effective appearance from the sole owner`() {
        assertEquals(
            KiyoriSettingsQuickTheme.FOLLOW_SYSTEM,
            resolveKiyoriSettingsQuickTheme(
                useSystemTheme = true,
                themeMode = UserPreferencesManager.THEME_MODE_LIGHT,
            ),
        )
        assertEquals(
            KiyoriSettingsQuickTheme.LIGHT,
            resolveKiyoriSettingsQuickTheme(
                useSystemTheme = false,
                themeMode = UserPreferencesManager.THEME_MODE_LIGHT,
            ),
        )
        assertEquals(
            KiyoriSettingsQuickTheme.DARK,
            resolveKiyoriSettingsQuickTheme(
                useSystemTheme = false,
                themeMode = UserPreferencesManager.THEME_MODE_DARK,
            ),
        )
        assertTrue(
            resolveKiyoriSettingsEffectiveDarkTheme(
                useSystemTheme = true,
                themeMode = UserPreferencesManager.THEME_MODE_LIGHT,
                systemDarkTheme = true,
            ),
        )
        assertFalse(
            resolveKiyoriSettingsEffectiveDarkTheme(
                useSystemTheme = false,
                themeMode = UserPreferencesManager.THEME_MODE_LIGHT,
                systemDarkTheme = true,
            ),
        )
    }

    @Test
    fun `settings home uses twelve unique icons and semantic palettes`() {
        val entries = kiyoriSettingsHomeGroups.flatten()

        assertEquals(12, entries.size)
        assertEquals(12, entries.map { entry -> entry.icon.name }.toSet().size)
        assertEquals(
            KiyoriSettingsHomeIconPalette.entries.toList(),
            entries.map { entry -> entry.iconPalette },
        )
    }

    @Test
    fun `application settings roots round trip through the native route registry`() {
        listOf(
            Screen.AccountConnectionsSettings,
            Screen.Settings,
            Screen.AvatarSettings,
            Screen.VoiceWakeupSettings,
            Screen.TextToSpeechSettings,
            Screen.SpeechToTextSettings,
            Screen.AppearanceSettings,
            Screen.DataManagementSettings,
        ).forEach { screen ->
            val entry =
                AppRouteCatalog.toEntry(
                    screen = screen,
                    source = RouteEntrySource.KIYORI_SETTINGS,
                )

            assertEquals(RouteEntrySource.KIYORI_SETTINGS, entry.source)
            assertEquals(screen, ScreenRouteRegistry.screenFromEntry(entry))
            assertEquals(screen, ScreenRouteRegistry.buildScreen(entry.routeId, emptyMap()))
        }
    }

    @Test
    fun `every application settings child round trips through the Kiyori settings source`() {
        listOf(
            Screen.GitHubAccount,
            Screen.UserPreferencesSettings,
            Screen.ModelConfig,
            Screen.MnnModelDownload,
            Screen.FunctionalConfig,
            Screen.ModelPromptsSettings,
            Screen.PersonaCardGeneration,
            Screen.WaifuModeSettings,
            Screen.CustomEmojiManagement,
            Screen.TagMarket,
            Screen.ContextSummarySettings,
            Screen.ToolPermission,
            Screen.TokenUsageStatistics,
            Screen.ExternalHttpChatSettings,
            Screen.ThemeSettings,
            Screen.GlobalDisplaySettings,
            Screen.LayoutAdjustmentSettings,
            Screen.ChatHistorySettings,
            Screen.ChatBackupSettings,
            Screen.LanguageSettings,
            Screen.TextToSpeechSettings,
            Screen.SpeechToTextSettings,
            Screen.TextToSpeech,
        ).forEach { screen ->
            val entry =
                AppRouteCatalog.toEntry(
                    screen = screen,
                    source = RouteEntrySource.KIYORI_SETTINGS,
                )

            assertEquals(RouteEntrySource.KIYORI_SETTINGS, entry.source)
            assertEquals(screen, ScreenRouteRegistry.screenFromEntry(entry))
            assertEquals(screen, ScreenRouteRegistry.buildScreen(entry.routeId, emptyMap()))
        }
    }

    @Test
    fun `download settings use player style groups and expose every download consumer`() {
        assertEquals(
            listOf(5, 4, 2, 2, 1),
            kiyoriDownloadSettingsGroups.map { group -> group.entries.size },
        )
        assertEquals(
            listOf(
                "默认下载器",
                "默认保存位置",
                "并行下载任务",
                "普通文件线程数",
                "M3U8 线程数",
                "网络条件",
                "允许漫游下载",
                "完成与失败通知",
                "系统通知设置",
                "M3U8 离线包",
                "下载分块大小",
                "安装包自动清理",
                "跳过下载确认",
                "HTTP 协议",
            ),
            kiyoriDownloadSettingsGroups
                .flatMap(KiyoriDownloadSettingsGroupSpec::entries)
                .map(KiyoriDownloadSettingsEntrySpec::title),
        )
        assertEquals(
            mapOf(
                "默认下载器" to KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_ENGINE,
                "默认保存位置" to KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_DIRECTORY,
                "并行下载任务" to
                    KiyoriDownloadSettingsAction.SELECT_MAX_CONCURRENT_TASKS,
                "普通文件线程数" to
                    KiyoriDownloadSettingsAction.SELECT_NORMAL_THREAD_COUNT,
                "M3U8 线程数" to
                    KiyoriDownloadSettingsAction.SELECT_M3U8_THREAD_COUNT,
                "网络条件" to
                    KiyoriDownloadSettingsAction.SELECT_NETWORK_POLICY,
                "允许漫游下载" to
                    KiyoriDownloadSettingsAction.TOGGLE_ALLOW_ROAMING,
                "完成与失败通知" to
                    KiyoriDownloadSettingsAction.TOGGLE_RESULT_NOTIFICATIONS,
                "系统通知设置" to
                    KiyoriDownloadSettingsAction.OPEN_NOTIFICATION_SETTINGS,
                "M3U8 离线包" to
                    KiyoriDownloadSettingsAction.TOGGLE_M3U8_OFFLINE_PACKAGE,
                "下载分块大小" to
                    KiyoriDownloadSettingsAction.SELECT_CHUNK_SIZE,
                "安装包自动清理" to
                    KiyoriDownloadSettingsAction.TOGGLE_AUTO_CLEAN_APK,
                "跳过下载确认" to
                    KiyoriDownloadSettingsAction.TOGGLE_SKIP_CONFIRMATION,
                "HTTP 协议" to
                    KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_PROTOCOL,
            ),
            kiyoriDownloadSettingsGroups
                .flatMap(KiyoriDownloadSettingsGroupSpec::entries)
                .associate { entry -> entry.title to entry.action },
        )
        assertEquals(
            listOf(
                "下载器与性能",
                "网络与后台",
                "M3U8 与存储",
                "安装与确认",
                "网络协议",
            ),
            kiyoriDownloadSettingsGroups.map(KiyoriDownloadSettingsGroupSpec::title),
        )
        assertTrue(
            kiyoriDownloadSettingsGroups.all { group ->
                group.description.isNotBlank() &&
                    group.entries.all { entry -> entry.description.isNotBlank() }
            },
        )
        assertEquals("12MB", formatBrowserDownloadChunkSize(12288))
        assertEquals("256KB", formatBrowserDownloadChunkSize(256))
        assertEquals(
            listOf(
                "默认下载器",
                "网络条件",
                "允许漫游下载",
                "系统通知设置",
                "跳过下载确认",
            ),
            kiyoriDownloadSettingsGroups
                .flatMap(KiyoriDownloadSettingsGroupSpec::entries)
                .filter { entry ->
                    entry.dependency == KiyoriDownloadSettingsDependency.ALWAYS
                }
                .map(KiyoriDownloadSettingsEntrySpec::title),
        )
        val systemSettings = BrowserDownloadSettings(defaultEngine = BrowserDownloadEngine.SYSTEM)
        val internalSettings =
            BrowserDownloadSettings(defaultEngine = BrowserDownloadEngine.INTERNAL)
        kiyoriDownloadSettingsGroups
            .flatMap(KiyoriDownloadSettingsGroupSpec::entries)
            .forEach { entry ->
                assertTrue(isDownloadSettingEnabled(entry, internalSettings))
                assertEquals(
                    entry.dependency == KiyoriDownloadSettingsDependency.ALWAYS,
                    isDownloadSettingEnabled(entry, systemSettings),
                )
            }
        assertEquals("应用下载目录", downloadDirectoryLabel(internalSettings))
        assertEquals(
            "公开下载目录",
            downloadDirectoryLabel(
                internalSettings.copy(autoTransferToPublicDirectory = true),
            ),
        )
        assertEquals(
            "视频",
            downloadDirectoryLabel(
                internalSettings.copy(
                    customDirectoryUri = "content://tree/video",
                    customDirectoryName = "视频",
                ),
            ),
        )
        assertEquals("系统下载目录", downloadDirectoryLabel(systemSettings))
        assertEquals("任意网络", downloadNetworkPolicyLabel(BrowserDownloadNetworkPolicy.ANY))
        assertEquals(
            "仅非计费网络",
            downloadNetworkPolicyLabel(BrowserDownloadNetworkPolicy.UNMETERED),
        )
        val networkEntry =
            kiyoriDownloadSettingsGroups
                .flatMap(KiyoriDownloadSettingsGroupSpec::entries)
                .single { entry -> entry.action == KiyoriDownloadSettingsAction.SELECT_NETWORK_POLICY }
        assertEquals(
            "仅非计费网络",
            downloadSettingValue(
                networkEntry,
                internalSettings.copy(networkPolicy = BrowserDownloadNetworkPolicy.UNMETERED),
            ),
        )
    }

    @Test
    fun `player settings expose only capabilities owned by PlayerSettingsStore`() {
        assertEquals(
            listOf(4, 7, 6, 2, 2, 4),
            kiyoriPlayerSettingsGroups.map { group -> group.entries.size },
        )
        assertEquals(
            listOf(
                "默认播放倍速",
                "记忆播放倍速",
                "自动播放下一集",
                "队列播完后",
                "双击手势",
                "双击跳转时长",
                "长按加速",
                "按钮跳转时长",
                "精确进度定位",
                "显示章节进度条",
                "进度条缩略图预览",
                "记忆超分模式",
                "默认超分模式",
                "解码方式",
                "渲染预设",
                "GPU Next 渲染",
                "Vulkan 渲染上下文",
                "音量增强",
                "字幕缩放",
                "截图保存位置",
                "视频下载位置",
                "跟随重力自动旋转",
                "退出全屏后",
                "切到后台时",
                "在线播放缓存",
            ),
            kiyoriPlayerSettingsGroups
                .flatMap(KiyoriPlayerSettingsGroupSpec::entries)
                .map(KiyoriPlayerSettingsEntrySpec::title),
        )
        assertEquals(
            mapOf(
                "默认播放倍速" to KiyoriPlayerSettingsAction.SELECT_DEFAULT_SPEED,
                "记忆播放倍速" to
                    KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_PLAYBACK_SPEED,
                "自动播放下一集" to KiyoriPlayerSettingsAction.TOGGLE_AUTO_PLAY_NEXT,
                "队列播完后" to KiyoriPlayerSettingsAction.SELECT_QUEUE_END_BEHAVIOR,
                "双击手势" to KiyoriPlayerSettingsAction.SELECT_DOUBLE_TAP_ACTION,
                "双击跳转时长" to
                    KiyoriPlayerSettingsAction.SELECT_DOUBLE_TAP_SEEK_STEP,
                "长按加速" to
                    KiyoriPlayerSettingsAction.TOGGLE_LONG_PRESS_SPEED_BOOST,
                "按钮跳转时长" to KiyoriPlayerSettingsAction.SELECT_SEEK_STEP,
                "精确进度定位" to KiyoriPlayerSettingsAction.TOGGLE_PRECISE_SEEKING,
                "显示章节进度条" to KiyoriPlayerSettingsAction.TOGGLE_CHAPTER_BAR,
                "进度条缩略图预览" to
                    KiyoriPlayerSettingsAction.TOGGLE_SEEKBAR_THUMBNAIL,
                "记忆超分模式" to KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_ANIME4K,
                "默认超分模式" to KiyoriPlayerSettingsAction.SELECT_DEFAULT_ANIME4K,
                "解码方式" to KiyoriPlayerSettingsAction.SELECT_DECODER_BACKEND,
                "渲染预设" to KiyoriPlayerSettingsAction.SELECT_RENDERING_PROFILE,
                "GPU Next 渲染" to KiyoriPlayerSettingsAction.TOGGLE_GPU_NEXT,
                "Vulkan 渲染上下文" to KiyoriPlayerSettingsAction.TOGGLE_VULKAN,
                "音量增强" to KiyoriPlayerSettingsAction.TOGGLE_VOLUME_BOOST,
                "字幕缩放" to KiyoriPlayerSettingsAction.SELECT_SUBTITLE_SCALE,
                "截图保存位置" to
                    KiyoriPlayerSettingsAction.SELECT_SCREENSHOT_DIRECTORY,
                "视频下载位置" to
                    KiyoriPlayerSettingsAction.SELECT_VIDEO_DOWNLOAD_DIRECTORY,
                "跟随重力自动旋转" to
                    KiyoriPlayerSettingsAction.TOGGLE_FOLLOW_GRAVITY_ROTATION,
                "退出全屏后" to
                    KiyoriPlayerSettingsAction.SELECT_FULLSCREEN_EXIT_BEHAVIOR,
                "切到后台时" to KiyoriPlayerSettingsAction.SELECT_BACKGROUND_BEHAVIOR,
                "在线播放缓存" to KiyoriPlayerSettingsAction.SELECT_NETWORK_CACHE_POLICY,
            ),
            kiyoriPlayerSettingsGroups
                .flatMap(KiyoriPlayerSettingsGroupSpec::entries)
                .associate { entry -> entry.title to entry.action },
        )
        assertEquals(
            listOf(
                "播放与连播",
                "手势与进度",
                "画面与超分",
                "音频与字幕",
                "保存与下载",
                "窗口与在线",
            ),
            kiyoriPlayerSettingsGroups.map(KiyoriPlayerSettingsGroupSpec::title),
        )
    }

    @Test
    fun `browser settings expose only verified capabilities`() {
        assertEquals(
            listOf(2, 4, 3, 3, 2, 1, 5, 3),
            kiyoriBrowserSettingsGroups.map { group -> group.entries.size },
        )
        assertEquals(
            listOf(
                "广告拦截",
                "广告拦截器管理",
                "允许用户脚本",
                "插件中心",
                "插件权限与网站范围",
                "脚本诊断与日志",
                "网页主页自定义",
                "返回不重载",
                "滑屏前进后退",
                "恢复上次的搜索结果",
                "询问是否恢复页面",
                "保留多窗口",
                "强制页面缩放",
                "网页文字大小",
                "长按网页元素菜单",
                "允许网页打开应用",
                "允许网页获取位置",
                "自动保存和填充网站密码",
                "网站密码管理",
                "清除网站 Cookie",
                "搜索栏嗅探入口",
                "自动悬浮播放",
                "自动悬浮最小时长",
            ),
            kiyoriBrowserSettingsGroups
                .flatMap(KiyoriBrowserSettingsGroupSpec::entries)
                .map(KiyoriBrowserSettingsEntrySpec::title),
        )
        assertEquals(
            mapOf(
                "广告拦截" to
                    KiyoriBrowserSettingsAction.TOGGLE_AD_BLOCKING,
                "广告拦截器管理" to
                    KiyoriBrowserSettingsAction.OPEN_AD_BLOCKER_SETTINGS,
                "允许用户脚本" to
                    KiyoriBrowserSettingsAction.TOGGLE_USER_SCRIPTS_ALLOWED,
                "插件中心" to
                    KiyoriBrowserSettingsAction.OPEN_PLUGIN_CENTER,
                "插件权限与网站范围" to
                    KiyoriBrowserSettingsAction.OPEN_PLUGIN_PERMISSIONS,
                "脚本诊断与日志" to
                    KiyoriBrowserSettingsAction.OPEN_PLUGIN_DIAGNOSTICS,
                "网页主页自定义" to
                    KiyoriBrowserSettingsAction.OPEN_HOME_CUSTOMIZATION,
                "返回不重载" to
                    KiyoriBrowserSettingsAction.TOGGLE_RETURN_WITHOUT_RELOAD,
                "滑屏前进后退" to
                    KiyoriBrowserSettingsAction.TOGGLE_SWIPE_HISTORY_NAVIGATION,
                "恢复上次的搜索结果" to
                    KiyoriBrowserSettingsAction.TOGGLE_RESTORE_LAST_SEARCH_RESULT,
                "询问是否恢复页面" to
                    KiyoriBrowserSettingsAction.TOGGLE_ASK_BEFORE_RESTORING_PAGES,
                "保留多窗口" to
                    KiyoriBrowserSettingsAction.TOGGLE_RETAIN_MULTIPLE_WINDOWS,
                "强制页面缩放" to
                    KiyoriBrowserSettingsAction.TOGGLE_FORCE_PAGE_ZOOM,
                "网页文字大小" to
                    KiyoriBrowserSettingsAction.OPEN_WEB_TEXT_SIZE,
                "长按网页元素菜单" to
                    KiyoriBrowserSettingsAction.TOGGLE_WEB_ELEMENT_LONG_PRESS_MENU,
                "允许网页打开应用" to
                    KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_OPEN_APP,
                "允许网页获取位置" to
                    KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_GEOLOCATION,
                "自动保存和填充网站密码" to
                    KiyoriBrowserSettingsAction.TOGGLE_WEBSITE_PASSWORD_SAVING,
                "网站密码管理" to
                    KiyoriBrowserSettingsAction.OPEN_PASSWORD_MANAGER,
                "清除网站 Cookie" to
                    KiyoriBrowserSettingsAction.CLEAR_COOKIES,
                "搜索栏嗅探入口" to
                    KiyoriBrowserSettingsAction.TOGGLE_SEARCH_BAR_SNIFFER_ENTRY,
                "自动悬浮播放" to
                    KiyoriBrowserSettingsAction.TOGGLE_AUTOMATIC_FLOATING_PLAYBACK,
                "自动悬浮最小时长" to
                    KiyoriBrowserSettingsAction
                        .SELECT_AUTOMATIC_FLOATING_MINIMUM_DURATION,
            ),
            kiyoriBrowserSettingsGroups
                .flatMap(KiyoriBrowserSettingsGroupSpec::entries)
                .associate { entry -> entry.title to entry.action },
        )
        assertEquals(
            listOf(
                "内容过滤",
                "网页插件与脚本",
                "主页与导航",
                "启动与窗口",
                "网页显示",
                "网页交互",
                "网站权限与数据",
                "音视频嗅探",
            ),
            kiyoriBrowserSettingsGroups.map(KiyoriBrowserSettingsGroupSpec::title),
        )
        assertTrue(
            kiyoriBrowserSettingsGroups.all { group ->
                group.description.isNotBlank() &&
                    group.entries.all { entry -> entry.description.isNotBlank() }
            },
        )
        val initialBrowserSettings = FRESH_INSTALL_BROWSER_SETTINGS
        assertEquals("https://go.itab.link", initialBrowserSettings.homeUrl)
        assertTrue(initialBrowserSettings.returnWithoutReloadEnabled)
        assertTrue(initialBrowserSettings.forcePageZoomEnabled)
        assertTrue(initialBrowserSettings.websitePasswordSavingEnabled)
        assertTrue(initialBrowserSettings.webElementLongPressMenuEnabled)
        assertFalse(initialBrowserSettings.swipeHistoryNavigationEnabled)
        assertFalse(initialBrowserSettings.restoreLastSearchResultEnabled)
        assertFalse(initialBrowserSettings.askBeforeRestoringPagesEnabled)
        assertFalse(initialBrowserSettings.retainMultipleWindowsEnabled)
        assertTrue(initialBrowserSettings.siteSettingsRules.isEmpty())
        val browserSettings = WebSessionBrowserSettings(homeUrl = "https://example.com/home")
        val entries =
            kiyoriBrowserSettingsGroups.flatMap(KiyoriBrowserSettingsGroupSpec::entries)
        assertEquals(23, entries.size)
        assertEquals(
            listOf(2, 4, 3, 3, 2, 1, 5, 3),
            kiyoriBrowserSettingsGroups.map { group -> group.entries.size },
        )
        assertTrue(
            browserSettingToggleValue(
                entry = entries.single { entry -> entry.title == "广告拦截" },
                settings = browserSettings,
                userscriptState = WebSessionUserscriptUiState(),
                adBlockState = BrowserAdBlockState(enabled = true),
            ),
        )
        assertTrue(
            browserSettingToggleValue(
                entry = entries.single { entry -> entry.title == "长按网页元素菜单" },
                settings = browserSettings,
                userscriptState = WebSessionUserscriptUiState(),
            ),
        )
        assertFalse(
            browserSettingToggleValue(
                entry =
                    entries.single {
                        entry -> entry.title == "自动保存和填充网站密码"
                    },
                settings = browserSettings.copy(websitePasswordSavingEnabled = false),
                userscriptState = WebSessionUserscriptUiState(),
            ),
        )
        listOf(
            "滑屏前进后退",
            "恢复上次的搜索结果",
            "询问是否恢复页面",
            "保留多窗口",
        ).forEach { title ->
            assertFalse(
                browserSettingToggleValue(
                    entry = entries.single { entry -> entry.title == title },
                    settings = browserSettings,
                    userscriptState = WebSessionUserscriptUiState(),
                ),
            )
        }
        assertEquals(
            "https://example.com/home",
            browserSettingValue(
                entries.single { entry -> entry.title == "网页主页自定义" },
                browserSettings,
            ),
        )
        assertEquals(
            "1 个插件 · 0 个脚本",
            browserSettingValue(
                entries.single { entry -> entry.title == "插件中心" },
                browserSettings,
            ),
        )
        assertEquals(
            "全局已关闭",
            browserSettingValue(
                entry =
                    entries.single {
                        entry -> entry.title == "广告拦截器管理"
                    },
                settings = browserSettings,
                adBlockState = BrowserAdBlockState(enabled = false),
            ),
        )
        assertEquals(
            "运行环境不可用 · 0 条保留日志",
            browserSettingValue(
                entries.single { entry -> entry.title == "脚本诊断与日志" },
                browserSettings,
            ),
        )
        assertEquals(
            "默认 · 100%",
            browserSettingValue(
                entries.single { entry -> entry.title == "网页文字大小" },
                browserSettings,
            ),
        )
        assertEquals(
            "3 项",
            browserSettingValue(
                entry =
                    entries.single { entry -> entry.title == "网站密码管理" },
                settings = browserSettings,
                savedCredentialCount = 3,
            ),
        )
        assertEquals(
            "不可用",
            browserSettingValue(
                entry =
                    entries.single { entry -> entry.title == "网站密码管理" },
                settings = browserSettings,
                savedCredentialCount = 3,
                credentialVaultAvailable = false,
            ),
        )
        assertEquals(
            "解锁中",
            browserSettingValue(
                entry =
                    entries.single { entry -> entry.title == "网站密码管理" },
                settings = browserSettings,
                savedCredentialCount = 3,
                credentialVaultLoading = true,
                credentialVaultAvailable = false,
            ),
        )
        val durationEntry =
            entries.single {
                it.action ==
                    KiyoriBrowserSettingsAction.SELECT_AUTOMATIC_FLOATING_MINIMUM_DURATION
            }
        assertEquals(
            "1 分钟",
            browserSettingValue(durationEntry, browserSettings),
        )
        assertTrue(
            isBrowserSettingRuntimeEnabled(
                entry = durationEntry,
                userscriptState = WebSessionUserscriptUiState(),
                settings = browserSettings,
            ),
        )
        assertFalse(
            isBrowserSettingRuntimeEnabled(
                entry = durationEntry,
                userscriptState = WebSessionUserscriptUiState(),
                settings = browserSettings.copy(automaticFloatingPlaybackEnabled = false),
            ),
        )
        val selection =
            automaticFloatingMinimumDurationSelection(
                settings = browserSettings,
                onSelect = {},
                onCustom = {},
            )
        assertEquals(
            listOf(
                "30 秒",
                "1 分钟",
                "3 分钟",
                "5 分钟",
                "10 分钟",
                "30 分钟",
                "60 分钟",
                "自定义时长",
            ),
            selection.options.map(KiyoriSettingsSelectionOption::label),
        )
        assertEquals("1 分钟", selection.currentValue)
        assertEquals(60_000L, parseAutomaticFloatingDurationSeconds("60"))
        assertEquals(90_000L, parseAutomaticFloatingDurationSeconds("90"))
        assertEquals(null, parseAutomaticFloatingDurationSeconds("0"))
        assertEquals(null, parseAutomaticFloatingDurationSeconds("86401"))
        assertEquals("1 分 30 秒", formatAutomaticFloatingMinimumDuration(90_000L))
        assertEquals("默认 · 100%", formatWebTextZoomPercent(100))
        assertEquals("135%", formatWebTextZoomPercent(135))
        assertEquals("空白页", formatBrowserHomeUrl("about:blank"))
    }

    @Test
    fun `browser homepage accepts only the persisted runtime URL contract`() {
        assertTrue(isSupportedBrowserHomeUrl("about:blank"))
        assertTrue(isSupportedBrowserHomeUrl("https://example.com/path"))
        assertTrue(isSupportedBrowserHomeUrl("http://127.0.0.1:8080"))
        assertFalse(isSupportedBrowserHomeUrl("about:settings"))
        assertFalse(isSupportedBrowserHomeUrl("javascript:alert(1)"))
        assertFalse(isSupportedBrowserHomeUrl("file:///sdcard/index.html"))
    }

    @Test
    fun `minus one page preserves data cards and quick tool order`() {
        assertEquals(listOf("收藏", "书签", "历史", "下载"), kiyoriMinusOneDataItems.map(KiyoriMinusOneDataItem::title))
        assertTrue(kiyoriMinusOneDataItems.all { item -> item.count == 0 })
        assertEquals(
            listOf("收藏"),
            kiyoriMinusOneDataItems
                .filter { item -> item.action == KiyoriMinusOneDataAction.NONE }
                .map(KiyoriMinusOneDataItem::title),
        )
        assertEquals(
            listOf("下载"),
            kiyoriMinusOneDataItems
                .filter { item -> item.action == KiyoriMinusOneDataAction.OPEN_DOWNLOAD_DRAWER }
                .map(KiyoriMinusOneDataItem::title),
        )
        assertEquals(
            listOf("历史"),
            kiyoriMinusOneDataItems
                .filter { item -> item.action == KiyoriMinusOneDataAction.OPEN_HISTORY_DRAWER }
                .map(KiyoriMinusOneDataItem::title),
        )
        assertEquals(
            listOf("新版", "手册", "版本", "搜索", "工具箱", "清理", "备份", "退出"),
            kiyoriMinusOneQuickTools.map(KiyoriMinusOneQuickTool::title),
        )
        assertEquals(
            listOf(
                WebSessionBrowserMenuTone.ADD_BOOKMARK,
                WebSessionBrowserMenuTone.BOOKMARKS,
                WebSessionBrowserMenuTone.HISTORY,
                WebSessionBrowserMenuTone.DOWNLOADS,
            ),
            kiyoriMinusOneDataItems.map(KiyoriMinusOneDataItem::tone),
        )
        assertEquals(
            listOf(
                WebSessionBrowserMenuTone.PAGE_SOURCE,
                WebSessionBrowserMenuTone.READER_MODE,
                WebSessionBrowserMenuTone.PLUGINS,
                WebSessionBrowserMenuTone.AI_DIALOGUE,
                WebSessionBrowserMenuTone.TOOLBOX,
                WebSessionBrowserMenuTone.AD_MARKING,
                WebSessionBrowserMenuTone.DOWNLOADS,
                WebSessionBrowserMenuTone.EXIT_BROWSER,
            ),
            kiyoriMinusOneQuickTools.map(KiyoriMinusOneQuickTool::tone),
        )
        assertEquals(
            kiyoriMinusOneQuickTools.size,
            kiyoriMinusOneQuickTools.map(KiyoriMinusOneQuickTool::tone).toSet().size,
        )
    }

    @Test
    fun `file page preserves category quick access and storage order`() {
        assertEquals(
            listOf("图片", "视频", "音频", "文档", "安装包", "压缩包", "标签", "下载"),
            kiyoriFileCategoryItems.map(KiyoriFileEntryItem::title),
        )
        assertEquals(
            listOf("应用集", "WPS Office", "QQ", "微信", "截屏", "录音机", "蓝牙"),
            kiyoriFileQuickAccessItems.map(KiyoriFileEntryItem::title),
        )
        assertEquals(
            listOf("手机存储", "云盘", "iCloud", "最近删除"),
            kiyoriFileStorageItems.map(KiyoriFileStorageItem::title),
        )
        assertEquals(
            listOf("手机存储"),
            kiyoriFileStorageItems.filter(KiyoriFileStorageItem::usesDeviceCapacity).map(KiyoriFileStorageItem::title),
        )
        assertTrue(
            (kiyoriFileCategoryItems + kiyoriFileQuickAccessItems).all { item -> item.count == "0项" },
        )
    }
}
