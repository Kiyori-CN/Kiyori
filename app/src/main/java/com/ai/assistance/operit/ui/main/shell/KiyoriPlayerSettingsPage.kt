package com.ai.assistance.operit.ui.main.shell

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import com.ai.assistance.operit.core.player.Anime4KMode
import com.ai.assistance.operit.core.player.PLAYER_DOUBLE_TAP_SEEK_OPTIONS
import com.ai.assistance.operit.core.player.PLAYER_SEEK_STEP_OPTIONS
import com.ai.assistance.operit.core.player.PLAYER_SPEED_OPTIONS
import com.ai.assistance.operit.core.player.PLAYER_SUBTITLE_SCALE_OPTIONS
import com.ai.assistance.operit.core.player.PlayerBackgroundBehavior
import com.ai.assistance.operit.core.player.PlayerDecoderBackend
import com.ai.assistance.operit.core.player.PlayerDoubleTapAction
import com.ai.assistance.operit.core.player.PlayerFullscreenExitBehavior
import com.ai.assistance.operit.core.player.PlayerNetworkCachePolicy
import com.ai.assistance.operit.core.player.PlayerQueueEndBehavior
import com.ai.assistance.operit.core.player.PlayerRenderingProfile
import com.ai.assistance.operit.core.player.PlayerSettings
import com.ai.assistance.operit.core.player.PlayerSettingsStore
import com.ai.assistance.operit.core.player.formatPlayerSpeedLabel
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadManager
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettings
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettingsStore
import com.ai.assistance.operit.util.AppLogger

internal const val KIYORI_PLAYER_SETTINGS_PAGE_TITLE = "视频播放器"

internal enum class KiyoriPlayerSettingsDependency {
    ALWAYS,
    DOUBLE_TAP_SEEK,
    REMEMBER_ANIME4K,
}

internal enum class KiyoriPlayerSettingsAction {
    SELECT_DEFAULT_SPEED,
    TOGGLE_REMEMBER_PLAYBACK_SPEED,
    TOGGLE_AUTO_PLAY_NEXT,
    SELECT_QUEUE_END_BEHAVIOR,
    SELECT_DOUBLE_TAP_ACTION,
    SELECT_DOUBLE_TAP_SEEK_STEP,
    TOGGLE_LONG_PRESS_SPEED_BOOST,
    SELECT_SEEK_STEP,
    TOGGLE_PRECISE_SEEKING,
    TOGGLE_CHAPTER_BAR,
    TOGGLE_SEEKBAR_THUMBNAIL,
    TOGGLE_REMEMBER_ANIME4K,
    SELECT_DEFAULT_ANIME4K,
    SELECT_DECODER_BACKEND,
    SELECT_RENDERING_PROFILE,
    TOGGLE_GPU_NEXT,
    TOGGLE_VULKAN,
    TOGGLE_VOLUME_BOOST,
    SELECT_SUBTITLE_SCALE,
    SELECT_SCREENSHOT_DIRECTORY,
    SELECT_VIDEO_DOWNLOAD_DIRECTORY,
    TOGGLE_FOLLOW_GRAVITY_ROTATION,
    SELECT_NETWORK_CACHE_POLICY,
    SELECT_FULLSCREEN_EXIT_BEHAVIOR,
    SELECT_BACKGROUND_BEHAVIOR,
}

internal data class KiyoriPlayerSettingsEntrySpec(
    val title: String,
    val description: String,
    val kind: KiyoriSettingsRowKind,
    val action: KiyoriPlayerSettingsAction,
    val dependency: KiyoriPlayerSettingsDependency = KiyoriPlayerSettingsDependency.ALWAYS,
)

internal data class KiyoriPlayerSettingsGroupSpec(
    val title: String,
    val description: String,
    val entries: List<KiyoriPlayerSettingsEntrySpec>,
)

private fun playerNavigationSpec(
    title: String,
    description: String,
    action: KiyoriPlayerSettingsAction,
    dependency: KiyoriPlayerSettingsDependency = KiyoriPlayerSettingsDependency.ALWAYS,
) = KiyoriPlayerSettingsEntrySpec(
    title = title,
    description = description,
    kind = KiyoriSettingsRowKind.NAVIGATION,
    action = action,
    dependency = dependency,
)

