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
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

internal data class KiyoriSettingsHomeEntry(
    val title: String,
    val icon: ImageVector,
    val iconTint: Color,
    val action: KiyoriSettingsHomeAction = KiyoriSettingsHomeAction.NONE,
)

internal enum class KiyoriSettingsHomeAction {
    NONE,
    OPEN_AI_SETTINGS,
    OPEN_BROWSER_SETTINGS,
    OPEN_DOWNLOAD_SETTINGS,
}

internal val kiyoriSettingsHomeGroups =
    listOf(
        listOf(
            KiyoriSettingsHomeEntry(
                "AI 设置",
                Icons.Outlined.SmartToy,
                Color(0xFF1E88E5),
                KiyoriSettingsHomeAction.OPEN_AI_SETTINGS,
            ),
            KiyoriSettingsHomeEntry("剪贴板口令", Icons.Default.ContentPaste, Color(0xFF46C785)),
            KiyoriSettingsHomeEntry("小程序管理", Icons.Default.Apps, Color(0xFF59BCE8)),
            KiyoriSettingsHomeEntry("小程序订阅", Icons.Default.GridView, Color(0xFF5AA9EA)),
        ),
        listOf(
            KiyoriSettingsHomeEntry(
                "网页浏览器",
                Icons.Default.Language,
                Color(0xFF6A96F2),
                KiyoriSettingsHomeAction.OPEN_BROWSER_SETTINGS,
            ),
            KiyoriSettingsHomeEntry("视频播放器", Icons.Default.PlayCircle, Color(0xFFF06E71)),
            KiyoriSettingsHomeEntry("音乐播放器", Icons.Default.Audiotrack, Color(0xFF8A6FF2)),
            KiyoriSettingsHomeEntry("小说阅读器", Icons.AutoMirrored.Filled.MenuBook, Color(0xFFCC935C)),
        ),
        listOf(
            KiyoriSettingsHomeEntry(
                "文件下载器",
                Icons.Default.Download,
                Color(0xFFF27D84),
                KiyoriSettingsHomeAction.OPEN_DOWNLOAD_SETTINGS,
            ),
            KiyoriSettingsHomeEntry("文件管理器", Icons.Default.Folder, Color(0xFF56B38A)),
            KiyoriSettingsHomeEntry("广告拦截器", Icons.Default.Block, Color(0xFF58C68E)),
            KiyoriSettingsHomeEntry("日志记录器", Icons.Default.BugReport, Color(0xFF5A8FD8)),
        ),
        listOf(
            KiyoriSettingsHomeEntry("界面定制", Icons.Default.Palette, Color(0xFFE575A5)),
            KiyoriSettingsHomeEntry("数据备份与同步", Icons.Default.Backup, Color(0xFF4FAEE9)),
            KiyoriSettingsHomeEntry("开发手册与模式", Icons.AutoMirrored.Filled.MenuBook, Color(0xFF5DBDCC)),
            KiyoriSettingsHomeEntry("更多功能", Icons.Default.Widgets, Color(0xFF5E9CEE)),
        ),
    )

@Composable
internal fun KiyoriSettingsHomePage(
    onOpenAiSettings: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFFF5F5F2)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { KiyoriSettingsHomeHeader() }
        itemsIndexed(kiyoriSettingsHomeGroups) { _, group ->
            KiyoriSettingsHomeGroupCard(
                entries = group,
                onOpenAiSettings = onOpenAiSettings,
                onOpenBrowserSettings = onOpenBrowserSettings,
                onOpenDownloadSettings = onOpenDownloadSettings,
            )
        }
        item { Spacer(modifier = Modifier.height(96.dp)) }
    }
}

@Composable
private fun KiyoriSettingsHomeHeader() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F2))
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "设置",
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF202020),
            modifier = Modifier.weight(1f),
        )
        KiyoriSettingsHeaderAction(R.drawable.ic_kiyori_settings_header_top_search, "搜索")
        KiyoriSettingsHeaderAction(R.drawable.ic_kiyori_settings_header_scan, "扫描")
        KiyoriSettingsHeaderAction(R.drawable.ic_kiyori_settings_header_top_refresh, "刷新")
        KiyoriSettingsHeaderAction(R.drawable.ic_kiyori_settings_header_sun, "外观")
    }
}

@Composable
private fun KiyoriSettingsHeaderAction(
    iconResId: Int,
    contentDescription: String,
) {
    Box(
        modifier = Modifier.size(36.dp).clickable(onClick = {}).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = contentDescription,
            tint = Color(0xFF72726E),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun KiyoriSettingsHomeGroupCard(
    entries: List<KiyoriSettingsHomeEntry>,
    onOpenAiSettings: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, entry ->
                KiyoriSettingsHomeRow(
                    entry = entry,
                    onOpenAiSettings = onOpenAiSettings,
                    onOpenBrowserSettings = onOpenBrowserSettings,
                    onOpenDownloadSettings = onOpenDownloadSettings,
                )
                if (index != entries.lastIndex) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(0.6.dp)
                                .background(Color(0xFFF2F2EE)),
                    )
                }
            }
        }
    }
}

@Composable
private fun KiyoriSettingsHomeRow(
    entry: KiyoriSettingsHomeEntry,
    onOpenAiSettings: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    when (entry.action) {
                        KiyoriSettingsHomeAction.NONE -> Unit
                        KiyoriSettingsHomeAction.OPEN_AI_SETTINGS -> onOpenAiSettings()
                        KiyoriSettingsHomeAction.OPEN_BROWSER_SETTINGS -> onOpenBrowserSettings()
                        KiyoriSettingsHomeAction.OPEN_DOWNLOAD_SETTINGS -> onOpenDownloadSettings()
                    }
                }
                .padding(start = 16.dp, end = 14.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = entry.icon,
                contentDescription = entry.title,
                tint = entry.iconTint,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = entry.title,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2B2B2B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFBDBDB8),
            modifier = Modifier.size(18.dp),
        )
    }
}
