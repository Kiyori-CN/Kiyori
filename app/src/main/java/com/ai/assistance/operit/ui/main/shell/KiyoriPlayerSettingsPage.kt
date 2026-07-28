package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.player.Anime4KMode
import com.ai.assistance.operit.core.player.PLAYER_SEEK_STEP_OPTIONS
import com.ai.assistance.operit.core.player.PLAYER_SPEED_OPTIONS
import com.ai.assistance.operit.core.player.PLAYER_SUBTITLE_SCALE_OPTIONS
import com.ai.assistance.operit.core.player.PlayerBackgroundBehavior
import com.ai.assistance.operit.core.player.PlayerEndBehavior
import com.ai.assistance.operit.core.player.PlayerFullscreenExitBehavior
import com.ai.assistance.operit.core.player.PlayerHardwareDecodingPolicy
import com.ai.assistance.operit.core.player.PlayerNetworkCachePolicy
import com.ai.assistance.operit.core.player.PlayerSettings
import com.ai.assistance.operit.core.player.PlayerSettingsStore
import java.util.Locale

internal const val KIYORI_PLAYER_SETTINGS_PAGE_TITLE = "视频播放器设置"

private enum class PlayerSettingKey {
    HARDWARE_DECODING,
    DEFAULT_SPEED,
    PRECISE_SEEKING,
    SEEK_STEP,
    NETWORK_CACHE,
    SUBTITLE_SCALE,
    BACKGROUND_BEHAVIOR,
    FULLSCREEN_EXIT_BEHAVIOR,
    END_BEHAVIOR,
    ANIME4K_MODE,
}

private val playerSettingGroups =
    listOf(
        listOf(
            "硬件解码策略" to PlayerSettingKey.HARDWARE_DECODING,
            "默认倍速" to PlayerSettingKey.DEFAULT_SPEED,
            "精准进度定位" to PlayerSettingKey.PRECISE_SEEKING,
        ),
        listOf(
            "快进/快退时长" to PlayerSettingKey.SEEK_STEP,
            "网络缓存" to PlayerSettingKey.NETWORK_CACHE,
            "字幕缩放" to PlayerSettingKey.SUBTITLE_SCALE,
        ),
        listOf(
            "后台播放" to PlayerSettingKey.BACKGROUND_BEHAVIOR,
            "全屏返回行为" to PlayerSettingKey.FULLSCREEN_EXIT_BEHAVIOR,
            "播放结束行为" to PlayerSettingKey.END_BEHAVIOR,
        ),
        listOf("Anime4K 模式" to PlayerSettingKey.ANIME4K_MODE),
    )

private data class PlayerSettingOption(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

private data class PlayerSettingSelection(
    val title: String,
    val currentValue: String,
    val options: List<PlayerSettingOption>,
)

@Composable
internal fun KiyoriPlayerSettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { PlayerSettingsStore.getInstance(context) }
    val settings by store.state.collectAsState()
    var selection by remember { mutableStateOf<PlayerSettingSelection?>(null) }

    KiyoriCollapsingSettingsPage(
        title = KIYORI_PLAYER_SETTINGS_PAGE_TITLE,
        onBack = onBack,
        modifier = modifier,
    ) {
        itemsIndexed(playerSettingGroups) { _, group ->
            KiyoriSettingsGroupCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
            ) {
                group.forEachIndexed { index, (title, key) ->
                    PlayerSettingsRow(
                        title = title,
                        value = playerSettingValue(key, settings),
                        onClick = {
                            selection = playerSettingSelection(key, title, settings, store)
                        },
                    )
                    if (index != group.lastIndex) {
                        HorizontalDivider(color = Color(0xFFF2F2EE), thickness = 0.6.dp)
                    }
                }
            }
        }
    }

    selection?.let { current ->
        PlayerSettingSelectionSheet(
            selection = current,
            onDismiss = { selection = null },
            onSelect = { option ->
                option.onSelect()
                selection = null
            },
        )
    }
}

