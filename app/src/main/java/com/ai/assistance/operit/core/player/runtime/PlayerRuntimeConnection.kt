package com.ai.assistance.operit.core.player.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.ai.assistance.operit.core.player.PlayerDebugLogLevel

internal interface PlayerRuntimeConnectionListener {
    fun onRuntimeReady(runtimeGeneration: Long, processId: Int)

    fun onCommandCompleted(
        runtimeGeneration: Long,
        commandId: Long,
        commandType: PlayerRuntimeCommandType,
    )

    fun onCommandFailed(
        runtimeGeneration: Long,
        commandId: Long,
        commandType: PlayerRuntimeCommandType?,
        operation: String,
        message: String,
    )

    fun onSurfaceAttached(
        runtimeGeneration: Long,
        commandId: Long,
        surfaceGeneration: Long,
    )

    fun onSurfaceDetached(
        runtimeGeneration: Long,
        commandId: Long,
        surfaceGeneration: Long,
    )

    fun onPlaybackSnapshot(
        runtimeGeneration: Long,
        snapshot: PlayerRuntimePlaybackSnapshot,
    )

    fun onFileLoaded(
        runtimeGeneration: Long,
        loadCommandId: Long,
        tracks: PlayerRuntimeTrackSnapshot,
    )

    fun onMediaIdentityChanged(
        runtimeGeneration: Long,
        loadCommandId: Long,
        identity: PlayerRuntimeMediaIdentitySnapshot,
    )

    fun onSeek(runtimeGeneration: Long, loadCommandId: Long)

    fun onPlaybackRestart(runtimeGeneration: Long, loadCommandId: Long)

    fun onNaturalEnd(runtimeGeneration: Long)

    fun onRuntimeError(runtimeGeneration: Long, message: String)

    fun onDiagnosticLog(
        runtimeGeneration: Long,
        level: PlayerDebugLogLevel,
        tag: String,
        message: String,
    )

    fun onThumbnailReady(
        runtimeGeneration: Long,
        commandId: Long,
        positionSeconds: Double,
        bitmap: Bitmap?,
    )

    fun onScreenshotCompleted(
        runtimeGeneration: Long,
        commandId: Long,
        path: String,
    )

    fun onRuntimeDisconnected(runtimeGeneration: Long, expected: Boolean)

    fun onRuntimeConnectionError(runtimeGeneration: Long, message: String)
}

