package com.ai.assistance.operit.ui.features.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.player.PLAYER_SPEED_MENU_OPTIONS
import com.ai.assistance.operit.core.player.Anime4KMode
import com.ai.assistance.operit.core.player.PlayerChapter
import com.ai.assistance.operit.core.player.PlayerMediaSource
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSessionState
import com.ai.assistance.operit.core.player.PlayerSettings
import com.ai.assistance.operit.core.player.PlayerVideoFitMode
import com.ai.assistance.operit.core.player.formatPlayerSpeedLabel
import com.ai.assistance.operit.core.player.parsePlayerSpeedInput
import java.util.Locale
import kotlinx.coroutines.delay

internal val PlayerAccent = Color(0xFF7792FF)
internal val PlayerAccentSecondary = Color(0xFF9A7BFF)
private val PlayerPopupBackground = Color(0xF21B1E27)
private val PlayerPopupText = Color(0xFFF7F8FC)
private val PlayerPopupMutedText = Color(0xFFAEB5C5)

private data class PlayerPopupItem(
    val label: String,
    val supportingText: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val toggleState: Boolean? = null,
)

@Composable
internal fun PlayerControls(
    state: PlayerSessionState,
    settings: PlayerSettings,
    session: PlayerSession,
    controlsLocked: Boolean,
    networkSpeedBytesPerSecond: Long,
    batteryText: String,
    clockText: String,
    onBack: () -> Unit,
    onRotate: () -> Unit,
    onScreenshot: () -> Unit,
    onDownload: () -> Unit,
    onAutoRotateChanged: (Boolean) -> Unit,
    onShowPlaybackLog: () -> Unit,
    onLockChanged: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    onPopupVisibilityChanged: (Boolean) -> Unit,
    onSeekInteractionChanged: (Boolean) -> Unit,
) {
    if (controlsLocked) {
        Box(Modifier.fillMaxSize()) {
            LegacyImageButton(
                painter = painterResource(R.drawable.ic_kiyori_player_lock_outline),
                description = "解锁控制",
                onClick = { onLockChanged(false) },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                size = 44.dp,
                padding = 8.dp,
            )
            LegacyImageButton(
                painter = painterResource(R.drawable.ic_kiyori_player_lock_outline),
                description = "解锁控制",
                onClick = { onLockChanged(false) },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                size = 44.dp,
                padding = 8.dp,
            )
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isPortrait = maxHeight > maxWidth
        PlayerTopControls(
            state = state,
            networkSpeedBytesPerSecond = networkSpeedBytesPerSecond,
            batteryText = batteryText,
            clockText = clockText,
            session = session,
            settings = settings,
            isPortrait = isPortrait,
            onBack = onBack,
            onAutoRotateChanged = onAutoRotateChanged,
            onShowPlaybackLog = onShowPlaybackLog,
            onInteraction = onInteraction,
            onPopupVisibilityChanged = onPopupVisibilityChanged,
        )
        PlayerBottomControls(
            state = state,
            settings = settings,
            session = session,
            isPortrait = isPortrait,
            onRotate = onRotate,
            onInteraction = onInteraction,
            onPopupVisibilityChanged = onPopupVisibilityChanged,
            onSeekInteractionChanged = onSeekInteractionChanged,
        )
        PlayerSideActions(
            downloadEnabled = state.request?.source == PlayerMediaSource.BROWSER_CANDIDATE,
            onScreenshot = onScreenshot,
            onLock = { onLockChanged(true) },
            onDownload = onDownload,
            onInteraction = onInteraction,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        )
    }
}

@Composable
private fun PlayerTopControls(
    state: PlayerSessionState,
    networkSpeedBytesPerSecond: Long,
    batteryText: String,
    clockText: String,
    session: PlayerSession,
    settings: PlayerSettings,
    isPortrait: Boolean,
    onBack: () -> Unit,
    onAutoRotateChanged: (Boolean) -> Unit,
    onShowPlaybackLog: () -> Unit,
    onInteraction: () -> Unit,
    onPopupVisibilityChanged: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color(0xB0000000),
                        0.5f to Color(0x60000000),
                        1f to Color.Transparent,
                    ),
                ),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegacyImageButton(
                painter = painterResource(R.drawable.arrow_left),
                description = "返回",
                onClick = {
                    onInteraction()
                    onBack()
                },
                size = 40.dp,
                padding = 8.dp,
            )
            Spacer(Modifier.width(8.dp))
            if (isPortrait) {
                Text(
                    text = state.request?.title.orEmpty(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(64.dp),
                )
                Row(
                    modifier = Modifier.weight(1f).padding(end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerStatusColumn(
                        first = formatPlayerNetworkSpeed(networkSpeedBytesPerSecond).first,
                        second = formatPlayerNetworkSpeed(networkSpeedBytesPerSecond).second,
                        modifier = Modifier.weight(1.15f),
                        compact = true,
                    )
                    PlayerStatusColumn(
                        first = batteryText,
                        second = clockText,
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                    PlayerSubtitlePopup(
                        state = state,
                        session = session,
                        onInteraction = onInteraction,
                        onPopupVisibilityChanged = onPopupVisibilityChanged,
                        modifier = Modifier.weight(1f),
                        size = 28.dp,
                        padding = 2.dp,
                    )
                    PlayerDanmakuButton(
                        modifier = Modifier.weight(1f),
                        size = 28.dp,
                        padding = 2.dp,
                    )
                    PlayerAudioTrackPopup(
                        state = state,
                        session = session,
                        onInteraction = onInteraction,
                        onPopupVisibilityChanged = onPopupVisibilityChanged,
                        modifier = Modifier.weight(1f),
                        size = 28.dp,
                        padding = 2.dp,
                    )
                    PlayerAspectPopup(
                        mode = state.videoFitMode,
                        session = session,
                        onInteraction = onInteraction,
                        onPopupVisibilityChanged = onPopupVisibilityChanged,
                        modifier = Modifier.weight(1f),
                        size = 28.dp,
                        padding = 2.dp,
                    )
                    PlayerMorePopup(
                        autoRotateEnabled = settings.followGravityRotation,
                        onAutoRotateChanged = onAutoRotateChanged,
                        onShowPlaybackLog = onShowPlaybackLog,
                        onInteraction = onInteraction,
                        onPopupVisibilityChanged = onPopupVisibilityChanged,
                        modifier = Modifier.weight(1f),
                        size = 28.dp,
                        padding = 4.dp,
                    )
                }
            } else {
                Text(
                    text = state.request?.title.orEmpty(),
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 0.dp, top = 4.dp, end = 8.dp),
                )
                PlayerStatusColumn(
                    first = formatPlayerNetworkSpeed(networkSpeedBytesPerSecond).first,
                    second = formatPlayerNetworkSpeed(networkSpeedBytesPerSecond).second,
                )
                Spacer(Modifier.width(4.dp))
                PlayerStatusColumn(first = batteryText, second = clockText)
                Spacer(Modifier.width(2.dp))
                PlayerSubtitlePopup(
                    state = state,
                    session = session,
                    onInteraction = onInteraction,
                    onPopupVisibilityChanged = onPopupVisibilityChanged,
                )
                Spacer(Modifier.width(2.dp))
                PlayerDanmakuButton()
                Spacer(Modifier.width(2.dp))
                PlayerAudioTrackPopup(
                    state = state,
                    session = session,
                    onInteraction = onInteraction,
                    onPopupVisibilityChanged = onPopupVisibilityChanged,
                )
                Spacer(Modifier.width(2.dp))
                PlayerAspectPopup(
                    mode = state.videoFitMode,
                    session = session,
                    onInteraction = onInteraction,
                    onPopupVisibilityChanged = onPopupVisibilityChanged,
                )
                Spacer(Modifier.width(2.dp))
                PlayerMorePopup(
                    autoRotateEnabled = settings.followGravityRotation,
                    onAutoRotateChanged = onAutoRotateChanged,
                    onShowPlaybackLog = onShowPlaybackLog,
                    onInteraction = onInteraction,
                    onPopupVisibilityChanged = onPopupVisibilityChanged,
                )
            }
        }
    }
}

