package com.ai.assistance.operit.ui.features.player

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.os.BatteryManager
import android.os.SystemClock
import android.net.TrafficStats
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.core.player.PlayerDebugLogBuffer
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSurfaceRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun PlayerScreen(
    session: PlayerSession,
    onBack: () -> Unit,
    onFinishRequested: (Long) -> Unit,
    onRotate: () -> Unit,
    onScreenshot: () -> Unit,
    onDownload: () -> Unit,
) {
    val state by session.state.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var showPlaybackLog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var batteryAndTime by remember { mutableStateOf(readBatteryAndTime(context)) }
    var networkSpeedBytesPerSecond by remember { mutableLongStateOf(0L) }

    BackHandler(onBack = onBack)
    LaunchedEffect(state.hasMedia) {
        if (!state.hasMedia) onBack()
    }
    val fullscreenFinishRequestId = state.surfaceLease.fullscreenFinishRequestId
    LaunchedEffect(fullscreenFinishRequestId) {
        fullscreenFinishRequestId?.let(onFinishRequested)
    }
    LaunchedEffect(controlsVisible, controlsLocked, state.paused, state.loading) {
        if (controlsVisible && !controlsLocked && !state.paused && !state.loading) {
            delay(3_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(context) {
        var previousBytes = readTotalTrafficBytes()
        var previousTimestamp = SystemClock.elapsedRealtime()
        while (true) {
            delay(1_000)
            val currentBytes = readTotalTrafficBytes()
            val currentTimestamp = SystemClock.elapsedRealtime()
            val elapsedMillis = (currentTimestamp - previousTimestamp).coerceAtLeast(1L)
            networkSpeedBytesPerSecond =
                ((currentBytes - previousBytes).coerceAtLeast(0L) * 1_000L) / elapsedMillis
            batteryAndTime = readBatteryAndTime(context)
            previousBytes = currentBytes
            previousTimestamp = currentTimestamp
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                PlayerSurfaceView(
                    viewContext,
                    session,
                    role = PlayerSurfaceRole.FULLSCREEN,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
        PlayerGestureLayer(
            session = session,
            state = state,
            enabled = !controlsLocked,
            onToggleControls = { controlsVisible = !controlsVisible },
            modifier = Modifier.fillMaxSize(),
        )
        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(60.dp),
                color = Color.White,
            )
        }
        state.error?.let { message ->
            Column(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .background(Color(0xCC171717))
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("播放失败", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(message, color = Color(0xFFD1D1D1), fontSize = 13.sp)
            }
        }
        AnimatedVisibility(
            visible = controlsVisible || controlsLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerControls(
                state = state,
                session = session,
                controlsLocked = controlsLocked,
                networkSpeedBytesPerSecond = networkSpeedBytesPerSecond,
                batteryText = batteryAndTime.first,
                clockText = batteryAndTime.second,
                onBack = onBack,
                onRotate = onRotate,
                onScreenshot = onScreenshot,
                onDownload = onDownload,
                onShowPlaybackLog = { showPlaybackLog = true },
                onLockChanged = { controlsLocked = it },
            )
        }
        if (showPlaybackLog) {
            PlayerLogDialog(onDismiss = { showPlaybackLog = false })
        }
    }
}

@Composable
private fun PlayerLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    var logText by remember {
        mutableStateOf(
            PlayerDebugLogBuffer.snapshot().ifBlank {
                "暂无播放器日志。请先播放一次黑屏转圈的链接，再打开这里复制。"
            },
        )
    }
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("播放器日志", color = Color(0xFF222222), fontSize = 20.sp)
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .background(Color(0xFFF5F5F5))
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    SelectionContainer {
                        Text(logText, color = Color(0xFF222222), fontSize = 12.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Kiyori 播放器日志", logText))
                        Toast.makeText(context, "播放器日志已复制", Toast.LENGTH_SHORT).show()
                    }) { Text("复制") }
                    TextButton(onClick = {
                        PlayerDebugLogBuffer.clear()
                        logText = "暂无播放器日志。请先播放一次黑屏转圈的链接，再打开这里复制。"
                        Toast.makeText(context, "播放器日志已清空", Toast.LENGTH_SHORT).show()
                    }) { Text("清空") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

private fun readBatteryAndTime(context: Context): Pair<String, String> {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    return "$battery%" to time
}

private fun readTotalTrafficBytes(): Long =
    (TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()).coerceAtLeast(0L)
