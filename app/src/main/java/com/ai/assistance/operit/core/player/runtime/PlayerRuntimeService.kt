package com.ai.assistance.operit.core.player.runtime

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.util.Log
import android.view.Surface

internal class PlayerRuntimeService : Service() {
    private lateinit var runtimeThread: HandlerThread
    private lateinit var runtimeHandler: Handler

    private var callback: IPlayerRuntimeCallback? = null
    private var callbackGeneration: Long = 0L
    private var runtimeGeneration: Long = 0L
    private var eventSequence: Long = 0L
    private var engine: MpvPlayerEngine? = null
    private var mediaResolver: PlayerMediaResolver? = null
    private var remoteSurface: Surface? = null
    private var remoteSurfaceGeneration: Long? = null
    private var currentLoadCommandId: Long = 0L
    private var progressRunning = false

    private val engineListener =
        object : MpvPlayerEngineListener {
            override fun onBooleanProperty(name: String, value: Boolean) {
                if (name == "eof-reached" && value) {
                    runtimeHandler.post {
                        emit { currentCallback, generation, sequence ->
                            currentCallback.onNaturalEnd(generation, sequence)
                        }
                    }
                }
            }

            override fun onDoubleProperty(name: String, value: Double) = Unit

            override fun onFileLoaded() {
                runtimeHandler.post {
                    val activeEngine = engine ?: return@post
                    runCatching { activeEngine.readTracks() }
                        .onSuccess { tracks ->
                            val snapshot =
                                PlayerRuntimeTrackSnapshot(
                                    audioTracks =
                                        tracks.audioTracks.map { track -> track.toRuntimeTrack() },
                                    subtitleTracks =
                                        tracks.subtitleTracks.map { track -> track.toRuntimeTrack() },
                                )
                            emit { currentCallback, generation, sequence ->
                                currentCallback.onFileLoaded(
                                    generation,
                                    sequence,
                                    currentLoadCommandId,
                                    snapshot,
                                )
                            }
                        }
                        .onFailure { error ->
                            emitRuntimeError(
                                "无法读取媒体轨道：${error.message ?: error.javaClass.simpleName}",
                            )
                        }
                }
            }

            override fun onRuntimeError(message: String) {
                runtimeHandler.post { emitRuntimeError(message) }
            }
        }

    private val progressEmitter =
        object : Runnable {
            override fun run() {
                if (!progressRunning) return
                val activeEngine = engine ?: return
                runCatching { activeEngine.readProgress() }
                    .onSuccess { progress ->
                        val snapshot =
                            PlayerRuntimePlaybackSnapshot(
                                positionSeconds = progress.positionSeconds,
                                durationSeconds = progress.durationSeconds,
                                paused = progress.paused,
                                buffering = progress.buffering,
                                speed = progress.speed,
                                networkSpeedBytesPerSecond =
                                    progress.networkSpeedBytesPerSecond,
                            )
                        emit { currentCallback, generation, sequence ->
                            currentCallback.onPlaybackSnapshot(
                                generation,
                                sequence,
                                snapshot,
                            )
                        }
                    }
                    .onFailure { error ->
                        progressRunning = false
                        emitRuntimeError(
                            "无法读取播放状态：${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                if (progressRunning) {
                    runtimeHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
                }
            }
        }

    private val binder =
        object : IPlayerRuntime.Stub() {
            override fun registerCallback(
                runtimeGeneration: Long,
                callback: IPlayerRuntimeCallback?,
            ) {
                if (runtimeGeneration <= 0L || callback == null) return
                runtimeHandler.post {
                    this@PlayerRuntimeService.callback = callback
                    callbackGeneration = runtimeGeneration
                }
            }