@Composable
private fun PlayerSettingsRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 18.dp, end = 14.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2B2B2B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = Color(0xFF9A9895),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(7.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFBDBDB8),
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun playerSettingValue(key: PlayerSettingKey, settings: PlayerSettings): String =
    when (key) {
        PlayerSettingKey.HARDWARE_DECODING -> hardwareDecodingLabel(settings.hardwareDecodingPolicy)
        PlayerSettingKey.DEFAULT_SPEED -> formatPlayerSettingSpeed(settings.defaultSpeed)
        PlayerSettingKey.PRECISE_SEEKING -> if (settings.preciseSeeking) "开启" else "关闭"
        PlayerSettingKey.SEEK_STEP -> "${settings.seekStepSeconds} 秒"
        PlayerSettingKey.NETWORK_CACHE -> networkCacheLabel(settings.networkCachePolicy)
        PlayerSettingKey.SUBTITLE_SCALE -> formatSubtitleScale(settings.subtitleScale)
        PlayerSettingKey.BACKGROUND_BEHAVIOR -> backgroundBehaviorLabel(settings.backgroundBehavior)
        PlayerSettingKey.FULLSCREEN_EXIT_BEHAVIOR ->
            fullscreenExitBehaviorLabel(settings.fullscreenExitBehavior)
        PlayerSettingKey.END_BEHAVIOR -> endBehaviorLabel(settings.endBehavior)
        PlayerSettingKey.ANIME4K_MODE -> anime4KLabel(settings.anime4KMode)
    }