@Composable
private fun PlayerStatusColumn(
    first: String,
    second: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier =
            modifier
                .heightIn(min = 32.dp)
                .padding(horizontal = if (compact) 2.dp else 4.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = first,
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = second,
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
private fun PlayerSubtitlePopup(
    state: PlayerSessionState,
    session: PlayerSession,
    onInteraction: () -> Unit,
    onPopupVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    padding: androidx.compose.ui.unit.Dp = 4.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        onPopupVisibilityChanged(value)
        onInteraction()
    }
    val items = buildList {
        add(
            PlayerPopupItem(
                label = "关闭字幕",
                selected = state.selectedSubtitleTrackId == null,
            ),
        )
        state.subtitleTracks.forEach { track ->
            add(
                PlayerPopupItem(
                    label = track.title,
                    supportingText = track.language,
                    selected = track.id == state.selectedSubtitleTrackId,
                ),
            )
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LegacyImageButton(
            painter = painterResource(R.drawable.ic_kiyori_player_subtitle_outline),
            description = "字幕",
            onClick = { setExpanded(true) },
            size = size,
            padding = padding,
        )
        PlayerPopupMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) },
            title = "字幕",
            items = items,
            fixedHeight = items.size > 3,
            showScrollHint = items.size > 3,
        ) { position ->
            session.setSubtitleTrack(if (position == 0) null else state.subtitleTracks[position - 1].id)
            setExpanded(false)
        }
    }
}

