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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ai.assistance.operit.core.player.PlayerMediaRequest
import com.ai.assistance.operit.core.player.PlayerMediaSource
import com.ai.assistance.operit.core.player.PlayerPresentation
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.ui.theme.KiyoriBrowserTheme
import java.util.UUID

class PlayerActivity : ComponentActivity() {
    private lateinit var playerSession: PlayerSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        playerSession = PlayerSession.getInstance(this)
        if (!handlePlayerIntent(intent)) {
            finish()
            return
        }
        setContent {
            KiyoriBrowserTheme {
                PlayerScreen(
                    session = playerSession,
                    onBack = ::exitPlayer,
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
        if (!isChangingConfigurations && playerSession.state.value.presentation == PlayerPresentation.FULLSCREEN_PLAYER) {
            playerSession.onHostBackgrounded()
        }
    }

    override fun onDestroy() {
        if (isFinishing && playerSession.state.value.presentation == PlayerPresentation.FULLSCREEN_PLAYER) {
            playerSession.close()
            StandardBrowserSessionTools.browserHost?.restoreAfterPlayerFullscreen()
        }
        super.onDestroy()
    }

    private fun handlePlayerIntent(intent: Intent): Boolean {
        if (intent.getBooleanExtra(EXTRA_REUSE_ACTIVE_SESSION, false)) {
            if (!playerSession.state.value.hasMedia) return false
            playerSession.enterFullscreen()
            return true
        }
        if (intent.action != Intent.ACTION_VIEW) return false
        val uri = intent.data ?: return false
        val requestId =
            intent.getStringExtra(EXTRA_REQUEST_ID)?.takeIf(String::isNotBlank)
                ?: UUID.randomUUID().toString().also { intent.putExtra(EXTRA_REQUEST_ID, it) }
        playerSession.open(
            request =
                PlayerMediaRequest(
                    requestId = requestId,
                    uri = uri.toString(),
                    title = resolveDisplayName(uri),
                    source = PlayerMediaSource.EXTERNAL_INTENT,
                ),
            presentation = PlayerPresentation.FULLSCREEN_PLAYER,
        )
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
        playerSession.exitFullscreen()
        StandardBrowserSessionTools.browserHost?.restoreAfterPlayerFullscreen()
        finish()
    }

    private fun rotatePlayer() {
        requestedOrientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
        val accepted =
            request?.source == PlayerMediaSource.BROWSER_CANDIDATE &&
                StandardBrowserSessionTools.browserHost
                    ?.requestMediaCandidateDownload(request.requestId) == true
        Toast.makeText(
                this,
                if (accepted) "已交给文件下载器" else "当前视频没有可用的浏览器下载请求",
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
