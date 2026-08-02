package com.ai.assistance.operit.ui.features.player

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSurfaceRole
import java.util.UUID

internal fun createPlayerSurfaceView(
    context: Context,
    session: PlayerSession,
    role: PlayerSurfaceRole,
    mediaOverlay: Boolean = false,
): SurfaceView =
    SurfaceView(context).apply {
        // SurfaceView itself has no reusable XML or tooling state. Keep the player ownership in a
        // callback object so programmatic callers use the framework view and one surface owner.
        holder.addCallback(
            PlayerSurfaceOwner(
                surfaceView = this,
                session = session,
                role = role,
            ),
        )
        setZOrderMediaOverlay(mediaOverlay)
    }

private class PlayerSurfaceOwner(
    private val surfaceView: SurfaceView,
    private val session: PlayerSession,
    private val role: PlayerSurfaceRole,
) : SurfaceHolder.Callback {
    private val ownerToken = "player-surface:${UUID.randomUUID()}"
    private var generation: Long? = null

    override fun surfaceCreated(holder: SurfaceHolder) {
        val registeredGeneration = session.registerSurfaceOwner(role, ownerToken) ?: return
        generation = registeredGeneration
        session.attachSurface(
            role,
            ownerToken,
            registeredGeneration,
            holder.surface,
            surfaceView.width,
            surfaceView.height,
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