private fun playerToggleSpec(
    title: String,
    description: String,
    action: KiyoriPlayerSettingsAction,
) = KiyoriPlayerSettingsEntrySpec(
    title = title,
    description = description,
    kind = KiyoriSettingsRowKind.TOGGLE,
    action = action,
)

internal val kiyoriPlayerSettingsGroups =
    listOf(
        KiyoriPlayerSettingsGroupSpec(
            title = "播放与连播",
            description = "控制新视频的播放速度，以及真实播放队列到达结尾后的行为",
            entries =
                listOf(
                    playerNavigationSpec(
                        "默认播放倍速",
                        "未开启倍速记忆时，新视频从此倍速开始",
                        KiyoriPlayerSettingsAction.SELECT_DEFAULT_SPEED,
                    ),
                    playerToggleSpec(
                        "记忆播放倍速",
                        "保存播放器中最后一次实际选择的倍速",
                        KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_PLAYBACK_SPEED,
                    ),
                    playerToggleSpec(
                        "自动播放下一集",
                        "当前视频自然结束后播放队列中的下一项",
                        KiyoriPlayerSettingsAction.TOGGLE_AUTO_PLAY_NEXT,
                    ),
                    playerNavigationSpec(
                        "队列播完后",
                        "最后一项播放结束时停留、关闭播放器或循环当前项",
                        KiyoriPlayerSettingsAction.SELECT_QUEUE_END_BEHAVIOR,
                    ),
                ),
        ),
        KiyoriPlayerSettingsGroupSpec(
            title = "手势与进度",
            description = "调整双击、跳转精度、章节信息和拖动预览",
            entries =
                listOf(
                    playerNavigationSpec(
                        "双击手势",
                        "双击任意位置暂停/播放，或按左右半屏快退/快进",
                        KiyoriPlayerSettingsAction.SELECT_DOUBLE_TAP_ACTION,
                    ),
                    playerNavigationSpec(
                        "双击跳转时长",
                        "仅在左右双击快退/快进模式下使用",
                        KiyoriPlayerSettingsAction.SELECT_DOUBLE_TAP_SEEK_STEP,
                        KiyoriPlayerSettingsDependency.DOUBLE_TAP_SEEK,
                    ),
                    playerToggleSpec(
                        "长按加速",
                        "按当前速度分段临时提升到 1x、2x 或 3x，松手恢复按下前速度",
                        KiyoriPlayerSettingsAction.TOGGLE_LONG_PRESS_SPEED_BOOST,
                    ),
                    playerNavigationSpec(
                        "按钮跳转时长",
                        "播放器快退和快进按钮每次跳转的秒数",
                        KiyoriPlayerSettingsAction.SELECT_SEEK_STEP,
                    ),
                    playerToggleSpec(
                        "精确进度定位",
                        "定位到更准确的画面；在线流和长视频可能需要更多解码时间",
                        KiyoriPlayerSettingsAction.TOGGLE_PRECISE_SEEKING,
                    ),
                    playerToggleSpec(
                        "显示章节进度条",
                        "在进度条绘制章节节点，并显示当前章节名称",
                        KiyoriPlayerSettingsAction.TOGGLE_CHAPTER_BAR,
                    ),
                    playerToggleSpec(
                        "进度条缩略图预览",
                        "本地视频直接提取；完整缓存完成的在线直链从同一 MPV 缓存快速提取",
                        KiyoriPlayerSettingsAction.TOGGLE_SEEKBAR_THUMBNAIL,
                    ),
                ),
        ),
        KiyoriPlayerSettingsGroupSpec(
            title = "画面与超分",
            description = "Anime4K 可实时切换；渲染内核选项在下次创建播放器时完整生效",
            entries =
                listOf(
                    playerToggleSpec(
                        "记忆超分模式",
                        "新视频自动使用设定的 Anime4K 默认模式",
                        KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_ANIME4K,
                    ),
                    playerNavigationSpec(
                        "默认超分模式",
                        "选择关、A、B、C、A+、B+ 或 C+ Anime4K 着色器组合",
                        KiyoriPlayerSettingsAction.SELECT_DEFAULT_ANIME4K,
                        KiyoriPlayerSettingsDependency.REMEMBER_ANIME4K,
                    ),
                    playerNavigationSpec(
                        "解码方式",
                        "明确选择软件解码、MediaCodec 直通或 MediaCodec Copy；不会自动切换",
                        KiyoriPlayerSettingsAction.SELECT_DECODER_BACKEND,
                    ),
                    playerNavigationSpec(
                        "渲染预设",
                        "只切换 MPV 缩放、画质或低延迟 profile，不改变解码方式",
                        KiyoriPlayerSettingsAction.SELECT_RENDERING_PROFILE,
                    ),
                    playerToggleSpec(
                        "GPU Next 渲染",
                        "下次创建播放器内核时使用 gpu-next 视频输出",
                        KiyoriPlayerSettingsAction.TOGGLE_GPU_NEXT,
                    ),
                    playerToggleSpec(
                        "Vulkan 渲染上下文",
                        "下次创建播放器内核时使用 Android Vulkan context",
                        KiyoriPlayerSettingsAction.TOGGLE_VULKAN,
                    ),
                ),
        ),
        KiyoriPlayerSettingsGroupSpec(
            title = "音频与字幕",
            description = "直接作用于 MPV 的软件音量和字幕渲染",
            entries =
                listOf(
                    playerToggleSpec(
                        "音量增强",
                        "允许 MPV 软件音量超过普通 100% 上限",
                        KiyoriPlayerSettingsAction.TOGGLE_VOLUME_BOOST,
                    ),
                    playerNavigationSpec(
                        "字幕缩放",
                        "调整内嵌和外挂字幕的显示比例",
                        KiyoriPlayerSettingsAction.SELECT_SUBTITLE_SCALE,
                    ),
                ),
        ),
        KiyoriPlayerSettingsGroupSpec(
            title = "保存与下载",
            description = "默认跟随文件下载器，也可以为播放器分别指定独立目录",
            entries =
                listOf(
                    playerNavigationSpec(
                        "截图保存位置",
                        "保存右侧截图按钮生成的 PNG 图片",
                        KiyoriPlayerSettingsAction.SELECT_SCREENSHOT_DIRECTORY,
                    ),
                    playerNavigationSpec(
                        "视频下载位置",
                        "保存右侧下载按钮获取的在线视频",
                        KiyoriPlayerSettingsAction.SELECT_VIDEO_DOWNLOAD_DIRECTORY,
                    ),
                ),
        ),
        KiyoriPlayerSettingsGroupSpec(
            title = "窗口与在线",
            description = "协调屏幕方向、全屏退出、后台播放和在线播放缓存",
            entries =
                listOf(
                    playerToggleSpec(
                        "跟随重力自动旋转",
                        "根据设备方向自动切换横屏或竖屏；开启后停用手动旋转按钮",
                        KiyoriPlayerSettingsAction.TOGGLE_FOLLOW_GRAVITY_ROTATION,
                    ),
                    playerNavigationSpec(
                        "退出全屏后",
                        "浏览器视频可返回同一会话悬浮窗，本地视频会关闭",
                        KiyoriPlayerSettingsAction.SELECT_FULLSCREEN_EXIT_BEHAVIOR,
                    ),
                    playerNavigationSpec(
                        "切到后台时",
                        "决定全屏播放器进入后台时暂停还是继续播放",
                        KiyoriPlayerSettingsAction.SELECT_BACKGROUND_BEHAVIOR,
                    ),
                    playerNavigationSpec(
                        "在线播放缓存",
                        "选择省流、均衡、流畅优先或完整缓存策略；新视频生效",
                        KiyoriPlayerSettingsAction.SELECT_NETWORK_CACHE_POLICY,
                    ),
                ),
        ),
    )