private fun playerSettingSelection(
    key: PlayerSettingKey,
    title: String,
    settings: PlayerSettings,
    store: PlayerSettingsStore,
): PlayerSettingSelection {
    val options =
        when (key) {
            PlayerSettingKey.HARDWARE_DECODING ->
                PlayerHardwareDecodingPolicy.entries.map { value ->
                    PlayerSettingOption(
                        hardwareDecodingLabel(value),
                        value == settings.hardwareDecodingPolicy,
                    ) { store.setHardwareDecodingPolicy(value) }
                }
            PlayerSettingKey.DEFAULT_SPEED ->
                PLAYER_SPEED_OPTIONS.map { value ->
                    PlayerSettingOption(
                        formatPlayerSettingSpeed(value),
                        value == settings.defaultSpeed,
                    ) { store.setDefaultSpeed(value) }
                }
            PlayerSettingKey.PRECISE_SEEKING ->
                listOf(true, false).map { enabled ->
                    PlayerSettingOption(
                        if (enabled) "开启" else "关闭",
                        enabled == settings.preciseSeeking,
                    ) { store.setPreciseSeeking(enabled) }
                }
            PlayerSettingKey.SEEK_STEP ->
                PLAYER_SEEK_STEP_OPTIONS.map { value ->
                    PlayerSettingOption("$value 秒", value == settings.seekStepSeconds) {
                        store.setSeekStepSeconds(value)
                    }
                }
            PlayerSettingKey.NETWORK_CACHE ->
                PlayerNetworkCachePolicy.entries.map { value ->
                    PlayerSettingOption(
                        networkCacheLabel(value),
                        value == settings.networkCachePolicy,
                    ) { store.setNetworkCachePolicy(value) }
                }
            PlayerSettingKey.SUBTITLE_SCALE ->
                PLAYER_SUBTITLE_SCALE_OPTIONS.map { value ->
                    PlayerSettingOption(
                        formatSubtitleScale(value),
                        value == settings.subtitleScale,
                    ) { store.setSubtitleScale(value) }
                }
            PlayerSettingKey.BACKGROUND_BEHAVIOR ->
                PlayerBackgroundBehavior.entries.map { value ->
                    PlayerSettingOption(
                        backgroundBehaviorLabel(value),
                        value == settings.backgroundBehavior,
                    ) { store.setBackgroundBehavior(value) }
                }
            PlayerSettingKey.FULLSCREEN_EXIT_BEHAVIOR ->
                PlayerFullscreenExitBehavior.entries.map { value ->
                    PlayerSettingOption(
                        fullscreenExitBehaviorLabel(value),
                        value == settings.fullscreenExitBehavior,
                    ) { store.setFullscreenExitBehavior(value) }
                }
            PlayerSettingKey.END_BEHAVIOR ->
                PlayerEndBehavior.entries.map { value ->
                    PlayerSettingOption(endBehaviorLabel(value), value == settings.endBehavior) {
                        store.setEndBehavior(value)
                    }
                }
            PlayerSettingKey.ANIME4K_MODE ->
                Anime4KMode.entries.map { value ->
                    PlayerSettingOption(anime4KLabel(value), value == settings.anime4KMode) {
                        store.setAnime4KMode(value)
                    }
                }
        }
    return PlayerSettingSelection(
        title = title,
        currentValue = playerSettingValue(key, settings),
        options = options,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSettingSelectionSheet(
    selection: PlayerSettingSelection,
    onDismiss: () -> Unit,
    onSelect: (PlayerSettingOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White,
        scrimColor = Color(0x73000000),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                text = "${selection.title}，当前：${selection.currentValue}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            )
            HorizontalDivider(color = Color(0xFFEFEFEF))
            selection.options.forEach { option ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable { onSelect(option) }
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(option.label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (option.selected) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
                HorizontalDivider(color = Color(0xFFEFEFEF))
            }
            Text(
                text = "取消",
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(vertical = 17.dp),
            )
        }
    }
}

private fun hardwareDecodingLabel(value: PlayerHardwareDecodingPolicy): String =
    when (value) {
        PlayerHardwareDecodingPolicy.AUTOMATIC -> "自动"
        PlayerHardwareDecodingPolicy.MEDIA_CODEC -> "MediaCodec 直通"
        PlayerHardwareDecodingPolicy.MEDIA_CODEC_COPY -> "MediaCodec Copy"
        PlayerHardwareDecodingPolicy.SOFTWARE -> "软件解码"
    }

private fun backgroundBehaviorLabel(value: PlayerBackgroundBehavior): String =
    when (value) {
        PlayerBackgroundBehavior.PAUSE -> "进入后台时暂停"
        PlayerBackgroundBehavior.CONTINUE -> "进入后台时继续"
    }

private fun fullscreenExitBehaviorLabel(value: PlayerFullscreenExitBehavior): String =
    when (value) {
        PlayerFullscreenExitBehavior.RETURN_TO_FLOATING -> "返回浏览器悬浮窗"
        PlayerFullscreenExitBehavior.CLOSE -> "关闭播放器"
    }

private fun networkCacheLabel(value: PlayerNetworkCachePolicy): String =
    when (value) {
        PlayerNetworkCachePolicy.COMPACT -> "节省内存 · 64 MB"
        PlayerNetworkCachePolicy.BALANCED -> "平衡 · 128 MB"
        PlayerNetworkCachePolicy.LARGE -> "大缓存 · 256 MB"
    }

private fun endBehaviorLabel(value: PlayerEndBehavior): String =
    when (value) {
        PlayerEndBehavior.PAUSE -> "停在结尾"
        PlayerEndBehavior.CLOSE -> "关闭播放器"
        PlayerEndBehavior.LOOP -> "单曲循环"
    }

private fun anime4KLabel(value: Anime4KMode): String =
    when (value) {
        Anime4KMode.OFF -> "关闭"
        Anime4KMode.FAST -> "快速"
        Anime4KMode.BALANCED -> "平衡"
        Anime4KMode.QUALITY -> "高质量"
    }

private fun formatPlayerSettingSpeed(value: Double): String =
    if (value % 1.0 == 0.0) {
        "${value.toInt()}x"
    } else {
        String.format(Locale.US, "%.2gx", value)
    }

private fun formatSubtitleScale(value: Double): String = "${(value * 100).toInt()}%"
