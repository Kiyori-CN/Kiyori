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
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.core.player.PlayerDebugLogBuffer
import com.ai.assistance.operit.core.player.PlayerDebugLogFilter
import com.ai.assistance.operit.core.player.PlayerDebugLogLevel
import com.ai.assistance.operit.core.player.PlayerDebugLogLine
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSessionState
import com.ai.assistance.operit.core.player.PlayerSettingsStore
import com.ai.assistance.operit.core.player.PlayerSurfaceRole
import com.ai.assistance.operit.core.player.buildPlayerDebugLogReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val settingsStore = remember(context) { PlayerSettingsStore.getInstance(context) }
    val settings by settingsStore.state.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var unlockButtonsVisible by remember { mutableStateOf(false) }
    var controlsPopupVisible by remember { mutableStateOf(false) }
    var seekInteractionActive by remember { mutableStateOf(false) }
    var gestureInteractionActive by remember { mutableStateOf(false) }
    var controlsInteractionRevision by remember { mutableLongStateOf(0L) }
    var showPlaybackLog by remember { mutableStateOf(false) }
    var batteryAndTime by remember { mutableStateOf(readBatteryAndTime(context)) }
    var networkSpeedBytesPerSecond by remember { mutableLongStateOf(0L) }

    fun registerControlsInteraction() {
        controlsInteractionRevision += 1L
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(state.hasMedia) {
        if (!state.hasMedia) onBack()
    }
    val fullscreenFinishRequestId = state.surfaceLease.fullscreenFinishRequestId
    LaunchedEffect(fullscreenFinishRequestId) {
        fullscreenFinishRequestId?.let(onFinishRequested)
    }
    LaunchedEffect(
        controlsVisible,
        controlsLocked,
        state.paused,
        state.loading,
        controlsPopupVisible,
        showPlaybackLog,
        seekInteractionActive,
        gestureInteractionActive,
        controlsInteractionRevision,
    ) {
        val inputs =
            PlayerControlsAutoHideInputs(
                controlsVisible = controlsVisible,
                controlsLocked = controlsLocked,
                paused = state.paused,
                loading = state.loading,
                popupVisible = controlsPopupVisible,
                logVisible = showPlaybackLog,
                seekActive = seekInteractionActive,
                gestureActive = gestureInteractionActive,
            )
        if (shouldAutoHidePlayerControls(inputs)) {
            delay(PLAYER_CONTROLS_AUTO_HIDE_MILLIS)
            controlsVisible = false
        }
    }
    LaunchedEffect(
        controlsLocked,
        unlockButtonsVisible,
        controlsInteractionRevision,
    ) {
        if (controlsLocked && unlockButtonsVisible) {
            delay(PLAYER_UNLOCK_BUTTONS_AUTO_HIDE_MILLIS)
            unlockButtonsVisible = false
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
            doubleTapAction = settings.doubleTapAction,
            doubleTapSeekSeconds = settings.doubleTapSeekSeconds,
            gesturesEnabled = !controlsLocked,
            onSingleTap = {
                when (
                    resolvePlayerControlsTapAction(
                        controlsVisible = controlsVisible,
                        controlsLocked = controlsLocked,
                    )
                ) {
                    PlayerControlsTapAction.SHOW_CONTROLS -> {
                        controlsVisible = true
                        registerControlsInteraction()
                    }
                    PlayerControlsTapAction.HIDE_CONTROLS -> controlsVisible = false
                    PlayerControlsTapAction.SHOW_UNLOCK_BUTTONS -> {
                        unlockButtonsVisible = true
                        registerControlsInteraction()
                    }
                }
            },
            onInteraction = ::registerControlsInteraction,
            onGestureActiveChanged = { active ->
                gestureInteractionActive = active
                if (!active) registerControlsInteraction()
            },
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
            visible =
                if (controlsLocked) {
                    unlockButtonsVisible
                } else {
                    controlsVisible
                },
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerControls(
                state = state,
                settings = settings,
                session = session,
                controlsLocked = controlsLocked,
                networkSpeedBytesPerSecond = networkSpeedBytesPerSecond,
                batteryText = batteryAndTime.first,
                clockText = batteryAndTime.second,
                onBack = onBack,
                onRotate = onRotate,
                onScreenshot = onScreenshot,
                onDownload = onDownload,
                onShowPlaybackLog = {
                    controlsPopupVisible = false
                    showPlaybackLog = true
                    registerControlsInteraction()
                },
                onLockChanged = { locked ->
                    controlsLocked = locked
                    controlsVisible = !locked
                    unlockButtonsVisible = locked
                    controlsPopupVisible = false
                    seekInteractionActive = false
                    gestureInteractionActive = false
                    registerControlsInteraction()
                },
                onInteraction = ::registerControlsInteraction,
                onPopupVisibilityChanged = { visible ->
                    controlsPopupVisible = visible
                    registerControlsInteraction()
                },
                onSeekInteractionChanged = { active ->
                    seekInteractionActive = active
                    if (!active) registerControlsInteraction()
                },
            )
        }
        if (showPlaybackLog) {
            PlayerLogDialog(
                state = state,
                onDismiss = {
                    showPlaybackLog = false
                    registerControlsInteraction()
                },
            )
        }
    }
}