private enum class PlayerDirectoryPickerTarget {
    SCREENSHOT,
    VIDEO_DOWNLOAD,
}

@Composable
internal fun KiyoriPlayerSettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { PlayerSettingsStore.getInstance(context) }
    val settings by store.state.collectAsState()
    val browserDownloadSettingsStore =
        remember(context) { BrowserDownloadSettingsStore.getInstance(context) }
    val browserDownloadSettings by browserDownloadSettingsStore.state.collectAsState()
    var selection by remember { mutableStateOf<KiyoriSettingsSelection?>(null) }
    var directoryPickerTarget by remember { mutableStateOf<PlayerDirectoryPickerTarget?>(null) }
    val directoryPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            val target = directoryPickerTarget
            directoryPickerTarget = null
            if (uri == null || target == null) {
                return@rememberLauncherForActivityResult
            }
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                val displayName =
                    DocumentFile.fromTreeUri(context, uri)?.name
                        ?: throw IllegalStateException("无法读取所选目录名称")
                when (target) {
                    PlayerDirectoryPickerTarget.SCREENSHOT ->
                        store.setScreenshotDirectory(uri.toString(), displayName)
                    PlayerDirectoryPickerTarget.VIDEO_DOWNLOAD ->
                        store.setVideoDownloadDirectory(uri.toString(), displayName)
                }
            } catch (error: Exception) {
                BrowserDownloadManager.getInstance(context)
                    .releasePersistedDirectoryPermissionIfUnused(uri.toString())
                AppLogger.e(
                    "KiyoriPlayerSettings",
                    "Failed to persist player storage directory",
                    error,
                )
                Toast.makeText(
                        context,
                        "保存目录失败：${error.message ?: error.javaClass.simpleName}",
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
        }

    KiyoriCollapsingSettingsPage(
        title = KIYORI_PLAYER_SETTINGS_PAGE_TITLE,
        onBack = onBack,
        modifier = modifier,
    ) {
        items(kiyoriPlayerSettingsGroups, key = KiyoriPlayerSettingsGroupSpec::title) { group ->
            KiyoriSettingsGroupSection(
                title = group.title,
                description = group.description,
            ) {
                group.entries.forEachIndexed { index, entry ->
                    val enabled = isPlayerSettingEnabled(entry, settings)
                    KiyoriSettingsRow(
                        title = entry.title,
                        description = entry.description,
                        kind = entry.kind,
                        value =
                            if (entry.kind == KiyoriSettingsRowKind.NAVIGATION) {
                                playerSettingValue(entry.action, settings)
                            } else {
                                null
                            },
                        checked =
                            if (entry.kind == KiyoriSettingsRowKind.TOGGLE) {
                                playerSettingToggleValue(entry.action, settings)
                            } else {
                                false
                            },
                        enabled = enabled,
                        onClick = {
                            if (entry.kind == KiyoriSettingsRowKind.NAVIGATION) {
                                selection =
                                    playerSettingSelection(
                                        entry = entry,
                                        settings = settings,
                                        browserDownloadSettings = browserDownloadSettings,
                                        store = store,
                                        onRequestDirectory = { target ->
                                            directoryPickerTarget = target
                                            directoryPickerLauncher.launch(null)
                                        },
                                    )
                            } else {
                                togglePlayerSetting(entry.action, settings, store)
                            }
                        },
                    )
                    if (index != group.entries.lastIndex) {
                        KiyoriSettingsDivider()
                    }
                }
            }
        }
    }

    selection?.let { current ->
        KiyoriSettingsSelectionSheet(
            selection = current,
            onDismiss = { selection = null },
            onSelect = { option ->
                option.onSelect()
            },
        )
    }
}

