package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.player.PLAYER_SEEK_STEP_OPTIONS
import com.ai.assistance.operit.core.player.PLAYER_SPEED_OPTIONS
import com.ai.assistance.operit.core.player.PlayerDecoderPreset
import com.ai.assistance.operit.core.player.PlayerEndBehavior
import com.ai.assistance.operit.core.player.PlayerFullscreenExitBehavior
import com.ai.assistance.operit.core.player.PlayerSettings
import com.ai.assistance.operit.core.player.PlayerSettingsStore
import java.util.Locale

internal const val KIYORI_PLAYER_SETTINGS_PAGE_TITLE = "视频播放器设置"

internal enum class KiyoriPlayerSettingsEntryKind {
    NAVIGATION,
    TOGGLE,
}

internal enum class KiyoriPlayerSettingsAction {
    NONE,
    SELECT_DECODER_PRESET,
    SELECT_DEFAULT_SPEED,
    SELECT_FULLSCREEN_EXIT_BEHAVIOR,
    SELECT_SEEK_STEP,
    TOGGLE_END_AUTO_RETURN,
    TOGGLE_GPU_NEXT,
    TOGGLE_VULKAN,
    TOGGLE_REMEMBER_ANIME4K,
    TOGGLE_REMEMBER_PLAYBACK_SPEED,
    TOGGLE_VOLUME_BOOST,
}

internal data class KiyoriPlayerSettingsEntrySpec(
    val title: String,
    val kind: KiyoriPlayerSettingsEntryKind,
    val value: String? = null,
    val staticToggleValue: Boolean = false,
    val action: KiyoriPlayerSettingsAction = KiyoriPlayerSettingsAction.NONE,
)

private fun playerNavigationSpec(
    title: String,
    value: String? = null,
    action: KiyoriPlayerSettingsAction = KiyoriPlayerSettingsAction.NONE,
): KiyoriPlayerSettingsEntrySpec =
    KiyoriPlayerSettingsEntrySpec(
        title = title,
        kind = KiyoriPlayerSettingsEntryKind.NAVIGATION,
        value = value,
        action = action,
    )

private fun playerToggleSpec(
    title: String,
    staticToggleValue: Boolean,
    action: KiyoriPlayerSettingsAction = KiyoriPlayerSettingsAction.NONE,
): KiyoriPlayerSettingsEntrySpec =
    KiyoriPlayerSettingsEntrySpec(
        title = title,
        kind = KiyoriPlayerSettingsEntryKind.TOGGLE,
        staticToggleValue = staticToggleValue,
        action = action,
    )

internal val kiyoriPlayerSettingsGroups =
    listOf(
        listOf(
            playerNavigationSpec("极速播放模式2.0"),
            playerNavigationSpec("自定义播放器"),
            playerNavigationSpec("备用播放器"),
            playerNavigationSpec("跳过片头片尾"),
        ),
        listOf(
            playerNavigationSpec("小窗模式"),
            playerToggleSpec("AI全屏显示", staticToggleValue = true),
            playerNavigationSpec(
                "直接全屏播放/返回",
                action = KiyoriPlayerSettingsAction.SELECT_FULLSCREEN_EXIT_BEHAVIOR,
            ),
            playerToggleSpec("重力感应自动横屏", staticToggleValue = false),
            playerToggleSpec("双指捏合缩放", staticToggleValue = true),
        ),
        listOf(
            playerToggleSpec("与其他应用同时播放", staticToggleValue = true),
            playerToggleSpec("流量网络下自动播放", staticToggleValue = false),
            playerNavigationSpec("蓝牙断开自动暂停", value = "仅音乐"),
            playerToggleSpec("非Wifi网络提示", staticToggleValue = true),
            playerToggleSpec("音乐失败自动下一曲", staticToggleValue = false),
            playerNavigationSpec("视频播放跳转"),
            playerToggleSpec(
                "视频播放完自动返回",
                staticToggleValue = true,
                action = KiyoriPlayerSettingsAction.TOGGLE_END_AUTO_RETURN,
            ),
            playerNavigationSpec("投屏复制链接"),
        ),
        listOf(
            playerNavigationSpec(
                "倍速记忆设置",
                action = KiyoriPlayerSettingsAction.SELECT_DEFAULT_SPEED,
            ),
            playerNavigationSpec("长按倍速设置"),
            playerNavigationSpec(
                "双击快进快退",
                value = "10s",
                action = KiyoriPlayerSettingsAction.SELECT_SEEK_STEP,
            ),
            playerToggleSpec("全局底部进度条", staticToggleValue = false),
        ),
        listOf(
            playerToggleSpec("隧道播放模式", staticToggleValue = true),
            playerNavigationSpec("清除播放进度"),
            playerNavigationSpec("自定义投屏"),
        ),
        listOf(playerNavigationSpec("M3U8广告清除")),
        listOf(
            playerNavigationSpec(
                "解码器预设",
                value = PlayerDecoderPreset.FAST.displayName,
                action = KiyoriPlayerSettingsAction.SELECT_DECODER_PRESET,
            ),
            playerToggleSpec(
                "GPU Next 渲染",
                staticToggleValue = false,
                action = KiyoriPlayerSettingsAction.TOGGLE_GPU_NEXT,
            ),
            playerToggleSpec(
                "Vulkan 渲染上下文",
                staticToggleValue = false,
                action = KiyoriPlayerSettingsAction.TOGGLE_VULKAN,
            ),
            playerToggleSpec(
                "记忆超分模式",
                staticToggleValue = false,
                action = KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_ANIME4K,
            ),
            playerToggleSpec(
                "记忆播放倍速",
                staticToggleValue = false,
                action = KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_PLAYBACK_SPEED,
            ),
            playerToggleSpec(
                "音量增强",
                staticToggleValue = false,
                action = KiyoriPlayerSettingsAction.TOGGLE_VOLUME_BOOST,
            ),
        ),
    )

