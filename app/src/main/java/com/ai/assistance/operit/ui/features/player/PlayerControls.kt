package com.ai.assistance.operit.ui.features.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.player.PLAYER_SPEED_OPTIONS
import com.ai.assistance.operit.core.player.PlayerMediaSource
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSessionState
import com.ai.assistance.operit.core.player.PlayerVideoFitMode
import java.util.Locale

private val LegacyPrimary = Color(0xFF667EEA)
private val LegacyPopupBackground = Color(0xFFE8ECFE)
private val LegacyPopupText = Color(0xFF333333)

@Composable
internal fun PlayerControls(
    state: PlayerSessionState,
    session: PlayerSession,
    controlsLocked: Boolean,
    networkSpeedBytesPerSecond: Long,
    batteryText: String,
    clockText: String,
    onBack: () -> Unit,
    onRotate: () -> Unit,
    onScreenshot: () -> Unit,
    onDownload: () -> Unit,
    onShowPlaybackLog: () -> Unit,
    onLockChanged: (Boolean) -> Unit,
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
            isPortrait = isPortrait,
            onBack = onBack,
            onShowPlaybackLog = onShowPlaybackLog,
        )
        PlayerBottomControls(
            state = state,
            session = session,
            isPortrait = isPortrait,
            onRotate = onRotate,
        )
        PlayerSideActions(
            downloadEnabled = state.request?.source == PlayerMediaSource.BROWSER_CANDIDATE,
            onScreenshot = onScreenshot,
            onLock = { onLockChanged(true) },
            onDownload = onDownload,
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
    isPortrait: Boolean,
    onBack: () -> Unit,
    onShowPlaybackLog: () -> Unit,
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
                onClick = onBack,
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
                        first = formatNetworkSpeed(networkSpeedBytesPerSecond).first,
                        second = formatNetworkSpeed(networkSpeedBytesPerSecond).second,
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
                        modifier = Modifier.weight(1f),
                        size = 28.dp,
                        padding = 4.dp,
                    )
                    PlayerDanmakuButton(
                        modifier = Modifier.weight(1f),
                        size = 28.dp,
                        padding = 4.dp,
                    )
                    PlayerAudioTrackPopup(
                        state = state,
                        session = session,
                        modifier = Modifier.weight(1f),
                        size = 28.dp,
                        padding = 4.dp,
                    )
                    PlayerAspectPopup(
                        session = session,
                        modifier = Modifier.weight(1f),
                        size = 28.dp,
                        padding = 4.dp,
                    )
                    PlayerMorePopup(
                        onShowPlaybackLog = onShowPlaybackLog,
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
                    first = formatNetworkSpeed(networkSpeedBytesPerSecond).first,
                    second = formatNetworkSpeed(networkSpeedBytesPerSecond).second,
                )
                Spacer(Modifier.width(4.dp))
                PlayerStatusColumn(first = batteryText, second = clockText)
                Spacer(Modifier.width(2.dp))
                PlayerSubtitlePopup(state, session)
                Spacer(Modifier.width(2.dp))
                PlayerDanmakuButton()
                Spacer(Modifier.width(2.dp))
                PlayerAudioTrackPopup(state, session)
                Spacer(Modifier.width(2.dp))
                PlayerAspectPopup(session)
                Spacer(Modifier.width(2.dp))
                PlayerMorePopup(onShowPlaybackLog = onShowPlaybackLog)
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
        modifier = modifier.height(32.dp).padding(horizontal = if (compact) 2.dp else 4.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(first, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(second, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun PlayerSubtitlePopup(
    state: PlayerSessionState,
    session: PlayerSession,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    padding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = buildList {
        add("关闭字幕")
        state.subtitleTracks.forEach { track -> add(track.title) }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LegacyImageButton(
            painter = painterResource(R.drawable.ic_kiyori_player_subtitle_outline),
            description = "字幕",
            onClick = { expanded = true },
            size = size,
            padding = padding,
        )
        LegacyPopupMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = items,
            fixedHeight = items.size > 3,
            showScrollHint = items.size > 3,
        ) { position ->
            session.setSubtitleTrack(if (position == 0) null else state.subtitleTracks[position - 1].id)
            expanded = false
        }
    }
}

@Composable
private fun PlayerDanmakuButton(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    padding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LegacyImageButton(
            painter = painterResource(R.drawable.ic_kiyori_player_danmaku_outline),
            description = "弹幕",
            onClick = {},
            size = size,
            padding = padding,
        )
    }
}

@Composable
private fun PlayerAudioTrackPopup(
    state: PlayerSessionState,
    session: PlayerSession,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    padding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = state.audioTracks.map { it.title }.ifEmpty { listOf("没有可用的音频轨道") }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LegacyImageButton(
            painter = painterResource(R.drawable.ic_kiyori_player_audio_outline),
            description = "音轨",
            onClick = { expanded = true },
            size = size,
            padding = padding,
        )
        LegacyPopupMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = items,
        ) { position ->
            state.audioTracks.getOrNull(position)?.let { session.setAudioTrack(it.id) }
            expanded = false
        }
    }
}