private fun isPlayerSettingEnabled(
    entry: KiyoriPlayerSettingsEntrySpec,
    settings: PlayerSettings,
): Boolean =
    when (entry.dependency) {
        KiyoriPlayerSettingsDependency.ALWAYS -> true
        KiyoriPlayerSettingsDependency.DOUBLE_TAP_SEEK ->
            settings.doubleTapAction == PlayerDoubleTapAction.SEEK
        KiyoriPlayerSettingsDependency.REMEMBER_ANIME4K -> settings.rememberAnime4KMode
    }

private fun togglePlayerSetting(
    action: KiyoriPlayerSettingsAction,
    settings: PlayerSettings,
    store: PlayerSettingsStore,
) {
    when (action) {
        KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_PLAYBACK_SPEED ->
            store.setRememberPlaybackSpeed(!settings.rememberPlaybackSpeed)
        KiyoriPlayerSettingsAction.TOGGLE_AUTO_PLAY_NEXT ->
            store.setAutoPlayNext(!settings.autoPlayNext)
        KiyoriPlayerSettingsAction.TOGGLE_PRECISE_SEEKING ->
            store.setPreciseSeeking(!settings.preciseSeeking)
        KiyoriPlayerSettingsAction.TOGGLE_LONG_PRESS_SPEED_BOOST ->
            store.setLongPressSpeedBoostEnabled(!settings.longPressSpeedBoostEnabled)
        KiyoriPlayerSettingsAction.TOGGLE_CHAPTER_BAR ->
            store.setChapterBarEnabled(!settings.chapterBarEnabled)
        KiyoriPlayerSettingsAction.TOGGLE_SEEKBAR_THUMBNAIL ->
            store.setSeekbarThumbnailEnabled(!settings.seekbarThumbnailEnabled)
        KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_ANIME4K ->
            store.setRememberAnime4KMode(!settings.rememberAnime4KMode)
        KiyoriPlayerSettingsAction.TOGGLE_GPU_NEXT ->
            store.setGpuNextEnabled(!settings.gpuNextEnabled)
        KiyoriPlayerSettingsAction.TOGGLE_VULKAN ->
            store.setVulkanEnabled(!settings.vulkanEnabled)
        KiyoriPlayerSettingsAction.TOGGLE_VOLUME_BOOST ->
            store.setVolumeBoostEnabled(!settings.volumeBoostEnabled)
        KiyoriPlayerSettingsAction.TOGGLE_FOLLOW_GRAVITY_ROTATION ->
            store.setFollowGravityRotation(!settings.followGravityRotation)
        else -> error("Player setting action is not a toggle: $action")
    }
}

