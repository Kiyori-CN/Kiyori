package com.ai.assistance.operit.ui.features.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSessionState
import com.ai.assistance.operit.core.player.PlayerDoubleTapAction
import com.ai.assistance.operit.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class PlayerGestureMode {
    SEEK,
    BRIGHTNESS,
    VOLUME,
}

@Composable
internal fun PlayerGestureLayer(
    session: PlayerSession,
    state: PlayerSessionState,
    doubleTapAction: PlayerDoubleTapAction,
    doubleTapSeekSeconds: Int,
    gesturesEnabled: Boolean,
    onSingleTap: () -> Unit,
    onInteraction: () -> Unit,
    onGestureActiveChanged: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val activity = LocalContext.current as PlayerActivity
    val audioManager = remember(activity) { activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val scope = rememberCoroutineScope()
    val latestState by rememberUpdatedState(state)
    val latestDoubleTapAction by rememberUpdatedState(doubleTapAction)
    val latestDoubleTapSeekSeconds by rememberUpdatedState(doubleTapSeekSeconds)
    val latestGesturesEnabled by rememberUpdatedState(gesturesEnabled)
    val latestOnSingleTap by rememberUpdatedState(onSingleTap)
    val latestOnInteraction by rememberUpdatedState(onInteraction)
    val latestOnGestureActiveChanged by rememberUpdatedState(onGestureActiveChanged)
    var size by remember { mutableStateOf(IntSize.Zero) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var verticalProgress by remember { mutableStateOf<Float?>(null) }
    var verticalOnRight by remember { mutableStateOf(false) }
    var verticalIsBrightness by remember { mutableStateOf(false) }
    var verticalPercent by remember { mutableStateOf(0) }
    var lastTapTimeMillis by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    var pendingSingleTap by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(900)
            feedback = null
            verticalProgress = null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            pendingSingleTap?.cancel()
            latestOnGestureActiveChanged(false)
        }
    }

    Box(
        modifier =
            modifier
                .onSizeChanged { size = it }
                // 播放进度会高频更新，因此手势 detector 必须只创建一次；把 position/duration
                // 放进 pointerInput key 会在 ACTION_DOWN 与 ACTION_UP 之间取消识别协程。
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPosition = down.position
                        val downState = latestState
                        val gestureAllowed = latestGesturesEnabled
                        val basePosition = downState.positionSeconds
                        val baseBrightness =
                            activity.window.attributes.screenBrightness.takeIf { it >= 0f } ?: 0.5f
                        val baseVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val maximumVolume =
                            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        var mode: PlayerGestureMode? = null
                        var pendingSeek: Double? = null
                        var dragActive = false
                        var gestureActiveReported = false

                        try {
                            do {
                                val event = awaitPointerEvent()
                                val pointer =
                                    event.changes.firstOrNull { change -> change.id == down.id }
                                        ?: break
                                val total = pointer.position - downPosition
                                if (!dragActive && total.getDistance() > viewConfiguration.touchSlop) {
                                    dragActive = true
                                    if (gestureAllowed) {
                                        gestureActiveReported = true
                                        latestOnGestureActiveChanged(true)
                                        verticalProgress = null
                                    }
                                }

                                if (dragActive && gestureAllowed) {
                                    if (mode == null) {
                                        mode =
                                            if (abs(total.x) >= abs(total.y)) {
                                                PlayerGestureMode.SEEK
                                            } else if (downPosition.x < size.width / 2f) {
                                                PlayerGestureMode.BRIGHTNESS
                                            } else {
                                                PlayerGestureMode.VOLUME
                                            }
                                    }
                                    when (requireNotNull(mode)) {
                                        PlayerGestureMode.SEEK -> {
                                            val duration = downState.durationSeconds.coerceAtLeast(0.0)
                                            val span = duration.coerceAtMost(300.0)
                                            val delta =
                                                if (size.width > 0) {
                                                    total.x / size.width * span
                                                } else {
                                                    0.0
                                                }
                                            val target =
                                                (basePosition + delta).coerceIn(0.0, duration)
                                            pendingSeek = target
                                            feedback =
                                                "${formatPlayerTime(target)}\n" +
                                                    "[${if (delta >= 0) "+" else ""}${delta.toInt()}秒]"
                                        }
                                        PlayerGestureMode.BRIGHTNESS -> {
                                            val attributes = activity.window.attributes
                                            val delta =
                                                if (size.height > 0) -total.y / size.height else 0f
                                            val next = (baseBrightness + delta).coerceIn(0.01f, 1f)
                                            attributes.screenBrightness = next
                                            activity.window.attributes = attributes
                                            verticalProgress = next
                                            verticalOnRight = false
                                            verticalIsBrightness = true
                                            verticalPercent = (next * 100).toInt()
                                            feedback = "亮度"
                                        }
                                        PlayerGestureMode.VOLUME -> {
                                            val delta =
                                                if (size.height > 0) {
                                                    (-total.y / size.height * maximumVolume).roundToInt()
                                                } else {
                                                    0
                                                }
                                            val next = (baseVolume + delta).coerceIn(0, maximumVolume)
                                            audioManager.setStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                next,
                                                0,
                                            )
                                            verticalProgress = next.toFloat() / maximumVolume
                                            verticalOnRight = true
                                            verticalIsBrightness = false
                                            verticalPercent = next * 100 / maximumVolume
                                            feedback = "音量"
                                        }
                                    }
                                    if (pointer.positionChange() != Offset.Zero) {
                                        pointer.consume()
                                    }
                                }

                                if (!pointer.pressed) {
                                    if (dragActive) {
                                        if (gestureAllowed) {
                                            pendingSeek?.let(session::seekTo)
                                            latestOnInteraction()
                                        }
                                    } else if (!gestureAllowed) {
                                        pendingSingleTap?.cancel()
                                        lastTapTimeMillis = 0L
                                        latestOnSingleTap()
                                    } else {
                                        val tapTimeMillis = pointer.uptimeMillis
                                        val isDoubleTap =
                                            pendingSingleTap?.isActive == true &&
                                                tapTimeMillis - lastTapTimeMillis <=
                                                    PLAYER_DOUBLE_TAP_TIMEOUT_MILLIS &&
                                                (downPosition - lastTapPosition).getDistance() <=
                                                    PLAYER_DOUBLE_TAP_MAX_DISTANCE_PX
                                        if (isDoubleTap) {
                                            pendingSingleTap?.cancel()
                                            pendingSingleTap = null
                                            lastTapTimeMillis = 0L
                                            when (latestDoubleTapAction) {
                                                PlayerDoubleTapAction.PLAY_PAUSE -> {
                                                    session.togglePause()
                                                    feedback =
                                                        if (latestState.paused) "播放" else "暂停"
                                                }
                                                PlayerDoubleTapAction.SEEK -> {
                                                    val seconds = latestDoubleTapSeekSeconds
                                                    if (downPosition.x < size.width / 2f) {
                                                        session.seekBy(-seconds)
                                                        feedback = "后退 ${seconds}秒"
                                                    } else {
                                                        session.seekBy(seconds)
                                                        feedback = "前进 ${seconds}秒"
                                                    }
                                                }
                                            }
                                            latestOnInteraction()
                                        } else {
                                            pendingSingleTap?.takeIf(Job::isActive)?.let {
                                                it.cancel()
                                                latestOnSingleTap()
                                            }
                                            // 单击要等待双击窗口确认；先刷新自动隐藏计时，避免控制层
                                            // 恰好在等待的 250ms 内隐藏并让最终单击产生相反结果。
                                            latestOnInteraction()
                                            lastTapTimeMillis = tapTimeMillis
                                            lastTapPosition = downPosition
                                            pendingSingleTap =
                                                scope.launch {
                                                    delay(PLAYER_DOUBLE_TAP_TIMEOUT_MILLIS)
                                                    latestOnSingleTap()
                                                    lastTapTimeMillis = 0L
                                                    pendingSingleTap = null
                                                }
                                        }
                                    }
                                }
                            } while (event.changes.any { change -> change.pressed })
                        } finally {
                            if (gestureActiveReported) {
                                latestOnGestureActiveChanged(false)
                            }
                        }
                    }
                },
    ) {
        if (verticalProgress != null) {
            PlayerVerticalIndicator(
                value = verticalProgress ?: 0f,
                percent = verticalPercent,
                isBrightness = verticalIsBrightness,
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

private const val PLAYER_DOUBLE_TAP_TIMEOUT_MILLIS = 250L
private const val PLAYER_DOUBLE_TAP_MAX_DISTANCE_PX = 100f

@Composable
private fun PlayerVerticalIndicator(
    value: Float,
    percent: Int,
    isBrightness: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.width(58.dp).height(170.dp).padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(if (isBrightness) R.drawable.ic_brightness else R.drawable.ic_volume),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = percent.toString(),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
        )
        Box(
            modifier = Modifier.padding(top = 6.dp).width(4.dp).height(96.dp).background(Color(0x40FFFFFF)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight(value.coerceIn(0f, 1f))
                        .background(Color(0xFF2196F3)),
            )
        }
    }
}
