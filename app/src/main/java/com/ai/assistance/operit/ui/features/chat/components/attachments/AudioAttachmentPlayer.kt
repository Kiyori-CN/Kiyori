package com.ai.assistance.operit.ui.features.chat.components.attachments

import android.net.Uri
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.util.AppLogger
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView

private const val AUDIO_ATTACHMENT_PLAYER_TAG = "AudioAttachmentPlayer"

@Composable
fun AudioAttachmentPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false
) {
    val context = LocalContext.current

    val player = remember(uri) {
        ExoPlayer.Builder(context).build()
    }

    LaunchedEffect(uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = autoPlay
    }

    DisposableEffect(player) {
        onDispose {
            try {
                player.stop()
                player.clearMediaItems()
                player.release()
            } catch (error: Exception) {
                AppLogger.w(AUDIO_ATTACHMENT_PLAYER_TAG, "释放音频附件播放器失败", error)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            StyledPlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
        },
        modifier = modifier
    )
}