internal class PlayerRuntimeConnection(
    context: Context,
    private val listener: PlayerRuntimeConnectionListener,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var remote: IPlayerRuntime? = null
    private var serviceBinder: IBinder? = null
    private var isBound = false
    private var disconnectExpected = false
    private var runtimeGeneration: Long = 0L
    private var pendingConfig: PlayerRuntimeConfig? = null
    private var nextCommandId: Long = 0L
    private var eventCursor: PlayerRuntimeEventCursor? = null
    private val pendingCommands = LinkedHashMap<Long, PlayerRuntimeCommandType>()
    private val deathRecipient =
        IBinder.DeathRecipient {
            mainHandler.post { handleDisconnected() }
        }

    private val callback =
        object : IPlayerRuntimeCallback.Stub() {
            override fun onRuntimeReady(
                runtimeGeneration: Long,
                eventSequence: Long,
                commandId: Long,
                processId: Int,
            ) {
                postEvent(runtimeGeneration, eventSequence) {
                    if (takeCommand(commandId, PlayerRuntimeCommandType.INITIALIZE) != null) {
                        listener.onRuntimeReady(runtimeGeneration, processId)
                    }
                }
            }

            override fun onCommandCompleted(
                runtimeGeneration: Long,
                eventSequence: Long,
                commandId: Long,
            ) {
                postEvent(runtimeGeneration, eventSequence) {
                    val commandType = pendingCommands.remove(commandId) ?: return@postEvent
                    listener.onCommandCompleted(runtimeGeneration, commandId, commandType)
                }
            }

            override fun onCommandFailed(
                runtimeGeneration: Long,
                eventSequence: Long,
                commandId: Long,
                operation: String?,
                message: String?,
            ) {
                postEvent(runtimeGeneration, eventSequence) {
                    listener.onCommandFailed(
                        runtimeGeneration = runtimeGeneration,
                        commandId = commandId,
                        commandType = pendingCommands.remove(commandId),
                        operation = operation.orEmpty(),
                        message = message.orEmpty(),
                    )
                }
            }

            override fun onSurfaceAttached(
                runtimeGeneration: Long,
                eventSequence: Long,
                commandId: Long,
                surfaceGeneration: Long,
            ) {
                postEvent(runtimeGeneration, eventSequence) {
                    if (takeCommand(commandId, PlayerRuntimeCommandType.ATTACH_SURFACE) != null) {
                        listener.onSurfaceAttached(
                            runtimeGeneration,
                            commandId,
                            surfaceGeneration,
                        )
                    }
                }
            }

            override fun onSurfaceDetached(
                runtimeGeneration: Long,
                eventSequence: Long,
                commandId: Long,
                surfaceGeneration: Long,
            ) {
                postEvent(runtimeGeneration, eventSequence) {
                    if (takeCommand(commandId, PlayerRuntimeCommandType.DETACH_SURFACE) != null) {
                        listener.onSurfaceDetached(
                            runtimeGeneration,
                            commandId,
                            surfaceGeneration,
                        )
                    }
                }
            }

            override fun onPlaybackSnapshot(
                runtimeGeneration: Long,
                eventSequence: Long,
                snapshot: PlayerRuntimePlaybackSnapshot?,
            ) {
                if (snapshot == null) return
                postEvent(runtimeGeneration, eventSequence) {
                    listener.onPlaybackSnapshot(runtimeGeneration, snapshot)
                }
            }

            override fun onFileLoaded(
                runtimeGeneration: Long,
                eventSequence: Long,
                loadCommandId: Long,
                tracks: PlayerRuntimeTrackSnapshot?,
            ) {
                if (tracks == null) return
                postEvent(runtimeGeneration, eventSequence) {
                    listener.onFileLoaded(runtimeGeneration, loadCommandId, tracks)
                }
            }

            override fun onMediaIdentityChanged(
                runtimeGeneration: Long,
                eventSequence: Long,
                loadCommandId: Long,
                identity: PlayerRuntimeMediaIdentitySnapshot?,
            ) {
                if (identity == null) return
                postEvent(runtimeGeneration, eventSequence) {
                    listener.onMediaIdentityChanged(
                        runtimeGeneration,
                        loadCommandId,
                        identity,
                    )
                }
            }

            override fun onSeek(
                runtimeGeneration: Long,
                eventSequence: Long,
                loadCommandId: Long,
            ) {
                postEvent(runtimeGeneration, eventSequence) {
                    listener.onSeek(runtimeGeneration, loadCommandId)
                }
            }

            override fun onPlaybackRestart(
                runtimeGeneration: Long,
                eventSequence: Long,
                loadCommandId: Long,
            ) {
                postEvent(runtimeGeneration, eventSequence) {
                    listener.onPlaybackRestart(runtimeGeneration, loadCommandId)
                }
            }

            override fun onNaturalEnd(runtimeGeneration: Long, eventSequence: Long) {
                postEvent(runtimeGeneration, eventSequence) {
                    listener.onNaturalEnd(runtimeGeneration)
                }
            }

            override fun onRuntimeError(
                runtimeGeneration: Long,
                eventSequence: Long,
                message: String?,
            ) {
                postEvent(runtimeGeneration, eventSequence) {
                    listener.onRuntimeError(runtimeGeneration, message.orEmpty())
                }
            }

            override fun onDiagnosticLog(
                runtimeGeneration: Long,
                eventSequence: Long,
                level: Int,
                tag: String?,
                message: String?,
            ) {
                val resolvedLevel = PlayerDebugLogLevel.fromWireValue(level) ?: return
                postEvent(runtimeGeneration, eventSequence) {
                    listener.onDiagnosticLog(
                        runtimeGeneration = runtimeGeneration,
                        level = resolvedLevel,
                        tag = tag.orEmpty(),
                        message = message.orEmpty(),
                    )
                }
            }

            override fun onScreenshotCompleted(
                runtimeGeneration: Long,
                eventSequence: Long,
                commandId: Long,
                path: String?,
            ) {
                if (path == null) return
                postEvent(runtimeGeneration, eventSequence) {
                    if (takeCommand(commandId, PlayerRuntimeCommandType.SCREENSHOT) != null) {
                        listener.onScreenshotCompleted(runtimeGeneration, commandId, path)
                    }
                }
            }

            override fun onThumbnailReady(
                runtimeGeneration: Long,
                eventSequence: Long,
                commandId: Long,
                positionSeconds: Double,
                bitmap: Bitmap?,
            ) {
                postEvent(runtimeGeneration, eventSequence) {
                    if (takeCommand(commandId, PlayerRuntimeCommandType.THUMBNAIL) != null) {
                        listener.onThumbnailReady(
                            runtimeGeneration = runtimeGeneration,
                            commandId = commandId,
                            positionSeconds = positionSeconds,
                            bitmap = bitmap,
                        )
                    }
                }
            }
        }

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (!isBound || runtimeGeneration <= 0L) return
                if (service == null) {
                    notifyConnectionError("播放器运行时返回空 Binder")
                    return
                }
                serviceBinder = service
                try {
                    service.linkToDeath(deathRecipient, 0)
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to register player runtime death recipient", error)
                    handleDisconnected()
                    return
                }
                remote = IPlayerRuntime.Stub.asInterface(service)
                beginInitialization()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                handleDisconnected()
            }

            override fun onBindingDied(name: ComponentName?) {
                handleDisconnected()
            }

            override fun onNullBinding(name: ComponentName?) {
                notifyConnectionError("播放器运行时不支持绑定")
                disconnect()
            }
        }

    fun connect(runtimeGeneration: Long, config: PlayerRuntimeConfig) {
        requireMainThread()
        require(runtimeGeneration > 0L) { "Player runtime generation must be positive" }
        check(!isBound) { "Player runtime is already bound" }
        this.runtimeGeneration = runtimeGeneration
        pendingConfig = config
        nextCommandId = 0L
        pendingCommands.clear()
        eventCursor = PlayerRuntimeEventCursor(runtimeGeneration)
        disconnectExpected = false
        val bound =
            runCatching {
                appContext.bindService(
                    Intent(appContext, PlayerRuntimeService::class.java),
                    serviceConnection,
                    Context.BIND_AUTO_CREATE,
                )
            }.onFailure { error ->
                Log.e(TAG, "Unable to bind player runtime", error)
            }.getOrDefault(false)
        isBound = bound
        if (!bound) {
            notifyConnectionError("无法绑定播放器运行时")
        }
    }

    fun disconnect() {
        requireMainThread()
        disconnectExpected = true
        serviceBinder?.let { binder ->
            runCatching { binder.unlinkToDeath(deathRecipient, 0) }
                .onFailure { error ->
                    Log.d(TAG, "Player runtime death recipient was already unlinked", error)
                }
        }
        if (isBound) {
            runCatching { appContext.unbindService(serviceConnection) }
                .onFailure { error -> Log.e(TAG, "Unable to unbind player runtime", error) }
        }
        isBound = false
        remote = null
        serviceBinder = null
        pendingConfig = null
        pendingCommands.clear()
        eventCursor = null
        runtimeGeneration = 0L
    }

    fun load(request: PlayerRuntimeLoadRequest): Long? =
        sendCommand(PlayerRuntimeCommandType.LOAD) { activeRemote, generation, commandId ->
            activeRemote.load(generation, commandId, request)
        }

    fun attachSurface(
        surfaceGeneration: Long,
        surface: Surface,
        width: Int,
        height: Int,
    ): Long? =
        sendCommand(PlayerRuntimeCommandType.ATTACH_SURFACE) {
                activeRemote,
                generation,
                commandId,
            ->
            activeRemote.attachSurface(
                generation,
                commandId,
                surfaceGeneration,
                surface,
                width,
                height,
            )
        }

    fun updateSurface(
        surfaceGeneration: Long,
        width: Int,
        height: Int,
    ): Long? =
        sendCommand(PlayerRuntimeCommandType.UPDATE_SURFACE) {
                activeRemote,
                generation,
                commandId,
            ->
            activeRemote.updateSurface(
                generation,
                commandId,
                surfaceGeneration,
                width,
                height,
            )
        }

    fun detachSurface(surfaceGeneration: Long): Long? =
        sendCommand(PlayerRuntimeCommandType.DETACH_SURFACE) {
                activeRemote,
                generation,
                commandId,
            ->
            activeRemote.detachSurface(generation, commandId, surfaceGeneration)
        }

    fun setPaused(paused: Boolean): Long? =
        sendCommand(PlayerRuntimeCommandType.SET_PAUSED) { activeRemote, generation, commandId ->
            activeRemote.setPaused(generation, commandId, paused)
        }

    fun seekTo(positionSeconds: Double, precise: Boolean): Long? =
        sendCommand(PlayerRuntimeCommandType.SEEK) { activeRemote, generation, commandId ->
            activeRemote.seekTo(generation, commandId, positionSeconds, precise)
        }

    fun setSpeed(speed: Double): Long? =
        sendCommand(PlayerRuntimeCommandType.SET_SPEED) { activeRemote, generation, commandId ->
            activeRemote.setSpeed(generation, commandId, speed)
        }

    fun setAudioTrack(trackId: Int): Long? =
        sendCommand(PlayerRuntimeCommandType.SET_AUDIO_TRACK) {
                activeRemote,
                generation,
                commandId,
            ->
            activeRemote.setAudioTrack(generation, commandId, trackId)
        }

    fun setSubtitleTrack(trackId: Int?): Long? =
        sendCommand(PlayerRuntimeCommandType.SET_SUBTITLE_TRACK) {
                activeRemote,
                generation,
                commandId,
            ->
            activeRemote.setSubtitleTrack(
                generation,
                commandId,
                trackId ?: 0,
                trackId == null,
            )
        }

    fun applySettings(config: PlayerRuntimeConfig): Long? =
        sendCommand(PlayerRuntimeCommandType.APPLY_SETTINGS) {
                activeRemote,
                generation,
                commandId,
            ->
            activeRemote.applySettings(generation, commandId, config)
        }

    fun applyVideoFitMode(mode: String): Long? =
        sendCommand(PlayerRuntimeCommandType.APPLY_VIDEO_FIT) {
                activeRemote,
                generation,
                commandId,
            ->
            activeRemote.applyVideoFitMode(generation, commandId, mode)
        }

    fun requestThumbnail(
        loadCommandId: Long,
        positionSeconds: Double,
        maxSize: Int,
    ): Long? =
        sendCommand(PlayerRuntimeCommandType.THUMBNAIL) {
                activeRemote,
                generation,
                commandId,
            ->
            activeRemote.requestThumbnail(
                generation,
                commandId,
                loadCommandId,
                positionSeconds,
                maxSize,
            )
        }

    fun captureScreenshot(path: String): Long? =
        sendCommand(PlayerRuntimeCommandType.SCREENSHOT) { activeRemote, generation, commandId ->
            activeRemote.captureScreenshot(generation, commandId, path)
        }

    fun close(): Long? =
        sendCommand(PlayerRuntimeCommandType.CLOSE) { activeRemote, generation, commandId ->
            activeRemote.close(generation, commandId)
        }

    private fun beginInitialization() {
        val activeRemote = remote ?: return
        val config = pendingConfig ?: return
        val generation = runtimeGeneration
        val commandId = nextCommandId()
        pendingCommands[commandId] = PlayerRuntimeCommandType.INITIALIZE
        try {
            activeRemote.registerCallback(generation, callback)
            activeRemote.initialize(generation, commandId, config)
        } catch (error: Exception) {
            pendingCommands.remove(commandId)
            Log.e(TAG, "Unable to initialize player runtime", error)
            notifyConnectionError(
                "无法初始化播放器运行时：${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    private inline fun sendCommand(
        commandType: PlayerRuntimeCommandType,
        dispatch: (IPlayerRuntime, Long, Long) -> Unit,
    ): Long? {
        requireMainThread()
        val activeRemote = remote ?: return null
        val generation = runtimeGeneration.takeIf { it > 0L } ?: return null
        val commandId = nextCommandId()
        pendingCommands[commandId] = commandType
        return try {
            dispatch(activeRemote, generation, commandId)
            commandId
        } catch (error: Exception) {
            pendingCommands.remove(commandId)
            Log.e(TAG, "Unable to send player runtime command $commandType", error)
            listener.onRuntimeConnectionError(
                generation,
                "播放器运行时命令发送失败：${error.message ?: error.javaClass.simpleName}",
            )
            null
        }
    }

    private fun postEvent(
        runtimeGeneration: Long,
        eventSequence: Long,
        block: () -> Unit,
    ) {
        mainHandler.post {
            val currentCursor = eventCursor ?: return@post
            val acceptance =
                acceptPlayerRuntimeEvent(
                    currentCursor,
                    runtimeGeneration,
                    eventSequence,
                )
            if (!acceptance.accepted) return@post
            eventCursor = acceptance.cursor
            block()
        }
    }

    private fun takeCommand(
        commandId: Long,
        expectedType: PlayerRuntimeCommandType,
    ): PlayerRuntimeCommandType? {
        val commandType = pendingCommands[commandId]
        if (commandType != expectedType) return null
        pendingCommands.remove(commandId)
        return commandType
    }

    private fun handleDisconnected() {
        val generation = runtimeGeneration
        val expected = disconnectExpected
        serviceBinder?.let { binder ->
            runCatching { binder.unlinkToDeath(deathRecipient, 0) }
        }
        runtimeGeneration = 0L
        isBound = false
        remote = null
        serviceBinder = null
        pendingCommands.clear()
        eventCursor = null
        if (generation > 0L) {
            listener.onRuntimeDisconnected(generation, expected)
        }
    }

    private fun notifyConnectionError(message: String) {
        val generation = runtimeGeneration
        if (generation > 0L) {
            listener.onRuntimeConnectionError(generation, message)
        }
    }

    private fun nextCommandId(): Long {
        nextCommandId += 1L
        return nextCommandId
    }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "PlayerRuntimeConnection must be called on the main thread"
        }
    }

    private companion object {
        const val TAG = "PlayerRuntimeConnection"
    }
}
