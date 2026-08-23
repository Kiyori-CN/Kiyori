package com.ai.assistance.operit.core.player.runtime

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.util.Log
import android.util.LruCache
import android.view.Surface
import com.ai.assistance.operit.core.player.PlayerDebugLogLevel
import com.ai.assistance.operit.core.player.describePlayerMediaUriForDiagnostics
import com.ai.assistance.operit.core.player.isSupportedPlayerSpeed
import com.ai.assistance.operit.core.player.sanitizePlayerDiagnosticMessage
import com.ai.assistance.operit.core.player.shortPlayerDiagnosticId
import com.kiyori.platform.network.formatKiyoriMihomoRuntimeDiagnostic
import com.kiyori.platform.network.KiyoriMihomoRuntimePhase
import com.kiyori.platform.network.KiyoriNetworkProxyManager
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class PlayerRuntimeService : Service() {
    private lateinit var runtimeThread: HandlerThread
    private lateinit var runtimeHandler: Handler
    private lateinit var thumbnailExecutor: ExecutorService
    private val proxyRuntimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastProxyRuntimeDiagnosticKey: String? = null
    private var lastAppliedProxyRuntimeGeneration: Long? = null

    private var callback: IPlayerRuntimeCallback? = null
    private var callbackGeneration: Long = 0L
    private var runtimeGeneration: Long = 0L
    private var eventSequence: Long = 0L
    private var engine: MpvPlayerEngine? = null
    private var mediaResolver: PlayerMediaResolver? = null
    private var networkSnapshotObserver: PlayerNetworkSnapshotObserver? = null
    private var remoteSurface: Surface? = null
    private var remoteSurfaceGeneration: Long? = null
    private var currentLoadCommandId: Long = 0L
    private var currentMediaSource: String? = null
    private var lastMediaIdentitySnapshot: PlayerRuntimeMediaIdentitySnapshot? = null
    private var progressRunning = false
    private var thumbnailWorkerRunning = false
    private var pendingThumbnailRequest: PendingThumbnailRequest? = null
    private var activeThumbnailRequest: PendingThumbnailRequest? = null
    private val thumbnailCache =
        object : LruCache<String, Bitmap>(THUMBNAIL_CACHE_MAX_KB) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount / 1024).coerceAtLeast(1)
        }

    private val engineListener =
        object : MpvPlayerEngineListener {
            override fun onBooleanProperty(name: String, value: Boolean) {
                if (name == "eof-reached" && value) {
                    runtimeHandler.post {
                        emitDiagnostic(
                            PlayerDebugLogLevel.INFO,
                            TAG,
                            "收到自然播放结束属性 eof-reached=true",
                        )
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
                                    chapters =
                                        tracks.chapters.map { chapter ->
                                            chapter.toRuntimeChapter()
                                        },
                                    fileFormat = tracks.fileFormat,
                                    videoCodec = tracks.videoCodec,
                                    audioCodec = tracks.audioCodec,
                                    videoTrackCount = tracks.videoTrackCount,
                                    activeHardwareDecoder = tracks.activeHardwareDecoder,
                                    videoPixelFormat = tracks.videoPixelFormat,
                                    videoCodecProfile = tracks.videoCodecProfile,
                                )
                            lastMediaIdentitySnapshot = snapshot.toMediaIdentitySnapshot()
                            emitDiagnostic(
                                PlayerDebugLogLevel.INFO,
                                TAG,
                                    "媒体文件加载完成 audioTracks=${snapshot.audioTracks.size} " +
                                    "subtitleTracks=${snapshot.subtitleTracks.size} " +
                                    "videoTracks=${snapshot.videoTrackCount} " +
                                    "container=${snapshot.fileFormat ?: "unknown"} " +
                                    "videoCodec=${snapshot.videoCodec ?: "none"} " +
                                    "audioCodec=${snapshot.audioCodec ?: "none"} " +
                                    "hwdec=${snapshot.activeHardwareDecoder ?: "none"} " +
                                    "pixelFormat=${snapshot.videoPixelFormat ?: "unknown"} " +
                                    "codecProfile=${snapshot.videoCodecProfile ?: "unknown"} " +
                                    "chapters=${snapshot.chapters.size}",
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

            override fun onVideoReconfigured() {
                runtimeHandler.post {
                    val activeEngine = engine ?: return@post
                    if (currentLoadCommandId <= 0L || lastMediaIdentitySnapshot == null) {
                        return@post
                    }
                    try {
                        val identity = activeEngine.readMediaIdentity().toRuntimeSnapshot()
                        if (identity == lastMediaIdentitySnapshot) {
                            return@post
                        }
                        lastMediaIdentitySnapshot = identity
                        emitDiagnostic(
                            PlayerDebugLogLevel.INFO,
                            TAG,
                            "媒体身份已刷新 videoTracks=${identity.videoTrackCount} " +
                                "container=${identity.fileFormat ?: "unknown"} " +
                                "videoCodec=${identity.videoCodec ?: "none"} " +
                                "audioCodec=${identity.audioCodec ?: "none"} " +
                                "hwdec=${identity.activeHardwareDecoder ?: "none"} " +
                                "pixelFormat=${identity.videoPixelFormat ?: "unknown"} " +
                                "codecProfile=${identity.videoCodecProfile ?: "unknown"}",
                        )
                        emit { currentCallback, generation, sequence ->
                            currentCallback.onMediaIdentityChanged(
                                generation,
                                sequence,
                                currentLoadCommandId,
                                identity,
                            )
                        }
                    } catch (error: Exception) {
                        emitDiagnostic(
                            PlayerDebugLogLevel.WARN,
                            TAG,
                            "媒体身份刷新失败：${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                }
            }

            override fun onSeek() {
                runtimeHandler.post {
                    emit { currentCallback, generation, sequence ->
                        currentCallback.onSeek(
                            generation,
                            sequence,
                            currentLoadCommandId,
                        )
                    }
                }
            }

            override fun onPlaybackRestart() {
                runtimeHandler.post {
                    emit { currentCallback, generation, sequence ->
                        currentCallback.onPlaybackRestart(
                            generation,
                            sequence,
                            currentLoadCommandId,
                        )
                    }
                }
            }

            override fun onRuntimeError(message: String) {
                runtimeHandler.post { emitRuntimeError(message) }
            }

            override fun onDiagnosticLog(
                level: PlayerDebugLogLevel,
                tag: String,
                message: String,
            ) {
                runtimeHandler.post { emitDiagnostic(level, tag, message) }
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
                                fullVideoCacheActive = progress.fullVideoCacheActive,
                                fullVideoCacheComplete = progress.fullVideoCacheComplete,
                                fullVideoCacheStartSeconds = progress.fullVideoCacheStartSeconds,
                                fullVideoCacheEndSeconds = progress.fullVideoCacheEndSeconds,
                                fullVideoCachePhase = progress.fullVideoCachePhase,
                                fullVideoCacheReason = progress.fullVideoCacheReason,
                                fullVideoCacheStateEvidence =
                                    progress.fullVideoCacheStateEvidence,
                                fullVideoCacheFileBytes = progress.fullVideoCacheFileBytes,
                                fullVideoCacheExpectedBytes = progress.fullVideoCacheExpectedBytes,
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
                        onFailure = { closeRuntimeResources() },
                        onSuccess = {
                            closeRuntimeResources()
                            emitDiagnostic(
                                PlayerDebugLogLevel.INFO,
                                TAG,
                                "创建 mpv runtime decoderBackend=${config.decoderBackendId} " +
                                    "renderingProfile=${config.renderingProfileId} " +
                                    "gpuNext=${config.gpuNextEnabled} vulkan=${config.vulkanEnabled} " +
                                    "shaderCount=${config.shaderFiles.size}",
                            )
                            val created = MpvPlayerEngine(applicationContext, engineListener)
                            engine = created
                            created.initialize(config.toPlayerSettings())
                            mediaResolver = PlayerMediaResolver(applicationContext)
                            networkSnapshotObserver =
                                PlayerNetworkSnapshotObserver(
                                    context = applicationContext,
                                    handler = runtimeHandler,
                                ) { reason, snapshot ->
                                    emitDiagnostic(
                                        PlayerDebugLogLevel.INFO,
                                        NETWORK_TAG,
                                        "播放器网络环境 reason=$reason ${snapshot.diagnosticSummary()}",
                                    )
                                }.also(PlayerNetworkSnapshotObserver::start)
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
                    emitDiagnostic(
                        PlayerDebugLogLevel.INFO,
                        TAG,
                        "接收媒体加载 command=$commandId " +
                            "request=${shortPlayerDiagnosticId(request.requestId)} " +
                            "media=${describePlayerMediaUriForDiagnostics(request.uri)} " +
                            "headerCount=${request.headers.size}",
                    )
                    val activeEngine = requireNotNull(engine) { "播放器运行时尚未初始化" }
                    val resolver = requireNotNull(mediaResolver) { "媒体解析器尚未初始化" }
                    val target = resolver.resolve(request.uri, request.headers)
                    currentLoadCommandId = commandId
                    currentMediaSource = target
                    lastMediaIdentitySnapshot = null
                    pendingThumbnailRequest?.temporaryFile?.delete()
                    activeThumbnailRequest?.temporaryFile?.delete()
                    pendingThumbnailRequest = null
                    thumbnailCache.evictAll()
                    PlayerRuntimeProcessState.update(
                        applicationContext,
                        phase = "LOAD",
                        runtimeGeneration = runtimeGeneration,
                    )
                    activeEngine.load(
                        requestId = request.requestId,
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
                            emitDiagnostic(
                                PlayerDebugLogLevel.INFO,
                                TAG,
                                "接收 Surface attach command=$commandId generation=$surfaceGeneration " +
                                    "size=${width}x$height",
                            )
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
                    emitDiagnostic(
                        PlayerDebugLogLevel.INFO,
                        TAG,
                        "接收 Surface detach command=$commandId generation=$surfaceGeneration",
                    )
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
                if (!isSupportedPlayerSpeed(speed)) return
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
                    activeEngine.applyRenderingProfile(settings.renderingProfile)
                    activeEngine.applyDecoderBackend(settings.decoderBackend)
                    activeEngine.applyPreciseSeeking(settings.preciseSeeking)
                    activeEngine.applySubtitleScale(settings.subtitleScale)
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

            override fun requestThumbnail(
                runtimeGeneration: Long,
                commandId: Long,
                loadCommandId: Long,
                positionSeconds: Double,
                maxSize: Int,
            ) {
                if (
                    !isValidCommand(runtimeGeneration, commandId) ||
                        loadCommandId <= 0L ||
                        !positionSeconds.isFinite() ||
                        positionSeconds < 0.0 ||
                        maxSize !in 64..512
                ) {
                    return
                }
                runtimeHandler.post {
                    if (
                        runtimeGeneration != this@PlayerRuntimeService.runtimeGeneration ||
                            loadCommandId != currentLoadCommandId
                    ) {
                        emitCommandFailure(
                            commandId = commandId,
                            operation = "提取进度缩略图",
                            message = "缩略图请求对应的媒体已经切换",
                        )
                        return@post
                    }
                    val source =
                        currentMediaSource
                            ?: run {
                                emitCommandFailure(
                                    commandId = commandId,
                                    operation = "提取进度缩略图",
                                    message = "播放器尚未加载可预览的媒体",
                                )
                                return@post
                            }
                    val bucketPosition =
                        (positionSeconds * THUMBNAIL_BUCKETS_PER_SECOND)
                            .roundToInt()
                            .coerceAtLeast(0) / THUMBNAIL_BUCKETS_PER_SECOND
                    val cacheKey = "$source|$bucketPosition|$maxSize"
                    thumbnailCache.get(cacheKey)?.let { bitmap ->
                        emitThumbnailReady(commandId, bucketPosition, bitmap)
                        return@post
                    }
                    pendingThumbnailRequest?.let { superseded ->
                        superseded.temporaryFile?.delete()
                        emitThumbnailReady(
                            superseded.commandId,
                            superseded.responsePositionSeconds,
                            null,
                        )
                    }
                    val preparedSource =
                        try {
                            requireNotNull(engine) { "播放器运行时尚未初始化" }
                                .prepareThumbnailSource(source, bucketPosition)
                        } catch (error: Exception) {
                            emitCommandFailure(
                                commandId = commandId,
                                operation = "提取进度缩略图",
                                message = error.message ?: error.javaClass.simpleName,
                            )
                            return@post
                        }
                    pendingThumbnailRequest =
                        PendingThumbnailRequest(
                            runtimeGeneration = runtimeGeneration,
                            commandId = commandId,
                            loadCommandId = loadCommandId,
                            originalSource = source,
                            extractionSource = preparedSource.source,
                            extractionPositionSeconds = preparedSource.positionSeconds,
                            responsePositionSeconds = bucketPosition,
                            maxSize = maxSize,
                            cacheKey = cacheKey,
                            temporaryFile = preparedSource.temporaryFile,
                        )
                    startNextThumbnailRequest()
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
        thumbnailExecutor =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, THUMBNAIL_THREAD_NAME).apply { isDaemon = true }
            }
        startProxyRuntimeDiagnostics()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        proxyRuntimeScope.cancel()
        if (::runtimeHandler.isInitialized) {
            runtimeHandler.post {
                closeRuntimeResources()
                callback = null
                callbackGeneration = 0L
                thumbnailExecutor.shutdownNow()
                runtimeThread.quitSafely()
            }
        }
        super.onDestroy()
    }

    private fun startProxyRuntimeDiagnostics() {
        proxyRuntimeScope.launch {
            KiyoriNetworkProxyManager.getInstance(applicationContext).runtimeState.collect { state ->
                if (
                    state.runtimeGeneration == null &&
                        state.phase == KiyoriMihomoRuntimePhase.STOPPED &&
                        state.message == null
                ) {
                    return@collect
                }
                runtimeHandler.post {
                    val key =
                        listOf(
                            state.phase,
                            state.runtimeGeneration,
                            state.mixedPort,
                            state.controllerPort,
                            state.controllerHealthy,
                            state.mixedPortListening,
                            state.stopReason,
                            state.failureKind,
                            state.message,
                        ).joinToString("|")
                    if (key == lastProxyRuntimeDiagnosticKey) return@post
                    lastProxyRuntimeDiagnosticKey = key
                    val level =
                        if (state.phase == KiyoriMihomoRuntimePhase.ERROR) {
                            PlayerDebugLogLevel.ERROR
                        } else {
                            PlayerDebugLogLevel.INFO
                        }
                    emitDiagnostic(
                        level,
                        PROXY_RUNTIME_TAG,
                        formatKiyoriMihomoRuntimeDiagnostic(state),
                    )
                    if (
                        state.phase == KiyoriMihomoRuntimePhase.RUNNING &&
                            state.runtimeGeneration != null &&
                            state.runtimeGeneration != lastAppliedProxyRuntimeGeneration
                    ) {
                        engine?.let { activeEngine ->
                            runCatching { activeEngine.refreshApplicationProxyRoute() }
                                .onSuccess { lastAppliedProxyRuntimeGeneration = state.runtimeGeneration }
                                .onFailure { error ->
                                    emitDiagnostic(
                                        PlayerDebugLogLevel.ERROR,
                                        PROXY_RUNTIME_TAG,
                                        "恢复后刷新播放器代理失败 type=${error::class.java.simpleName}",
                                    )
                                }
                        }
                    }
                }
            }
        }
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
        emitDiagnostic(
            PlayerDebugLogLevel.DEBUG,
            TAG,
            "开始命令 command=$commandId operation=$operation",
        )
        try {
            onSuccess()
        } catch (error: Exception) {
            onFailure()
            val message = error.message ?: error.javaClass.simpleName
            Log.e(TAG, "$operation failed", error)
            emitDiagnostic(
                PlayerDebugLogLevel.ERROR,
                TAG,
                "命令失败 command=$commandId operation=$operation ${formatDiagnosticError(error)}",
            )
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

    private fun emitCommandFailure(
        commandId: Long,
        operation: String,
        message: String,
    ) {
        emitDiagnostic(
            PlayerDebugLogLevel.WARN,
            TAG,
            "命令失败 command=$commandId operation=$operation message=$message",
        )
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

    private fun emitThumbnailReady(
        commandId: Long,
        positionSeconds: Double,
        bitmap: Bitmap?,
    ) {
        emit { currentCallback, generation, sequence ->
            currentCallback.onThumbnailReady(
                generation,
                sequence,
                commandId,
                positionSeconds,
                bitmap,
            )
        }
    }

    private fun emitRuntimeError(message: String) {
        emitDiagnostic(PlayerDebugLogLevel.ERROR, TAG, message)
        emit { currentCallback, generation, sequence ->
            currentCallback.onRuntimeError(generation, sequence, message)
        }
    }

    private fun emitDiagnostic(
        level: PlayerDebugLogLevel,
        tag: String,
        message: String,
    ) {
        val sanitized = sanitizePlayerDiagnosticMessage(message)
        if (sanitized.isBlank()) return
        emit { currentCallback, generation, sequence ->
            currentCallback.onDiagnosticLog(
                generation,
                sequence,
                level.wireValue,
                tag,
                sanitized,
            )
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
        pendingThumbnailRequest?.temporaryFile?.delete()
        activeThumbnailRequest?.temporaryFile?.delete()
        pendingThumbnailRequest = null
        activeThumbnailRequest = null
        thumbnailCache.evictAll()
        networkSnapshotObserver?.stop()
        networkSnapshotObserver = null
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
        currentMediaSource = null
        lastMediaIdentitySnapshot = null
    }

    private fun startNextThumbnailRequest() {
        if (thumbnailWorkerRunning) return
        val request = pendingThumbnailRequest ?: return
        pendingThumbnailRequest = null
        val activeEngine =
            engine
                ?: run {
                    emitCommandFailure(
                        commandId = request.commandId,
                        operation = "提取进度缩略图",
                        message = "播放器运行时尚未初始化",
                    )
                    return
                }
        thumbnailWorkerRunning = true
        activeThumbnailRequest = request
        thumbnailExecutor.execute {
            val result =
                runCatching {
                    activeEngine.grabThumbnail(
                        source = request.extractionSource,
                        positionSeconds = request.extractionPositionSeconds,
                        maxSize = request.maxSize,
                    )
                }
            runtimeHandler.post {
                val stillCurrent =
                    request.runtimeGeneration == runtimeGeneration &&
                        request.loadCommandId == currentLoadCommandId &&
                        request.originalSource == currentMediaSource
                if (stillCurrent) {
                    result
                        .onSuccess { bitmap ->
                            bitmap?.let { thumbnailCache.put(request.cacheKey, it) }
                            emitThumbnailReady(
                                request.commandId,
                                request.responsePositionSeconds,
                                bitmap,
                            )
                        }
                        .onFailure { error ->
                            val message = error.message ?: error.javaClass.simpleName
                            emitCommandFailure(
                                commandId = request.commandId,
                                operation = "提取进度缩略图",
                                message = message,
                            )
                        }
                } else {
                    emitThumbnailReady(
                        request.commandId,
                        request.responsePositionSeconds,
                        null,
                    )
                }
                request.temporaryFile?.delete()
                if (activeThumbnailRequest === request) {
                    activeThumbnailRequest = null
                }
                thumbnailWorkerRunning = false
                startNextThumbnailRequest()
            }
        }
    }

    private fun releaseRemoteSurface() {
        remoteSurface?.release()
        remoteSurface = null
        remoteSurfaceGeneration = null
    }

    private fun isValidCommand(runtimeGeneration: Long, commandId: Long): Boolean =
        runtimeGeneration > 0L && commandId > 0L

    private fun formatDiagnosticError(error: Throwable): String =
        buildString {
            append(error.javaClass.simpleName)
            error.message?.takeIf(String::isNotBlank)?.let { message ->
                append(": ")
                append(message)
            }
            error.stackTrace.take(MAX_DIAGNOSTIC_STACK_FRAMES).forEach { frame ->
                append(" | at ")
                append(frame.className)
                append('.')
                append(frame.methodName)
                append(':')
                append(frame.lineNumber)
            }
        }

    private companion object {
        const val TAG = "PlayerRuntimeService"
        const val NETWORK_TAG = "PlayerNetwork"
        const val PROXY_RUNTIME_TAG = "KiyoriMihomo"
        const val RUNTIME_THREAD_NAME = "KiyoriPlayerRuntime"
        const val PROGRESS_INTERVAL_MS = 250L
        const val MAX_DIAGNOSTIC_STACK_FRAMES = 8
        const val THUMBNAIL_THREAD_NAME = "KiyoriPlayerThumbnail"
        const val THUMBNAIL_CACHE_MAX_KB = 20 * 1024
        const val THUMBNAIL_BUCKETS_PER_SECOND = 2.0
    }
}

private fun PlayerRuntimeTrackSnapshot.toMediaIdentitySnapshot(): PlayerRuntimeMediaIdentitySnapshot =
    PlayerRuntimeMediaIdentitySnapshot(
        fileFormat = fileFormat,
        videoCodec = videoCodec,
        audioCodec = audioCodec,
        videoTrackCount = videoTrackCount,
        activeHardwareDecoder = activeHardwareDecoder,
        videoPixelFormat = videoPixelFormat,
        videoCodecProfile = videoCodecProfile,
    )

private fun MpvPlayerMediaIdentitySnapshot.toRuntimeSnapshot(): PlayerRuntimeMediaIdentitySnapshot =
    PlayerRuntimeMediaIdentitySnapshot(
        fileFormat = fileFormat,
        videoCodec = videoCodec,
        audioCodec = audioCodec,
        videoTrackCount = videoTrackCount,
        activeHardwareDecoder = activeHardwareDecoder,
        videoPixelFormat = videoPixelFormat,
        videoCodecProfile = videoCodecProfile,
    )

private data class PendingThumbnailRequest(
    val runtimeGeneration: Long,
    val commandId: Long,
    val loadCommandId: Long,
    val originalSource: String,
    val extractionSource: String,
    val extractionPositionSeconds: Double,
    val responsePositionSeconds: Double,
    val maxSize: Int,
    val cacheKey: String,
    val temporaryFile: File?,
)
