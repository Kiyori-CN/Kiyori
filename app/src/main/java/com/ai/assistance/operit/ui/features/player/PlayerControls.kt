package com.ai.assistance.operit.ui.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.player.Anime4KMode
import com.ai.assistance.operit.core.player.PLAYER_SPEED_OPTIONS
import com.ai.assistance.operit.core.player.PlayerMediaSource
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSessionState
import com.ai.assistance.operit.core.player.PlayerVideoFitMode
import java.util.Locale

@Composable
internal fun PlayerControls(
    state: PlayerSessionState,
    session: PlayerSession,
    controlsLocked: Boolean,
    batteryText: String,
    clockText: String,
    onBack: () -> Unit,
    onRotate: () -> Unit,
    onScreenshot: () -> Unit,
    onDownload: () -> Unit,
    onLockChanged: (Boolean) -> Unit,
) {
    if (controlsLocked) {
        Box(Modifier.fillMaxSize()) {
            PlayerIconButton(
                icon = Icons.Filled.Lock,
                description = "解锁控制",
                onClick = { onLockChanged(false) },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                size = 44,
            )
            PlayerIconButton(
                icon = Icons.Filled.Lock,
                description = "解锁控制",
                onClick = { onLockChanged(false) },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                size = 44,
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlayerTopControls(
            state = state,
            batteryText = batteryText,
            clockText = clockText,
            session = session,
            onBack = onBack,
            onScreenshot = onScreenshot,
            onDownload = onDownload,
        )
        PlayerBottomControls(state, session, onRotate)
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
    batteryText: String,
    clockText: String,
    session: PlayerSession,
    onBack: () -> Unit,
    onScreenshot: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xB3000000), Color.Transparent),
                    ),
                )
                .padding(start = 8.dp, top = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PlayerIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack, size = 40)
        Text(
            text = state.request?.title.orEmpty(),
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp, top = 5.dp, end = 8.dp),
        )
        PlayerStatusColumn(formatNetworkSpeed(state.networkSpeedBytesPerSecond).first, formatNetworkSpeed(state.networkSpeedBytesPerSecond).second)
        Spacer(Modifier.width(6.dp))
        PlayerStatusColumn(batteryText, clockText)
        PlayerIconButton(Icons.Filled.ClosedCaption, "字幕", session::cycleSubtitleTrack, size = 32)
        PlayerIconButton(Icons.Filled.AspectRatio, videoFitLabel(state.videoFitMode), session::cycleVideoFitMode, size = 32)
        PlayerMoreMenu(
            session = session,
            onScreenshot = onScreenshot,
            onDownload = onDownload,
            downloadEnabled = state.request?.source == PlayerMediaSource.BROWSER_CANDIDATE,
        )
    }
}

@Composable
private fun PlayerStatusColumn(first: String, second: String) {
    Column(
        modifier = Modifier.height(32.dp).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(first, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(second, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun PlayerMoreMenu(
    session: PlayerSession,
    onScreenshot: () -> Unit,
    onDownload: () -> Unit,
    downloadEnabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PlayerIconButton(Icons.Filled.MoreVert, "更多", { expanded = true }, size = 32)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("截图") },
                onClick = {
                    onScreenshot()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("下载视频") },
                onClick = {
                    onDownload()
                    expanded = false
                },
                enabled = downloadEnabled,
            )
            DropdownMenuItem(
                text = { Text("关闭播放器") },
                onClick = {
                    session.close()
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun BoxScope.PlayerBottomControls(
    state: PlayerSessionState,
    session: PlayerSession,
    onRotate: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC000000)),
                    ),
                )
                .padding(start = 12.dp, top = 22.dp, end = 12.dp, bottom = 6.dp),
    ) {
        var seekDraft by remember { mutableStateOf<Double?>(null) }
        val duration = state.durationSeconds.coerceAtLeast(0.0)
        val position = (seekDraft ?: state.positionSeconds).coerceIn(0.0, duration.coerceAtLeast(0.01))
        Text(
            text = "${formatPlayerTime(position)}/${formatPlayerTime(duration)}",
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 6.dp),
        )
        Slider(
            value = position.toFloat(),
            onValueChange = { seekDraft = it.toDouble() },
            onValueChangeFinished = {
                seekDraft?.let(session::seekTo)
                seekDraft = null
            },
            valueRange = 0f..duration.coerceAtLeast(0.01).toFloat(),
            modifier = Modifier.fillMaxWidth().height(30.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                PlayerIconButton(Icons.Filled.SkipPrevious, "上一项", {}, enabled = false, size = 38)
                Spacer(Modifier.width(7.dp))
                PlayerIconButton(Icons.Filled.FastRewind, "后退", session::seekBackward, size = 38)
                Spacer(Modifier.width(7.dp))
                PlayerIconButton(
                    if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    if (state.paused) "播放" else "暂停",
                    session::togglePause,
                    size = 42,
                )
                Spacer(Modifier.width(7.dp))
                PlayerIconButton(Icons.Filled.FastForward, "前进", session::seekForward, size = 38)
                Spacer(Modifier.width(7.dp))
                PlayerIconButton(Icons.Filled.SkipNext, "下一项", {}, enabled = false, size = 38)
                Spacer(Modifier.width(7.dp))
                PlayerSpeedMenu(state.speed, session::setSpeed)
            }
            TextButton(
                onClick = session::cycleAnime4KMode,
                modifier = Modifier.align(Alignment.BottomStart).height(34.dp),
            ) {
                Text(anime4KButtonLabel(state.anime4KMode), color = Color.White, fontSize = 11.sp)
            }
            TextButton(
                onClick = onRotate,
                modifier = Modifier.align(Alignment.BottomEnd).height(34.dp),
            ) {
                Icon(Icons.Filled.RotateRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(3.dp))
                Text("旋转", color = Color.White, fontSize = 11.sp)
            }
        }
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
        PlayerIconButton(Icons.Filled.CameraAlt, "截图", onScreenshot, size = 44)
        Spacer(Modifier.height(28.dp))
        PlayerIconButton(Icons.Filled.LockOpen, "锁定控制", onLock, size = 44)
        Spacer(Modifier.height(28.dp))
        PlayerIconButton(Icons.Filled.Download, "下载视频", onDownload, enabled = downloadEnabled, size = 44)
    }
}

@Composable
private fun PlayerSpeedMenu(speed: Double, onSpeedSelected: (Double) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PlayerIconButton(Icons.Filled.Speed, formatPlayerSpeed(speed), { expanded = true }, size = 38)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PLAYER_SPEED_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(formatPlayerSpeed(option)) },
                    onClick = {
                        onSpeedSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Int,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size.dp).alpha(if (enabled) 1f else 0.5f),
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size((size - 12).dp))
    }
}

private fun anime4KButtonLabel(mode: Anime4KMode): String =
    when (mode) {
        Anime4KMode.OFF -> "超分"
        Anime4KMode.FAST -> "超分·快"
        Anime4KMode.BALANCED -> "超分·中"
        Anime4KMode.QUALITY -> "超分·高"
    }

private fun videoFitLabel(mode: PlayerVideoFitMode): String =
    when (mode) {
        PlayerVideoFitMode.FIT -> "适应画面"
        PlayerVideoFitMode.CROP -> "填充画面"
        PlayerVideoFitMode.RATIO_16_9 -> "16:9"
        PlayerVideoFitMode.RATIO_4_3 -> "4:3"
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