@Composable
private fun PlayerLogDialog(
    state: PlayerSessionState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    var exporting by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(PlayerDebugLogFilter.ALL) }
    var clearConfirmationRequired by remember { mutableStateOf(false) }
    var logRevision by remember { mutableLongStateOf(PlayerDebugLogBuffer.revision.value) }
    val logSnapshot = remember(logRevision, selectedFilter) {
        PlayerDebugLogBuffer.snapshotState(selectedFilter)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            val latestRevision = PlayerDebugLogBuffer.revision.value
            if (latestRevision != logRevision) {
                logRevision = latestRevision
            }
        }
    }

    LaunchedEffect(clearConfirmationRequired) {
        if (clearConfirmationRequired) {
            delay(3_000)
            clearConfirmationRequired = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 标题、筛选、正文和操作区必须各自占位，否则横屏高度不足时正文会把按钮挤出窗口。
            androidx.compose.material3.Surface(
                modifier =
                    Modifier
                        .fillMaxWidth(0.94f)
                        .widthIn(max = 720.dp)
                        .fillMaxHeight(0.9f)
                        .heightIn(max = 680.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "播放器日志",
                                color = Color(0xFF202124),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text =
                                    "实时更新 · 最新日志在前 · PID " +
                                        (state.runtimeProcessId?.toString() ?: "未连接"),
                                color = Color(0xFF6B7280),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) {
                            Text("关闭", fontSize = 13.sp)
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE5E7EB))

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlayerDebugLogFilter.entries.forEach { filter ->
                            PlayerLogFilterChip(
                                filter = filter,
                                selected = selectedFilter == filter,
                                onClick = {
                                    selectedFilter = filter
                                    clearConfirmationRequired = false
                                },
                            )
                        }
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                "${selectedFilter.displayName} · " +
                                    "${logSnapshot.lineCount}/${logSnapshot.totalLineCount} 条",
                            color = Color(0xFF4B5563),
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        if (logSnapshot.droppedLineCount > 0L) {
                            Text(
                                text = "已丢弃 ${logSnapshot.droppedLineCount} 条旧日志",
                                color = Color(0xFFB3261E),
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF7F8FA))
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFFE5E7EB),
                                    shape = RoundedCornerShape(8.dp),
                                ),
                    ) {
                        if (logSnapshot.entries.isEmpty()) {
                            Text(
                                text = "当前分类暂无日志",
                                modifier = Modifier.align(Alignment.Center),
                                color = Color(0xFF6B7280),
                                fontSize = 13.sp,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 4.dp),
                            ) {
                                items(
                                    items = logSnapshot.entries.asReversed(),
                                    key = { entry -> entry.id },
                                ) { entry ->
                                    PlayerLogEntryRow(entry)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE5E7EB))

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = {
                                if (clearConfirmationRequired) {
                                    PlayerDebugLogBuffer.clear()
                                    clearConfirmationRequired = false
                                    Toast.makeText(
                                        context,
                                        "播放器日志已清空",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    clearConfirmationRequired = true
                                }
                            },
                            colors =
                                ButtonDefaults.textButtonColors(
                                    containerColor =
                                        if (clearConfirmationRequired) {
                                            Color(0xFFFFE9E7)
                                        } else {
                                            Color.Transparent
                                        },
                                    contentColor = Color(0xFFB3261E),
                                ),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Text(
                                text = if (clearConfirmationRequired) "确认清空" else "清空",
                                fontSize = 13.sp,
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        OutlinedButton(
                            onClick = {
                                val report =
                                    buildPlayerDebugLogReport(context, state, selectedFilter)
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "Kiyori 播放器日志",
                                        report,
                                    ),
                                )
                                Toast.makeText(
                                    context,
                                    "当前分类日志已复制",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF4557C7),
                                ),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                        ) {
                            Text("复制日志", fontSize = 13.sp)
                        }

                        Button(
                            enabled = !exporting,
                            onClick = {
                                val report =
                                    buildPlayerDebugLogReport(context, state, selectedFilter)
                                exporting = true
                                scope.launch {
                                    PlayerLogExportHelper.export(context, report)
                                        .onSuccess { path ->
                                            Toast.makeText(
                                                context,
                                                "播放器日志已导出：$path",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                        .onFailure { error ->
                                            val message =
                                                error.message ?: error.javaClass.simpleName
                                            PlayerDebugLogBuffer.append(
                                                PlayerDebugLogLevel.ERROR,
                                                "PlayerLogExport",
                                                "导出播放器日志失败：$message",
                                            )
                                            Toast.makeText(
                                                context,
                                                "播放器日志导出失败：$message",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    exporting = false
                                }
                            },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4557C7),
                                    contentColor = Color.White,
                                ),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                        ) {
                            if (exporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.size(6.dp))
                            }
                            Text(
                                text = if (exporting) "导出中" else "导出文件",
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerLogFilterChip(
    filter: PlayerDebugLogFilter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(34.dp),
        shape = RoundedCornerShape(17.dp),
        colors =
            ButtonDefaults.textButtonColors(
                containerColor = if (selected) Color(0xFFE4E9FF) else Color(0xFFF2F3F5),
                contentColor = if (selected) Color(0xFF4557C7) else Color(0xFF4B5563),
            ),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(
            text = filter.displayName,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun PlayerLogEntryRow(entry: PlayerDebugLogLine) {
    val levelColor =
        when (entry.level) {
            PlayerDebugLogLevel.DEBUG -> Color(0xFF6B7280)
            PlayerDebugLogLevel.INFO -> Color(0xFF4557C7)
            PlayerDebugLogLevel.WARN -> Color(0xFF9A6700)
            PlayerDebugLogLevel.ERROR -> Color(0xFFB3261E)
        }
    val rowBackground =
        when (entry.level) {
            PlayerDebugLogLevel.ERROR -> Color(0xFFFFF3F2)
            PlayerDebugLogLevel.WARN -> Color(0xFFFFFAEB)
            else -> Color.Transparent
        }

    SelectionContainer {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(rowBackground)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(levelColor.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = entry.level.displayName,
                        color = levelColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = entry.timestamp.drop(5),
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                    color = Color(0xFF6B7280),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Text(
                    text = entry.tag,
                    modifier = Modifier.padding(start = 8.dp).widthIn(max = 180.dp),
                    color = Color(0xFF4557C7),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = entry.message,
                modifier = Modifier.padding(top = 5.dp),
                color = Color(0xFF202124),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
    HorizontalDivider(color = Color(0xFFE5E7EB))
}

private fun readBatteryAndTime(context: Context): Pair<String, String> {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    return "$battery%" to time
}

private fun readTotalTrafficBytes(): Long =
    (TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()).coerceAtLeast(0L)