@Composable
private fun PlayerDanmakuButton(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    padding: androidx.compose.ui.unit.Dp = 4.dp,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LegacyImageButton(
            painter = painterResource(R.drawable.ic_kiyori_player_danmaku_outline),
            description = "弹幕（当前资源不支持）",
            onClick = {},
            size = size,
            padding = padding,
            enabled = false,
        )
    }
}

@Composable
private fun PlayerAudioTrackPopup(
    state: PlayerSessionState,
    session: PlayerSession,
    onInteraction: () -> Unit,
    onPopupVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    padding: androidx.compose.ui.unit.Dp = 4.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        onPopupVisibilityChanged(value)
        onInteraction()
    }
    val items =
        state.audioTracks.map { track ->
            PlayerPopupItem(
                label = track.title,
                supportingText = track.language,
                selected = track.id == state.selectedAudioTrackId,
            )
        }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LegacyImageButton(
            painter = painterResource(R.drawable.ic_kiyori_player_audio_outline),
            description = "音轨",
            onClick = { setExpanded(true) },
            size = size,
            padding = padding,
            enabled = items.isNotEmpty(),
        )
        PlayerPopupMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) },
            title = "音轨",
            items = items,
        ) { position ->
            state.audioTracks.getOrNull(position)?.let { session.setAudioTrack(it.id) }
            setExpanded(false)
        }
    }
}

@Composable
private fun PlayerAspectPopup(
    mode: PlayerVideoFitMode,
    session: PlayerSession,
    onInteraction: () -> Unit,
    onPopupVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    padding: androidx.compose.ui.unit.Dp = 4.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        onPopupVisibilityChanged(value)
        onInteraction()
    }
    val modes =
        listOf(
            PlayerVideoFitMode.FIT to "适应屏幕",
            PlayerVideoFitMode.STRETCH to "拉伸画面",
            PlayerVideoFitMode.CROP to "裁剪填充",
        )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LegacyImageButton(
            painter = painterResource(R.drawable.ic_kiyori_player_aspect_outline),
            description = "画面比例",
            onClick = { setExpanded(true) },
            size = size,
            padding = padding,
        )
        PlayerPopupMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) },
            title = "画面比例",
            items =
                modes.map { (candidate, label) ->
                    PlayerPopupItem(
                        label = label,
                        selected = candidate == mode,
                    )
                },
        ) { position ->
            modes.getOrNull(position)?.first?.let(session::setVideoFitMode)
            setExpanded(false)
        }
    }
}

