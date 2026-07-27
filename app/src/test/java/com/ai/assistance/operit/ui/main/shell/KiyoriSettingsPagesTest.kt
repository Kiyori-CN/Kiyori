package com.ai.assistance.operit.ui.main.shell

import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.isSupportedBrowserHomeUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriSettingsPagesTest {
    @Test
    fun `shared settings header follows the reference start middle and pinned frames`() {
        assertEquals("网页浏览器设置", KIYORI_BROWSER_SETTINGS_PAGE_TITLE)
        assertEquals("文件下载器设置", KIYORI_DOWNLOAD_SETTINGS_PAGE_TITLE)
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
    }

    @Test
    fun `settings home keeps four balanced groups and exposes real settings actions`() {
        assertEquals(
            listOf(4, 4, 4, 4),
            kiyoriSettingsHomeGroups.map { group -> group.size },
        )
        assertEquals(
            listOf(
                "AI 设置",
                "剪贴板口令",
                "小程序管理",
                "小程序订阅",
                "网页浏览器",
                "视频播放器",
                "音乐播放器",
                "小说阅读器",
                "文件下载器",
                "文件管理器",
                "广告拦截器",
                "日志记录器",
                "界面定制",
                "数据备份与同步",
                "开发手册与模式",
                "更多功能",
            ),
            kiyoriSettingsHomeGroups.flatten().map(KiyoriSettingsHomeEntry::title),
        )
        assertEquals(
            listOf("AI 设置"),
            kiyoriSettingsHomeGroups
                .flatten()
                .filter { entry -> entry.action == KiyoriSettingsHomeAction.OPEN_AI_SETTINGS }
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
    }

    @Test
    fun `download settings preserve legacy groups and expose every legacy consumer`() {
        assertEquals(
            listOf(5, 3, 3, 1),
            kiyoriDownloadSettingsGroups.map { group -> group.size },
        )
        assertEquals(
            listOf(
                "自定义下载器",
                "自定义下载目录",
                "同时下载任务数",
                "普通格式下载线程数",
                "M3U8下载线程数",
                "M3U8自动合并",
                "自动转存公开目录",
                "自定义下载分块大小",
                "安装包自动清理",
                "下载无需弹窗确认",
                "下载完成强提示",
                "切换下载协议",
            ),
            kiyoriDownloadSettingsGroups.flatten().map(KiyoriDownloadSettingsEntrySpec::title),
        )
        assertEquals(
            mapOf(
                "自定义下载器" to KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_ENGINE,
                "自定义下载目录" to KiyoriDownloadSettingsAction.SELECT_CUSTOM_DIRECTORY,
                "同时下载任务数" to
                    KiyoriDownloadSettingsAction.SELECT_MAX_CONCURRENT_TASKS,
                "普通格式下载线程数" to
                    KiyoriDownloadSettingsAction.SELECT_NORMAL_THREAD_COUNT,
                "M3U8下载线程数" to
                    KiyoriDownloadSettingsAction.SELECT_M3U8_THREAD_COUNT,
                "M3U8自动合并" to
                    KiyoriDownloadSettingsAction.TOGGLE_AUTO_MERGE_M3U8,
                "自动转存公开目录" to
                    KiyoriDownloadSettingsAction.TOGGLE_AUTO_TRANSFER_TO_PUBLIC_DIRECTORY,
                "自定义下载分块大小" to
                    KiyoriDownloadSettingsAction.SELECT_CHUNK_SIZE,
                "安装包自动清理" to
                    KiyoriDownloadSettingsAction.TOGGLE_AUTO_CLEAN_APK,
                "下载无需弹窗确认" to
                    KiyoriDownloadSettingsAction.TOGGLE_SKIP_CONFIRMATION,
                "下载完成强提示" to
                    KiyoriDownloadSettingsAction.TOGGLE_COMPLETION_TIP,
                "切换下载协议" to
                    KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_PROTOCOL,
            ),
            kiyoriDownloadSettingsGroups
                .flatten()
                .associate { entry -> entry.title to entry.action },
        )
        assertEquals("12MB", formatBrowserDownloadChunkSize(12288))
        assertEquals("256KB", formatBrowserDownloadChunkSize(256))
        assertEquals(13, KIYORI_DOWNLOAD_SELECTION_HEADER_VERTICAL_PADDING_DP)
        assertEquals(10, KIYORI_DOWNLOAD_SELECTION_OPTION_VERTICAL_PADDING_DP)
        assertEquals(44, KIYORI_DOWNLOAD_SELECTION_OPTION_MIN_HEIGHT_DP)
        assertEquals(20, KIYORI_DOWNLOAD_SELECTION_CHECK_ICON_SIZE_DP)
        assertEquals(13, KIYORI_DOWNLOAD_SELECTION_CANCEL_VERTICAL_PADDING_DP)
    }

    @Test
    fun `browser settings preserve legacy groups and only connect verified capabilities`() {
        assertEquals(
            listOf(5, 5, 2, 5, 6),
            kiyoriBrowserSettingsGroups.map { group -> group.size },
        )
        assertEquals(
            listOf(
                "网页插件管理",
                "悬浮嗅探播放",
                "悬浮嗅探模式",
                "返回不重载",
                "启动时恢复标签",
                "网页主页自定义",
                "标签栏样式设置",
                "手势前进后退",
                "底部上滑手势",
                "搜索引擎切换条",
                "音视频嗅探提示",
                "嗅探规则管理",
                "允许网页打开应用",
                "允许网页获取位置",
                "网页翻译接口",
                "网站配置管理",
                "网站密码管理",
                "网页字体大小",
                "强制页面缩放",
                "腾讯X5调试",
                "自定义UA设置",
                "浏览器代理替换",
                "强制新窗口打开",
            ),
            kiyoriBrowserSettingsGroups.flatten().map(KiyoriBrowserSettingsEntrySpec::title),
        )
        assertEquals(
            mapOf(
                "悬浮嗅探播放" to
                    KiyoriBrowserSettingsAction.TOGGLE_FLOATING_SNIFF_PLAYBACK,
                "网页主页自定义" to
                    KiyoriBrowserSettingsAction.OPEN_HOME_CUSTOMIZATION,
                "允许网页打开应用" to
                    KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_OPEN_APP,
                "允许网页获取位置" to
                    KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_GEOLOCATION,
            ),
            kiyoriBrowserSettingsGroups
                .flatten()
                .filter { entry ->
                    entry.action != KiyoriBrowserSettingsAction.NONE &&
                        entry.action != KiyoriBrowserSettingsAction.OPEN_PLACEHOLDER
                }
                .associate { entry -> entry.title to entry.action },
        )
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
            listOf("下载"),
            kiyoriMinusOneDataItems
                .filter { item -> item.action == KiyoriMinusOneDataAction.OPEN_DOWNLOAD_DRAWER }
                .map(KiyoriMinusOneDataItem::title),
        )
        assertEquals(
            listOf("新版", "手册", "版本", "搜索", "工具箱", "清理", "备份", "退出"),
            kiyoriMinusOneQuickTools.map(KiyoriMinusOneQuickTool::title),
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
