package com.ai.assistance.operit.ui.features.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.player.LongPressSpeedBoostResult
import com.ai.assistance.operit.core.player.PlayerDoubleTapAction
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSessionState
import com.ai.assistance.operit.core.player.formatPlayerSpeedLabel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PlayerGestureMode {
    SEEK,
    BRIGHTNESS,
    VOLUME,
}

private data class PlayerGestureFeedback(
    val title: String,
    val detail: String,
    val progress: Float? = null,
)

@Composable
internal fun PlayerGestureLayer(
    session: PlayerSession,
    state: PlayerSessionState,
    doubleTapAction: PlayerDoubleTapAction,
    doubleTapSeekSeconds: Int,
    longPressSpeedBoostEnabled: Boolean,
    gesturesEnabled: Boolean,
    onSingleTap: () -> Unit,
    onInteraction: () -> Unit,
    onGestureActiveChanged: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val activity = LocalContext.current as PlayerActivity
    val audioManager =
        remember(activity) {
            activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
    val scope = rememberCoroutineScope()
    val latestState by rememberUpdatedState(state)
    val latestDoubleTapAction by rememberUpdatedState(doubleTapAction)
    val latestDoubleTapSeekSeconds by rememberUpdatedState(doubleTapSeekSeconds)
    val latestLongPressSpeedBoostEnabled by rememberUpdatedState(longPressSpeedBoostEnabled)
    val latestGesturesEnabled by rememberUpdatedState(gesturesEnabled)
    val latestOnSingleTap by rememberUpdatedState(onSingleTap)
    val latestOnInteraction by rememberUpdatedState(onInteraction)
    val latestOnGestureActiveChanged by rememberUpdatedState(onGestureActiveChanged)
    var size by remember { mutableStateOf(IntSize.Zero) }
    var feedback by remember { mutableStateOf<PlayerGestureFeedback?>(null) }
    var feedbackRevision by remember { mutableLongStateOf(0L) }
    var feedbackPersistent by remember { mutableStateOf(false) }
    var verticalProgress by remember { mutableStateOf<Float?>(null) }
    var verticalOnRight by remember { mutableStateOf(false) }
    var verticalIsBrightness by remember { mutableStateOf(false) }
    var verticalPercent by remember { mutableIntStateOf(0) }
    var lastTapTimeMillis by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    var pendingSingleTap by remember { mutableStateOf<Job?>(null) }
    var longPressBoostActive by remember { mutableStateOf(false) }

    LaunchedEffect(feedbackRevision, feedbackPersistent) {
        val revision = feedbackRevision
        if ((feedback != null || verticalProgress != null) && !feedbackPersistent) {
            delay(PLAYER_GESTURE_FEEDBACK_HIDE_MILLIS)
            if (feedbackRevision == revision) {
                feedback = null
                verticalProgress = null
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            pendingSingleTap?.cancel()
            if (longPressBoostActive) {
                session.endLongPressSpeedBoost()
                longPressBoostActive = false
            }
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
                        val baseVolume =
                            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val maximumVolume =
                            audioManager
                                .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                .coerceAtLeast(1)
                        var mode: PlayerGestureMode? = null
                        var pendingSeek: Double? = null
                        var dragActive = false
                        var movedBeyondSlop = false
                        var pointerPressed = true
                        var longPressConsumed = false
                        var longPressResult: LongPressSpeedBoostResult? = null
                        var gestureActiveReported = false
                        val longPressJob =
                            if (
                                gestureAllowed &&
                                    latestLongPressSpeedBoostEnabled &&
                                    downState.hasMedia
                            ) {
                                scope.launch {
                                    delay(PLAYER_LONG_PRESS_SPEED_THRESHOLD_MILLIS)
                                    if (pointerPressed && !movedBeyondSlop) {
                                        longPressConsumed = true
                                        gestureActiveReported = true
                                        latestOnGestureActiveChanged(true)
                                        longPressResult = session.beginLongPressSpeedBoost()
                                        longPressBoostActive = longPressResult != null
                                        feedback =
                                            longPressResult?.let { result ->
                                                PlayerGestureFeedback(
                                                    title = formatPlayerSpeedLabel(result.boostedSpeed),
                                                    detail =
                                                        "长按加速 · 松手恢复 " +
                                                            formatPlayerSpeedLabel(result.originalSpeed),
                                                )
                                            } ?: PlayerGestureFeedback(
                                                title = formatPlayerSpeedLabel(downState.speed),
                                                detail =
                                                    if (downState.speed >= 3.0) {
                                                        "当前已是最高倍速"
                                                    } else {
                                                        "播放器暂时无法加速"
                                                    },
                                            )
                                        // 加速状态由 PlayerSession 持有；提示只短暂说明状态变化，
                                        // 不能在整个长按期间遮挡画面。
                                        feedbackPersistent = false
                                        feedbackRevision += 1L
                                        latestOnInteraction()
                                    }
                                }
                            } else {
                                null
                            }

                        try {
                            do {
                                val event = awaitPointerEvent()
                                val pointer =
                                    event.changes.firstOrNull { change -> change.id == down.id }
                                        ?: break
                                val total = pointer.position - downPosition
                                if (
                                    !dragActive &&
                                        !longPressConsumed &&
                                        total.getDistance() > viewConfiguration.touchSlop
                                ) {
                                    movedBeyondSlop = true
                                    longPressJob?.cancel()
                                    dragActive = true
                                    if (gestureAllowed) {
                                        gestureActiveReported = true
                                        latestOnGestureActiveChanged(true)
                                        verticalProgress = null
                                        feedbackPersistent = true
                                    }
                                }

                                if (longPressConsumed) {
                                    if (pointer.positionChange() != Offset.Zero) {
                                        pointer.consume()
                                    }
                                } else if (dragActive && gestureAllowed) {
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
                                            val duration =
                                                downState.durationSeconds.coerceAtLeast(0.0)
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
                                                PlayerGestureFeedback(
                                                    title = formatPlayerTime(target),
                                                    detail =
                                                        if (delta >= 0.0) {
                                                            "快进 +${abs(delta).toInt()} 秒"
                                                        } else {
                                                            "快退 -${abs(delta).toInt()} 秒"
                                                        },
                                                    progress =
                                                        if (duration > 0.0) {
                                                            (target / duration).toFloat()
                                                        } else {
                                                            0f
                                                        },
                                                )
                                            feedbackRevision += 1L
                                        }
                                        PlayerGestureMode.BRIGHTNESS -> {
                                            val attributes = activity.window.attributes
                                            val delta =
                                                if (size.height > 0) -total.y / size.height else 0f
                                            val next =
                                                (baseBrightness + delta).coerceIn(0.01f, 1f)
                                            attributes.screenBrightness = next
                                            activity.window.attributes = attributes
                                            verticalProgress = next
                                            // 功能仍由左半屏控制，只把提示移到右侧，避免手指遮挡。
                                            verticalOnRight = true
                                            verticalIsBrightness = true
                                            verticalPercent = (next * 100).toInt()
                                            feedback = null
                                            feedbackRevision += 1L
                                        }
                                        PlayerGestureMode.VOLUME -> {
                                            val delta =
                                                if (size.height > 0) {
                                                    (
                                                        -total.y /
                                                            size.height *
                                                            maximumVolume
                                                    ).roundToInt()
                                                } else {
                                                    0
                                                }
                                            val next =
                                                (baseVolume + delta).coerceIn(0, maximumVolume)
                                            audioManager.setStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                next,
                                                0,
                                            )
                                            verticalProgress =
                                                next.toFloat() / maximumVolume
                                            // 功能仍由右半屏控制，只把提示移到左侧，避免手指遮挡。
                                            verticalOnRight = false
                                            verticalIsBrightness = false
                                            verticalPercent = next * 100 / maximumVolume
                                            feedback = null
                                            feedbackRevision += 1L
                                        }
                                    }
                                    if (pointer.positionChange() != Offset.Zero) {
                                        pointer.consume()
                                    }
                                }

                                if (!pointer.pressed) {
                                    pointerPressed = false
                                    longPressJob?.cancel()
                                    if (longPressConsumed) {
                                        longPressResult?.let { result ->
                                            session.endLongPressSpeedBoost()
                                            longPressBoostActive = false
                                            feedback =
                                                PlayerGestureFeedback(
                                                    title =
                                                        formatPlayerSpeedLabel(
                                                            result.originalSpeed,
                                                        ),
                                                    detail = "已恢复原速",
                                                )
                                        }
                                        feedbackPersistent = false
                                        feedbackRevision += 1L
                                        latestOnInteraction()
                                    } else if (dragActive) {
                                        if (gestureAllowed) {
                                            pendingSeek?.let(session::seekTo)
                                            feedbackPersistent = false
                                            feedbackRevision += 1L
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
                                                        PlayerGestureFeedback(
                                                            title =
                                                                if (latestState.paused) {
                                                                    "继续播放"
                                                                } else {
                                                                    "暂停播放"
                                                                },
                                                            detail = "双击画面",
                                                        )
                                                }
                                                PlayerDoubleTapAction.SEEK -> {
                                                    val seconds =
                                                        latestDoubleTapSeekSeconds
                                                    val duration =
                                                        latestState.durationSeconds
                                                            .coerceAtLeast(0.0)
                                                    val target =
                                                        if (
                                                            downPosition.x <
                                                                size.width / 2f
                                                        ) {
                                                            session.seekBy(-seconds)
                                                            (
                                                                latestState.positionSeconds -
                                                                    seconds
                                                            ).coerceIn(0.0, duration)
                                                        } else {
                                                            session.seekBy(seconds)
                                                            (
                                                                latestState.positionSeconds +
                                                                    seconds
                                                            ).coerceIn(0.0, duration)
                                                        }
                                                    feedback =
                                                        PlayerGestureFeedback(
                                                            title =
                                                                if (
                                                                    downPosition.x <
                                                                        size.width / 2f
                                                                ) {
                                                                    "快退 $seconds 秒"
                                                                } else {
                                                                    "快进 $seconds 秒"
                                                                },
                                                            detail = formatPlayerTime(target),
                                                            progress =
                                                                if (duration > 0.0) {
                                                                    (target / duration).toFloat()
                                                                } else {
                                                                    0f
                                                                },
                                                        )
                                                }
                                            }
                                            feedbackPersistent = false
                                            feedbackRevision += 1L
                                            latestOnInteraction()
                                        } else {
                                            pendingSingleTap
                                                ?.takeIf(Job::isActive)
                                                ?.let {
                                                    it.cancel()
                                                    latestOnSingleTap()
                                                }
                                            // 单击要等待双击窗口确认；先刷新自动隐藏计时，避免控制层
                                            // 恰好在等待窗口内隐藏并让最终单击产生相反结果。
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
                            pointerPressed = false
                            longPressJob?.cancel()
                            if (longPressBoostActive) {
                                session.endLongPressSpeedBoost()
                                longPressBoostActive = false
                            }
                            if (feedbackPersistent) {
                                feedbackPersistent = false
                                feedbackRevision += 1L
                            }
                            if (gestureActiveReported) {
                                latestOnGestureActiveChanged(false)
                            }
                        }
                    }
                },
    ) {
        verticalProgress?.let { progress ->
            PlayerVerticalIndicator(
                value = progress,
                percent = verticalPercent,
                isBrightness = verticalIsBrightness,
                modifier =
                    if (verticalOnRight) {
                        Modifier.align(Alignment.CenterEnd).padding(end = 30.dp)
                    } else {
                        Modifier.align(Alignment.CenterStart).padding(start = 30.dp)
                    },
            )
        }
        feedback?.let { current ->
            PlayerCenterGestureFeedback(
                feedback = current,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private const val PLAYER_DOUBLE_TAP_TIMEOUT_MILLIS = 250L
private const val PLAYER_DOUBLE_TAP_MAX_DISTANCE_PX = 100f
private const val PLAYER_LONG_PRESS_SPEED_THRESHOLD_MILLIS = 480L
private const val PLAYER_GESTURE_FEEDBACK_HIDE_MILLIS = 1_000L

@Composable
private fun PlayerCenterGestureFeedback(
    feedback: PlayerGestureFeedback,
    modifier: Modifier,
) {
    Column(
        modifier =
            modifier
                .width(178.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xD91A1D25))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = feedback.title,
            color = Color.White,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = feedback.detail,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
        )
        feedback.progress?.let { progress ->
            Spacer(Modifier.height(11.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF6F8CFF), Color(0xFF8B6CFF)),
                                ),
                            ),
                )
            }
        }
    }
}

@Composable
private fun PlayerVerticalIndicator(
    value: Float,
    percent: Int,
    isBrightness: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier =
            modifier
                .width(64.dp)
                .height(184.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xD91A1D25))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter =
                painterResource(
                    if (isBrightness) R.drawable.ic_brightness else R.drawable.ic_volume,
                ),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = "$percent%",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 7.dp),
        )
        Box(
            modifier =
                Modifier
                    .padding(top = 9.dp)
                    .width(6.dp)
                    .height(104.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(6.dp)
                        .fillMaxHeight(value.coerceIn(0f, 1f))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF8B6CFF), Color(0xFF6F8CFF)),
                            ),
                        ),
            )
        }
    }
}