            override fun initialize(
                runtimeGeneration: Long,
                commandId: Long,
                config: PlayerRuntimeConfig?,
            ) {
                if (!isValidCommand(runtimeGeneration, commandId) || config == null) return
                runtimeHandler.post {
                    if (callbackGeneration != runtimeGeneration) return@post
                    this@PlayerRuntimeService.runtimeGeneration = runtimeGeneration
                    eventSequence = 0L
                    execute(
                        commandId = commandId,
                        operation = "初始化播放器运行时",
                        onSuccess = {
                            closeRuntimeResources()
                            val created = MpvPlayerEngine(applicationContext, engineListener)
                            created.initialize(config.toPlayerSettings())
                            engine = created
                            mediaResolver = PlayerMediaResolver(applicationContext)
                            PlayerRuntimeProcessState.update(
                                applicationContext,
                                phase = "READY",
                                runtimeGeneration = runtimeGeneration,
                            )
                            emit { currentCallback, generation, sequence ->
                                currentCallback.onRuntimeReady(
                                    generation,
                                    sequence,
                                    commandId,
                                    Process.myPid(),
                                )
                            }
                        },
                    )
                }
            }

            override fun load(
                runtimeGeneration: Long,
                commandId: Long,
                request: PlayerRuntimeLoadRequest?,
            ) {
                if (!isValidCommand(runtimeGeneration, commandId) || request == null) return
                postCommand(runtimeGeneration, commandId, "加载媒体") {
                    val activeEngine = requireNotNull(engine) { "播放器运行时尚未初始化" }
                    val resolver = requireNotNull(mediaResolver) { "媒体解析器尚未初始化" }
                    val target = resolver.resolve(request.uri)
                    currentLoadCommandId = commandId
                    PlayerRuntimeProcessState.update(
                        applicationContext,
                        phase = "LOAD",
                        runtimeGeneration = runtimeGeneration,
                    )
                    activeEngine.load(
                        target = target,
                        headers = request.headers,
                        settings = request.config.toPlayerSettings(),
                        shaderFiles = request.config.shaderFiles,
                        initialSpeed = request.initialSpeed,
                    )
                    startProgressEmitter()
                    emitCommandCompleted(commandId)
                }
            }

            override fun attachSurface(
                runtimeGeneration: Long,
                commandId: Long,
                surfaceGeneration: Long,
                surface: Surface?,
                width: Int,
                height: Int,
            ) {
                if (
                    !isValidCommand(runtimeGeneration, commandId) ||
                        surfaceGeneration <= 0L ||
                        surface == null ||
                        width < 0 ||
                        height < 0
                ) {
                    surface?.release()
                    return
                }
                runtimeHandler.post {
                    if (runtimeGeneration != this@PlayerRuntimeService.runtimeGeneration) {
                        surface.release()
                        return@post
                    }
                    execute(
                        commandId = commandId,
                        operation = "连接播放画面",
                        onFailure = { surface.release() },
                        onSuccess = {
                            check(remoteSurface == null) { "播放器仍持有旧 Surface" }
                            requireNotNull(engine) { "播放器运行时尚未初始化" }
                                .attachSurface(surface, width, height)
                            remoteSurface = surface
                            remoteSurfaceGeneration = surfaceGeneration
                            PlayerRuntimeProcessState.update(
                                applicationContext,
                                phase = "ATTACHED",
                                runtimeGeneration = runtimeGeneration,
                                surfaceGeneration = surfaceGeneration,
                            )
                            emit { currentCallback, generation, sequence ->
                                currentCallback.onSurfaceAttached(
                                    generation,
                                    sequence,
                                    commandId,
                                    surfaceGeneration,
                                )
                            }
                        },
                    )
                }
            }

            override fun updateSurface(
                runtimeGeneration: Long,
                commandId: Long,
                surfaceGeneration: Long,
                width: Int,
                height: Int,
            ) {
                if (
                    !isValidCommand(runtimeGeneration, commandId) ||
                        surfaceGeneration <= 0L ||
                        width < 0 ||
                        height < 0
                ) {
                    return
                }
                postCommand(runtimeGeneration, commandId, "更新播放画面尺寸") {
                    check(remoteSurfaceGeneration == surfaceGeneration) {
                        "Surface generation 已过期"
                    }
                    requireNotNull(engine) { "播放器运行时尚未初始化" }
                        .updateSurfaceSize(width, height)
                    emitCommandCompleted(commandId)
                }
            }