private fun playerSettingValue(
    action: KiyoriPlayerSettingsAction,
    settings: PlayerSettings,
): String =
    when (action) {
        KiyoriPlayerSettingsAction.SELECT_DEFAULT_SPEED ->
            formatPlayerSpeedLabel(settings.defaultSpeed)
        KiyoriPlayerSettingsAction.SELECT_QUEUE_END_BEHAVIOR ->
            formatQueueEndBehavior(settings.queueEndBehavior)
        KiyoriPlayerSettingsAction.SELECT_DOUBLE_TAP_ACTION ->
            formatDoubleTapAction(settings.doubleTapAction)
        KiyoriPlayerSettingsAction.SELECT_DOUBLE_TAP_SEEK_STEP ->
            "${settings.doubleTapSeekSeconds}s"
        KiyoriPlayerSettingsAction.SELECT_SEEK_STEP -> "${settings.seekStepSeconds}s"
        KiyoriPlayerSettingsAction.SELECT_DEFAULT_ANIME4K ->
            formatAnime4KMode(settings.anime4KMode)
        KiyoriPlayerSettingsAction.SELECT_DECODER_BACKEND ->
            settings.decoderBackend.displayName
        KiyoriPlayerSettingsAction.SELECT_RENDERING_PROFILE ->
            settings.renderingProfile.displayName
        KiyoriPlayerSettingsAction.SELECT_SUBTITLE_SCALE ->
            formatSubtitleScale(settings.subtitleScale)
        KiyoriPlayerSettingsAction.SELECT_SCREENSHOT_DIRECTORY ->
            formatPlayerDirectoryValue(
                settings.screenshotDirectoryUri,
                settings.screenshotDirectoryName,
            )
        KiyoriPlayerSettingsAction.SELECT_VIDEO_DOWNLOAD_DIRECTORY ->
            formatPlayerDirectoryValue(
                settings.videoDownloadDirectoryUri,
                settings.videoDownloadDirectoryName,
            )
        KiyoriPlayerSettingsAction.SELECT_NETWORK_CACHE_POLICY ->
            formatNetworkCachePolicy(settings.networkCachePolicy)
        KiyoriPlayerSettingsAction.SELECT_FULLSCREEN_EXIT_BEHAVIOR ->
            formatFullscreenExitBehavior(settings.fullscreenExitBehavior)
        KiyoriPlayerSettingsAction.SELECT_BACKGROUND_BEHAVIOR ->
            formatBackgroundBehavior(settings.backgroundBehavior)
        else -> ""
    }

