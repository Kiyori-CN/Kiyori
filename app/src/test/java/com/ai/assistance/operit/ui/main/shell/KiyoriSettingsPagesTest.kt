package com.ai.assistance.operit.ui.main.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriSettingsPagesTest {
    @Test
    fun `settings home keeps four balanced groups and exposes one AI settings action`() {
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
    }

    @Test
    fun `minus one page preserves data cards and quick tool order`() {
        assertEquals(listOf("收藏", "书签", "历史", "下载"), kiyoriMinusOneDataItems.map(KiyoriMinusOneDataItem::title))
        assertTrue(kiyoriMinusOneDataItems.all { item -> item.count == 0 })
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
