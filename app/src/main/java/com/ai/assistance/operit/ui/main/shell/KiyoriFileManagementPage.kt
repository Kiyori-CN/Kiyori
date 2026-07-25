package com.ai.assistance.operit.ui.main.shell

import android.content.Context
import android.os.StatFs
import android.text.format.Formatter
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.R

internal data class KiyoriFileEntryItem(
    val title: String,
    val count: String,
    val backgroundColors: List<Color>,
    val icon: ImageVector,
)

internal data class KiyoriFileStorageItem(
    val title: String,
    val value: String?,
    val iconResId: Int,
    val usesDeviceCapacity: Boolean = false,
)

internal val kiyoriFileCategoryItems =
    listOf(
        KiyoriFileEntryItem("图片", "0项", listOf(Color(0xFF74AFFF), Color(0xFF4F86E6)), Icons.Default.Image),
        KiyoriFileEntryItem("视频", "0项", listOf(Color(0xFFFF7888), Color(0xFFE65567)), Icons.Default.PlayCircle),
        KiyoriFileEntryItem("音频", "0项", listOf(Color(0xFF4B4B57), Color(0xFF2F3138)), Icons.Default.MusicNote),
        KiyoriFileEntryItem("文档", "0项", listOf(Color(0xFFFFE06A), Color(0xFFF4C53A)), Icons.Default.Description),
        KiyoriFileEntryItem("安装包", "0项", listOf(Color(0xFF69D690), Color(0xFF4BC16C)), Icons.Default.Android),
        KiyoriFileEntryItem("压缩包", "0项", listOf(Color(0xFFFFE06A), Color(0xFFF4C53A)), Icons.Default.Folder),
        KiyoriFileEntryItem("标签", "0项", listOf(Color(0xFF7DB3FF), Color(0xFF4A86E8)), Icons.Default.LocalOffer),
        KiyoriFileEntryItem("下载", "0项", listOf(Color(0xFFFFE7A6), Color(0xFFF5C14A)), Icons.Default.Download),
    )

internal val kiyoriFileQuickAccessItems =
    listOf(
        KiyoriFileEntryItem("应用集", "0项", listOf(Color(0xFF86B8FF), Color(0xFF5D93E8)), Icons.Default.Apps),
        KiyoriFileEntryItem("WPS Office", "0项", listOf(Color(0xFFFFA2AA), Color(0xFFFF6C7A)), Icons.Default.Description),
        KiyoriFileEntryItem("QQ", "0项", listOf(Color(0xFF5E6676), Color(0xFF373C47)), Icons.AutoMirrored.Filled.Chat),
        KiyoriFileEntryItem("微信", "0项", listOf(Color(0xFF73E98F), Color(0xFF37C95B)), Icons.Default.Forum),
        KiyoriFileEntryItem("截屏", "0项", listOf(Color(0xFF8EC0FF), Color(0xFF5E97EC)), Icons.Default.Crop),
        KiyoriFileEntryItem("录音机", "0项", listOf(Color(0xFF7284A5), Color(0xFF51627F)), Icons.Default.GraphicEq),
        KiyoriFileEntryItem("蓝牙", "0项", listOf(Color(0xFF8EC0FF), Color(0xFF5E97EC)), Icons.Default.Bluetooth),
    )

internal val kiyoriFileStorageItems =
    listOf(
        KiyoriFileStorageItem(
            "手机存储",
            null,
            R.drawable.ic_kiyori_file_storage_phone,
            usesDeviceCapacity = true,
        ),
        KiyoriFileStorageItem("云盘", null, R.drawable.ic_kiyori_file_storage_cloud_drive),
        KiyoriFileStorageItem("iCloud", null, R.drawable.ic_kiyori_file_storage_icloud),
        KiyoriFileStorageItem("最近删除", "0项", R.drawable.ic_kiyori_file_storage_recent_deleted),
    )

@Composable
internal fun KiyoriFileManagementPage(modifier: Modifier = Modifier) {
    val deviceStorageCapacity = rememberDeviceStorageCapacityLabel()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        KiyoriFileManagementTopBar()
        Spacer(modifier = Modifier.height(28.dp))
        KiyoriFileEntryGrid(kiyoriFileCategoryItems)
        Spacer(modifier = Modifier.height(36.dp))
        KiyoriFileSectionHeader("快捷访问")
        Spacer(modifier = Modifier.height(14.dp))
        KiyoriFileEntryGrid(kiyoriFileQuickAccessItems)
        Spacer(modifier = Modifier.height(36.dp))
        KiyoriFileSectionHeader("存储位置")
        Spacer(modifier = Modifier.height(14.dp))
        kiyoriFileStorageItems.forEachIndexed { index, item ->
            KiyoriFileStorageRow(
                item = item,
                value = if (item.usesDeviceCapacity) deviceStorageCapacity else item.value,
            )
            if (index != kiyoriFileStorageItems.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun KiyoriFileManagementTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF7F7F7))
                    .clickable(onClick = {})
                    .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = Color(0xFF8C8C8C), modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("搜索", fontSize = 14.sp, color = Color(0xFF8C8C8C))
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardVoice, null, tint = Color(0xFF8C8C8C), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(onClick = {}, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.MoreVert, "更多", tint = Color(0xFF111111), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun KiyoriFileSectionHeader(title: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFFB0B0B0))
        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.clickable(onClick = {}), verticalAlignment = Alignment.CenterVertically) {
            Text("全部", fontSize = 15.sp, color = Color(0xFFC3C3C3))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFFC3C3C3), modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun KiyoriFileEntryGrid(items: List<KiyoriFileEntryItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(4).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { item ->
                    KiyoriFileEntryTile(item = item, modifier = Modifier.weight(1f))
                }
                repeat(4 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KiyoriFileEntryTile(item: KiyoriFileEntryItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable(onClick = {}),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(item.backgroundColors)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, item.title, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            item.title,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF111111),
        )
        Text(item.count, fontSize = 9.sp, lineHeight = 10.sp, color = Color(0xFFC1C1C1))
    }
}

@Composable
private fun KiyoriFileStorageRow(
    item: KiyoriFileStorageItem,
    value: String?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = {})
                .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(item.iconResId),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111111))
        Spacer(modifier = Modifier.weight(1f))
        value?.let { storageValue ->
            Text(
                text = storageValue,
                fontSize = 13.sp,
                color = Color(0xFFC0C0C0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFFC0C0C0), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun rememberDeviceStorageCapacityLabel(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var label by remember(context) { mutableStateOf(readDeviceStorageCapacityLabel(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    label = readDeviceStorageCapacityLabel(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return label
}

private fun readDeviceStorageCapacityLabel(context: Context): String {
    val storage = StatFs(context.filesDir.absolutePath)
    val available = Formatter.formatFileSize(context, storage.availableBytes)
    val total = Formatter.formatFileSize(context, storage.totalBytes)
    return "可用 $available / $total"
}
