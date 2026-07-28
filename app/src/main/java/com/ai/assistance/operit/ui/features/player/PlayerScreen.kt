package com.ai.assistance.operit.ui.features.player

import android.content.Context
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.core.player.PlayerSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun PlayerScreen(
    session: PlayerSession,
    onBack: () -> Unit,
    onRotate: () -> Unit,
    onScreenshot: () -> Unit,
    onDownload: () -> Unit,
) {
    val state by session.state.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var batteryAndTime by remember { mutableStateOf(readBatteryAndTime(context)) }

    BackHandler(onBack = onBack)
    LaunchedEffect(state.hasMedia) {
        if (!state.hasMedia) onBack()
    }
    LaunchedEffect(controlsVisible, controlsLocked, state.paused, state.loading) {
        if (controlsVisible && !controlsLocked && !state.paused && !state.loading) {
            delay(3_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(context) {
        while (true) {
            batteryAndTime = readBatteryAndTime(context)
            delay(30_000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { viewContext -> PlayerSurfaceView(viewContext, session) },
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
                batteryText = batteryAndTime.first,
                clockText = batteryAndTime.second,
                onBack = onBack,
                onRotate = onRotate,
                onScreenshot = onScreenshot,
                onDownload = onDownload,
                onLockChanged = { controlsLocked = it },
            )
        }
    }
}

private fun readBatteryAndTime(context: Context): Pair<String, String> {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    return "$battery%" to time
}
