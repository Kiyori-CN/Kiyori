package com.ai.assistance.operit.ui.features.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSessionState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class PlayerGestureMode {
    SEEK,
    BRIGHTNESS,
    VOLUME,
}

@Composable
internal fun PlayerGestureLayer(
    session: PlayerSession,
    state: PlayerSessionState,
    enabled: Boolean,
    onToggleControls: () -> Unit,
    modifier: Modifier,
) {
    val activity = LocalContext.current as PlayerActivity
    val audioManager = remember(activity) { activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var mode by remember { mutableStateOf<PlayerGestureMode?>(null) }
    var start by remember { mutableStateOf(Offset.Zero) }
    var total by remember { mutableStateOf(Offset.Zero) }
    var basePosition by remember { mutableStateOf(0.0) }
    var baseBrightness by remember { mutableStateOf(0.5f) }
    var baseVolume by remember { mutableStateOf(0) }
    var pendingSeek by remember { mutableStateOf<Double?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var verticalProgress by remember { mutableStateOf<Float?>(null) }
    var verticalOnRight by remember { mutableStateOf(false) }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(900)
            feedback = null
            verticalProgress = null
        }
    }

    Box(
        modifier =
            modifier
                .onSizeChanged { size = it }
                .pointerInput(enabled, state.positionSeconds) {
                    if (enabled) {
                        detectTapGestures(
                            onTap = { onToggleControls() },
                            onDoubleTap = { offset ->
                                if (offset.x < size.width / 2f) {
                                    session.seekBackward()
                                    feedback = "后退"
                                } else {
                                    session.seekForward()
                                    feedback = "前进"
                                }
                            },
                        )
                    }
                }
                .pointerInput(enabled, state.positionSeconds, state.durationSeconds, size) {
                    if (enabled) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                start = offset
                                total = Offset.Zero
                                mode = null
                                basePosition = state.positionSeconds
                                baseBrightness =
                                    activity.window.attributes.screenBrightness.takeIf { it >= 0f } ?: 0.5f
                                baseVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                pendingSeek = null
                                verticalProgress = null
                            },
                            onDragEnd = {
                                pendingSeek?.let(session::seekTo)
                                pendingSeek = null
                                mode = null
                            },
                            onDragCancel = {
                                pendingSeek = null
                                mode = null
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            total += dragAmount
                            if (mode == null && (abs(total.x) > 12f || abs(total.y) > 12f)) {
                                mode =
                                    if (abs(total.x) >= abs(total.y)) {
                                        PlayerGestureMode.SEEK
                                    } else if (start.x < size.width / 2f) {
                                        PlayerGestureMode.BRIGHTNESS
                                    } else {
                                        PlayerGestureMode.VOLUME
                                    }
                            }
                            when (mode) {
                                PlayerGestureMode.SEEK -> {
                                    val span = state.durationSeconds.coerceAtMost(300.0)
                                    val delta = if (size.width > 0) total.x / size.width * span else 0.0
                                    val target =
                                        (basePosition + delta).coerceIn(
                                            0.0,
                                            state.durationSeconds.coerceAtLeast(0.0),
                                        )
                                    pendingSeek = target
                                    feedback = "${formatPlayerTime(target)}\n[${if (delta >= 0) "+" else ""}${delta.toInt()}秒]"
                                }
                                PlayerGestureMode.BRIGHTNESS -> {
                                    val attributes = activity.window.attributes
                                    val delta = if (size.height > 0) -total.y / size.height else 0f
                                    val next = (baseBrightness + delta).coerceIn(0.01f, 1f)
                                    attributes.screenBrightness = next
                                    activity.window.attributes = attributes
                                    verticalProgress = next
                                    verticalOnRight = false
                                    feedback = "亮度 ${(next * 100).toInt()}"
                                }
                                PlayerGestureMode.VOLUME -> {
                                    val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val delta =
                                        if (size.height > 0) {
                                            (-total.y / size.height * maximum).roundToInt()
                                        } else {
                                            0
                                        }
                                    val next = (baseVolume + delta).coerceIn(0, maximum)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                    verticalProgress = next.toFloat() / maximum.coerceAtLeast(1)
                                    verticalOnRight = true
                                    feedback = "音量 ${(next * 100 / maximum.coerceAtLeast(1))}"
                                }
                                null -> Unit
                            }
                        }
                    }
                },
    ) {
        if (verticalProgress != null) {
            PlayerVerticalIndicator(
                value = verticalProgress ?: 0f,
                label = feedback.orEmpty(),
                modifier =
                    if (verticalOnRight) {
                        Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
                    } else {
                        Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
                    },
            )
        } else if (feedback != null) {
            Text(
                text = feedback.orEmpty(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .background(Color(0xB3000000))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PlayerVerticalIndicator(value: Float, label: String, modifier: Modifier) {
    Column(
        modifier = modifier.width(58.dp).height(170.dp).padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier.padding(top = 8.dp).width(4.dp).weight(1f).background(Color(0x55FFFFFF)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight(value.coerceIn(0f, 1f))
                        .background(Color.White),
            )
        }
    }
}
