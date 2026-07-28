package com.ai.assistance.operit.ui.features.player

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.ai.assistance.operit.core.player.PlayerSession
import java.util.UUID

internal class PlayerSurfaceView(
    context: Context,
    private val session: PlayerSession,
    mediaOverlay: Boolean = false,
) : SurfaceView(context), SurfaceHolder.Callback {
    private val ownerToken = "player-surface:${UUID.randomUUID()}"

    init {
        holder.addCallback(this)
        // Browser floating playback shares a window with the live WebView. Its Surface must sit
        // above that sibling; fullscreen playback retains the normal window-owned surface order.
        setZOrderMediaOverlay(mediaOverlay)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        session.attachSurface(ownerToken, holder.surface, width, height)
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) {
        session.updateSurface(ownerToken, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        session.detachSurface(ownerToken, holder.surface)
    }
}
