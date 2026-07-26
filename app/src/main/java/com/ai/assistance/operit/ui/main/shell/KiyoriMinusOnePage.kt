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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadManager

internal enum class KiyoriMinusOneDataAction {
    NONE,
    OPEN_DOWNLOAD_CENTER,
}

internal data class KiyoriMinusOneDataItem(
    val title: String,
    val count: Int,
    val accentColor: Color,
    val backgroundColors: List<Color>,
    val action: KiyoriMinusOneDataAction = KiyoriMinusOneDataAction.NONE,
)

internal data class KiyoriMinusOneQuickTool(
    val title: String,
    val iconResId: Int,
)

internal val kiyoriMinusOneDataItems =
    listOf(
        KiyoriMinusOneDataItem("收藏", 0, Color(0xFF39A95F), listOf(Color(0xFFF0FAF2), Color.White)),
        KiyoriMinusOneDataItem("书签", 0, Color(0xFFE45D65), listOf(Color(0xFFFEF0F1), Color.White)),
        KiyoriMinusOneDataItem("历史", 0, Color(0xFF6E48E6), listOf(Color(0xFFF4F0FE), Color.White)),
        KiyoriMinusOneDataItem(
            "下载",
            0,
            Color(0xFFE1BE4E),
            listOf(Color(0xFFFFF8E9), Color.White),
            KiyoriMinusOneDataAction.OPEN_DOWNLOAD_CENTER,
        ),
    )

internal val kiyoriMinusOneQuickTools =
    listOf(
        KiyoriMinusOneQuickTool("新版", R.drawable.ic_kiyori_minus_one_new),
        KiyoriMinusOneQuickTool("手册", R.drawable.ic_kiyori_minus_one_manual),
        KiyoriMinusOneQuickTool("版本", R.drawable.ic_kiyori_minus_one_version),
        KiyoriMinusOneQuickTool("搜索", R.drawable.ic_kiyori_minus_one_search),
        KiyoriMinusOneQuickTool("工具箱", R.drawable.ic_kiyori_minus_one_toolbox),
        KiyoriMinusOneQuickTool("清理", R.drawable.ic_kiyori_minus_one_clean),
        KiyoriMinusOneQuickTool("备份", R.drawable.ic_kiyori_minus_one_backup),
        KiyoriMinusOneQuickTool("退出", R.drawable.ic_kiyori_minus_one_exit),
    )

@Composable
internal fun KiyoriMinusOnePage(
    onOpenDownloadCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val downloadManager = remember(context) { BrowserDownloadManager.getInstance(context) }
    val downloadTasks by downloadManager.taskSnapshots.collectAsState()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .verticalScroll(rememberScrollState()),
    ) {
        KiyoriMinusOneTopBar()
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KiyoriMinusOneDataSection(
                downloadCount = downloadTasks.size,
                onOpenDownloadCenter = onOpenDownloadCenter,
            )
            KiyoriMinusOneQuickToolsSection()
        }
    }
}

@Composable
private fun KiyoriMinusOneTopBar() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(start = 20.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "负一屏",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111111),
        )
        IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭负一屏",
                tint = Color(0xFF6B6B6B),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun KiyoriMinusOneDataSection(
    downloadCount: Int,
    onOpenDownloadCenter: () -> Unit,
) {
    Text(
        text = "我的数据",
        fontSize = 13.sp,
        color = Color(0xFF8B8B8B),
        modifier = Modifier.padding(start = 4.dp),
    )
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            kiyoriMinusOneDataItems.forEach { item ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.verticalGradient(item.backgroundColors))
                            .clickable {
                                when (item.action) {
                                    KiyoriMinusOneDataAction.NONE -> Unit
                                    KiyoriMinusOneDataAction.OPEN_DOWNLOAD_CENTER ->
                                        onOpenDownloadCenter()
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = item.accentColor)
                        Text(
                            text =
                                if (item.action == KiyoriMinusOneDataAction.OPEN_DOWNLOAD_CENTER) {
                                    downloadCount.toString()
                                } else {
                                    item.count.toString()
                                },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = item.accentColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KiyoriMinusOneQuickToolsSection() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("快捷工具", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF131313))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF222222),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                kiyoriMinusOneQuickTools.chunked(4).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowItems.forEach { tool ->
                            Column(
                                modifier = Modifier.width(64.dp).clickable(onClick = {}),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(tool.iconResId),
                                        contentDescription = tool.title,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                                Text(
                                    text = tool.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF111111),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
