package com.ai.assistance.operit.core.player

import android.content.Context
import com.ai.assistance.operit.core.player.runtime.PlayerMediaStreamBridge
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimeMediaTransport
import com.kiyori.platform.network.KiyoriNetworkModule
import com.kiyori.platform.network.KiyoriNetworkProxyManager
import com.kiyori.platform.network.KiyoriNetworkRoute
import java.util.Locale

internal data class PlayerMediaTransport(
    val target: String,
    val transport: PlayerRuntimeMediaTransport,
    val runtimeGeneration: Long?,
)

/** Resolves only network transport in the main process; local descriptors remain :player-owned. */
internal class PlayerMediaTransportResolver(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private var streamBridge: PlayerMediaStreamBridge? = null

    fun resolveNetworkTransport(
        requestId: String,
        uriText: String,
        headers: Map<String, String>,
    ): PlayerMediaTransport {
        close()
        val scheme = uriText.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") {
            "Player network transport requires an HTTP(S) URI"
        }
        val route =
            KiyoriNetworkProxyManager
                .getInstance(appContext)
                .resolveTransportRouteBlocking(KiyoriNetworkModule.PLAYER)
        if (route.route == KiyoriNetworkRoute.Direct) {
            return PlayerMediaTransport(
                target = uriText,
                transport = PlayerRuntimeMediaTransport.DIRECT,
                runtimeGeneration = null,
            )
        }
        val bridge =
            PlayerMediaStreamBridge(
                targetUrl = uriText,
                requestHeaders = headers,
                proxySelector = route.proxySelector,
                diagnostic = { level, message ->
                    PlayerDebugLogBuffer.append(
                        level,
                        "PlayerMediaBridge",
                        "request=${shortPlayerDiagnosticId(requestId)} $message",
                    )
                },
            )
        streamBridge = bridge
        return PlayerMediaTransport(
            target = bridge.start(),
            transport = PlayerRuntimeMediaTransport.MAIN_PROCESS_PROXY_BRIDGE,
            runtimeGeneration = route.runtimeGeneration,
        )
    }

    override fun close() {
        streamBridge?.close()
        streamBridge = null
    }
}
