package com.ai.assistance.operit.ui.features.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ai.assistance.operit.core.player.PlayerMediaRequest
import com.ai.assistance.operit.core.player.PlayerMediaSource
import com.ai.assistance.operit.core.player.PlayerPresentation
import com.ai.assistance.operit.core.player.PlayerDebugLogBuffer
import com.ai.assistance.operit.core.player.PlayerDebugLogLevel
import com.ai.assistance.operit.core.player.PlayerQueueResolver
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSettingsStore
import com.ai.assistance.operit.core.player.PlayerSurfaceTransferPhase
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadDestination
import com.ai.assistance.operit.ui.theme.KiyoriBrowserTheme
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : ComponentActivity() {
    private lateinit var playerSession: PlayerSession
    private lateinit var settingsStore: PlayerSettingsStore
    private var appliedGravityRotation: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        playerSession = PlayerSession.getInstance(this)
        settingsStore = PlayerSettingsStore.getInstance(this)
        lifecycleScope.launch {
            settingsStore.state
                .map { settings -> settings.followGravityRotation }
                .distinctUntilChanged()
                .collect(::applyGravityRotationPolicy)
        }
        if (!handlePlayerIntent(intent)) {
            finish()
            return
        }
        setContent {
            KiyoriBrowserTheme {
                PlayerScreen(
                    session = playerSession,
                    onBack = ::exitPlayer,
                    onFinishRequested = ::finishPlayerActivity,
                    onRotate = ::rotatePlayer,
                    onScreenshot = ::captureScreenshot,
                    onDownload = ::downloadCurrentMedia,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handlePlayerIntent(intent)) finish()
    }

    override fun onStop() {
        super.onStop()
        if (
            !isChangingConfigurations &&
                playerSession.state.value.surfaceLease.phase ==
                    PlayerSurfaceTransferPhase.FULLSCREEN_ACTIVE
        ) {
            playerSession.onHostBackgrounded()
        }
    }

    override fun onDestroy() {
        playerSession.onFullscreenActivityDestroyed(
            changingConfigurations = isChangingConfigurations,
            finishing = isFinishing,
        )
        super.onDestroy()
    }

    private fun handlePlayerIntent(intent: Intent): Boolean {
        if (intent.getBooleanExtra(EXTRA_REUSE_ACTIVE_SESSION, false)) {
            val state = playerSession.state.value
            return state.hasMedia && state.presentation == PlayerPresentation.FULLSCREEN_PLAYER
        }
        if (intent.action != Intent.ACTION_VIEW) return false
        val uri = intent.data ?: return false
        val requestId =
            intent.getStringExtra(EXTRA_REQUEST_ID)?.takeIf(String::isNotBlank)
                ?: UUID.randomUUID().toString().also { intent.putExtra(EXTRA_REQUEST_ID, it) }
        val request =
            PlayerMediaRequest(
                requestId = requestId,
                uri = uri.toString(),
                title = resolveDisplayName(uri),
                source = PlayerMediaSource.EXTERNAL_INTENT,
            )
        playerSession.open(
            request = request,
            presentation = PlayerPresentation.FULLSCREEN_PLAYER,
        )
        lifecycleScope.launch(Dispatchers.IO) {
            val queueResult =
                runCatching {
                    PlayerQueueResolver.resolve(applicationContext, request)
                }
            withContext(Dispatchers.Main.immediate) {
                queueResult
                    .onSuccess { queue ->
                        playerSession.replaceQueueForCurrent(request.requestId, queue)
                    }
                    .onFailure { error ->
                        PlayerDebugLogBuffer.append(
                            PlayerDebugLogLevel.WARN,
                            "PlayerActivity",
                            "无法建立本地系列队列：${error.message ?: error.javaClass.simpleName}",
                        )
                    }
            }
        }
        return true
    }

    private fun resolveDisplayName(uri: Uri): String {
        if (uri.scheme == "content") {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            cursor.getString(index)?.takeIf(String::isNotBlank)?.let { return it }
                        }
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: "视频"
    }

    private fun exitPlayer() {
        if (playerSession.requestExitFullscreen() == null) {
            finish()
        }
    }

    private fun finishPlayerActivity(requestId: Long) {
        playerSession.acknowledgeFullscreenFinishRequest(requestId)
        finish()
    }

    private fun rotatePlayer() {
        if (settingsStore.current.followGravityRotation) {
            return
        }
        requestedOrientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
    }

    private fun applyGravityRotationPolicy(enabled: Boolean) {
        val previous = appliedGravityRotation
        if (previous == enabled) {
            return
        }
        appliedGravityRotation = enabled
        resolvePlayerGravityOrientationRequest(previous, enabled)?.let { orientation ->
            requestedOrientation = orientation
        }
    }

    private fun captureScreenshot() {
        playerSession.captureScreenshot { result ->
            Toast.makeText(
                    this,
                    result.fold(
                        onSuccess = { name -> "截图已保存：$name" },
                        onFailure = { error -> "截图失败：${error.message ?: error.javaClass.simpleName}" },
                    ),
                    Toast.LENGTH_SHORT,
                )
                .show()
        }
    }

    private fun downloadCurrentMedia() {
        val request = playerSession.state.value.request
        val settings = settingsStore.current
        val destination =
            if (settings.videoDownloadDirectoryUri.isBlank()) {
                BrowserDownloadDestination.FollowSettings
            } else {
                BrowserDownloadDestination.DocumentTree(
                    treeUri = settings.videoDownloadDirectoryUri,
                    displayName = settings.videoDownloadDirectoryName,
                )
            }
        val accepted =
            request?.source == PlayerMediaSource.BROWSER_CANDIDATE &&
                StandardBrowserSessionTools.browserHost
                    ?.requestMediaCandidateDownload(
                        candidateId = request.requestId,
                        sourceSessionId = request.sourceSessionId,
                        destination = destination,
                    ) == true
        Toast.makeText(
                this,
                if (accepted) {
                    when (destination) {
                        BrowserDownloadDestination.FollowSettings -> "已交给文件下载器"
                        is BrowserDownloadDestination.DocumentTree ->
                            "已交给内置下载器：${destination.displayName}"
                    }
                } else {
                    "当前视频没有可用的浏览器下载请求"
                },
                Toast.LENGTH_SHORT,
            )
            .show()
    }

    companion object {
        private const val EXTRA_REQUEST_ID = "com.ai.assistance.operit.player.extra.REQUEST_ID"
        private const val EXTRA_REUSE_ACTIVE_SESSION =
            "com.ai.assistance.operit.player.extra.REUSE_ACTIVE_SESSION"

        internal fun createReuseSessionIntent(context: Context): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_REUSE_ACTIVE_SESSION, true)
            }
    }
}