@Composable
private fun PlayerMorePopup(
    autoRotateEnabled: Boolean,
    onAutoRotateChanged: (Boolean) -> Unit,
    onShowPlaybackLog: () -> Unit,
    onInteraction: () -> Unit,
    onPopupVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    padding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        onPopupVisibilityChanged(value)
        onInteraction()
    }
    val items =
        listOf(
            PlayerPopupItem(
                label = "自动旋转",
                supportingText = if (autoRotateEnabled) "跟随设备方向" else "保持手动横竖屏",
                toggleState = autoRotateEnabled,
            ),
            PlayerPopupItem(
                label = "查看播放日志",
                supportingText = "检查 MPV、网络与画面渲染状态",
            ),
        )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LegacyImageButton(
            painter = painterResource(R.drawable.menudotsvertical),
            description = "更多选项",
            onClick = { setExpanded(true) },
            size = size,
            padding = padding,
        )
        PlayerPopupMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) },
            title = "更多选项",
            items = items,
        ) { position ->
            when (position) {
                0 -> onAutoRotateChanged(!autoRotateEnabled)
                1 -> {
                    setExpanded(false)
                    onShowPlaybackLog()
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PlayerBottomControls(
    state: PlayerSessionState,
    settings: PlayerSettings,
    session: PlayerSession,
    isPortrait: Boolean,
    onRotate: () -> Unit,
    onInteraction: () -> Unit,
    onPopupVisibilityChanged: (Boolean) -> Unit,
    onSeekInteractionChanged: (Boolean) -> Unit,
) {
    var seekDraft by remember { mutableStateOf<Double?>(null) }
    val duration = state.durationSeconds.coerceAtLeast(0.0)
    val position = (seekDraft ?: state.positionSeconds).coerceIn(0.0, duration.coerceAtLeast(0.01))
    val visibleChapters = if (settings.chapterBarEnabled) state.chapters else emptyList()
    val displayedChapter = chapterAtPosition(visibleChapters, position)

    LaunchedEffect(
        seekDraft,
        settings.seekbarThumbnailEnabled,
        state.loadGeneration,
    ) {
        val target = seekDraft
        if (target == null || !settings.seekbarThumbnailEnabled) {
            session.clearSeekPreview()
            return@LaunchedEffect
        }
        delay(120)
        session.requestSeekPreview(target)
    }

    if (seekDraft != null && settings.seekbarThumbnailEnabled) {
        PlayerSeekPreviewCard(
            preview = state.seekPreview,
            position = position,
            chapter = displayedChapter,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 108.dp),
        )
    }
    Box(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(108.dp).background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to Color(0x60000000),
                    1f to Color(0xB0000000),
                ),
            ),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 6.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 800.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${formatPlayerTime(position)}/${formatPlayerTime(duration)}",
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                        displayedChapter?.let { chapter ->
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = chapter.title,
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    LegacySeekBar(
                        position = position,
                        duration = duration,
                        chapters = visibleChapters,
                        onValueChange = { seekDraft = it },
                        onValueChangeFinished = { target ->
                            session.seekTo(target)
                            seekDraft = null
                            session.clearSeekPreview()
                        },
                        onValueChangeCanceled = {
                            seekDraft = null
                            session.clearSeekPreview()
                        },
                        onInteractionStarted = {
                            onSeekInteractionChanged(true)
                            onInteraction()
                        },
                        onInteractionFinished = {
                            onSeekInteractionChanged(false)
                            onInteraction()
                        },
                        modifier = Modifier.fillMaxWidth().height(30.dp),
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                if (isPortrait) {
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PortraitAnime4KControlCell(
                            mode = state.anime4KMode,
                            session = session,
                            onInteraction = onInteraction,
                            onPopupVisibilityChanged = onPopupVisibilityChanged,
                        )
                        PortraitImageControlCell(
                            painter = painterResource(R.drawable.ic_danmaku_visible),
                            description = "弹幕（当前资源不支持）",
                            onClick = {},
                            enabled = false,
                        )
                        PortraitImageControlCell(
                            painter = painterResource(R.drawable.previous_square),
                            description = "上一项",
                            onClick = {
                                onInteraction()
                                session.playPrevious()
                            },
                            enabled = state.hasPreviousQueueItem,
                        )
                        PortraitImageControlCell(
                            painter = painterResource(R.drawable.ic_rewind_new),
                            description = "后退",
                            onClick = {
                                onInteraction()
                                session.seekBackward()
                            },
                        )
                        PortraitImageControlCell(
                            painter = painterResource(if (state.paused) R.drawable.play else R.drawable.pause),
                            description = if (state.paused) "播放" else "暂停",
                            onClick = {
                                onInteraction()
                                session.togglePause()
                            },
                            size = 36.dp,
                        )
                        PortraitImageControlCell(
                            painter = painterResource(R.drawable.ic_forward_new),
                            description = "前进",
                            onClick = {
                                onInteraction()
                                session.seekForward()
                            },
                        )
                        PortraitImageControlCell(
                            painter = painterResource(R.drawable.next_square),
                            description = "下一项",
                            onClick = {
                                onInteraction()
                                session.playNext()
                            },
                            enabled = state.hasNextQueueItem,
                        )
                        Box(
                            modifier = Modifier.weight(1f).height(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PlayerSpeedMenu(
                                speed = state.speed,
                                session = session,
                                onInteraction = onInteraction,
                                onPopupVisibilityChanged = onPopupVisibilityChanged,
                                size = 32.dp,
                                padding = 5.dp,
                            )
                        }
                        PortraitTextControlCell(
                            text = if (settings.followGravityRotation) "自动" else "旋转",
                            onClick = {
                                onInteraction()
                                onRotate()
                            },
                            enabled = !settings.followGravityRotation,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        LegacyImageButton(
                            painterResource(R.drawable.ic_danmaku_visible),
                            "弹幕（当前资源不支持）",
                            onClick = {},
                            size = 38.dp,
                            padding = 6.dp,
                            enabled = false,
                        )
                        Spacer(Modifier.width(7.dp))
                        LegacyImageButton(
                            painterResource(R.drawable.previous_square),
                            "上一项",
                            onClick = {
                                onInteraction()
                                session.playPrevious()
                            },
                            size = 38.dp,
                            padding = 6.dp,
                            enabled = state.hasPreviousQueueItem,
                        )
                        Spacer(Modifier.width(7.dp))
                        LegacyImageButton(
                            painterResource(R.drawable.ic_rewind_new),
                            "后退",
                            onClick = {
                                onInteraction()
                                session.seekBackward()
                            },
                            size = 38.dp,
                            padding = 6.dp,
                        )
                        Spacer(Modifier.width(7.dp))
                        LegacyImageButton(
                            painter = painterResource(if (state.paused) R.drawable.play else R.drawable.pause),
                            description = if (state.paused) "播放" else "暂停",
                            onClick = {
                                onInteraction()
                                session.togglePause()
                            },
                            size = 42.dp,
                            padding = 6.dp,
                        )
                        Spacer(Modifier.width(7.dp))
                        LegacyImageButton(
                            painterResource(R.drawable.ic_forward_new),
                            "前进",
                            onClick = {
                                onInteraction()
                                session.seekForward()
                            },
                            size = 38.dp,
                            padding = 6.dp,
                        )
                        Spacer(Modifier.width(7.dp))
                        LegacyImageButton(
                            painterResource(R.drawable.next_square),
                            "下一项",
                            onClick = {
                                onInteraction()
                                session.playNext()
                            },
                            size = 38.dp,
                            padding = 6.dp,
                            enabled = state.hasNextQueueItem,
                        )
                        Spacer(Modifier.width(7.dp))
                        PlayerSpeedMenu(
                            speed = state.speed,
                            session = session,
                            onInteraction = onInteraction,
                            onPopupVisibilityChanged = onPopupVisibilityChanged,
                        )
                    }
                    Anime4KTextControl(
                        mode = state.anime4KMode,
                        session = session,
                        onInteraction = onInteraction,
                        onPopupVisibilityChanged = onPopupVisibilityChanged,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .width(64.dp)
                                .height(34.dp),
                    )
                    LegacyTextButton(
                        text = if (settings.followGravityRotation) "自动" else "旋转",
                        onClick = {
                            onInteraction()
                            onRotate()
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).height(34.dp),
                        enabled = !settings.followGravityRotation,
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PortraitImageControlCell(
    painter: Painter,
    description: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier.weight(1f).height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        LegacyImageButton(
            painter = painter,
            description = description,
            onClick = onClick,
            size = size,
            padding = 5.dp,
            enabled = enabled,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PortraitAnime4KControlCell(
    mode: Anime4KMode,
    session: PlayerSession,
    onInteraction: () -> Unit,
    onPopupVisibilityChanged: (Boolean) -> Unit,
) {
    Anime4KTextControl(
        mode = mode,
        session = session,
        onInteraction = onInteraction,
        onPopupVisibilityChanged = onPopupVisibilityChanged,
        modifier = Modifier.weight(1f).height(48.dp),
    )
}

@Composable
private fun Anime4KTextControl(
    mode: Anime4KMode,
    session: PlayerSession,
    onInteraction: () -> Unit,
    onPopupVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        onPopupVisibilityChanged(value)
        onInteraction()
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable { setExpanded(true) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "超分",
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = formatAnime4KControlMode(mode),
                color = PlayerAccent,
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.offset(y = (-1).dp),
            )
        }
        PlayerPopupMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) },
            title = "Anime4K 模式",
            items =
                Anime4KMode.entries.map { candidate ->
                    PlayerPopupItem(
                        label = formatAnime4KMode(candidate).first,
                        supportingText = formatAnime4KMode(candidate).second,
                        selected = candidate == mode,
                    )
                },
            fixedHeight = true,
            showScrollHint = true,
        ) { position ->
            Anime4KMode.entries.getOrNull(position)?.let(session::setAnime4KMode)
            setExpanded(false)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PortraitTextControlCell(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .height(48.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlayerSideActions(
    downloadEnabled: Boolean,
    onScreenshot: () -> Unit,
    onLock: () -> Unit,
    onDownload: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LegacyImageButton(
            painterResource(R.drawable.ic_kiyori_player_camera_outline),
            "截图",
            onClick = {
                onInteraction()
                onScreenshot()
            },
            size = 44.dp,
            padding = 8.dp,
        )
        Spacer(Modifier.height(28.dp))
        LegacyImageButton(
            painterResource(R.drawable.ic_kiyori_player_unlock_outline),
            "锁定控制",
            onClick = {
                onInteraction()
                onLock()
            },
            size = 44.dp,
            padding = 8.dp,
        )
        Spacer(Modifier.height(28.dp))
        LegacyImageButton(
            painterResource(R.drawable.ic_kiyori_player_download_outline),
            "下载视频",
            onClick = {
                onInteraction()
                onDownload()
            },
            size = 44.dp,
            padding = 8.dp,
            enabled = downloadEnabled,
        )
    }
}

@Composable
internal fun PlayerSpeedMenu(
    speed: Double,
    session: PlayerSession,
    onInteraction: () -> Unit,
    onPopupVisibilityChanged: (Boolean) -> Unit,
    size: androidx.compose.ui.unit.Dp = 38.dp,
    padding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    var customSpeedDialogVisible by remember { mutableStateOf(false) }
    fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        onPopupVisibilityChanged(value)
        onInteraction()
    }
    fun setCustomSpeedDialogVisible(value: Boolean) {
        if (customSpeedDialogVisible == value) return
        customSpeedDialogVisible = value
        onPopupVisibilityChanged(value)
        onInteraction()
    }
    Box {
        LegacyImageButton(
            painterResource(R.drawable.tachometer_alt_fastest),
            formatPlayerSpeedLabel(speed),
            onClick = { setExpanded(true) },
            size = size,
            padding = padding,
        )
        PlayerPopupMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) },
            title = "播放速度",
            items =
                PLAYER_SPEED_MENU_OPTIONS.map { candidate ->
                    PlayerPopupItem(
                        label = formatPlayerSpeedLabel(candidate),
                        selected = candidate == speed,
                    )
                } +
                    PlayerPopupItem(
                        label = "自定义倍速",
                        supportingText = "输入 0.00x–3.00x，最多两位小数",
                    ),
            fixedHeight = true,
            showScrollHint = true,
            compactItems = true,
            maxListHeight = 340.dp,
        ) { position ->
            val selectedSpeed = PLAYER_SPEED_MENU_OPTIONS.getOrNull(position)
            if (selectedSpeed != null) {
                session.setSpeed(selectedSpeed)
                setExpanded(false)
            } else {
                setExpanded(false)
                setCustomSpeedDialogVisible(true)
            }
        }
    }
    if (customSpeedDialogVisible) {
        PlayerCustomSpeedDialog(
            currentSpeed = speed,
            onDismiss = { setCustomSpeedDialogVisible(false) },
            onConfirm = { customSpeed ->
                if (customSpeed == 0.0) {
                    session.setPaused(true)
                } else {
                    session.setSpeed(customSpeed)
                }
                setCustomSpeedDialogVisible(false)
            },
        )
    }
}

@Composable
private fun PlayerCustomSpeedDialog(
    currentSpeed: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var input by
        remember(currentSpeed) {
            mutableStateOf(formatPlayerSpeedLabel(currentSpeed).removeSuffix("x"))
        }
    val parsedSpeed = parsePlayerSpeedInput(input)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PlayerPopupBackground,
        title = {
            Text(
                text = "自定义播放速度",
                color = PlayerPopupText,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { value ->
                    if (value.length <= 4) {
                        input = value
                    }
                },
                singleLine = true,
                isError = input.isNotBlank() && parsedSpeed == null,
                label = { Text("倍速") },
                suffix = { Text("x") },
                supportingText = {
                    Text(
                        text =
                            if (input.isNotBlank() && parsedSpeed == null) {
                                "请输入 0.00–3.00，最多两位小数"
                            } else {
                                "0.00x 会暂停播放；重新播放时沿用原倍速"
                            },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsedSpeed?.let(onConfirm) },
                enabled = parsedSpeed != null,
            ) {
                Text("应用", color = PlayerAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = PlayerPopupMutedText)
            }
        },
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
private fun PlayerPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    items: List<PlayerPopupItem>,
    fixedHeight: Boolean = false,
    showScrollHint: Boolean = false,
    compactItems: Boolean = false,
    maxListHeight: androidx.compose.ui.unit.Dp = 300.dp,
    onItemClick: (Int) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(min = 196.dp, max = 288.dp),
        shape = RoundedCornerShape(20.dp),
        containerColor = PlayerPopupBackground,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                text = title,
                color = PlayerPopupText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            )
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            val listModifier =
                if (fixedHeight) {
                    Modifier.heightIn(max = maxListHeight).verticalScroll(rememberScrollState())
                } else {
                    Modifier
                }
            Column(modifier = listModifier.padding(top = 4.dp)) {
                items.forEachIndexed { index, item ->
                    TextButton(
                        onClick = { if (item.enabled) onItemClick(index) },
                        enabled = item.enabled,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = if (compactItems) 36.dp else 52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (item.selected) {
                                        PlayerAccent.copy(alpha = 0.16f)
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp,
                                vertical = if (compactItems) 3.dp else 8.dp,
                            ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.label,
                                    color =
                                        if (item.selected) {
                                            PlayerAccent
                                        } else {
                                            PlayerPopupText
                                        },
                                    fontSize = 14.sp,
                                    fontWeight =
                                        if (item.selected) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Medium
                                        },
                                )
                                item.supportingText
                                    ?.takeIf(String::isNotBlank)
                                    ?.let { supportingText ->
                                        Text(
                                            text = supportingText,
                                            color = PlayerPopupMutedText,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                            }
                            item.toggleState?.let { checked ->
                                Switch(
                                    checked = checked,
                                    onCheckedChange = null,
                                    modifier = Modifier.size(width = 42.dp, height = 24.dp),
                                    colors =
                                        SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = PlayerAccent,
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
                                            uncheckedBorderColor = Color.White.copy(alpha = 0.20f),
                                        ),
                                )
                            }
                            if (item.selected && item.toggleState == null) {
                                Text(
                                    text = "✓",
                                    color = PlayerAccent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (showScrollHint && items.size > 5) {
                Text(
                    text = "上下滑动查看更多",
                    color = PlayerPopupMutedText,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun LegacyImageButton(
    painter: Painter,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp,
    padding: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .alpha(if (enabled) 1f else 0.42f)
                // 仅约束点击涟漪范围；按钮静态状态只显示图标，不绘制圆形底色或描边。
                .clip(CircleShape)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = description,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Composable
private fun LegacyTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .alpha(if (enabled) 1f else 0.42f)
                // 文本控制保持原点击热区，但不再绘制胶囊底色或描边。
                .clip(RoundedCornerShape(14.dp))
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LegacySeekBar(
    position: Double,
    duration: Double,
    chapters: List<PlayerChapter>,
    onValueChange: (Double) -> Unit,
    onValueChangeFinished: (Double) -> Unit,
    onValueChangeCanceled: () -> Unit,
    onInteractionStarted: () -> Unit,
    onInteractionFinished: () -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val horizontalPaddingPx = with(density) { 6.dp.toPx() }
    val progress = if (duration > 0.0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
    fun resolvePosition(x: Float, width: Float): Double {
        val start = horizontalPaddingPx
        val end = width - horizontalPaddingPx
        val fraction = ((x - start) / (end - start)).coerceIn(0f, 1f)
        return (duration * fraction).coerceIn(0.0, duration)
    }
    Canvas(
        modifier = modifier
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    onInteractionStarted()
                    val target = resolvePosition(offset.x, size.width.toFloat())
                    onValueChange(target)
                    onValueChangeFinished(target)
                    onInteractionFinished()
                }
            }
            .pointerInput(duration) {
                var pending = position
                detectDragGestures(
                    onDragStart = { offset ->
                        onInteractionStarted()
                        pending = resolvePosition(offset.x, size.width.toFloat())
                        onValueChange(pending)
                    },
                    onDragEnd = {
                        onValueChangeFinished(pending)
                        onInteractionFinished()
                    },
                    onDragCancel = {
                        onValueChangeCanceled()
                        onInteractionFinished()
                    },
                ) { change, _ ->
                    change.consume()
                    pending = resolvePosition(change.position.x, size.width.toFloat())
                    onValueChange(pending)
                }
            },
    ) {
        val horizontalPadding = horizontalPaddingPx
        val trackHeight = 3.dp.toPx()
        val trackY = size.height / 2f
        val trackWidth = (size.width - horizontalPadding * 2f).coerceAtLeast(0f)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.8f),
            topLeft = Offset(horizontalPadding, trackY - trackHeight / 2f),
            size = Size(trackWidth, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
        )
        drawRoundRect(
            brush =
                Brush.horizontalGradient(
                    listOf(PlayerAccent, PlayerAccentSecondary),
                ),
            topLeft = Offset(horizontalPadding, trackY - trackHeight / 2f),
            size = Size(trackWidth * progress, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
        )
        if (duration > 0.0) {
            chapters.forEach { chapter ->
                if (chapter.startSeconds <= 0.0 || chapter.startSeconds >= duration) return@forEach
                val chapterProgress = (chapter.startSeconds / duration).toFloat().coerceIn(0f, 1f)
                val x = horizontalPadding + trackWidth * chapterProgress
                drawLine(
                    color = Color.White,
                    start = Offset(x, trackY - 5.dp.toPx()),
                    end = Offset(x, trackY + 5.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
        }
        drawCircle(
            color = PlayerAccent,
            radius = 6.dp.toPx(),
            center = Offset(horizontalPadding + trackWidth * progress, trackY),
        )
    }
}

@Composable
private fun PlayerSeekPreviewCard(
    preview: com.ai.assistance.operit.core.player.PlayerSeekPreview?,
    position: Double,
    chapter: PlayerChapter?,
    modifier: Modifier,
) {
    Column(
        modifier =
            modifier
                .width(176.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xE8191A1D))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(94.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF08090B)),
            contentAlignment = Alignment.Center,
        ) {
            preview?.bitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "进度预览",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (preview?.loading == true) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else if (preview?.bitmap == null) {
                Text(
                    text = "正在准备预览",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 11.sp,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatPlayerTime(position),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            chapter?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = it.title,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun chapterAtPosition(
    chapters: List<PlayerChapter>,
    positionSeconds: Double,
): PlayerChapter? =
    chapters.lastOrNull { chapter -> chapter.startSeconds <= positionSeconds }

private fun formatAnime4KControlMode(mode: Anime4KMode): String =
    when (mode) {
        Anime4KMode.OFF -> "关"
        Anime4KMode.A -> "A"
        Anime4KMode.B -> "B"
        Anime4KMode.C -> "C"
        Anime4KMode.A_PLUS -> "A+"
        Anime4KMode.B_PLUS -> "B+"
        Anime4KMode.C_PLUS -> "C+"
    }

private fun formatAnime4KMode(mode: Anime4KMode): Pair<String, String> =
    when (mode) {
        Anime4KMode.OFF -> "关 - 原始画质" to "不加载 Anime4K 着色器"
        Anime4KMode.A -> "A - 强力重建" to "Restore 与双阶段 Upscale"
        Anime4KMode.B -> "B - 柔和重建" to "Soft Restore 与双阶段 Upscale"
        Anime4KMode.C -> "C - 降噪处理" to "Denoise Upscale 与二次放大"
        Anime4KMode.A_PLUS -> "A+ - 双重强化" to "强力重建后再次恢复细节"
        Anime4KMode.B_PLUS -> "B+ - 双重柔和" to "柔和重建后再次恢复细节"
        Anime4KMode.C_PLUS -> "C+ - 降噪强化" to "降噪放大后追加重建"
    }

internal fun formatPlayerNetworkSpeed(bytesPerSecond: Long): Pair<String, String> =
    if (bytesPerSecond >= 1024L * 1024L) {
        String.format(Locale.US, "%.1f", bytesPerSecond / (1024.0 * 1024.0)) to "MB/s"
    } else {
        String.format(Locale.US, "%.1f", bytesPerSecond / 1024.0) to "KB/s"
    }

internal fun formatPlayerTime(seconds: Double): String {
    val total = seconds.takeIf(Double::isFinite)?.toLong()?.coerceAtLeast(0L) ?: 0L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val remainingSeconds = total % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, remainingSeconds)
    }
}