private fun playerSettingToggleValue(
    action: KiyoriPlayerSettingsAction,
    settings: PlayerSettings,
): Boolean =
    when (action) {
        KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_PLAYBACK_SPEED ->
            settings.rememberPlaybackSpeed
        KiyoriPlayerSettingsAction.TOGGLE_AUTO_PLAY_NEXT -> settings.autoPlayNext
        KiyoriPlayerSettingsAction.TOGGLE_PRECISE_SEEKING -> settings.preciseSeeking
        KiyoriPlayerSettingsAction.TOGGLE_LONG_PRESS_SPEED_BOOST ->
            settings.longPressSpeedBoostEnabled
        KiyoriPlayerSettingsAction.TOGGLE_CHAPTER_BAR -> settings.chapterBarEnabled
        KiyoriPlayerSettingsAction.TOGGLE_SEEKBAR_THUMBNAIL ->
            settings.seekbarThumbnailEnabled
        KiyoriPlayerSettingsAction.TOGGLE_REMEMBER_ANIME4K -> settings.rememberAnime4KMode
        KiyoriPlayerSettingsAction.TOGGLE_GPU_NEXT -> settings.gpuNextEnabled
        KiyoriPlayerSettingsAction.TOGGLE_VULKAN -> settings.vulkanEnabled
        KiyoriPlayerSettingsAction.TOGGLE_VOLUME_BOOST -> settings.volumeBoostEnabled
        KiyoriPlayerSettingsAction.TOGGLE_FOLLOW_GRAVITY_ROTATION ->
            settings.followGravityRotation
        else -> false
    }

