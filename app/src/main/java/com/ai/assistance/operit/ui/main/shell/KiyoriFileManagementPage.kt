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
import androidx.compose.foundation.layout.heightIn
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
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriUiShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

internal data class KiyoriFileEntryItem(
    val title: String,
    val count: String,
    val tone: KiyoriSemanticTone,
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
        KiyoriFileEntryItem("图片", "0项", KiyoriSemanticTone.PINK, Icons.Default.Image),
        KiyoriFileEntryItem("视频", "0项", KiyoriSemanticTone.RED, Icons.Default.PlayCircle),
        KiyoriFileEntryItem("音频", "0项", KiyoriSemanticTone.PURPLE, Icons.Default.MusicNote),
        KiyoriFileEntryItem("文档", "0项", KiyoriSemanticTone.ORANGE, Icons.Default.Description),
        KiyoriFileEntryItem("安装包", "0项", KiyoriSemanticTone.GREEN, Icons.Default.Android),
        KiyoriFileEntryItem("压缩包", "0项", KiyoriSemanticTone.ORANGE, Icons.Default.Folder),
        KiyoriFileEntryItem("标签", "0项", KiyoriSemanticTone.BLUE, Icons.Default.LocalOffer),
        KiyoriFileEntryItem("下载", "0项", KiyoriSemanticTone.GREEN, Icons.Default.Download),
    )

internal val kiyoriFileQuickAccessItems =
    listOf(
        KiyoriFileEntryItem("应用集", "0项", KiyoriSemanticTone.BLUE, Icons.Default.Apps),
        KiyoriFileEntryItem("WPS Office", "0项", KiyoriSemanticTone.RED, Icons.Default.Description),
        KiyoriFileEntryItem("QQ", "0项", KiyoriSemanticTone.BLUE, Icons.AutoMirrored.Filled.Chat),
        KiyoriFileEntryItem("微信", "0项", KiyoriSemanticTone.GREEN, Icons.Default.Forum),
        KiyoriFileEntryItem("截屏", "0项", KiyoriSemanticTone.CYAN, Icons.Default.Crop),
        KiyoriFileEntryItem("录音机", "0项", KiyoriSemanticTone.PURPLE, Icons.Default.GraphicEq),
        KiyoriFileEntryItem("蓝牙", "0项", KiyoriSemanticTone.BLUE, Icons.Default.Bluetooth),
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
internal fun KiyoriFileManagementPage(
    onOpenPhoneStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deviceStorageCapacity = rememberDeviceStorageCapacityLabel()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                onClick = if (item.usesDeviceCapacity) onOpenPhoneStorage else null,
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
        Surface(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(KiyoriUiShapes.field),
            shape = KiyoriUiShapes.field,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    modifier = Modifier.size(19.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "搜索",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.KeyboardVoice,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.MoreVert,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun KiyoriFileSectionHeader(title: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "全部",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.size(18.dp),
            )
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
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KiyoriSemanticIconBadge(
            imageVector = item.icon,
            tone = item.tone,
            contentDescription = item.title,
            containerSize = 40.dp,
            iconSize = 18.dp,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            item.count,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun KiyoriFileStorageRow(
    item: KiyoriFileStorageItem,
    value: String?,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(KiyoriUiShapes.card)
                .clickable(enabled = onClick != null) { onClick?.invoke() }
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
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        value?.let { storageValue ->
            Text(
                text = storageValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(18.dp),
        )
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
