package com.ai.assistance.operit.core.player

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Looper
import android.view.Surface
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimeCommandType
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimeConfig
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimeConnection
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimeConnectionListener
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimeLoadRequest
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimeMediaIdentitySnapshot
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimePlaybackSnapshot
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimeTrackSnapshot
import com.ai.assistance.operit.core.player.runtime.PlayerSeekLifecycleEvent
import com.ai.assistance.operit.core.player.runtime.isPlayerUserSeekEvent
import com.ai.assistance.operit.core.player.runtime.reducePlayerSeeking
import com.ai.assistance.operit.core.player.runtime.toPlayerChapter
import com.ai.assistance.operit.core.player.runtime.toPlayerTrack
import com.ai.assistance.operit.core.player.runtime.toRuntimeConfig
import com.ai.assistance.operit.core.player.runtime.toRuntimeId
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettingsStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserDownloadApplicationDirectory
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserDownloadPublicDirectory
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.crash.PlayerCrashCoordinator
import com.ai.assistance.operit.util.crash.PlayerCrashEventType
import com.ai.assistance.operit.util.crash.PlayerCrashJournal
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PlayerSession private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val settingsStore = PlayerSettingsStore.getInstance(appContext)
    private val historyStore = WebSessionHistoryStore.getInstance(appContext)
    private val shaderManager = Anime4KShaderManager(appContext)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerSessionState())

    val state: StateFlow<PlayerSessionState> = _state.asStateFlow()

    private var pendingSurfaceLease: SurfaceParcel? = null
    private var activeSurfaceLease: SurfaceParcel? = null
    private var pendingMediaLoad: PendingMediaLoad? = null
    private var lastLoadCommandId: Long? = null
    private var pendingUserSeekLoadCommandId: Long? = null
    private var closeCommandId: Long? = null
    private var closeRequested = false
    private var failedSession: FailedPlayerSession? = null
    private var pendingRestartSeekSeconds: Double? = null
    private var activeQueue: List<PlayerMediaRequest> = emptyList()
    private var activeQueueIndex: Int = 0
    private var pendingThumbnailCommandId: Long? = null
    private var activeLongPressSpeedBoost: ActiveLongPressSpeedBoost? = null
    private val pendingScreenshots = LinkedHashMap<Long, PendingScreenshot>()

    private val runtimeListener =
        object : PlayerRuntimeConnectionListener {
            override fun onRuntimeReady(runtimeGeneration: Long, processId: Int) {
                if (!isCurrentRuntime(runtimeGeneration)) return
                if (processId <= 0) {
                    handleUnexpectedRuntimeStop("播放器运行时返回了无效进程 ID")
                    return
                }
                val snapshot = _state.value
                if (snapshot.runtimeState != PlayerRuntimeState.BINDING) return
                _state.value =
                    snapshot.copy(
                        runtimeState = PlayerRuntimeState.READY,
                        runtimeProcessId = processId,
                        error = null,
                    )
                PlayerDebugLogBuffer.append(
                    TAG,
                    "播放器运行时就绪 generation=$runtimeGeneration pid=$processId",
                )
                PlayerCrashJournal.record(
                    appContext,
                    PlayerCrashEventType.RUNTIME_READY,
                    runtimeGeneration,
                )
                requestPendingSurfaceAttach()
                startPendingMediaLoad()
            }

            override fun onCommandCompleted(
                runtimeGeneration: Long,
                commandId: Long,
                commandType: PlayerRuntimeCommandType,
            ) {
                if (!isCurrentRuntime(runtimeGeneration)) return
                PlayerDebugLogBuffer.append(
                    PlayerDebugLogLevel.DEBUG,
                    TAG,
                    "命令完成 type=$commandType command=$commandId generation=$runtimeGeneration",
                )
                if (
                    commandType == PlayerRuntimeCommandType.CLOSE &&
                        closeCommandId == commandId
                ) {
                    runtimeConnection.disconnect()
                    finalizeClosedSession()
                }
            }

            override fun onCommandFailed(
                runtimeGeneration: Long,
                commandId: Long,
                commandType: PlayerRuntimeCommandType?,
                operation: String,
                message: String,
            ) {
                if (!isCurrentRuntime(runtimeGeneration)) return
                PlayerDebugLogBuffer.append(
                    PlayerDebugLogLevel.ERROR,
                    TAG,
                    "命令失败 type=${commandType ?: "unknown"} command=$commandId " +
                        "operation=$operation message=$message",
                )
                when (commandType) {
                    PlayerRuntimeCommandType.ATTACH_SURFACE ->
                        handleSurfaceAttachFailure(commandId)
                    PlayerRuntimeCommandType.DETACH_SURFACE ->
                        handleSurfaceDetachFailure(commandId)
                    PlayerRuntimeCommandType.SCREENSHOT -> {
                        failScreenshot(commandId, IllegalStateException(message))
                        return
                    }
                    PlayerRuntimeCommandType.THUMBNAIL -> {
                        if (pendingThumbnailCommandId == commandId) {
                            pendingThumbnailCommandId = null
                            _state.value =
                                _state.value.copy(
                                    seekPreview =
                                        _state.value.seekPreview?.copy(loading = false),
                                )
                        }
                        PlayerDebugLogBuffer.append(
                            PlayerDebugLogLevel.WARN,
                            TAG,
                            "进度缩略图提取失败：$message",
                        )
                        return
                    }
                    PlayerRuntimeCommandType.CLOSE -> {
                        runtimeConnection.disconnect()
                        finalizeClosedSession()
                        return
                    }
                    else -> Unit
                }
                setError("$operation：$message", IllegalStateException(message))
            }

            override fun onSurfaceAttached(
                runtimeGeneration: Long,
                commandId: Long,
                surfaceGeneration: Long,
            ) {
                if (!isCurrentRuntime(runtimeGeneration)) return
                val pending =
                    pendingSurfaceLease?.takeIf { surface ->
                        surface.generation == surfaceGeneration &&
                            surface.attachCommandId == commandId
                    } ?: return
                val nextLease =
                    activatePendingPlayerSurface(
                        _state.value.surfaceLease,
                        pending.role,
                        pending.ownerToken,
                        pending.generation,
                    )
                activeSurfaceLease =
                    pending.copy(
                        attachCommandId = null,
                        detachCommandId = null,
                    )
                pendingSurfaceLease = null
                _state.value =
                    _state.value.copy(
                        surfaceLease = nextLease,
                        runtimeState = PlayerRuntimeState.ACTIVE,
                        error = null,
                    )
                PlayerDebugLogBuffer.append(
                    TAG,
                    "远端确认 Surface attach role=${pending.role} generation=${pending.generation}",
                )
                PlayerCrashJournal.record(
                    appContext,
                    PlayerCrashEventType.ATTACH_ACK,
                    runtimeGeneration,
                    pending.generation,
                    commandId,
                )
                if (pending.detachRequested) {
                    requestActiveSurfaceDetach()
                } else {
                    startPendingMediaLoad()
                }
            }

            override fun onSurfaceDetached(
                runtimeGeneration: Long,
                commandId: Long,
                surfaceGeneration: Long,
            ) {
                if (!isCurrentRuntime(runtimeGeneration)) return
                val active =
                    activeSurfaceLease?.takeIf { surface ->
                        surface.generation == surfaceGeneration &&
                            surface.detachCommandId == commandId
                    } ?: return
                activeSurfaceLease = null
                val completed = completePlayerSurfaceDetach(_state.value.surfaceLease)
                val returnToFloating =
                    completed.phase == PlayerSurfaceTransferPhase.WAITING_FLOATING_SURFACE &&
                        completed.transferTarget == PlayerSurfaceRole.FLOATING
                _state.value =
                    _state.value.copy(
                        presentation =
                            if (returnToFloating) {
                                PlayerPresentation.FLOATING_PLAYER
                            } else {
                                _state.value.presentation
                            },
                        surfaceLease = completed,
                        runtimeState = PlayerRuntimeState.READY,
                    )
                PlayerDebugLogBuffer.append(
                    TAG,
                    "远端确认 Surface detach role=${active.role} generation=${active.generation}",
                )
                PlayerCrashJournal.record(
                    appContext,
                    PlayerCrashEventType.DETACH_ACK,
                    runtimeGeneration,
                    active.generation,
                    commandId,
                )
                if (closeRequested) {
                    sendRuntimeClose()
                } else {
                    requestPendingSurfaceAttach()
                }
            }

            override fun onPlaybackSnapshot(
                runtimeGeneration: Long,
                snapshot: PlayerRuntimePlaybackSnapshot,
            ) {
                if (!isCurrentRuntime(runtimeGeneration) || !_state.value.hasMedia) return
                val current = _state.value
                _state.value =
                    current.copy(
                        positionSeconds =
                            snapshot.positionSeconds
                                ?.takeIf(Double::isFinite)
                                ?: current.positionSeconds,
                        durationSeconds =
                            snapshot.durationSeconds
                                ?.takeIf(Double::isFinite)
                                ?: current.durationSeconds,
                        paused = snapshot.paused ?: current.paused,
                        buffering = snapshot.buffering ?: current.buffering,
                        speed =
                            snapshot.speed
                                ?.takeIf { speed -> speed.isFinite() && speed > 0.0 }
                                ?: current.speed,
                        networkSpeedBytesPerSecond =
                            snapshot.networkSpeedBytesPerSecond.coerceAtLeast(0L),
                        fullVideoCacheActive = snapshot.fullVideoCacheActive,
                        fullVideoCacheComplete = snapshot.fullVideoCacheComplete,
                        fullVideoCacheStartSeconds = snapshot.fullVideoCacheStartSeconds,
                        fullVideoCacheEndSeconds = snapshot.fullVideoCacheEndSeconds,
                        fullVideoCachePhase = snapshot.fullVideoCachePhase,
                        fullVideoCacheReason = snapshot.fullVideoCacheReason,
                        fullVideoCacheStateEvidence =
                            snapshot.fullVideoCacheStateEvidence,
                        fullVideoCacheFileBytes = snapshot.fullVideoCacheFileBytes,
                        fullVideoCacheExpectedBytes = snapshot.fullVideoCacheExpectedBytes,
                    )
            }

            override fun onFileLoaded(
                runtimeGeneration: Long,
                loadCommandId: Long,
                tracks: PlayerRuntimeTrackSnapshot,
            ) {
                if (
                    !isCurrentRuntime(runtimeGeneration) ||
                        lastLoadCommandId != loadCommandId
                ) {
                    return
                }
                val audioTracks = tracks.audioTracks.map { track -> track.toPlayerTrack() }
                val subtitleTracks = tracks.subtitleTracks.map { track -> track.toPlayerTrack() }
                val chapters = tracks.chapters.map { chapter -> chapter.toPlayerChapter() }
                _state.value =
                    _state.value.copy(
                        error = null,
                        audioTracks = audioTracks,
                        subtitleTracks = subtitleTracks,
                        selectedAudioTrackId = audioTracks.singleOrNull { it.selected }?.id,
                        selectedSubtitleTrackId =
                            subtitleTracks.singleOrNull { it.selected }?.id,
                        chapters = chapters,
                        mediaContainer = tracks.fileFormat,
                        videoCodec = tracks.videoCodec,
                        audioCodec = tracks.audioCodec,
                        videoTrackCount = tracks.videoTrackCount,
                        activeHardwareDecoder = tracks.activeHardwareDecoder,
                        videoPixelFormat = tracks.videoPixelFormat,
                        videoCodecProfile = tracks.videoCodecProfile,
                    )
                pendingRestartSeekSeconds?.let { position ->
                    pendingRestartSeekSeconds = null
                    if (position > 0.0) seekTo(position)
                }
                PlayerDebugLogBuffer.append(
                    TAG,
                    "媒体文件加载完成 command=$loadCommandId " +
                        "audioTracks=${audioTracks.size} subtitleTracks=${subtitleTracks.size} " +
                        "videoTracks=${tracks.videoTrackCount} " +
                        "container=${tracks.fileFormat ?: "unknown"} " +
                        "videoCodec=${tracks.videoCodec ?: "none"} " +
                        "audioCodec=${tracks.audioCodec ?: "none"} " +
                        "hwdec=${tracks.activeHardwareDecoder ?: "none"} " +
                        "pixelFormat=${tracks.videoPixelFormat ?: "unknown"} " +
                        "codecProfile=${tracks.videoCodecProfile ?: "unknown"} " +
                        "chapters=${chapters.size}",
                )
            }

            override fun onMediaIdentityChanged(
                runtimeGeneration: Long,
                loadCommandId: Long,
                identity: PlayerRuntimeMediaIdentitySnapshot,
            ) {
                if (
                    !isCurrentRuntime(runtimeGeneration) ||
                        lastLoadCommandId != loadCommandId
                ) {
                    return
                }
                _state.value =
                    _state.value.copy(
                        mediaContainer = identity.fileFormat,
                        videoCodec = identity.videoCodec,
                        audioCodec = identity.audioCodec,
                        videoTrackCount = identity.videoTrackCount,
                        activeHardwareDecoder = identity.activeHardwareDecoder,
                        videoPixelFormat = identity.videoPixelFormat,
                        videoCodecProfile = identity.videoCodecProfile,
                    )
                PlayerDebugLogBuffer.append(
                    PlayerDebugLogLevel.DEBUG,
                    TAG,
                    "媒体身份更新 command=$loadCommandId " +
                        "container=${identity.fileFormat ?: "unknown"} " +
                        "videoCodec=${identity.videoCodec ?: "none"} " +
                        "audioCodec=${identity.audioCodec ?: "none"} " +
                        "hwdec=${identity.activeHardwareDecoder ?: "none"} " +
                        "pixelFormat=${identity.videoPixelFormat ?: "unknown"} " +
                        "codecProfile=${identity.videoCodecProfile ?: "unknown"}",
                )
            }

            override fun onSeek(
                runtimeGeneration: Long,
                loadCommandId: Long,
            ) {
                if (
                    !isCurrentRuntime(runtimeGeneration) ||
                        lastLoadCommandId != loadCommandId
                ) {
                    return
                }
                if (
                    !isPlayerUserSeekEvent(
                        pendingUserSeekLoadCommandId = pendingUserSeekLoadCommandId,
                        eventLoadCommandId = loadCommandId,
                    )
                ) {
                    PlayerDebugLogBuffer.append(
                        PlayerDebugLogLevel.DEBUG,
                        TAG,
                        "记录播放器内部 seek loadCommand=$loadCommandId " +
                            "surfacePhase=${_state.value.surfaceLease.phase}",
                    )
                    return
                }
                val current = _state.value
                _state.value =
                    current.copy(
                        seeking =
                            reducePlayerSeeking(
                                currentSeeking = current.seeking,
                                event = PlayerSeekLifecycleEvent.MPV_SEEK,
                            ),
                    )
                PlayerDebugLogBuffer.append(
                    TAG,
                    "媒体跳转开始 loadCommand=$loadCommandId",
                )
            }

            override fun onPlaybackRestart(
                runtimeGeneration: Long,
                loadCommandId: Long,
            ) {
                if (
                    !isCurrentRuntime(runtimeGeneration) ||
                        lastLoadCommandId != loadCommandId
                ) {
                    return
                }
                val current = _state.value
                val completedSeek =
                    current.seeking &&
                        isPlayerUserSeekEvent(
                            pendingUserSeekLoadCommandId = pendingUserSeekLoadCommandId,
                            eventLoadCommandId = loadCommandId,
                        )
                if (completedSeek) {
                    pendingUserSeekLoadCommandId = null
                }
                _state.value =
                    current.copy(
                        loading = false,
                        buffering = false,
                        seeking =
                            reducePlayerSeeking(
                                currentSeeking = current.seeking,
                                event = PlayerSeekLifecycleEvent.MPV_PLAYBACK_RESTART,
                            ),
                        error = null,
                    )
                PlayerDebugLogBuffer.append(
                    TAG,
                    if (completedSeek) {
                        "媒体跳转完成 loadCommand=$loadCommandId"
                    } else {
                        "媒体已恢复输出 loadCommand=$loadCommandId"
                    },
                )
            }

            override fun onNaturalEnd(runtimeGeneration: Long) {
                if (isCurrentRuntime(runtimeGeneration)) {
                    PlayerDebugLogBuffer.append(TAG, "媒体自然播放结束")
                    handleNaturalEndOfFile()
                }
            }

            override fun onRuntimeError(runtimeGeneration: Long, message: String) {
                if (!isCurrentRuntime(runtimeGeneration)) return
                PlayerDebugLogBuffer.append(
                    PlayerDebugLogLevel.ERROR,
                    TAG,
                    "播放器运行时错误：$message",
                )
                setError(message, IllegalStateException(message))
            }

            override fun onDiagnosticLog(
                runtimeGeneration: Long,
                level: PlayerDebugLogLevel,
                tag: String,
                message: String,
            ) {
                if (!isCurrentRuntime(runtimeGeneration)) return
                PlayerDebugLogBuffer.append(level, tag, message)
            }

            override fun onScreenshotCompleted(
                runtimeGeneration: Long,
                commandId: Long,
                path: String,
            ) {
                if (!isCurrentRuntime(runtimeGeneration)) return
                val pending = pendingScreenshots.remove(commandId) ?: return
                if (pending.file.absolutePath != path) {
                    pending.file.delete()
                    pending.onResult(
                        Result.failure(
                            IllegalStateException("播放器截图结果路径不匹配"),
                        ),
                    )
                    return
                }
                completeScreenshot(pending)
            }

            override fun onThumbnailReady(
                runtimeGeneration: Long,
                commandId: Long,
                positionSeconds: Double,
                bitmap: android.graphics.Bitmap?,
            ) {
                if (
                    !isCurrentRuntime(runtimeGeneration) ||
                        pendingThumbnailCommandId != commandId
                ) {
                    return
                }
                pendingThumbnailCommandId = null
                val preview = _state.value.seekPreview ?: return
                _state.value =
                    _state.value.copy(
                        seekPreview =
                            preview.copy(
                                positionSeconds = positionSeconds,
                                bitmap = bitmap,
                                loading = false,
                            ),
                    )
            }

            override fun onRuntimeDisconnected(runtimeGeneration: Long, expected: Boolean) {
                if (!isCurrentRuntime(runtimeGeneration)) return
                PlayerDebugLogBuffer.append(
                    if (expected) PlayerDebugLogLevel.INFO else PlayerDebugLogLevel.ERROR,
                    TAG,
                    "播放器运行时连接断开 expected=$expected generation=$runtimeGeneration",
                )
                if (expected || closeRequested || _state.value.runtimeState == PlayerRuntimeState.CLOSING) {
                    finalizeClosedSession()
                    return
                }
                handleUnexpectedRuntimeStop("播放器运行时连接已中断")
            }

            override fun onRuntimeConnectionError(runtimeGeneration: Long, message: String) {
                if (!isCurrentRuntime(runtimeGeneration)) return
                PlayerDebugLogBuffer.append(
                    PlayerDebugLogLevel.ERROR,
                    TAG,
                    "播放器运行时连接错误：$message",
                )
                runtimeConnection.disconnect()
                handleUnexpectedRuntimeStop(message)
            }
        }

    private val runtimeConnection: PlayerRuntimeConnection by lazy(LazyThreadSafetyMode.NONE) {
        PlayerRuntimeConnection(appContext, runtimeListener)
    }

    init {
        mainScope.launch {
            settingsStore.state.drop(1).collect { settings ->
                val snapshot = _state.value
                if (!snapshot.hasMedia || !snapshot.runtimeState.acceptsCommands()) {
                    return@collect
                }
                try {
                    val anime4KMode =
                        if (settings.rememberAnime4KMode) {
                            settings.anime4KMode
                        } else {
                            snapshot.anime4KMode
                        }
                    val shaderFiles = shaderManager.resolveShaderFiles(anime4KMode)
                    val config = settings.toRuntimeConfig(shaderFiles)
                    if (runtimeConnection.applySettings(config) == null) {
                        setError(
                            "播放器设置命令无法发送",
                            IllegalStateException("Player runtime is unavailable"),
                        )
                        return@collect
                    }
                    _state.value =
                        _state.value.copy(
                            decoderBackend = settings.decoderBackend,
                            renderingProfile = settings.renderingProfile,
                            anime4KMode = anime4KMode,
                            activeShaderFiles = shaderFiles,
                            error = null,
                        )
                } catch (error: Exception) {
                    setError(
                        "播放器设置无法应用：${error.message ?: error.javaClass.simpleName}",
                        error,
                    )
                }
            }
        }
    }

    fun open(request: PlayerMediaRequest, presentation: PlayerPresentation) {
        requireMainThread()
        activeQueue = listOf(request)
        activeQueueIndex = 0
        openInternal(request, presentation, clearDiagnostics = true)
    }

    fun openQueue(
        queue: List<PlayerMediaRequest>,
        currentIndex: Int,
        presentation: PlayerPresentation,
    ) {
        requireMainThread()
        require(queue.isNotEmpty()) { "Player queue cannot be empty" }
        require(currentIndex in queue.indices) { "Player queue index is out of bounds" }
        activeQueue = queue.toList()
        activeQueueIndex = currentIndex
        openInternal(queue[currentIndex], presentation, clearDiagnostics = true)
    }

    private fun openInternal(
        request: PlayerMediaRequest,
        presentation: PlayerPresentation,
        clearDiagnostics: Boolean,
    ) {
        requireMainThread()
        if (_state.value.request?.requestId != request.requestId) {
            activeLongPressSpeedBoost = null
        }
        if (_state.value.runtimeState == PlayerRuntimeState.CLOSING) {
            setError(
                "播放器正在关闭，无法接收新的媒体请求",
                IllegalStateException("Player runtime is closing"),
            )
            return
        }
        val settings = settingsStore.current
        val queueAwareState =
            _state.value.copy(
                queueIndex = activeQueueIndex,
                queueSize = activeQueue.size,
                seekPreview = null,
            )
        val transition =
            resolvePlayerOpenTransition(queueAwareState, request, presentation, settings)
        if (!transition.shouldLoad) {
            _state.value = transition.state
            return
        }

        if (clearDiagnostics) {
            PlayerDebugLogBuffer.clear()
        }
        PlayerCrashJournal.clear(appContext)
        PlayerDebugLogBuffer.append(
            TAG,
            "打开媒体 request=${shortPlayerDiagnosticId(request.requestId)} source=${request.source} " +
                "decoderBackend=${settings.decoderBackend.persistedId} " +
                "renderingProfile=${settings.renderingProfile.persistedId} " +
                "media=${describePlayerMediaUriForDiagnostics(request.uri)} " +
                "headerCount=${request.headers.size}",
        )
        _state.value = transition.state
        if (request.persistPlaybackHistory) {
            mainScope.launch(Dispatchers.IO) {
                historyStore.recordMediaPlayback(
                    uri = request.uri,
                    title = request.title,
                    sourcePageUrl = request.sourcePageUrl.orEmpty(),
                )
            }
        }
        prepareSurfaceLeaseForPresentation(presentation)
        pendingMediaLoad = null
        pendingUserSeekLoadCommandId = null
        pendingThumbnailCommandId = null
        try {
            val shaderFiles = shaderManager.resolveShaderFiles(transition.state.anime4KMode)
            val config = settings.toRuntimeConfig(shaderFiles)
            _state.value = _state.value.copy(activeShaderFiles = shaderFiles)
            pendingMediaLoad =
                PendingMediaLoad(
                    requestId = request.requestId,
                    uri = request.uri,
                    headers = request.headers.toMap(),
                    config = config,
                    initialSpeed = transition.state.speed,
                )
            when (_state.value.runtimeState) {
                PlayerRuntimeState.STOPPED,
                PlayerRuntimeState.DEAD,
                -> connectRuntime(config)
                PlayerRuntimeState.BINDING -> Unit
                PlayerRuntimeState.READY,
                PlayerRuntimeState.ACTIVE,
                -> startPendingMediaLoad()
                PlayerRuntimeState.CLOSING ->
                    error("Player runtime entered closing during open")
            }
        } catch (error: Exception) {
            setError("无法加载视频：${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    fun requestFullscreenFromFloating() {
        requireMainThread()
        val snapshot = _state.value
        check(snapshot.presentation == PlayerPresentation.FLOATING_PLAYER) {
            "Only floating playback can request fullscreen transfer"
        }
        val nextLease =
            when (snapshot.surfaceLease.currentOwner?.role) {
                PlayerSurfaceRole.FLOATING ->
                    beginFloatingToFullscreenTransfer(snapshot.surfaceLease)
                null ->
                    requestFullscreenActivityLaunchIfReady(
                        preparePlayerSurfaceLease(
                            snapshot.surfaceLease,
                            PlayerSurfaceRole.FULLSCREEN,
                        ),
                    )
                PlayerSurfaceRole.FULLSCREEN ->
                    error("Fullscreen Surface already owns the player lease")
            }
        discardSupersededPendingSurface(nextLease)
        _state.value =
            snapshot.copy(
                presentation = PlayerPresentation.FULLSCREEN_PLAYER,
                surfaceLease = nextLease,
            )
        PlayerDebugLogBuffer.append(
            TAG,
            "请求悬浮转全屏 phase=${nextLease.phase}",
        )
    }

    fun requestFullscreenActivityLaunchWhenReady() {
        requireMainThread()
        val snapshot = _state.value
        check(snapshot.presentation == PlayerPresentation.FULLSCREEN_PLAYER) {
            "Fullscreen Activity launch requires fullscreen presentation"
        }
        _state.value =
            snapshot.copy(
                surfaceLease =
                    requestFullscreenActivityLaunchIfReady(snapshot.surfaceLease),
            )
    }

    fun requestExitFullscreen(): Long? {
        requireMainThread()
        val snapshot = _state.value
        if (!snapshot.hasMedia) return null
        if (!shouldReturnFullscreenPlayerToFloating(snapshot, settingsStore.current)) {
            close()
            return null
        }
        snapshot.surfaceLease.fullscreenFinishRequestId?.let { return it }
        val nextLease =
            when (snapshot.surfaceLease.currentOwner?.role) {
                PlayerSurfaceRole.FULLSCREEN ->
                    beginFullscreenToFloatingTransfer(snapshot.surfaceLease)
                null ->
                    requestFullscreenActivityFinish(
                        preparePlayerSurfaceLease(
                            snapshot.surfaceLease,
                            PlayerSurfaceRole.FLOATING,
                        ),
                    )
                PlayerSurfaceRole.FLOATING ->
                    error("Floating Surface already owns the player lease")
            }
        discardSupersededPendingSurface(nextLease)
        _state.value =
            snapshot.copy(
                presentation =
                    if (nextLease.currentOwner == null) {
                        PlayerPresentation.FLOATING_PLAYER
                    } else {
                        snapshot.presentation
                    },
                surfaceLease = nextLease,
            )
        PlayerDebugLogBuffer.append(
            TAG,
            "请求退出全屏 nextPresentation=${_state.value.presentation} phase=${nextLease.phase}",
        )
        return nextLease.fullscreenFinishRequestId
    }

    fun registerSurfaceOwner(role: PlayerSurfaceRole, ownerToken: String): Long? {
        requireMainThread()
        if (!_state.value.hasMedia) return null
        val registration =
            registerPlayerSurfaceOwner(_state.value.surfaceLease, role, ownerToken)
        val generation = registration.generation
        if (generation == null) {
            PlayerDebugLogBuffer.append(
                TAG,
                "拒绝过期 Surface 登记 role=$role phase=${registration.state.phase}",
            )
            return null
        }
        discardSupersededPendingSurface(registration.state)
        _state.value = _state.value.copy(surfaceLease = registration.state)
        PlayerDebugLogBuffer.append(
            TAG,
            "登记 Surface role=$role generation=$generation phase=${registration.state.phase}",
        )
        return generation
    }

    fun attachSurface(
        role: PlayerSurfaceRole,
        ownerToken: String,
        generation: Long,
        surface: Surface,
        width: Int,
        height: Int,
    ) {
        requireMainThread()
        if (!_state.value.hasMedia || !surface.isValid) return
        val lease = _state.value.surfaceLease
        val active = activeSurfaceLease
        if (
            isCurrentPlayerSurfaceOwner(lease, role, ownerToken, generation) &&
                active?.surface === surface
        ) {
            runtimeConnection.updateSurface(generation, width, height)
            return
        }
        if (!isPendingPlayerSurfaceTarget(lease, role, ownerToken, generation)) {
            PlayerDebugLogBuffer.append(
                TAG,
                "忽略过期 Surface attach role=$role generation=$generation phase=${lease.phase}",
            )
            return
        }
        pendingSurfaceLease =
            SurfaceParcel(
                role = role,
                ownerToken = ownerToken,
                generation = generation,
                surface = surface,
                width = width,
                height = height,
            )
        if (lease.currentOwner != null || !lease.nativeDetachCompleted) {
            PlayerDebugLogBuffer.append(
                TAG,
                "Surface 等待旧租约释放 role=$role generation=$generation phase=${lease.phase}",
            )
            return
        }
        requestPendingSurfaceAttach()
    }

    fun updateSurface(
        role: PlayerSurfaceRole,
        ownerToken: String,
        generation: Long,
        surface: Surface,
        width: Int,
        height: Int,
    ) {
        requireMainThread()
        val active = activeSurfaceLease
        if (
            isCurrentPlayerSurfaceOwner(
                _state.value.surfaceLease,
                role,
                ownerToken,
                generation,
            ) &&
                active?.surface === surface
        ) {
            runtimeConnection.updateSurface(generation, width, height)
            return
        }
        pendingSurfaceLease
            ?.takeIf { pending ->
                pending.role == role &&
                    pending.ownerToken == ownerToken &&
                    pending.generation == generation &&
                    pending.surface === surface
            }
            ?.let { pending ->
                pendingSurfaceLease = pending.copy(width = width, height = height)
            }
    }

    fun detachSurface(
        role: PlayerSurfaceRole,
        ownerToken: String,
        generation: Long,
        surface: Surface?,
    ) {
        requireMainThread()
        val pending = pendingSurfaceLease
        if (
            pending != null &&
                pending.role == role &&
                pending.ownerToken == ownerToken &&
                pending.generation == generation &&
                (surface == null || pending.surface === surface)
        ) {
            if (_state.value.surfaceLease.nativeState == PlayerNativeSurfaceState.ATTACHING) {
                pendingSurfaceLease = pending.copy(detachRequested = true)
            } else {
                pendingSurfaceLease = null
                val lease = _state.value.surfaceLease
                if (isPendingPlayerSurfaceTarget(lease, role, ownerToken, generation)) {
                    _state.value =
                        _state.value.copy(
                            surfaceLease = lease.copy(pendingTarget = null),
                        )
                }
            }
            return
        }
        val active = activeSurfaceLease
        if (
            !isCurrentPlayerSurfaceOwner(
                _state.value.surfaceLease,
                role,
                ownerToken,
                generation,
            ) ||
                active == null ||
                (surface != null && active.surface !== surface)
        ) {
            PlayerDebugLogBuffer.append(
                TAG,
                "忽略过期 Surface destroy role=$role generation=$generation",
            )
            return
        }
        requestActiveSurfaceDetach()
    }

    fun togglePause() {
        requireMainThread()
        if (_state.value.hasMedia) setPaused(!_state.value.paused)
    }

    fun setPaused(paused: Boolean) {
        requireMainThread()
        if (!_state.value.hasMedia || !_state.value.runtimeState.acceptsCommands()) return
        if (runtimeConnection.setPaused(paused) != null) {
            _state.value = _state.value.copy(paused = paused)
            PlayerDebugLogBuffer.append(
                PlayerDebugLogLevel.DEBUG,
                TAG,
                "发送暂停命令 paused=$paused",
            )
        }
    }

    fun seekTo(positionSeconds: Double) {
        requireMainThread()
        if (!_state.value.hasMedia || !_state.value.runtimeState.acceptsCommands()) return
        val duration = _state.value.durationSeconds
        val target =
            if (duration > 0.0) {
                positionSeconds.coerceIn(0.0, duration)
            } else {
                positionSeconds.coerceAtLeast(0.0)
        }
        if (runtimeConnection.seekTo(target, settingsStore.current.preciseSeeking) != null) {
            pendingUserSeekLoadCommandId = lastLoadCommandId
            PlayerDebugLogBuffer.append(
                PlayerDebugLogLevel.DEBUG,
                TAG,
                "发送跳转命令 position=$target precise=${settingsStore.current.preciseSeeking}",
            )
        }
    }

    fun seekBackward() {
        seekTo(_state.value.positionSeconds - settingsStore.current.seekStepSeconds)
    }

    fun seekForward() {
        seekTo(_state.value.positionSeconds + settingsStore.current.seekStepSeconds)
    }

    fun seekBy(seconds: Int) {
        requireMainThread()
        seekTo(_state.value.positionSeconds + seconds)
    }

    fun setSpeed(speed: Double) {
        requireMainThread()
        require(isSupportedPlayerSpeed(speed)) { "Unsupported player speed: $speed" }
        if (!_state.value.hasMedia || !_state.value.runtimeState.acceptsCommands()) return
        activeLongPressSpeedBoost = null
        if (runtimeConnection.setSpeed(speed) != null) {
            _state.value = _state.value.copy(speed = speed)
            PlayerDebugLogBuffer.append(
                PlayerDebugLogLevel.DEBUG,
                TAG,
                "发送倍速命令 speed=$speed",
            )
            if (settingsStore.current.rememberPlaybackSpeed) {
                settingsStore.setLastPlaybackSpeed(speed)
            }
        }
    }

    fun beginLongPressSpeedBoost(): LongPressSpeedBoostResult? {
        requireMainThread()
        activeLongPressSpeedBoost?.let { active ->
            return LongPressSpeedBoostResult(
                originalSpeed = active.originalSpeed,
                boostedSpeed = active.boostedSpeed,
            )
        }
        val snapshot = _state.value
        if (!snapshot.hasMedia || !snapshot.runtimeState.acceptsCommands()) return null
        val boostedSpeed = resolveLongPressPlayerSpeed(snapshot.speed) ?: return null
        if (runtimeConnection.setSpeed(boostedSpeed) == null) return null
        activeLongPressSpeedBoost =
            ActiveLongPressSpeedBoost(
                requestId = requireNotNull(snapshot.request).requestId,
                loadGeneration = snapshot.loadGeneration,
                runtimeGeneration = snapshot.runtimeGeneration,
                originalSpeed = snapshot.speed,
                boostedSpeed = boostedSpeed,
            )
        _state.value = snapshot.copy(speed = boostedSpeed)
        PlayerDebugLogBuffer.append(
            PlayerDebugLogLevel.DEBUG,
            TAG,
            "开始长按加速 original=${snapshot.speed} boosted=$boostedSpeed",
        )
        return LongPressSpeedBoostResult(
            originalSpeed = snapshot.speed,
            boostedSpeed = boostedSpeed,
        )
    }

    fun endLongPressSpeedBoost() {
        requireMainThread()
        val active = activeLongPressSpeedBoost ?: return
        activeLongPressSpeedBoost = null
        val snapshot = _state.value
        if (
            snapshot.request?.requestId != active.requestId ||
                snapshot.loadGeneration != active.loadGeneration ||
                snapshot.runtimeGeneration != active.runtimeGeneration ||
                !snapshot.runtimeState.acceptsCommands()
        ) {
            return
        }
        if (runtimeConnection.setSpeed(active.originalSpeed) == null) {
            setError(
                "无法恢复长按加速前的播放速度",
                IllegalStateException("Player runtime is unavailable"),
            )
            return
        }
        _state.value = snapshot.copy(speed = active.originalSpeed)
        PlayerDebugLogBuffer.append(
            PlayerDebugLogLevel.DEBUG,
            TAG,
            "结束长按加速 restore=${active.originalSpeed}",
        )
    }

    fun setAudioTrack(trackId: Int) {
        requireMainThread()
        val snapshot = _state.value
        if (!snapshot.hasMedia || !snapshot.runtimeState.acceptsCommands()) return
        if (runtimeConnection.setAudioTrack(trackId) != null) {
            PlayerDebugLogBuffer.append(
                PlayerDebugLogLevel.DEBUG,
                TAG,
                "发送音轨切换命令 track=$trackId",
            )
            _state.value =
                snapshot.copy(
                    selectedAudioTrackId = trackId,
                    audioTracks =
                        snapshot.audioTracks.map { track ->
                            track.copy(selected = track.id == trackId)
                        },
                )
        }
    }

    fun setSubtitleTrack(trackId: Int?) {
        requireMainThread()
        val snapshot = _state.value
        if (!snapshot.hasMedia || !snapshot.runtimeState.acceptsCommands()) return
        if (runtimeConnection.setSubtitleTrack(trackId) != null) {
            PlayerDebugLogBuffer.append(
                PlayerDebugLogLevel.DEBUG,
                TAG,
                "发送字幕切换命令 track=${trackId ?: "off"}",
            )
            _state.value =
                snapshot.copy(
                    selectedSubtitleTrackId = trackId,
                    subtitleTracks =
                        snapshot.subtitleTracks.map { track ->
                            track.copy(selected = track.id == trackId)
                        },
                )
        }
    }

    fun setVideoFitMode(mode: PlayerVideoFitMode) {
        requireMainThread()
        if (!_state.value.hasMedia || !_state.value.runtimeState.acceptsCommands()) return
        if (runtimeConnection.applyVideoFitMode(mode.toRuntimeId()) != null) {
            _state.value = _state.value.copy(videoFitMode = mode)
            PlayerDebugLogBuffer.append(
                PlayerDebugLogLevel.DEBUG,
                TAG,
                "发送画面模式命令 mode=$mode",
            )
        }
    }

    fun cycleAudioTrack() {
        requireMainThread()
        val snapshot = _state.value
        if (snapshot.audioTracks.isEmpty()) return
        val currentIndex =
            snapshot.audioTracks.indexOfFirst { track ->
                track.id == snapshot.selectedAudioTrackId
            }
        setAudioTrack(snapshot.audioTracks[(currentIndex + 1).mod(snapshot.audioTracks.size)].id)
    }

    fun cycleSubtitleTrack() {
        requireMainThread()
        val snapshot = _state.value
        if (snapshot.subtitleTracks.isEmpty()) return
        val currentIndex =
            snapshot.subtitleTracks.indexOfFirst { track ->
                track.id == snapshot.selectedSubtitleTrackId
            }
        if (
            snapshot.selectedSubtitleTrackId != null &&
                currentIndex == snapshot.subtitleTracks.lastIndex
        ) {
            setSubtitleTrack(null)
            return
        }
        val next = snapshot.subtitleTracks[(currentIndex + 1).coerceAtLeast(0)]
        setSubtitleTrack(next.id)
    }

    fun cycleVideoFitMode() {
        requireMainThread()
        setVideoFitMode(_state.value.videoFitMode.next())
    }

    fun cycleAnime4KMode() {
        requireMainThread()
        val current = _state.value.anime4KMode
        val next = Anime4KMode.entries[(current.ordinal + 1) % Anime4KMode.entries.size]
        setAnime4KMode(next)
    }

    fun setAnime4KMode(mode: Anime4KMode) {
        requireMainThread()
        if (settingsStore.current.rememberAnime4KMode) {
            settingsStore.setAnime4KMode(mode)
            return
        }
        if (!_state.value.runtimeState.acceptsCommands()) return
        try {
            val shaderFiles = shaderManager.resolveShaderFiles(mode)
            val config = settingsStore.current.toRuntimeConfig(shaderFiles)
            if (runtimeConnection.applySettings(config) != null) {
                PlayerDebugLogBuffer.append(
                    PlayerDebugLogLevel.DEBUG,
                    TAG,
                    "发送 Anime4K 设置命令 mode=$mode shaderCount=${shaderFiles.size}",
                )
                _state.value =
                    _state.value.copy(
                        anime4KMode = mode,
                        activeShaderFiles = shaderFiles,
                    )
            }
        } catch (error: Exception) {
            setError(
                "无法切换 Anime4K 模式：${error.message ?: error.javaClass.simpleName}",
                error,
            )
        }
    }

    fun replaceQueueForCurrent(
        requestId: String,
        queue: List<PlayerMediaRequest>,
    ) {
        requireMainThread()
        val currentRequest = _state.value.request ?: return
        if (currentRequest.requestId != requestId || queue.isEmpty()) return
        val currentIndex =
            queue.indexOfFirst { candidate ->
                candidate.requestId == requestId || candidate.uri == currentRequest.uri
            }
        if (currentIndex < 0) return
        activeQueue = queue.toList()
        activeQueueIndex = currentIndex
        _state.value =
            _state.value.copy(
                queueIndex = currentIndex,
                queueSize = activeQueue.size,
            )
        PlayerDebugLogBuffer.append(
            TAG,
            "更新播放队列 index=$currentIndex size=${activeQueue.size}",
        )
    }

    fun playPrevious(): Boolean {
        requireMainThread()
        if (activeQueue.isEmpty() || activeQueueIndex <= 0) return false
        openQueueItem(activeQueueIndex - 1)
        return true
    }

    fun playNext(): Boolean {
        requireMainThread()
        if (activeQueue.isEmpty() || activeQueueIndex >= activeQueue.lastIndex) return false
        openQueueItem(activeQueueIndex + 1)
        return true
    }

    fun requestSeekPreview(positionSeconds: Double) {
        requireMainThread()
        val snapshot = _state.value
        val loadCommandId = lastLoadCommandId ?: return
        val requestUri = snapshot.request?.uri.orEmpty()
        if (
            !settingsStore.current.seekbarThumbnailEnabled ||
                !snapshot.hasMedia ||
                !snapshot.runtimeState.acceptsCommands() ||
                snapshot.durationSeconds <= 0.0 ||
                requestUri.startsWith("http://", ignoreCase = true) ||
                requestUri.startsWith("https://", ignoreCase = true)
        ) {
            return
        }
        val target = positionSeconds.coerceIn(0.0, snapshot.durationSeconds)
        val previous = snapshot.seekPreview
        if (
            previous != null &&
                kotlin.math.abs(previous.positionSeconds - target) < 0.25 &&
                (previous.loading || previous.bitmap != null)
        ) {
            return
        }
        _state.value =
            snapshot.copy(
                seekPreview =
                    PlayerSeekPreview(
                        positionSeconds = target,
                        bitmap = previous?.bitmap,
                        loading = true,
                    ),
            )
        val commandId =
            runtimeConnection.requestThumbnail(
                loadCommandId = loadCommandId,
                positionSeconds = target,
                maxSize = SEEK_PREVIEW_MAX_SIZE,
            )
        if (commandId == null) {
            _state.value =
                _state.value.copy(
                    seekPreview = _state.value.seekPreview?.copy(loading = false),
                )
            return
        }
        pendingThumbnailCommandId = commandId
    }

    fun clearSeekPreview() {
        requireMainThread()
        pendingThumbnailCommandId = null
        if (_state.value.seekPreview != null) {
            _state.value = _state.value.copy(seekPreview = null)
        }
    }

    fun captureScreenshot(onResult: (Result<String>) -> Unit) {
        requireMainThread()
        if (!_state.value.hasMedia || !_state.value.runtimeState.acceptsCommands()) {
            onResult(Result.failure(IllegalStateException("当前没有可截图的视频")))
            return
        }
        val temporary =
            File(
                appContext.cacheDir,
                "player-screenshot-${System.currentTimeMillis()}.png",
            )
        val commandId = runtimeConnection.captureScreenshot(temporary.absolutePath)
        if (commandId == null) {
            onResult(Result.failure(IllegalStateException("播放器运行时无法接收截图命令")))
            return
        }
        pendingScreenshots[commandId] = PendingScreenshot(temporary, onResult)
    }

    fun onHostBackgrounded() {
        requireMainThread()
        endLongPressSpeedBoost()
        if (
            _state.value.hasMedia &&
                settingsStore.current.backgroundBehavior == PlayerBackgroundBehavior.PAUSE
        ) {
            setPaused(true)
        }
    }

    fun close() {
        requireMainThread()
        val snapshot = _state.value
        if (
            snapshot.runtimeState == PlayerRuntimeState.STOPPED &&
                !snapshot.hasMedia
        ) {
            return
        }
        closeRequested = true
        activeLongPressSpeedBoost = null
        closeCommandId = null
        pendingMediaLoad = null
        lastLoadCommandId = null
        pendingUserSeekLoadCommandId = null
        pendingThumbnailCommandId = null
        failAllScreenshots(IllegalStateException("播放器已关闭"))
        val previousRuntimeState = snapshot.runtimeState
        PlayerDebugLogBuffer.append(
            TAG,
            "请求关闭播放器 runtime=$previousRuntimeState presentation=${snapshot.presentation}",
        )
        PlayerCrashJournal.record(
            appContext,
            PlayerCrashEventType.RUNTIME_CLOSE,
            snapshot.runtimeGeneration,
        )
        val closingLease = beginClosingPlayerSurfaceLease(snapshot.surfaceLease)
        _state.value =
            snapshot.copy(
                surfaceLease = closingLease,
                runtimeState = PlayerRuntimeState.CLOSING,
                loading = false,
                buffering = false,
                seeking = false,
            )
        if (
            previousRuntimeState == PlayerRuntimeState.STOPPED ||
                previousRuntimeState == PlayerRuntimeState.BINDING ||
                previousRuntimeState == PlayerRuntimeState.DEAD
        ) {
            runtimeConnection.disconnect()
            finalizeClosedSession()
            return
        }
        when (_state.value.surfaceLease.nativeState) {
            PlayerNativeSurfaceState.DETACHED -> sendRuntimeClose()
            PlayerNativeSurfaceState.ATTACHING -> {
                pendingSurfaceLease =
                    pendingSurfaceLease?.copy(detachRequested = true)
            }
            PlayerNativeSurfaceState.ATTACHED -> requestActiveSurfaceDetach()
            PlayerNativeSurfaceState.DETACHING -> Unit
        }
    }

    private fun connectRuntime(config: PlayerRuntimeConfig) {
        val generation = _state.value.runtimeGeneration + 1L
        _state.value =
            _state.value.copy(
                runtimeGeneration = generation,
                runtimeState = PlayerRuntimeState.BINDING,
                error = null,
            )
        PlayerDebugLogBuffer.append(TAG, "绑定播放器运行时 generation=$generation")
        PlayerCrashJournal.record(
            appContext,
            PlayerCrashEventType.RUNTIME_BINDING,
            generation,
        )
        runtimeConnection.connect(generation, config)
    }

    private fun requestPendingSurfaceAttach() {
        val pending = pendingSurfaceLease ?: return
        val snapshot = _state.value
        if (
            !snapshot.runtimeState.acceptsCommands() ||
                pending.attachCommandId != null ||
                snapshot.surfaceLease.currentOwner != null ||
                snapshot.surfaceLease.nativeState != PlayerNativeSurfaceState.DETACHED
        ) {
            return
        }
        if (
            !isPendingPlayerSurfaceTarget(
                snapshot.surfaceLease,
                pending.role,
                pending.ownerToken,
                pending.generation,
            )
        ) {
            return
        }
        val attachingLease =
            beginPendingPlayerSurfaceAttach(
                snapshot.surfaceLease,
                pending.role,
                pending.ownerToken,
                pending.generation,
            )
        _state.value = snapshot.copy(surfaceLease = attachingLease)
        val commandId =
            runtimeConnection.attachSurface(
                surfaceGeneration = pending.generation,
                surface = pending.surface,
                width = pending.width,
                height = pending.height,
            )
        if (commandId == null) {
            _state.value =
                _state.value.copy(
                    surfaceLease =
                        rejectPendingPlayerSurfaceAttach(
                            _state.value.surfaceLease,
                            pending.role,
                            pending.ownerToken,
                            pending.generation,
                        ),
                )
            setError(
                "无法发送播放画面连接命令",
                IllegalStateException("Player runtime is unavailable"),
            )
            return
        }
        pendingSurfaceLease = pending.copy(attachCommandId = commandId)
        PlayerCrashJournal.record(
            appContext,
            PlayerCrashEventType.ATTACH_SENT,
            _state.value.runtimeGeneration,
            pending.generation,
            commandId,
        )
        PlayerDebugLogBuffer.append(
            TAG,
            "发送 Surface attach role=${pending.role} generation=${pending.generation}",
        )
    }

    private fun requestActiveSurfaceDetach() {
        val active = activeSurfaceLease ?: return
        val snapshot = _state.value
        if (snapshot.surfaceLease.nativeState != PlayerNativeSurfaceState.ATTACHED) return
        val detachingLease =
            beginPlayerSurfaceDetach(
                snapshot.surfaceLease,
                active.role,
                active.ownerToken,
                active.generation,
            )
        _state.value = snapshot.copy(surfaceLease = detachingLease)
        val commandId = runtimeConnection.detachSurface(active.generation)
        if (commandId == null) {
            _state.value =
                _state.value.copy(
                    surfaceLease = rejectPlayerSurfaceDetach(_state.value.surfaceLease),
                )
            setError(
                "无法发送播放画面断开命令",
                IllegalStateException("Player runtime is unavailable"),
            )
            return
        }
        activeSurfaceLease = active.copy(detachCommandId = commandId)
        PlayerCrashJournal.record(
            appContext,
            PlayerCrashEventType.DETACH_SENT,
            _state.value.runtimeGeneration,
            active.generation,
            commandId,
        )
        PlayerDebugLogBuffer.append(
            TAG,
            "发送 Surface detach role=${active.role} generation=${active.generation}",
        )
    }

    private fun startPendingMediaLoad() {
        val pending = pendingMediaLoad ?: return
        val snapshot = _state.value
        if (
            snapshot.runtimeState != PlayerRuntimeState.ACTIVE ||
                snapshot.surfaceLease.nativeState != PlayerNativeSurfaceState.ATTACHED ||
                activeSurfaceLease == null
        ) {
            return
        }
        check(snapshot.request?.requestId == pending.requestId) {
            "Pending player request does not match the active session"
        }
        val commandId =
            runtimeConnection.load(
                PlayerRuntimeLoadRequest(
                    requestId = pending.requestId,
                    uri = pending.uri,
                    headers = pending.headers,
                    config = pending.config,
                    initialSpeed = pending.initialSpeed,
                ),
            )
        if (commandId == null) {
            pendingMediaLoad = null
            setError(
                "无法发送媒体加载命令",
                IllegalStateException("Player runtime is unavailable"),
            )
            return
        }
        lastLoadCommandId = commandId
        pendingMediaLoad = null
        PlayerCrashJournal.record(
            appContext,
            PlayerCrashEventType.LOAD_SENT,
            snapshot.runtimeGeneration,
            activeSurfaceLease?.generation,
            commandId,
        )
        PlayerDebugLogBuffer.append(
            TAG,
            "Surface attach 已确认，发送媒体加载 request=${pending.requestId}",
        )
    }

    private fun handleSurfaceAttachFailure(commandId: Long) {
        val pending =
            pendingSurfaceLease?.takeIf { surface -> surface.attachCommandId == commandId }
                ?: return
        if (_state.value.surfaceLease.nativeState == PlayerNativeSurfaceState.ATTACHING) {
            _state.value =
                _state.value.copy(
                    surfaceLease =
                        rejectPendingPlayerSurfaceAttach(
                            _state.value.surfaceLease,
                            pending.role,
                            pending.ownerToken,
                            pending.generation,
                        ),
                )
        }
        pendingSurfaceLease =
            if (pending.detachRequested) {
                val lease = _state.value.surfaceLease
                if (
                    isPendingPlayerSurfaceTarget(
                        lease,
                        pending.role,
                        pending.ownerToken,
                        pending.generation,
                    )
                ) {
                    _state.value =
                        _state.value.copy(surfaceLease = lease.copy(pendingTarget = null))
                }
                null
            } else {
                pending.copy(attachCommandId = null)
            }
        if (closeRequested) {
            sendRuntimeClose()
        }
    }

    private fun handleSurfaceDetachFailure(commandId: Long) {
        val active =
            activeSurfaceLease?.takeIf { surface -> surface.detachCommandId == commandId }
                ?: return
        if (_state.value.surfaceLease.nativeState == PlayerNativeSurfaceState.DETACHING) {
            _state.value =
                _state.value.copy(
                    surfaceLease = rejectPlayerSurfaceDetach(_state.value.surfaceLease),
                )
        }
        activeSurfaceLease = active.copy(detachCommandId = null)
        if (closeRequested) {
            sendRuntimeCloseCommand()
        }
    }

    private fun sendRuntimeClose() {
        if (closeCommandId != null) return
        if (_state.value.surfaceLease.nativeState != PlayerNativeSurfaceState.DETACHED) return
        sendRuntimeCloseCommand()
    }

    private fun sendRuntimeCloseCommand() {
        if (closeCommandId != null) return
        val commandId = runtimeConnection.close()
        if (commandId == null) {
            PlayerDebugLogBuffer.append(
                PlayerDebugLogLevel.ERROR,
                TAG,
                "无法发送关闭播放器运行时命令",
            )
            runtimeConnection.disconnect()
            finalizeClosedSession()
            return
        }
        closeCommandId = commandId
        PlayerDebugLogBuffer.append(
            PlayerDebugLogLevel.DEBUG,
            TAG,
            "发送关闭播放器运行时命令 command=$commandId",
        )
    }

    private fun finalizeClosedSession() {
        val snapshot = _state.value
        val resetLease =
            resetPlayerSurfaceLeaseAfterRuntimeStop(
                beginClosingPlayerSurfaceLease(snapshot.surfaceLease),
            )
        val closedLease = completeClosingPlayerSurfaceLease(resetLease)
        pendingSurfaceLease = null
        activeSurfaceLease = null
        pendingMediaLoad = null
        lastLoadCommandId = null
        pendingUserSeekLoadCommandId = null
        pendingThumbnailCommandId = null
        activeLongPressSpeedBoost = null
        closeCommandId = null
        closeRequested = false
        activeQueue = emptyList()
        activeQueueIndex = 0
        _state.value =
            PlayerSessionState(
                loadGeneration = snapshot.loadGeneration,
                runtimeGeneration = snapshot.runtimeGeneration,
                runtimeState = PlayerRuntimeState.STOPPED,
                runtimeProcessId = null,
                surfaceLease = closedLease,
            )
    }

    private fun handleUnexpectedRuntimeStop(message: String) {
        val snapshot = _state.value
        val processId = snapshot.runtimeProcessId
        snapshot.request?.let { request ->
            failedSession =
                FailedPlayerSession(
                    request = request,
                    presentation = snapshot.presentation,
                    positionSeconds = snapshot.positionSeconds,
                    speed = snapshot.speed,
                    runtimeGeneration = snapshot.runtimeGeneration,
                )
        }
        PlayerCrashJournal.record(
            appContext,
            PlayerCrashEventType.RUNTIME_DEATH,
            snapshot.runtimeGeneration,
            snapshot.surfaceLease.currentOwner?.generation,
        )
        if (processId != null) {
            PlayerCrashCoordinator.onRuntimeDeath(
                context = appContext,
                runtimeGeneration = snapshot.runtimeGeneration,
                processId = processId,
            )
        }
        failAllScreenshots(IllegalStateException(message))
        pendingMediaLoad = null
        pendingThumbnailCommandId = null
        activeLongPressSpeedBoost = null
        pendingSurfaceLease = null
        activeSurfaceLease = null
        closeCommandId = null
        closeRequested = false
        var deadLease = resetPlayerSurfaceLeaseAfterRuntimeStop(snapshot.surfaceLease)
        if (snapshot.presentation == PlayerPresentation.FULLSCREEN_PLAYER) {
            deadLease = requestFullscreenActivityFinish(deadLease)
        }
        _state.value =
            snapshot.copy(
                presentation = PlayerPresentation.BROWSER_ONLY,
                surfaceLease = deadLease,
                runtimeState = PlayerRuntimeState.DEAD,
                loading = false,
                buffering = false,
                seeking = false,
                paused = true,
                error = message,
            )
        pendingUserSeekLoadCommandId = null
        PlayerDebugLogBuffer.append(PlayerDebugLogLevel.ERROR, TAG, message)
    }

    fun restartAfterCrash(runtimeGeneration: Long): PlayerPresentation? {
        requireMainThread()
        val failed =
            failedSession?.takeIf { session ->
                session.runtimeGeneration == runtimeGeneration &&
                    _state.value.runtimeState == PlayerRuntimeState.DEAD
            } ?: return null
        failedSession = null
        val snapshot = _state.value
        _state.value =
            PlayerSessionState(
                loadGeneration = snapshot.loadGeneration,
                runtimeGeneration = snapshot.runtimeGeneration,
                surfaceLease =
                    PlayerSurfaceLeaseState(
                        generation = snapshot.surfaceLease.generation,
                        activityRequestGeneration =
                            snapshot.surfaceLease.activityRequestGeneration,
                    ),
            )
        open(failed.request, failed.presentation)
        pendingMediaLoad =
            pendingMediaLoad?.copy(initialSpeed = failed.speed)
        pendingRestartSeekSeconds = failed.positionSeconds
        _state.value = _state.value.copy(speed = failed.speed)
        return failed.presentation
    }

    private fun completeScreenshot(pending: PendingScreenshot) {
        mainScope.launch(Dispatchers.IO) {
            val generated =
                repeatUntilNotNull(SCREENSHOT_WAIT_ATTEMPTS, SCREENSHOT_WAIT_INTERVAL_MS) {
                    pending.file.takeIf { file -> file.isFile && file.length() > 0L }
                }
            val result =
                runCatching {
                    val file = requireNotNull(generated) { "mpv did not produce a screenshot" }
                    saveScreenshot(file)
                }
            pending.file.delete()
            withContext(Dispatchers.Main.immediate) { pending.onResult(result) }
        }
    }

    private fun failScreenshot(commandId: Long, error: Throwable) {
        val pending = pendingScreenshots.remove(commandId) ?: return
        pending.file.delete()
        pending.onResult(Result.failure(error))
    }

    private fun failAllScreenshots(error: Throwable) {
        val pending = pendingScreenshots.values.toList()
        pendingScreenshots.clear()
        pending.forEach { screenshot ->
            screenshot.file.delete()
            screenshot.onResult(Result.failure(error))
        }
    }

    private suspend fun <T> repeatUntilNotNull(
        attempts: Int,
        intervalMs: Long,
        block: () -> T?,
    ): T? {
        repeat(attempts) {
            block()?.let { value -> return value }
            delay(intervalMs)
        }
        return null
    }

    private fun saveScreenshot(source: File): String {
        val displayName = "Kiyori_${System.currentTimeMillis()}.png"
        val playerSettings = settingsStore.current
        val browserSettings = BrowserDownloadSettingsStore.getInstance(appContext).current
        val destination =
            resolvePlayerScreenshotDestination(
                playerDirectoryUri = playerSettings.screenshotDirectoryUri,
                browserUsesSystemDownloader =
                    browserSettings.defaultEngine == BrowserDownloadEngine.SYSTEM,
                browserDirectoryUri = browserSettings.customDirectoryUri,
                browserAutoTransferToPublicDirectory =
                    browserSettings.autoTransferToPublicDirectory,
            )
        return when (destination.kind) {
            PlayerScreenshotDestinationKind.DOCUMENT_TREE ->
                saveScreenshotToDocumentTree(
                    source = source,
                    treeUri = destination.treeUri,
                    displayName = displayName,
                )
            PlayerScreenshotDestinationKind.PUBLIC_DOWNLOAD_DIRECTORY ->
                saveScreenshotToFileDirectory(
                    source = source,
                    directory = browserDownloadPublicDirectory(),
                    displayName = displayName,
                    scanMedia = true,
                )
            PlayerScreenshotDestinationKind.APPLICATION_DOWNLOAD_DIRECTORY ->
                saveScreenshotToFileDirectory(
                    source = source,
                    directory = browserDownloadApplicationDirectory(appContext),
                    displayName = displayName,
                    scanMedia = false,
                )
        }
    }

    private fun saveScreenshotToDocumentTree(
        source: File,
        treeUri: String,
        displayName: String,
    ): String {
        val tree =
            requireNotNull(DocumentFile.fromTreeUri(appContext, treeUri.toUri())) {
                "Unable to open screenshot document tree"
            }
        require(tree.isDirectory && tree.canWrite()) {
            "Screenshot document tree is not writable"
        }
        val uniqueName = resolveUniqueScreenshotName(displayName) { name -> tree.findFile(name) != null }
        val document =
            requireNotNull(tree.createFile("image/png", uniqueName)) {
                "Unable to create screenshot document"
            }
        try {
            appContext.contentResolver.openOutputStream(document.uri, "w").use { output ->
                requireNotNull(output) { "Unable to open screenshot document output" }
                source.inputStream().use { input -> input.copyTo(output) }
            }
        } catch (error: Throwable) {
            runCatching { document.delete() }
            throw error
        }
        return document.name ?: uniqueName
    }

    private fun saveScreenshotToFileDirectory(
        source: File,
        directory: File,
        displayName: String,
        scanMedia: Boolean,
    ): String {
        require(directory.isDirectory || directory.mkdirs()) {
            "Unable to create screenshot directory: ${directory.absolutePath}"
        }
        val uniqueName = resolveUniqueScreenshotName(displayName) { name -> File(directory, name).exists() }
        val destination = File(directory, uniqueName)
        source.copyTo(destination, overwrite = false)
        if (scanMedia) {
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(destination.absolutePath),
                arrayOf("image/png"),
                null,
            )
        }
        return destination.name
    }

    private fun resolveUniqueScreenshotName(
        requestedName: String,
        exists: (String) -> Boolean,
    ): String {
        val dotIndex = requestedName.lastIndexOf('.')
        val baseName =
            if (dotIndex > 0) {
                requestedName.substring(0, dotIndex)
            } else {
                requestedName
            }
        val extension =
            if (dotIndex > 0) {
                requestedName.substring(dotIndex)
            } else {
                ""
            }
        var candidate = requestedName
        var index = 1
        while (exists(candidate)) {
            candidate = "$baseName ($index)$extension"
            index += 1
        }
        return candidate
    }

    private fun setError(message: String, error: Throwable) {
        AppLogger.e(TAG, message, error)
        PlayerDebugLogBuffer.append(
            PlayerDebugLogLevel.ERROR,
            TAG,
            "$message cause=${error.javaClass.simpleName}: ${error.message.orEmpty()}",
        )
        _state.value =
            _state.value.copy(
                loading = false,
                buffering = false,
                seeking = false,
                error = message,
            )
        pendingUserSeekLoadCommandId = null
    }

    private fun handleNaturalEndOfFile() {
        val snapshot = _state.value
        if (!snapshot.hasMedia) return
        val settings = settingsStore.current
        if (settings.autoPlayNext && snapshot.hasNextQueueItem && playNext()) {
            return
        }
        when (settings.queueEndBehavior) {
            PlayerQueueEndBehavior.STAY ->
                _state.value = snapshot.copy(paused = true, buffering = false)
            PlayerQueueEndBehavior.CLOSE -> close()
            PlayerQueueEndBehavior.LOOP_CURRENT -> {
                seekTo(0.0)
                setPaused(false)
            }
        }
    }

    private fun openQueueItem(index: Int) {
        check(index in activeQueue.indices) { "Player queue index is out of bounds" }
        activeQueueIndex = index
        val request = activeQueue[index]
        PlayerDebugLogBuffer.append(
            TAG,
            "切换播放队列 index=$index size=${activeQueue.size} " +
                "request=${shortPlayerDiagnosticId(request.requestId)}",
        )
        openInternal(
            request = request,
            presentation = _state.value.presentation,
            clearDiagnostics = false,
        )
    }

    private fun isCurrentRuntime(runtimeGeneration: Long): Boolean =
        runtimeGeneration == _state.value.runtimeGeneration && runtimeGeneration > 0L

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "PlayerSession must be called on the main thread"
        }
    }

    private fun prepareSurfaceLeaseForPresentation(presentation: PlayerPresentation) {
        val role =
            when (presentation) {
                PlayerPresentation.FLOATING_PLAYER -> PlayerSurfaceRole.FLOATING
                PlayerPresentation.FULLSCREEN_PLAYER -> PlayerSurfaceRole.FULLSCREEN
                PlayerPresentation.BROWSER_ONLY -> return
            }
        val lease = _state.value.surfaceLease
        val currentRole = lease.currentOwner?.role
        val nextLease =
            when {
                currentRole == null -> preparePlayerSurfaceLease(lease, role)
                currentRole == role -> lease
                currentRole == PlayerSurfaceRole.FLOATING &&
                    role == PlayerSurfaceRole.FULLSCREEN ->
                    beginFloatingToFullscreenTransfer(lease)
                currentRole == PlayerSurfaceRole.FULLSCREEN &&
                    role == PlayerSurfaceRole.FLOATING ->
                    beginFullscreenToFloatingTransfer(lease)
                else -> error("Unsupported player Surface presentation transfer")
            }
        discardSupersededPendingSurface(nextLease)
        _state.value = _state.value.copy(surfaceLease = nextLease)
    }

    private fun discardSupersededPendingSurface(nextLease: PlayerSurfaceLeaseState) {
        val pending = pendingSurfaceLease ?: return
        if (
            !isPendingPlayerSurfaceTarget(
                nextLease,
                pending.role,
                pending.ownerToken,
                pending.generation,
            )
        ) {
            pendingSurfaceLease = null
        }
    }

    fun acknowledgeFullscreenLaunchRequest(requestId: Long) {
        requireMainThread()
        _state.value =
            _state.value.copy(
                surfaceLease =
                    acknowledgeFullscreenLaunchRequest(_state.value.surfaceLease, requestId),
            )
    }

    fun acknowledgeFullscreenFinishRequest(requestId: Long) {
        requireMainThread()
        _state.value =
            _state.value.copy(
                surfaceLease =
                    acknowledgeFullscreenFinishRequest(_state.value.surfaceLease, requestId),
            )
    }

    fun onFullscreenActivityDestroyed(
        changingConfigurations: Boolean,
        finishing: Boolean,
    ) {
        requireMainThread()
        if (changingConfigurations || !finishing) return
        val snapshot = _state.value
        val expectedTransfer =
            snapshot.surfaceLease.phase ==
                PlayerSurfaceTransferPhase.FULLSCREEN_TO_FLOATING_WAITING_FULLSCREEN_DESTROY ||
                snapshot.surfaceLease.phase ==
                    PlayerSurfaceTransferPhase.WAITING_FLOATING_SURFACE ||
                snapshot.surfaceLease.phase == PlayerSurfaceTransferPhase.CLOSING
        if (!expectedTransfer && snapshot.presentation == PlayerPresentation.FULLSCREEN_PLAYER) {
            close()
        }
    }

    private fun PlayerRuntimeState.acceptsCommands(): Boolean =
        this == PlayerRuntimeState.READY || this == PlayerRuntimeState.ACTIVE

    private data class PendingMediaLoad(
        val requestId: String,
        val uri: String,
        val headers: Map<String, String>,
        val config: PlayerRuntimeConfig,
        val initialSpeed: Double,
    )

    private data class SurfaceParcel(
        val role: PlayerSurfaceRole,
        val ownerToken: String,
        val generation: Long,
        val surface: Surface,
        val width: Int,
        val height: Int,
        val attachCommandId: Long? = null,
        val detachCommandId: Long? = null,
        val detachRequested: Boolean = false,
    )

    private data class PendingScreenshot(
        val file: File,
        val onResult: (Result<String>) -> Unit,
    )

    private data class FailedPlayerSession(
        val request: PlayerMediaRequest,
        val presentation: PlayerPresentation,
        val positionSeconds: Double,
        val speed: Double,
        val runtimeGeneration: Long,
    )

    private data class ActiveLongPressSpeedBoost(
        val requestId: String,
        val loadGeneration: Long,
        val runtimeGeneration: Long,
        val originalSpeed: Double,
        val boostedSpeed: Double,
    )

    companion object {
        private const val TAG = "PlayerSession"
        private const val SCREENSHOT_WAIT_ATTEMPTS = 20
        private const val SCREENSHOT_WAIT_INTERVAL_MS = 50L
        private const val SEEK_PREVIEW_MAX_SIZE = 320

        @Volatile private var instance: PlayerSession? = null

        fun getInstance(context: Context): PlayerSession =
            instance ?: synchronized(this) {
                instance
                    ?: PlayerSession(context.applicationContext).also { session ->
                        instance = session
                    }
            }
    }
}