@Composable
private fun PlayerAspectPopup(
    session: PlayerSession,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    padding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = listOf("适应屏幕", "拉伸", "裁剪")
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LegacyImageButton(
            painter = painterResource(R.drawable.ic_kiyori_player_aspect_outline),
            description = "画面比例",
            onClick = { expanded = true },
            size = size,
            padding = padding,
        )
        LegacyPopupMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = items,
        ) { position ->
            session.setVideoFitMode(
                when (position) {
                    1 -> PlayerVideoFitMode.STRETCH
                    2 -> PlayerVideoFitMode.CROP
                    else -> PlayerVideoFitMode.FIT
                },
            )
            expanded = false
        }
    }
}

@Composable
private fun PlayerMorePopup(
    onShowPlaybackLog: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    padding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = listOf("解码", "投屏", "听视频", "片头片尾", "自动旋转", "查看日志")
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LegacyImageButton(
            painter = painterResource(R.drawable.menudotsvertical),
            description = "更多选项",
            onClick = { expanded = true },
            size = size,
            padding = padding,
        )
        LegacyPopupMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = items,
            fixedHeight = true,
            showScrollHint = true,
        ) { position ->
            if (position == items.lastIndex) onShowPlaybackLog()
            expanded = false
        }
    }
}

@Composable
private fun BoxScope.PlayerBottomControls(
    state: PlayerSessionState,
    session: PlayerSession,
    isPortrait: Boolean,
    onRotate: () -> Unit,
) {
    var seekDraft by remember { mutableStateOf<Double?>(null) }
    val duration = state.durationSeconds.coerceAtLeast(0.0)
    val position = (seekDraft ?: state.positionSeconds).coerceIn(0.0, duration.coerceAtLeast(0.01))
    Box(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(100.dp).background(
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
                    Text(
                        text = "${formatPlayerTime(position)}/${formatPlayerTime(duration)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    LegacySeekBar(
                        position = position,
                        duration = duration,
                        onValueChange = { seekDraft = it },
                        onValueChangeFinished = { target ->
                            session.seekTo(target)
                            seekDraft = null
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
                        PortraitTextControlCell("超分", session::cycleAnime4KMode)
                        PortraitImageControlCell(
                            painter = painterResource(R.drawable.ic_danmaku_visible),
                            description = "弹幕",
                            onClick = {},
                        )
                        PortraitImageControlCell(
                            painter = painterResource(R.drawable.previous_square),
                            description = "上一项",
                            onClick = {},
                            enabled = false,
                        )
                        PortraitImageControlCell(
                            painter = painterResource(R.drawable.ic_rewind_new),
                            description = "后退",
                            onClick = session::seekBackward,
                        )
                        PortraitImageControlCell(
                            painter = painterResource(if (state.paused) R.drawable.play else R.drawable.pause),
                            description = if (state.paused) "播放" else "暂停",
                            onClick = session::togglePause,
                            size = 36.dp,
                        )
                        PortraitImageControlCell(
                            painter = painterResource(R.drawable.ic_forward_new),
                            description = "前进",
                            onClick = session::seekForward,
                        )
                        PortraitImageControlCell(
                            painter = painterResource(R.drawable.next_square),
                            description = "下一项",
                            onClick = {},
                            enabled = false,
                        )
                        Box(
                            modifier = Modifier.weight(1f).height(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PlayerSpeedMenu(
                                speed = state.speed,
                                session = session,
                                size = 32.dp,
                                padding = 5.dp,
                            )
                        }
                        PortraitTextControlCell("旋转", onRotate)
                    }
                } else {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        LegacyImageButton(
                            painterResource(R.drawable.ic_danmaku_visible),
                            "弹幕",
                            onClick = {},
                            size = 38.dp,
                            padding = 6.dp,
                        )
                        Spacer(Modifier.width(7.dp))
                        LegacyImageButton(
                            painterResource(R.drawable.previous_square),
                            "上一项",
                            onClick = {},
                            size = 38.dp,
                            padding = 6.dp,
                            enabled = false,
                        )
                        Spacer(Modifier.width(7.dp))
                        LegacyImageButton(
                            painterResource(R.drawable.ic_rewind_new),
                            "后退",
                            onClick = session::seekBackward,
                            size = 38.dp,
                            padding = 6.dp,
                        )
                        Spacer(Modifier.width(7.dp))
                        LegacyImageButton(
                            painter = painterResource(if (state.paused) R.drawable.play else R.drawable.pause),
                            description = if (state.paused) "播放" else "暂停",
                            onClick = session::togglePause,
                            size = 42.dp,
                            padding = 6.dp,
                        )
                        Spacer(Modifier.width(7.dp))
                        LegacyImageButton(
                            painterResource(R.drawable.ic_forward_new),
                            "前进",
                            onClick = session::seekForward,
                            size = 38.dp,
                            padding = 6.dp,
                        )
                        Spacer(Modifier.width(7.dp))
                        LegacyImageButton(
                            painterResource(R.drawable.next_square),
                            "下一项",
                            onClick = {},
                            size = 38.dp,
                            padding = 6.dp,
                            enabled = false,
                        )
                        Spacer(Modifier.width(7.dp))
                        PlayerSpeedMenu(speed = state.speed, session = session)
                    }
                    LegacyTextButton(
                        text = "超分",
                        onClick = session::cycleAnime4KMode,
                        modifier = Modifier.align(Alignment.BottomStart).height(34.dp),
                    )
                    LegacyTextButton(
                        text = "旋转",
                        onClick = onRotate,
                        modifier = Modifier.align(Alignment.BottomEnd).height(34.dp),
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
private fun androidx.compose.foundation.layout.RowScope.PortraitTextControlCell(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f).height(48.dp).clickable(onClick = onClick),
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
    modifier: Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LegacyImageButton(
            painterResource(R.drawable.ic_kiyori_player_camera_outline),
            "截图",
            onScreenshot,
            size = 44.dp,
            padding = 8.dp,
        )
        Spacer(Modifier.height(28.dp))
        LegacyImageButton(
            painterResource(R.drawable.ic_kiyori_player_unlock_outline),
            "锁定控制",
            onLock,
            size = 44.dp,
            padding = 8.dp,
        )
        Spacer(Modifier.height(28.dp))
        LegacyImageButton(
            painterResource(R.drawable.ic_kiyori_player_download_outline),
            "下载视频",
            onDownload,
            size = 44.dp,
            padding = 8.dp,
            enabled = downloadEnabled,
        )
    }
}

@Composable
private fun PlayerSpeedMenu(
    speed: Double,
    session: PlayerSession,
    size: androidx.compose.ui.unit.Dp = 38.dp,
    padding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        LegacyImageButton(
            painterResource(R.drawable.tachometer_alt_fastest),
            formatPlayerSpeed(speed),
            onClick = { expanded = true },
            size = size,
            padding = padding,
        )
        LegacyPopupMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = PLAYER_SPEED_OPTIONS.map(::formatPlayerSpeed),
            fixedHeight = true,
            showScrollHint = true,
        ) { position ->
            PLAYER_SPEED_OPTIONS.getOrNull(position)?.let(session::setSpeed)
            expanded = false
        }
    }
}

@Composable
private fun LegacyPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<String>,
    fixedHeight: Boolean = false,
    showScrollHint: Boolean = false,
    onItemClick: (Int) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .background(LegacyPopupBackground, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .widthIn(min = 100.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (fixedHeight) {
            Column(Modifier.height(144.dp).verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, item ->
                    TextButton(
                        onClick = { onItemClick(index) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(item, color = LegacyPopupText, fontSize = 15.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            if (showScrollHint && items.size > 3) {
                Text(
                    text = "可上下滑动",
                    color = LegacyPopupText.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        } else {
            items.forEachIndexed { index, item ->
                TextButton(
                    onClick = { onItemClick(index) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(item, color = LegacyPopupText, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
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
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.5f)
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
private fun LegacyTextButton(text: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
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
    onValueChange: (Double) -> Unit,
    onValueChangeFinished: (Double) -> Unit,
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
                    val target = resolvePosition(offset.x, size.width.toFloat())
                    onValueChange(target)
                    onValueChangeFinished(target)
                }
            }
            .pointerInput(duration) {
                var pending = position
                detectDragGestures(
                    onDragStart = { offset ->
                        pending = resolvePosition(offset.x, size.width.toFloat())
                        onValueChange(pending)
                    },
                    onDragEnd = { onValueChangeFinished(pending) },
                    onDragCancel = { onValueChangeFinished(pending) },
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
            color = LegacyPrimary,
            topLeft = Offset(horizontalPadding, trackY - trackHeight / 2f),
            size = Size(trackWidth * progress, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
        )
        drawCircle(
            color = LegacyPrimary,
            radius = 6.dp.toPx(),
            center = Offset(horizontalPadding + trackWidth * progress, trackY),
        )
    }
}

private fun formatNetworkSpeed(bytesPerSecond: Long): Pair<String, String> =
    if (bytesPerSecond >= 1024L * 1024L) {
        String.format(Locale.US, "%.1f", bytesPerSecond / (1024.0 * 1024.0)) to "MB/s"
    } else {
        String.format(Locale.US, "%.1f", bytesPerSecond / 1024.0) to "KB/s"
    }

private fun formatPlayerSpeed(speed: Double): String =
    if (speed % 1.0 == 0.0) "${speed.toInt()}x" else String.format(Locale.US, "%.2gx", speed)

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
