package com.ai.assistance.operit.ui.features.websession.browser

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSessionState
import com.ai.assistance.operit.core.player.PlayerSettingsStore
import com.ai.assistance.operit.core.player.PlayerSurfaceRole
import com.ai.assistance.operit.ui.features.player.PlayerAccent
import com.ai.assistance.operit.ui.features.player.PlayerAccentSecondary
import com.ai.assistance.operit.ui.features.player.PlayerSpeedMenu
import com.ai.assistance.operit.ui.features.player.createPlayerSurfaceView
import com.ai.assistance.operit.ui.features.player.formatPlayerNetworkSpeed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val FLOATING_UNLOCK_AUTO_HIDE_MS = 3_000L

@Composable
internal fun WebSessionFloatingPlayer(
    session: PlayerSession,
    state: PlayerSessionState,
    onTogglePause: () -> Unit,
    onFullscreen: () -> Unit,
    onDownload: (String) -> Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playerSettingsStore = remember(context) { PlayerSettingsStore.getInstance(context) }
    val playerSettings by playerSettingsStore.state.collectAsState()
    val requestId = state.request?.requestId.orEmpty()
    var controlsVisible by remember(requestId) { mutableStateOf(true) }
    var controlsLocked by remember(requestId) { mutableStateOf(false) }
    var lockedUnlockVisible by remember(requestId) { mutableStateOf(false) }
    var playerWidthPx by remember(requestId) { mutableIntStateOf(0) }
    var horizontalSeekStartSeconds by remember(requestId) { mutableFloatStateOf(0f) }
    var horizontalSeekDeltaPx by remember(requestId) { mutableFloatStateOf(0f) }
    var horizontalSeekPreviewSeconds by remember(requestId) { mutableStateOf<Double?>(null) }
    var batteryText by remember { mutableStateOf(readFloatingPlayerBatteryText(context)) }
    var clockText by remember { mutableStateOf(formatFloatingPlayerClock()) }
    LaunchedEffect(context) {
        while (true) {
            batteryText = readFloatingPlayerBatteryText(context)
            clockText = formatFloatingPlayerClock()
            delay(60_000L)
        }
    }
    LaunchedEffect(controlsLocked, lockedUnlockVisible) {
        if (controlsLocked && lockedUnlockVisible) {
            delay(FLOATING_UNLOCK_AUTO_HIDE_MS)
            if (controlsLocked) lockedUnlockVisible = false
        }
    }

    val durationSeconds = state.durationSeconds.coerceAtLeast(0.0)
    val positionSeconds = state.positionSeconds.coerceIn(0.0, durationSeconds.coerceAtLeast(0.0))
    val seekStepSeconds = playerSettings.seekStepSeconds
    val featureAction = onFullscreen
    val networkSpeed =
        remember(state.networkSpeedBytesPerSecond) {
            formatPlayerNetworkSpeed(state.networkSpeedBytesPerSecond)
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = Color(0xFF111111),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .onSizeChanged { playerWidthPx = it.width }
                    .pointerInput(requestId, controlsLocked, controlsVisible) {
                        detectTapGestures { offset ->
                            if (!controlsLocked) {
                                val topControlsHeightPx = 56.dp.toPx()
                                val bottomControlsHeightPx = 92.dp.toPx()
                                val sideActionsWidthPx = 52.dp.toPx()
                                val tapOnVideoBody =
                                    offset.y > topControlsHeightPx &&
                                        offset.y < size.height - bottomControlsHeightPx &&
                                        offset.x < size.width - sideActionsWidthPx
                                if (!controlsVisible || tapOnVideoBody) {
                                    controlsVisible = !controlsVisible
                                }
                            }
                        }
                    }
                    .pointerInput(requestId, controlsLocked, durationSeconds, playerWidthPx) {
                        if (!controlsLocked && durationSeconds > 0.0 && playerWidthPx > 0) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    horizontalSeekStartSeconds =
                                        state.positionSeconds.coerceIn(0.0, durationSeconds).toFloat()
                                    horizontalSeekDeltaPx = 0f
                                    horizontalSeekPreviewSeconds = horizontalSeekStartSeconds.toDouble()
                                    controlsVisible = true
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    horizontalSeekDeltaPx += dragAmount
                                    val deltaSeconds =
                                        durationSeconds * (horizontalSeekDeltaPx / playerWidthPx.toFloat())
                                    horizontalSeekPreviewSeconds =
                                        (horizontalSeekStartSeconds + deltaSeconds)
                                            .coerceIn(0.0, durationSeconds)
                                },
                                onDragCancel = {
                                    horizontalSeekPreviewSeconds = null
                                    horizontalSeekDeltaPx = 0f
                                },
                                onDragEnd = {
                                    horizontalSeekPreviewSeconds?.let(session::seekTo)
                                    horizontalSeekPreviewSeconds = null
                                    horizontalSeekDeltaPx = 0f
                                },
                            )
                        }
                    },
        ) {
            AndroidView(
                factory = { viewContext ->
                    createPlayerSurfaceView(
                        context = viewContext,
                        session = session,
                        role = PlayerSurfaceRole.FLOATING,
                        mediaOverlay = true,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (!controlsLocked) {
                if (controlsVisible) {
                    FloatingPlayerTopControls(
                        title = state.request?.title.orEmpty().ifBlank { "在线视频" },
                        networkSpeedValue = networkSpeed.first,
                        networkSpeedUnit = networkSpeed.second,
                        batteryText = batteryText,
                        clockText = clockText,
                        onClose = onClose,
                        onSubtitle = featureAction,
                        onDanmaku = featureAction,
                        onAudio = featureAction,
                        onAspectRatio = featureAction,
                        onMore = featureAction,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    FloatingPlayerSideActions(
                        onScreenshot = {
                            session.captureScreenshot { result ->
                                Toast.makeText(
                                    context,
                                    result.fold(
                                        onSuccess = { "截图已保存：$it" },
                                        onFailure = { "截图失败：${it.message ?: it.javaClass.simpleName}" },
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onLock = {
                            controlsLocked = true
                            lockedUnlockVisible = true
                        },
                        onDownload = {
                            val accepted = requestId.isNotBlank() && onDownload(requestId)
                            Toast.makeText(
                                context,
                                if (accepted) "已交给文件下载器" else "当前视频没有可用的浏览器下载请求",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                    FloatingPlayerBottomControls(
                        currentPositionSeconds = positionSeconds,
                        durationSeconds = durationSeconds,
                        isPlaying = !state.paused,
                        seekSeconds = seekStepSeconds,
                        onSeekTo = session::seekTo,
                        onDanmaku = featureAction,
                        onRewind = session::seekBackward,
                        onPlayPause = onTogglePause,
                        onForward = session::seekForward,
                        speed = state.speed,
                        session = session,
                        onInteraction = { controlsVisible = true },
                        onPopupVisibilityChanged = { controlsVisible = true },
                        onAnime4K = session::cycleAnime4KMode,
                        onRotate = onFullscreen,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .pointerInput(requestId, lockedUnlockVisible) {
                                detectTapGestures {
                                    lockedUnlockVisible = !lockedUnlockVisible
                                }
                            },
                )
                if (lockedUnlockVisible) {
                    FloatingPlayerLockedUnlockButtons(
                        onUnlock = {
                            controlsLocked = false
                            lockedUnlockVisible = false
                        },
                    )
                }
            }

            horizontalSeekPreviewSeconds?.let { previewSeconds ->
                FloatingPlayerStatusMessage(
                    text =
                        "${formatFloatingPlayerTime(previewSeconds)}/" +
                            formatFloatingPlayerTime(durationSeconds),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (state.loading || state.buffering) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
            state.error?.let { message ->
                FloatingPlayerStatusMessage(
                    text = message,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun FloatingPlayerTopControls(
    title: String,
    networkSpeedValue: String,
    networkSpeedUnit: String,
    batteryText: String,
    clockText: String,
    onClose: () -> Unit,
    onSubtitle: () -> Unit,
    onDanmaku: () -> Unit,
    onAudio: () -> Unit,
    onAspectRatio: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0f to Color(0xB0000000),
                                0.5f to Color(0x60000000),
                                1f to Color.Transparent,
                            ),
                    ),
                )
                .padding(start = 4.dp, top = 4.dp, end = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingPlayerResourceButton(
                description = "退出小窗",
                resId = R.drawable.arrow_left,
                size = 28.dp,
                iconSize = 16.dp,
                onClick = onClose,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FloatingPlayerStackedStatus(
                    primary = networkSpeedValue,
                    secondary = networkSpeedUnit,
                )
                FloatingPlayerStackedStatus(
                    primary = batteryText,
                    secondary = clockText,
                )
                FloatingPlayerResourceButton(
                    description = "字幕",
                    resId = R.drawable.ic_kiyori_player_subtitle_outline,
                    size = 24.dp,
                    iconSize = 14.dp,
                    onClick = onSubtitle,
                )
                FloatingPlayerResourceButton(
                    description = "弹幕",
                    resId = R.drawable.ic_kiyori_player_danmaku_outline,
                    size = 24.dp,
                    iconSize = 14.dp,
                    onClick = onDanmaku,
                )
                FloatingPlayerResourceButton(
                    description = "音轨",
                    resId = R.drawable.ic_kiyori_player_audio_outline,
                    size = 24.dp,
                    iconSize = 14.dp,
                    onClick = onAudio,
                )
                FloatingPlayerResourceButton(
                    description = "画面比例",
                    resId = R.drawable.ic_kiyori_player_aspect_outline,
                    size = 24.dp,
                    iconSize = 14.dp,
                    onClick = onAspectRatio,
                )
                FloatingPlayerResourceButton(
                    description = "更多选项",
                    resId = R.drawable.menudotsvertical,
                    size = 24.dp,
                    iconSize = 14.dp,
                    onClick = onMore,
                )
            }
        }
    }
}

@Composable
private fun FloatingPlayerStackedStatus(
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(min = 28.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = primary,
            color = Color.White,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = secondary,
            color = Color.White,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun BoxScope.FloatingPlayerSideActions(
    onScreenshot: () -> Unit,
    onLock: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FloatingPlayerResourceButton(
            description = "截图",
            resId = R.drawable.ic_kiyori_player_camera_outline,
            size = 30.dp,
            iconSize = 18.dp,
            onClick = onScreenshot,
        )
        FloatingPlayerResourceButton(
            description = "锁定控制",
            resId = R.drawable.ic_kiyori_player_unlock_outline,
            size = 30.dp,
            iconSize = 18.dp,
            onClick = onLock,
        )
        FloatingPlayerResourceButton(
            description = "下载",
            resId = R.drawable.ic_kiyori_player_download_outline,
            size = 30.dp,
            iconSize = 18.dp,
            onClick = onDownload,
        )
    }
}

@Composable
private fun FloatingPlayerStatusMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.52f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun FloatingPlayerBottomControls(
    currentPositionSeconds: Double,
    durationSeconds: Double,
    isPlaying: Boolean,
    seekSeconds: Int,
    onSeekTo: (Double) -> Unit,
    onDanmaku: () -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    speed: Double,
    session: PlayerSession,
    onInteraction: () -> Unit,
    onPopupVisibilityChanged: (Boolean) -> Unit,
    onAnime4K: () -> Unit,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0f to Color.Transparent,
                                0.5f to Color(0x60000000),
                                1f to Color(0xB0000000),
                            ),
                    ),
                )
                .padding(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Text(
                text =
                    "${formatFloatingPlayerTime(currentPositionSeconds)}/" +
                        formatFloatingPlayerTime(durationSeconds),
                color = Color.White,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
            FloatingPlayerSeekBar(
                currentPositionSeconds = currentPositionSeconds,
                durationSeconds = durationSeconds,
                onSeekTo = onSeekTo,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(32.dp),
            ) {
                FloatingPlayerTextButton(
                    text = "超分",
                    modifier = Modifier.align(Alignment.CenterStart),
                    onClick = onAnime4K,
                )
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FloatingPlayerResourceButton(
                        description = "弹幕",
                        resId = R.drawable.ic_danmaku_visible,
                        size = 26.dp,
                        iconSize = 16.dp,
                        onClick = onDanmaku,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    FloatingPlayerResourceButton(
                        description = "上一集",
                        resId = R.drawable.previous_square,
                        enabled = false,
                        size = 26.dp,
                        iconSize = 16.dp,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    FloatingPlayerResourceButton(
                        description = "后退 $seekSeconds 秒",
                        resId = R.drawable.ic_rewind_new,
                        size = 26.dp,
                        iconSize = 16.dp,
                        onClick = onRewind,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    FloatingPlayerResourceButton(
                        description = if (isPlaying) "暂停" else "播放",
                        resId = if (isPlaying) R.drawable.pause else R.drawable.play,
                        size = 30.dp,
                        iconSize = 18.dp,
                        onClick = onPlayPause,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    FloatingPlayerResourceButton(
                        description = "前进 $seekSeconds 秒",
                        resId = R.drawable.ic_forward_new,
                        size = 26.dp,
                        iconSize = 16.dp,
                        onClick = onForward,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    FloatingPlayerResourceButton(
                        description = "下一集",
                        resId = R.drawable.next_square,
                        enabled = false,
                        size = 26.dp,
                        iconSize = 16.dp,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    PlayerSpeedMenu(
                        speed = speed,
                        session = session,
                        onInteraction = onInteraction,
                        onPopupVisibilityChanged = onPopupVisibilityChanged,
                        size = 26.dp,
                        padding = 5.dp,
                    )
                }
                FloatingPlayerTextButton(
                    text = "旋转",
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = onRotate,
                )
            }
        }
    }
}

@Composable
private fun FloatingPlayerSeekBar(
    currentPositionSeconds: Double,
    durationSeconds: Double,
    onSeekTo: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var trackWidthPx by remember { mutableIntStateOf(0) }
    var seekDraftSeconds by remember(durationSeconds) { mutableStateOf<Double?>(null) }
    val density = LocalDensity.current
    val thumbSizePx = with(density) { 7.dp.roundToPx() }
    val displayedPositionSeconds = seekDraftSeconds ?: currentPositionSeconds
    val progressFraction =
        if (durationSeconds > 0.0) {
            (displayedPositionSeconds / durationSeconds).toFloat()
        } else {
            0f
        }.coerceIn(0f, 1f)
    fun resolveSeekTarget(positionX: Float): Double =
        durationSeconds *
            (positionX / trackWidthPx.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier =
            modifier
                .height(18.dp)
                .padding(horizontal = 6.dp)
                .onSizeChanged { trackWidthPx = it.width }
                .pointerInput(durationSeconds, trackWidthPx) {
                    if (durationSeconds > 0.0 && trackWidthPx > 0) {
                        detectTapGestures { offset ->
                            onSeekTo(resolveSeekTarget(offset.x))
                        }
                    }
                }
                .pointerInput(durationSeconds, trackWidthPx) {
                    if (durationSeconds > 0.0 && trackWidthPx > 0) {
                        var pendingTarget = currentPositionSeconds.coerceIn(0.0, durationSeconds)
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                pendingTarget = resolveSeekTarget(offset.x)
                                seekDraftSeconds = pendingTarget
                            },
                            onDragEnd = {
                                onSeekTo(pendingTarget)
                                seekDraftSeconds = null
                            },
                            onDragCancel = {
                                seekDraftSeconds = null
                            },
                        ) { change, _ ->
                            change.consume()
                            pendingTarget = resolveSeekTarget(change.position.x)
                            seekDraftSeconds = pendingTarget
                        }
                    }
                },
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(1.5.dp)),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(progressFraction)
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(PlayerAccent, PlayerAccentSecondary),
                        ),
                        RoundedCornerShape(1.5.dp),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        val maxOffset = (trackWidthPx - thumbSizePx).coerceAtLeast(0)
                        IntOffset((maxOffset * progressFraction).roundToInt(), 0)
                    }
                    .size(7.dp)
                    .background(PlayerAccent, CircleShape),
        )
    }
}

@Composable
private fun BoxScope.FloatingPlayerLockedUnlockButtons(
    onUnlock: () -> Unit,
) {
    FloatingPlayerResourceButton(
        description = "解锁控制",
        resId = R.drawable.ic_kiyori_player_lock_outline,
        size = 32.dp,
        iconSize = 20.dp,
        onClick = onUnlock,
        modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
    )
    FloatingPlayerResourceButton(
        description = "解锁控制",
        resId = R.drawable.ic_kiyori_player_lock_outline,
        size = 32.dp,
        iconSize = 20.dp,
        onClick = onUnlock,
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
    )
}

@Composable
private fun FloatingPlayerResourceButton(
    description: String,
    resId: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 34.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    onClick: () -> Unit = {},
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
    ) {
        Icon(
            painter = painterResource(id = resId),
            contentDescription = description,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun FloatingPlayerTextButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(26.dp)
                .widthIn(min = 36.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            maxLines = 1,
        )
    }
}

private fun formatFloatingPlayerTime(positionSeconds: Double): String {
    val totalSeconds = positionSeconds.coerceAtLeast(0.0).roundToInt()
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%02d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

private fun readFloatingPlayerBatteryText(context: Context): String {
    val intent =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "--%"
    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return "--%"
    return "${(level / scale.toFloat() * 100).roundToInt()}%"
}

private fun formatFloatingPlayerClock(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