private fun playerSettingSelection(
    entry: KiyoriPlayerSettingsEntrySpec,
    settings: PlayerSettings,
    browserDownloadSettings: BrowserDownloadSettings,
    store: PlayerSettingsStore,
    onRequestDirectory: (PlayerDirectoryPickerTarget) -> Unit,
): KiyoriSettingsSelection {
    val options =
        when (entry.action) {
            KiyoriPlayerSettingsAction.SELECT_DEFAULT_SPEED ->
                PLAYER_SPEED_OPTIONS.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = formatPlayerSpeedLabel(value),
                        selected = value == settings.defaultSpeed,
                    ) { store.setDefaultSpeed(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_QUEUE_END_BEHAVIOR ->
                PlayerQueueEndBehavior.entries.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = formatQueueEndBehavior(value),
                        description =
                            when (value) {
                                PlayerQueueEndBehavior.STAY -> "停留在最后一帧并保持播放器"
                                PlayerQueueEndBehavior.CLOSE -> "关闭当前播放会话"
                                PlayerQueueEndBehavior.LOOP_CURRENT -> "从头重新播放当前视频"
                            },
                        selected = value == settings.queueEndBehavior,
                    ) { store.setQueueEndBehavior(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_DOUBLE_TAP_ACTION ->
                PlayerDoubleTapAction.entries.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = formatDoubleTapAction(value),
                        description =
                            when (value) {
                                PlayerDoubleTapAction.PLAY_PAUSE ->
                                    "双击屏幕任意位置暂停或继续播放"
                                PlayerDoubleTapAction.SEEK ->
                                    "双击左半屏快退，双击右半屏快进"
                            },
                        selected = value == settings.doubleTapAction,
                    ) { store.setDoubleTapAction(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_DOUBLE_TAP_SEEK_STEP ->
                PLAYER_DOUBLE_TAP_SEEK_OPTIONS.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = "${value}s",
                        selected = value == settings.doubleTapSeekSeconds,
                    ) { store.setDoubleTapSeekSeconds(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_SEEK_STEP ->
                PLAYER_SEEK_STEP_OPTIONS.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = "${value}s",
                        selected = value == settings.seekStepSeconds,
                    ) { store.setSeekStepSeconds(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_DEFAULT_ANIME4K ->
                Anime4KMode.entries.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = formatAnime4KMode(value),
                        description = anime4KModeDescription(value),
                        selected = value == settings.anime4KMode,
                    ) { store.setAnime4KMode(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_DECODER_BACKEND ->
                PlayerDecoderBackend.entries.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = value.displayName,
                        description = value.description,
                        selected = value == settings.decoderBackend,
                    ) { store.setDecoderBackend(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_RENDERING_PROFILE ->
                PlayerRenderingProfile.entries.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = value.displayName,
                        description = value.description,
                        selected = value == settings.renderingProfile,
                    ) { store.setRenderingProfile(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_SUBTITLE_SCALE ->
                PLAYER_SUBTITLE_SCALE_OPTIONS.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = formatSubtitleScale(value),
                        selected = value == settings.subtitleScale,
                    ) { store.setSubtitleScale(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_SCREENSHOT_DIRECTORY ->
                playerDirectoryOptions(
                    currentUri = settings.screenshotDirectoryUri,
                    currentName = settings.screenshotDirectoryName,
                    inheritedDescription =
                        formatInheritedDownloadDirectory(browserDownloadSettings),
                    onFollowSettings = store::clearScreenshotDirectory,
                    onRequestDirectory = {
                        onRequestDirectory(PlayerDirectoryPickerTarget.SCREENSHOT)
                    },
                    independentDescription =
                        "直接将 PNG 写入所选目录，不改变文件下载器主设置",
                )
            KiyoriPlayerSettingsAction.SELECT_VIDEO_DOWNLOAD_DIRECTORY ->
                playerDirectoryOptions(
                    currentUri = settings.videoDownloadDirectoryUri,
                    currentName = settings.videoDownloadDirectoryName,
                    inheritedDescription =
                        formatInheritedDownloadDirectory(browserDownloadSettings),
                    onFollowSettings = store::clearVideoDownloadDirectory,
                    onRequestDirectory = {
                        onRequestDirectory(PlayerDirectoryPickerTarget.VIDEO_DOWNLOAD)
                    },
                    independentDescription =
                        "普通视频由现有内置下载器写入；M3U8 离线包按下载器规则保留在应用目录",
                )
            KiyoriPlayerSettingsAction.SELECT_NETWORK_CACHE_POLICY ->
                PlayerNetworkCachePolicy.entries.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = formatNetworkCachePolicy(value),
                        description = networkCachePolicyDescription(value),
                        selected = value == settings.networkCachePolicy,
                    ) { store.setNetworkCachePolicy(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_FULLSCREEN_EXIT_BEHAVIOR ->
                PlayerFullscreenExitBehavior.entries.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = formatFullscreenExitBehavior(value),
                        description =
                            when (value) {
                                PlayerFullscreenExitBehavior.RETURN_TO_FLOATING ->
                                    "浏览器候选返回同一会话悬浮窗；本地视频关闭"
                                PlayerFullscreenExitBehavior.CLOSE ->
                                    "退出全屏时关闭当前播放会话"
                            },
                        selected = value == settings.fullscreenExitBehavior,
                    ) { store.setFullscreenExitBehavior(value) }
                }
            KiyoriPlayerSettingsAction.SELECT_BACKGROUND_BEHAVIOR ->
                PlayerBackgroundBehavior.entries.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = formatBackgroundBehavior(value),
                        description =
                            when (value) {
                                PlayerBackgroundBehavior.PAUSE -> "进入后台时暂停"
                                PlayerBackgroundBehavior.CONTINUE -> "进入后台后保持播放状态"
                            },
                        selected = value == settings.backgroundBehavior,
                    ) { store.setBackgroundBehavior(value) }
                }
            else -> error("Player setting action does not own a selection sheet: ${entry.action}")
        }
    return KiyoriSettingsSelection(
        title = entry.title,
        currentValue = playerSettingValue(entry.action, settings),
        options = options,
    )
}

private fun playerDirectoryOptions(
    currentUri: String,
    currentName: String,
    inheritedDescription: String,
    onFollowSettings: () -> Unit,
    onRequestDirectory: () -> Unit,
    independentDescription: String,
): List<KiyoriSettingsSelectionOption> =
    listOf(
        KiyoriSettingsSelectionOption(
            label = "跟随文件下载器",
            description = inheritedDescription,
            selected = currentUri.isBlank(),
            onSelect = onFollowSettings,
        ),
        KiyoriSettingsSelectionOption(
            label = "使用独立目录",
            description =
                if (currentUri.isBlank()) {
                    independentDescription
                } else {
                    "$currentName · $independentDescription"
                },
            selected = currentUri.isNotBlank(),
            onSelect = onRequestDirectory,
        ),
    )

private fun formatQueueEndBehavior(value: PlayerQueueEndBehavior): String =
    when (value) {
        PlayerQueueEndBehavior.STAY -> "停在结尾"
        PlayerQueueEndBehavior.CLOSE -> "关闭播放器"
        PlayerQueueEndBehavior.LOOP_CURRENT -> "循环当前视频"
    }

private fun formatDoubleTapAction(value: PlayerDoubleTapAction): String =
    when (value) {
        PlayerDoubleTapAction.PLAY_PAUSE -> "暂停/播放"
        PlayerDoubleTapAction.SEEK -> "左退右进"
    }

internal fun formatAnime4KMode(value: Anime4KMode): String =
    when (value) {
        Anime4KMode.OFF -> "关"
        Anime4KMode.A -> "A"
        Anime4KMode.B -> "B"
        Anime4KMode.C -> "C"
        Anime4KMode.A_PLUS -> "A+"
        Anime4KMode.B_PLUS -> "B+"
        Anime4KMode.C_PLUS -> "C+"
    }

internal fun anime4KModeDescription(value: Anime4KMode): String =
    when (value) {
        Anime4KMode.OFF -> "原始画质，不加载 Anime4K 着色器"
        Anime4KMode.A -> "强力重建，使用 Restore 与双阶段 Upscale"
        Anime4KMode.B -> "柔和重建，使用 Soft Restore 与双阶段 Upscale"
        Anime4KMode.C -> "降噪处理，使用 Denoise Upscale 与二次放大"
        Anime4KMode.A_PLUS -> "双重强化，在二次放大前再次执行强力重建"
        Anime4KMode.B_PLUS -> "双重柔和，在二次放大前再次执行柔和重建"
        Anime4KMode.C_PLUS -> "降噪强化，在降噪放大后追加重建与二次放大"
    }

private fun formatFullscreenExitBehavior(value: PlayerFullscreenExitBehavior): String =
    when (value) {
        PlayerFullscreenExitBehavior.RETURN_TO_FLOATING -> "返回浏览器悬浮窗"
        PlayerFullscreenExitBehavior.CLOSE -> "关闭播放器"
    }

private fun formatBackgroundBehavior(value: PlayerBackgroundBehavior): String =
    when (value) {
        PlayerBackgroundBehavior.PAUSE -> "暂停播放"
        PlayerBackgroundBehavior.CONTINUE -> "继续播放"
    }

private fun formatNetworkCachePolicy(value: PlayerNetworkCachePolicy): String =
    when (value) {
        PlayerNetworkCachePolicy.COMPACT -> "省流模式"
        PlayerNetworkCachePolicy.BALANCED -> "智能均衡"
        PlayerNetworkCachePolicy.LARGE -> "流畅优先"
        PlayerNetworkCachePolicy.FULL_VIDEO -> "完整缓存"
    }

private fun networkCachePolicyDescription(value: PlayerNetworkCachePolicy): String =
    when (value) {
        PlayerNetworkCachePolicy.COMPACT ->
            "减少提前预读，适合流量受限场景；前向 64 MB / 后向 32 MB / 60s"
        PlayerNetworkCachePolicy.BALANCED ->
            "兼顾启动速度、拖动与流量；前向 128 MB / 后向 64 MB / 180s"
        PlayerNetworkCachePolicy.LARGE ->
            "扩大前后缓冲，优先弱网抗抖和长视频拖动；前向 256 MB / 后向 128 MB / 300s"
        PlayerNetworkCachePolicy.FULL_VIDEO ->
            "符合条件的 HTTP/HTTPS 直链点播会边播边缓存整个文件；占用较多内部存储，关闭播放器或切换视频后释放"
    }

private fun formatSubtitleScale(value: Double): String = "${(value * 100).toInt()}%"

private fun formatPlayerDirectoryValue(uri: String, name: String): String =
    if (uri.isBlank()) "跟随文件下载器" else name

private fun formatInheritedDownloadDirectory(settings: BrowserDownloadSettings): String =
    when {
        settings.defaultEngine == BrowserDownloadEngine.SYSTEM ->
            "当前主设置：Android 系统下载目录"
        settings.customDirectoryUri.isNotBlank() ->
            "当前主设置：${settings.customDirectoryName}"
        settings.autoTransferToPublicDirectory ->
            "当前主设置：公开下载目录"
        else ->
            "当前主设置：应用下载目录"
    }