private data class PlayerSettingOption(
    val label: String,
    val description: String? = null,
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
        itemsIndexed(kiyoriPlayerSettingsGroups) { _, group ->
            KiyoriSettingsGroupCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
            ) {
                group.forEachIndexed { index, entry ->
                    KiyoriPlayerSettingsRow(
                        entry = entry,
                        settings = settings,
                        onClick = {
                            when (entry.action) {
                                KiyoriPlayerSettingsAction.SELECT_DECODER_PRESET,
                                KiyoriPlayerSettingsAction.SELECT_DEFAULT_SPEED,
                                KiyoriPlayerSettingsAction.SELECT_FULLSCREEN_EXIT_BEHAVIOR,
                                KiyoriPlayerSettingsAction.SELECT_SEEK_STEP ->
                                    selection = playerSettingSelection(entry, settings, store)
                                KiyoriPlayerSettingsAction.TOGGLE_END_AUTO_RETURN ->
                                    store.setEndBehavior(
                                        if (settings.endBehavior == PlayerEndBehavior.CLOSE) {
                                            PlayerEndBehavior.PAUSE
                                        } else {
                                            PlayerEndBehavior.CLOSE
                                        },
                                    )
                                KiyoriPlayerSettingsAction.TOGGLE_GPU_NEXT ->
                                    store.setGpuNextEnabled(!settings.gpuNextEnabled)
                                KiyoriPlayerSettingsAction.TOGGLE_VULKAN ->
                                    store.setVulkanEnabled(!settings.vulkanEnabled)
                                KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_ANIME4K ->
                                    store.setRememberAnime4KMode(!settings.rememberAnime4KMode)
                                KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_PLAYBACK_SPEED ->
                                    store.setRememberPlaybackSpeed(!settings.rememberPlaybackSpeed)
                                KiyoriPlayerSettingsAction.TOGGLE_VOLUME_BOOST ->
                                    store.setVolumeBoostEnabled(!settings.volumeBoostEnabled)
                                KiyoriPlayerSettingsAction.NONE -> Unit
                            }
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
private fun KiyoriPlayerSettingsRow(
    entry: KiyoriPlayerSettingsEntrySpec,
    settings: PlayerSettings,
    onClick: () -> Unit,
) {
    val value =
        when (entry.action) {
            KiyoriPlayerSettingsAction.SELECT_DECODER_PRESET ->
                settings.decoderPreset.displayName
            KiyoriPlayerSettingsAction.SELECT_DEFAULT_SPEED ->
                formatPlayerSettingSpeed(settings.defaultSpeed)
            KiyoriPlayerSettingsAction.SELECT_SEEK_STEP -> "${settings.seekStepSeconds}s"
            else -> entry.value
        }
    val toggleValue =
        when (entry.action) {
            KiyoriPlayerSettingsAction.TOGGLE_END_AUTO_RETURN ->
                settings.endBehavior == PlayerEndBehavior.CLOSE
            KiyoriPlayerSettingsAction.TOGGLE_GPU_NEXT -> settings.gpuNextEnabled
            KiyoriPlayerSettingsAction.TOGGLE_VULKAN -> settings.vulkanEnabled
            KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_ANIME4K ->
                settings.rememberAnime4KMode
            KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_PLAYBACK_SPEED ->
                settings.rememberPlaybackSpeed
            KiyoriPlayerSettingsAction.TOGGLE_VOLUME_BOOST -> settings.volumeBoostEnabled
            else -> entry.staticToggleValue
        }
    val clickableModifier =
        if (entry.action == KiyoriPlayerSettingsAction.NONE) {
            Modifier
        } else {
            Modifier.clickable(onClick = onClick)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(clickableModifier)
                .padding(start = 18.dp, end = 14.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2B2B2B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        when (entry.kind) {
            KiyoriPlayerSettingsEntryKind.NAVIGATION -> {
                value?.let {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = Color(0xFF9A9895),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                Spacer(Modifier.width(7.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFFBDBDB8),
                    modifier = Modifier.size(18.dp),
                )
            }
            KiyoriPlayerSettingsEntryKind.TOGGLE ->
                KiyoriPlayerSettingsCheckIndicator(enabled = toggleValue)
        }
    }
}

@Composable
private fun KiyoriPlayerSettingsCheckIndicator(enabled: Boolean) {
    Box(
        modifier =
            Modifier
                .size(17.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (enabled) Color(0xFFCBBEFF) else Color.White)
                .border(
                    width = if (enabled) 0.dp else 1.dp,
                    color = if (enabled) Color.Transparent else Color(0xFFBAB4AE),
                    shape = RoundedCornerShape(3.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

private fun playerSettingSelection(
    entry: KiyoriPlayerSettingsEntrySpec,
    settings: PlayerSettings,
    store: PlayerSettingsStore,
): PlayerSettingSelection {
    val options =
        when (entry.action) {
            KiyoriPlayerSettingsAction.SELECT_DECODER_PRESET ->
                PlayerDecoderPreset.entries.map { value ->
                    PlayerSettingOption(
                        label = value.displayName,
                        description = value.description,
                        selected = value == settings.decoderPreset,
                    ) { store.setDecoderPreset(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_DEFAULT_SPEED ->
                PLAYER_SPEED_OPTIONS.map { value ->
                    PlayerSettingOption(
                        label = formatPlayerSettingSpeed(value),
                        selected = value == settings.defaultSpeed,
                    ) { store.setDefaultSpeed(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_FULLSCREEN_EXIT_BEHAVIOR ->
                PlayerFullscreenExitBehavior.entries.map { value ->
                    PlayerSettingOption(
                        label =
                            when (value) {
                                PlayerFullscreenExitBehavior.RETURN_TO_FLOATING ->
                                    "返回浏览器悬浮窗"
                                PlayerFullscreenExitBehavior.CLOSE -> "关闭播放器"
                            },
                        selected = value == settings.fullscreenExitBehavior,
                    ) { store.setFullscreenExitBehavior(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_SEEK_STEP ->
                PLAYER_SEEK_STEP_OPTIONS.map { value ->
                    PlayerSettingOption(
                        label = "${value}s",
                        selected = value == settings.seekStepSeconds,
                    ) { store.setSeekStepSeconds(value) }
                }
            else -> error("Player setting action does not own a selection sheet: ${entry.action}")
        }
    val currentValue =
        when (entry.action) {
            KiyoriPlayerSettingsAction.SELECT_DECODER_PRESET ->
                settings.decoderPreset.displayName
            KiyoriPlayerSettingsAction.SELECT_DEFAULT_SPEED ->
                formatPlayerSettingSpeed(settings.defaultSpeed)
            KiyoriPlayerSettingsAction.SELECT_FULLSCREEN_EXIT_BEHAVIOR ->
                if (
                    settings.fullscreenExitBehavior ==
                    PlayerFullscreenExitBehavior.RETURN_TO_FLOATING
                ) {
                    "返回浏览器悬浮窗"
                } else {
                    "关闭播放器"
                }
            KiyoriPlayerSettingsAction.SELECT_SEEK_STEP -> "${settings.seekStepSeconds}s"
        }
    return PlayerSettingSelection(
        title = entry.title,
        currentValue = currentValue,
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
                    Column(modifier = Modifier.weight(1f).padding(end = 14.dp)) {
                        Text(option.label, fontSize = 14.sp)
                        option.description?.let { description ->
                            Text(
                                text = description,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = Color(0xFF777570),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    if (option.selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFEFEFEF))
            }
            Text(
                text = "取消",
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 17.dp),
            )
        }
    }
}

private fun formatPlayerSettingSpeed(value: Double): String =
    if (value % 1.0 == 0.0) {
        "${value.toInt()}x"
    } else {
        String.format(Locale.US, "%.2gx", value)
    }