            override fun detachSurface(
                runtimeGeneration: Long,
                commandId: Long,
                surfaceGeneration: Long,
            ) {
                if (!isValidCommand(runtimeGeneration, commandId) || surfaceGeneration <= 0L) return
                postCommand(runtimeGeneration, commandId, "断开播放画面") {
                    check(remoteSurfaceGeneration == surfaceGeneration) {
                        "Surface generation 已过期"
                    }
                    requireNotNull(engine) { "播放器运行时尚未初始化" }.detachSurface()
                    releaseRemoteSurface()
                    PlayerRuntimeProcessState.update(
                        applicationContext,
                        phase = "DETACHED",
                        runtimeGeneration = runtimeGeneration,
                        surfaceGeneration = surfaceGeneration,
                    )
                    emit { currentCallback, generation, sequence ->
                        currentCallback.onSurfaceDetached(
                            generation,
                            sequence,
                            commandId,
                            surfaceGeneration,
                        )
                    }
                }
            }

            override fun setPaused(
                runtimeGeneration: Long,
                commandId: Long,
                paused: Boolean,
            ) {
                postSimpleCommand(runtimeGeneration, commandId, "设置暂停状态") {
                    setPaused(paused)
                }
            }

            override fun seekTo(
                runtimeGeneration: Long,
                commandId: Long,
                positionSeconds: Double,
                precise: Boolean,
            ) {
                if (!positionSeconds.isFinite() || positionSeconds < 0.0) return
                postSimpleCommand(runtimeGeneration, commandId, "跳转播放位置") {
                    seekTo(positionSeconds, precise)
                }
            }

            override fun setSpeed(
                runtimeGeneration: Long,
                commandId: Long,
                speed: Double,
            ) {
                if (!speed.isFinite() || speed <= 0.0) return
                postSimpleCommand(runtimeGeneration, commandId, "设置播放速度") {
                    setSpeed(speed)
                }
            }

            override fun setAudioTrack(
                runtimeGeneration: Long,
                commandId: Long,
                trackId: Int,
            ) {
                postSimpleCommand(runtimeGeneration, commandId, "切换音轨") {
                    setAudioTrack(trackId)
                }
            }

            override fun setSubtitleTrack(
                runtimeGeneration: Long,
                commandId: Long,
                trackId: Int,
                disabled: Boolean,
            ) {
                postSimpleCommand(runtimeGeneration, commandId, "切换字幕") {
                    setSubtitleTrack(if (disabled) null else trackId)
                }
            }

            override fun applySettings(
                runtimeGeneration: Long,
                commandId: Long,
                config: PlayerRuntimeConfig?,
            ) {
                if (config == null) return
                postCommand(runtimeGeneration, commandId, "应用播放器设置") {
                    val activeEngine = requireNotNull(engine) { "播放器运行时尚未初始化" }
                    val settings = config.toPlayerSettings()
                    activeEngine.applyDecoderPreset(settings.decoderPreset)
                    activeEngine.applyPreciseSeeking(settings.preciseSeeking)
                    activeEngine.applyNetworkCache(settings.networkCachePolicy)
                    activeEngine.applySubtitleScale(settings.subtitleScale)
                    activeEngine.applyEndBehavior(settings.endBehavior)
                    activeEngine.applyVolumeBoost(settings.volumeBoostEnabled)
                    activeEngine.applyShaders(config.shaderFiles)
                    emitCommandCompleted(commandId)
                }
            }

            override fun applyVideoFitMode(
                runtimeGeneration: Long,
                commandId: Long,
                mode: String?,
            ) {
                if (mode == null) return
                postSimpleCommand(runtimeGeneration, commandId, "切换画面比例") {
                    applyVideoFitMode(playerVideoFitModeFromRuntimeId(mode))
                }
            }

            override fun captureScreenshot(
                runtimeGeneration: Long,
                commandId: Long,
                path: String?,
            ) {
                if (path.isNullOrBlank()) return
                postCommand(runtimeGeneration, commandId, "截取视频画面") {
                    requireNotNull(engine) { "播放器运行时尚未初始化" }
                        .captureScreenshot(path)
                    emit { currentCallback, generation, sequence ->
                        currentCallback.onScreenshotCompleted(
                            generation,
                            sequence,
                            commandId,
                            path,
                        )
                    }
                }
            }

