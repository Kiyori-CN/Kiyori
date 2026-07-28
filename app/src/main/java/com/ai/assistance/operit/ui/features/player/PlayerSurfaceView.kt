package com.ai.assistance.operit.ui.features.player

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSurfaceRole
import java.util.UUID

internal class PlayerSurfaceView(
    context: Context,
    private val session: PlayerSession,
    private val role: PlayerSurfaceRole,
    mediaOverlay: Boolean = false,
) : SurfaceView(context), SurfaceHolder.Callback {
    private val ownerToken = "player-surface:${UUID.randomUUID()}"
    private var generation: Long? = null

    init {
        holder.addCallback(this)
        // Browser floating playback shares a window with the live WebView. Its Surface must sit
        // above that sibling; fullscreen playback retains the normal window-owned surface order.
        setZOrderMediaOverlay(mediaOverlay)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val registeredGeneration = session.registerSurfaceOwner(role, ownerToken) ?: return
        generation = registeredGeneration
        session.attachSurface(
            role,
            ownerToken,
            registeredGeneration,
            holder.surface,
            width,
            height,
        )
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) {
        val registeredGeneration = generation ?: return
        session.updateSurface(
            role,
            ownerToken,
            registeredGeneration,
            holder.surface,
            width,
            height,
        )
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val registeredGeneration = generation ?: return
        generation = null
        session.detachSurface(role, ownerToken, registeredGeneration, holder.surface)
    }
}