            override fun close(runtimeGeneration: Long, commandId: Long) {
                postCommand(runtimeGeneration, commandId, "关闭播放器运行时") {
                    PlayerRuntimeProcessState.update(
                        applicationContext,
                        phase = "CLOSING",
                        runtimeGeneration = runtimeGeneration,
                    )
                    stopProgressEmitter()
                    closeRuntimeResources()
                    emitCommandCompleted(commandId)
                    this@PlayerRuntimeService.runtimeGeneration = 0L
                    eventSequence = 0L
                    stopSelf()
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        runtimeThread = HandlerThread(RUNTIME_THREAD_NAME)
        runtimeThread.start()
        runtimeHandler = Handler(runtimeThread.looper)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        if (::runtimeHandler.isInitialized) {
            runtimeHandler.post {
                closeRuntimeResources()
                callback = null
                callbackGeneration = 0L
                runtimeThread.quitSafely()
            }
        }
        super.onDestroy()
    }

    private fun postSimpleCommand(
        runtimeGeneration: Long,
        commandId: Long,
        operation: String,
        action: MpvPlayerEngine.() -> Unit,
    ) {
        postCommand(runtimeGeneration, commandId, operation) {
            requireNotNull(engine) { "播放器运行时尚未初始化" }.action()
            emitCommandCompleted(commandId)
        }
    }

    private fun postCommand(
        runtimeGeneration: Long,
        commandId: Long,
        operation: String,
        action: () -> Unit,
    ) {
        if (!isValidCommand(runtimeGeneration, commandId)) return
        runtimeHandler.post {
            if (runtimeGeneration != this@PlayerRuntimeService.runtimeGeneration) return@post
            execute(commandId, operation, onSuccess = action)
        }
    }

    private fun execute(
        commandId: Long,
        operation: String,
        onFailure: () -> Unit = {},
        onSuccess: () -> Unit,
    ) {
        try {
            onSuccess()
        } catch (error: Exception) {
            onFailure()
            val message = error.message ?: error.javaClass.simpleName
            Log.e(TAG, "$operation failed", error)
            emit { currentCallback, generation, sequence ->
                currentCallback.onCommandFailed(
                    generation,
                    sequence,
                    commandId,
                    operation,
                    message,
                )
            }
        }
    }

    private fun emitCommandCompleted(commandId: Long) {
        emit { currentCallback, generation, sequence ->
            currentCallback.onCommandCompleted(generation, sequence, commandId)
        }
    }

    private fun emitRuntimeError(message: String) {
        emit { currentCallback, generation, sequence ->
            currentCallback.onRuntimeError(generation, sequence, message)
        }
    }

    private inline fun emit(
        callbackBlock: (IPlayerRuntimeCallback, Long, Long) -> Unit,
    ) {
        val currentCallback = callback ?: return
        val generation = runtimeGeneration
        if (generation <= 0L || callbackGeneration != generation) return
        val sequence = ++eventSequence
        runCatching { callbackBlock(currentCallback, generation, sequence) }
            .onFailure { error -> Log.e(TAG, "Unable to dispatch player runtime callback", error) }
    }

    private fun startProgressEmitter() {
        if (progressRunning) return
        progressRunning = true
        runtimeHandler.removeCallbacks(progressEmitter)
        runtimeHandler.post(progressEmitter)
    }

    private fun stopProgressEmitter() {
        progressRunning = false
        if (::runtimeHandler.isInitialized) {
            runtimeHandler.removeCallbacks(progressEmitter)
        }
    }

    private fun closeRuntimeResources() {
        stopProgressEmitter()
        val activeEngine = engine
        if (activeEngine != null) {
            if (remoteSurface != null) {
                activeEngine.detachSurface()
            }
            releaseRemoteSurface()
            activeEngine.destroy()
        } else {
            releaseRemoteSurface()
        }
        engine = null
        mediaResolver?.close()
        mediaResolver = null
        currentLoadCommandId = 0L
    }

    private fun releaseRemoteSurface() {
        remoteSurface?.release()
        remoteSurface = null
        remoteSurfaceGeneration = null
    }

    private fun isValidCommand(runtimeGeneration: Long, commandId: Long): Boolean =
        runtimeGeneration > 0L && commandId > 0L

    private companion object {
        const val TAG = "PlayerRuntimeService"
        const val RUNTIME_THREAD_NAME = "KiyoriPlayerRuntime"
        const val PROGRESS_INTERVAL_MS = 250L
    }
}
