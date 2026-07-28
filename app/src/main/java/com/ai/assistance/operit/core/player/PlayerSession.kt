package com.ai.assistance.operit.core.player

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Surface
import com.ai.assistance.operit.util.AppLogger
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
    private val shaderManager = Anime4KShaderManager(appContext)
    private val mediaResolver = PlayerMediaResolver(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerSessionState())

    val state: StateFlow<PlayerSessionState> = _state.asStateFlow()

    private var engine: MpvPlayerEngine? = null
    private var currentSurface: Surface? = null
    private var pendingSurfaceLease: PendingSurfaceLease? = null
    private var pendingMediaLoad: PendingMediaLoad? = null

    private val engineListener =
        object : MpvPlayerEngineListener {
            override fun onBooleanProperty(name: String, value: Boolean) {
                mainHandler.post {
                    when (name) {
                        "pause" -> _state.value = _state.value.copy(paused = value)
                        "paused-for-cache" -> _state.value = _state.value.copy(buffering = value)
                        "eof-reached" -> if (value) handleNaturalEndOfFile()
                    }
                }
            }

            override fun onDoubleProperty(name: String, value: Double) {
                mainHandler.post {
                    when (name) {
                        "time-pos" -> _state.value = _state.value.copy(positionSeconds = value)
                        "duration" -> _state.value = _state.value.copy(durationSeconds = value)
                        "speed" -> _state.value = _state.value.copy(speed = value)
                    }
                }
            }

            override fun onFileLoaded() {
                mainHandler.post {
                    updateTracks()
                    _state.value = _state.value.copy(loading = false, buffering = false, error = null)
                }
            }

            override fun onRuntimeError(message: String) {
                mainHandler.post {
                    setError(message, IllegalStateException(message))
                }
            }
        }

    private val progressPoll =
        object : Runnable {
            override fun run() {
                if (engine == null || !_state.value.hasMedia) return
                if (updateProgressFromMpv()) {
                    mainHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
                }
            }
        }

    init {
        mainScope.launch {
            settingsStore.state.drop(1).collect { settings ->
                val activeEngine = engine
                if (_state.value.hasMedia && activeEngine != null) {
                    try {
                        activeEngine.applyDecoderPreset(settings.decoderPreset)
                        activeEngine.applyPreciseSeeking(settings.preciseSeeking)
                        activeEngine.applyNetworkCache(settings.networkCachePolicy)
                        activeEngine.applySubtitleScale(settings.subtitleScale)
                        activeEngine.applyEndBehavior(settings.endBehavior)
                        activeEngine.applyVolumeBoost(settings.volumeBoostEnabled)
                        val anime4KMode =
                            if (settings.rememberAnime4KMode) {
                                settings.anime4KMode
                            } else {
                                _state.value.anime4KMode
                            }
                        val shaderFiles = shaderManager.resolveShaderFiles(anime4KMode)
                        activeEngine.applyShaders(shaderFiles)
                        _state.value =
                            _state.value.copy(
                                decoderPreset = settings.decoderPreset,
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
    }

    fun open(request: PlayerMediaRequest, presentation: PlayerPresentation) {
        requireMainThread()
        val settings = settingsStore.current
        val transition = resolvePlayerOpenTransition(_state.value, request, presentation, settings)
        if (!transition.shouldLoad) {
            _state.value = transition.state
            return
        }

        PlayerDebugLogBuffer.clear()
        PlayerDebugLogBuffer.append(
            TAG,
            "打开媒体 request=${request.requestId} source=${request.source} " +
                "decoder=${settings.decoderPreset.persistedId} " +
                "uri=${request.uri.substringBefore('?')}",
        )
        _state.value = transition.state
        prepareSurfaceLeaseForPresentation(presentation)
        pendingMediaLoad = null
        try {
            val activeEngine = ensureEngine(settings)
            val target = mediaResolver.resolve(request)
            val shaderFiles = shaderManager.resolveShaderFiles(transition.state.anime4KMode)
            _state.value = _state.value.copy(activeShaderFiles = shaderFiles)
            pendingMediaLoad = PendingMediaLoad(
                requestId = request.requestId,
                target = target,
                headers = request.headers,
            )
            // mpvlibAndroid owns core initialization before Android Surface callbacks. Media
            // loading still waits until the current lease has completed its native attach.
            startPendingMediaLoad(activeEngine)
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
        if (
            isCurrentPlayerSurfaceOwner(lease, role, ownerToken, generation) &&
                currentSurface === surface
        ) {
            engine?.let { activeEngine ->
                runEngineAction("无法更新播放画面尺寸") {
                    activeEngine.updateSurfaceSize(width, height)
                }
            }
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
            PendingSurfaceLease(role, ownerToken, generation, surface, width, height)
        if (lease.currentOwner != null || !lease.nativeDetachCompleted) {
            PlayerDebugLogBuffer.append(
                TAG,
                "Surface 等待旧租约释放 role=$role generation=$generation phase=${lease.phase}",
            )
            return
        }
        attachPendingSurfaceLease()
    }

    private fun attachPendingSurfaceLease() {
        val pending = pendingSurfaceLease ?: return
        val lease = _state.value.surfaceLease
        if (
            !isPendingPlayerSurfaceTarget(
                lease,
                pending.role,
                pending.ownerToken,
                pending.generation,
            ) ||
                lease.currentOwner != null ||
                !lease.nativeDetachCompleted
        ) {
            return
        }
        val surface = pending.surface
        PlayerDebugLogBuffer.append(
            TAG,
            "准备 native attach role=${pending.role} generation=${pending.generation} phase=${lease.phase}",
        )
        val activeEngine =
            engine ?: run {
                setError(
                    "播放器内核未在 Surface 到达前完成初始化",
                    IllegalStateException("Player engine is missing during Surface attach"),
                )
                return
            }
        if (!runEngineAction("无法连接播放画面") {
                activeEngine.attachSurface(surface, pending.width, pending.height)
            }) {
            return
        }
        currentSurface = surface
        pendingSurfaceLease = null
        _state.value =
            _state.value.copy(
                surfaceLease =
                    activatePendingPlayerSurface(
                        _state.value.surfaceLease,
                        pending.role,
                        pending.ownerToken,
                        pending.generation,
                    ),
            )
        PlayerDebugLogBuffer.append(
            TAG,
            "连接播放画面 role=${pending.role} generation=${pending.generation} phase=${_state.value.surfaceLease.phase}",
        )
        startPendingMediaLoad(activeEngine)
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
        if (
            isCurrentPlayerSurfaceOwner(
                _state.value.surfaceLease,
                role,
                ownerToken,
                generation,
            ) &&
                currentSurface === surface
        ) {
            engine?.let { activeEngine ->
                runEngineAction("无法更新播放画面尺寸") {
                    activeEngine.updateSurfaceSize(width, height)
                }
            }
            return
        }
        pendingSurfaceLease
            ?.takeIf {
                it.role == role &&
                    it.ownerToken == ownerToken &&
                    it.generation == generation &&
                    it.surface === surface
            }
            ?.let {
                pendingSurfaceLease = it.copy(width = width, height = height)
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
            pendingSurfaceLease = null
            val lease = _state.value.surfaceLease
            if (isPendingPlayerSurfaceTarget(lease, role, ownerToken, generation)) {
                _state.value =
                    _state.value.copy(
                        surfaceLease = lease.copy(pendingTarget = null),
                    )
            }
            return
        }
        if (
            !isCurrentPlayerSurfaceOwner(
                _state.value.surfaceLease,
                role,
                ownerToken,
                generation,
            ) ||
                (surface != null && currentSurface !== surface)
        ) {
            PlayerDebugLogBuffer.append(
                TAG,
                "忽略过期 Surface destroy role=$role generation=$generation",
            )
            return
        }
        PlayerDebugLogBuffer.append(
            TAG,
            "准备 native detach role=$role generation=$generation phase=${_state.value.surfaceLease.phase}",
        )
        if (!detachAttachedSurface()) return
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
            )
        PlayerDebugLogBuffer.append(
            TAG,
            "释放播放画面 role=$role generation=$generation phase=${completed.phase}",
        )
        attachPendingSurfaceLease()
    }

    fun togglePause() {
        requireMainThread()
        if (_state.value.hasMedia) setPaused(!_state.value.paused)
    }

    fun setPaused(paused: Boolean) {
        requireMainThread()
        val activeEngine = engine ?: return
        if (!_state.value.hasMedia) return
        if (runEngineAction("无法设置暂停状态") { activeEngine.setPaused(paused) }) {
            _state.value = _state.value.copy(paused = paused)
        }
    }

    fun seekTo(positionSeconds: Double) {
        requireMainThread()
        val activeEngine = engine ?: return
        if (!_state.value.hasMedia) return
        val duration = _state.value.durationSeconds
        val target =
            if (duration > 0.0) {
                positionSeconds.coerceIn(0.0, duration)
            } else {
                positionSeconds.coerceAtLeast(0.0)
            }
        if (
            runEngineAction("无法跳转播放位置") {
                activeEngine.seekTo(target, settingsStore.current.preciseSeeking)
            }
        ) {
            _state.value = _state.value.copy(positionSeconds = target)
        }
    }

    fun seekBackward() {
        seekTo(_state.value.positionSeconds - settingsStore.current.seekStepSeconds)
    }

    fun seekForward() {
        seekTo(_state.value.positionSeconds + settingsStore.current.seekStepSeconds)
    }

    fun setSpeed(speed: Double) {
        requireMainThread()
        require(speed in PLAYER_SPEED_OPTIONS) { "Unsupported player speed: $speed" }
        val activeEngine = engine ?: return
        if (!_state.value.hasMedia) return
        if (runEngineAction("无法设置播放速度") { activeEngine.setSpeed(speed) }) {
            _state.value = _state.value.copy(speed = speed)
            if (settingsStore.current.rememberPlaybackSpeed) {
                settingsStore.setLastPlaybackSpeed(speed)
            }
        }
    }

    fun setAudioTrack(trackId: Int) {
        requireMainThread()
        val activeEngine = engine ?: return
        val snapshot = _state.value
        if (!snapshot.hasMedia) return
        if (runEngineAction("无法切换音轨") { activeEngine.setAudioTrack(trackId) }) {
            _state.value = snapshot.copy(
                selectedAudioTrackId = trackId,
                audioTracks = snapshot.audioTracks.map { it.copy(selected = it.id == trackId) },
            )
        }
    }

    fun setSubtitleTrack(trackId: Int?) {
        requireMainThread()
        val activeEngine = engine ?: return
        val snapshot = _state.value
        if (!snapshot.hasMedia) return
        if (runEngineAction("无法切换字幕") { activeEngine.setSubtitleTrack(trackId) }) {
            _state.value = snapshot.copy(
                selectedSubtitleTrackId = trackId,
                subtitleTracks = snapshot.subtitleTracks.map { it.copy(selected = it.id == trackId) },
            )
        }
    }

    fun setVideoFitMode(mode: PlayerVideoFitMode) {
        requireMainThread()
        val activeEngine = engine ?: return
        if (!runEngineAction("无法切换画面比例") { activeEngine.applyVideoFitMode(mode) }) return
        _state.value = _state.value.copy(videoFitMode = mode)
    }

    fun cycleAudioTrack() {
        requireMainThread()
        val activeEngine = engine ?: return
        val snapshot = _state.value
        if (snapshot.audioTracks.isEmpty()) return
        val currentIndex = snapshot.audioTracks.indexOfFirst { it.id == snapshot.selectedAudioTrackId }
        val next = snapshot.audioTracks[(currentIndex + 1).mod(snapshot.audioTracks.size)]
        if (runEngineAction("无法切换音轨") { activeEngine.setAudioTrack(next.id) }) {
            _state.value =
                snapshot.copy(
                    selectedAudioTrackId = next.id,
                    audioTracks = snapshot.audioTracks.map { it.copy(selected = it.id == next.id) },
                )
        }
    }

    fun cycleSubtitleTrack() {
        requireMainThread()
        val activeEngine = engine ?: return
        val snapshot = _state.value
        if (snapshot.subtitleTracks.isEmpty()) return
        val currentIndex = snapshot.subtitleTracks.indexOfFirst { it.id == snapshot.selectedSubtitleTrackId }
        if (snapshot.selectedSubtitleTrackId != null && currentIndex == snapshot.subtitleTracks.lastIndex) {
            if (runEngineAction("无法关闭字幕") { activeEngine.setSubtitleTrack(null) }) {
                _state.value =
                    snapshot.copy(
                        selectedSubtitleTrackId = null,
                        subtitleTracks = snapshot.subtitleTracks.map { it.copy(selected = false) },
                    )
            }
            return
        }
        val next = snapshot.subtitleTracks[(currentIndex + 1).coerceAtLeast(0)]
        if (runEngineAction("无法切换字幕") { activeEngine.setSubtitleTrack(next.id) }) {
            _state.value =
                snapshot.copy(
                    selectedSubtitleTrackId = next.id,
                    subtitleTracks = snapshot.subtitleTracks.map { it.copy(selected = it.id == next.id) },
                )
        }
    }

    fun cycleVideoFitMode() {
        requireMainThread()
        val activeEngine = engine ?: return
        val next = _state.value.videoFitMode.next()
        if (runEngineAction("无法切换画面比例") { activeEngine.applyVideoFitMode(next) }) {
            _state.value = _state.value.copy(videoFitMode = next)
        }
    }

    fun cycleAnime4KMode() {
        requireMainThread()
        val current = _state.value.anime4KMode
        val next = Anime4KMode.entries[(current.ordinal + 1) % Anime4KMode.entries.size]
        if (settingsStore.current.rememberAnime4KMode) {
            settingsStore.setAnime4KMode(next)
            return
        }
        val activeEngine = engine ?: return
        val shaderFiles = shaderManager.resolveShaderFiles(next)
        if (runEngineAction("无法切换 Anime4K 模式") { activeEngine.applyShaders(shaderFiles) }) {
            _state.value = _state.value.copy(anime4KMode = next, activeShaderFiles = shaderFiles)
        }
    }

    fun captureScreenshot(onResult: (Result<String>) -> Unit) {
        requireMainThread()
        val activeEngine = engine
        if (activeEngine == null || !_state.value.hasMedia) {
            onResult(Result.failure(IllegalStateException("当前没有可截图的视频")))
            return
        }
        val temporary = File(appContext.cacheDir, "player-screenshot-${System.currentTimeMillis()}.png")
        try {
            activeEngine.captureScreenshot(temporary.absolutePath)
        } catch (error: Exception) {
            onResult(Result.failure(error))
            return
        }
        mainScope.launch(Dispatchers.IO) {
            val generated =
                repeatUntilNotNull(SCREENSHOT_WAIT_ATTEMPTS, SCREENSHOT_WAIT_INTERVAL_MS) {
                    temporary.takeIf { it.isFile && it.length() > 0L }
                }
            val result =
                runCatching {
                    val file = requireNotNull(generated) { "mpv did not produce a screenshot" }
                    saveScreenshot(file)
                }
            temporary.delete()
            withContext(Dispatchers.Main.immediate) { onResult(result) }
        }
    }

    fun onHostBackgrounded() {
        requireMainThread()
        if (_state.value.hasMedia && settingsStore.current.backgroundBehavior == PlayerBackgroundBehavior.PAUSE) {
            setPaused(true)
        }
    }

    fun close() {
        requireMainThread()
        mainHandler.removeCallbacks(progressPoll)
        pendingMediaLoad = null
        pendingSurfaceLease = null
        val closingLease = beginClosingPlayerSurfaceLease(_state.value.surfaceLease)
        _state.value = _state.value.copy(surfaceLease = closingLease)
        if (!detachAttachedSurface()) return
        runCatching { engine?.destroy() }
            .onFailure { AppLogger.e(TAG, "Failed to destroy MPV core", it) }
        engine = null
        currentSurface = null
        // Keep content:// descriptors valid until libmpv has stopped reading the active media.
        mediaResolver.close()
        _state.value =
            PlayerSessionState(
                loadGeneration = _state.value.loadGeneration,
                surfaceLease = completeClosingPlayerSurfaceLease(closingLease),
            )
    }

    private fun ensureEngine(settings: PlayerSettings): MpvPlayerEngine {
        engine?.let { return it }
        try {
            return MpvPlayerEngine(appContext, engineListener).also { created ->
                created.initialize(settings)
                engine = created
            }
        } catch (error: LinkageError) {
            throw IllegalStateException(
                "mpv 运行时无法链接：${error.message ?: error.javaClass.simpleName}",
                error,
            )
        }
    }

    private fun detachAttachedSurface(): Boolean {
        if (currentSurface == null) {
            return true
        }
        val detached = runEngineAction("无法断开播放画面") { engine?.detachSurface() }
        if (!detached) return false
        currentSurface = null
        return true
    }

    private fun startPendingMediaLoad(activeEngine: MpvPlayerEngine) {
        val pending = pendingMediaLoad ?: return
        if (!isPlayerMediaLoadReady(hasPendingLoad = true, hasAttachedSurface = currentSurface != null)) return
        check(_state.value.request?.requestId == pending.requestId) {
            "Pending player request does not match the active session"
        }
        pendingMediaLoad = null
        try {
            val settings = settingsStore.current
            val shaderFiles = shaderManager.resolveShaderFiles(_state.value.anime4KMode)
            _state.value = _state.value.copy(activeShaderFiles = shaderFiles)
            activeEngine.load(
                pending.target,
                pending.headers,
                settings,
                shaderFiles,
                initialSpeed = _state.value.speed,
            )
            mainHandler.removeCallbacks(progressPoll)
            mainHandler.post(progressPoll)
            PlayerDebugLogBuffer.append(TAG, "播放画面已连接，开始加载媒体")
        } catch (error: Exception) {
            setError("无法加载视频：${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    private fun updateProgressFromMpv(): Boolean {
        val activeEngine = engine ?: return false
        val snapshot = _state.value
        val progress =
            try {
                activeEngine.readProgress()
            } catch (error: Exception) {
                setError("无法读取播放状态：${error.message ?: error.javaClass.simpleName}", error)
                return false
            }
        _state.value =
            snapshot.copy(
                positionSeconds =
                    progress.positionSeconds?.takeIf(Double::isFinite) ?: snapshot.positionSeconds,
                durationSeconds =
                    progress.durationSeconds?.takeIf(Double::isFinite) ?: snapshot.durationSeconds,
                paused = progress.paused ?: snapshot.paused,
                speed =
                    progress.speed?.takeIf { it.isFinite() && it > 0.0 } ?: snapshot.speed,
                networkSpeedBytesPerSecond = progress.networkSpeedBytesPerSecond,
            )
        return true
    }

    private fun updateTracks() {
        val tracks =
            try {
                engine?.readTracks() ?: return
            } catch (error: Exception) {
                setError("无法读取媒体轨道：${error.message ?: error.javaClass.simpleName}", error)
                return
            }
        _state.value =
            _state.value.copy(
                audioTracks = tracks.audioTracks,
                subtitleTracks = tracks.subtitleTracks,
                selectedAudioTrackId = tracks.audioTracks.singleOrNull { it.selected }?.id,
                selectedSubtitleTrackId = tracks.subtitleTracks.singleOrNull { it.selected }?.id,
            )
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val directory =
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Kiyori",
                )
            check(directory.isDirectory || directory.mkdirs()) {
                "Unable to create screenshot directory: $directory"
            }
            val destination = File(directory, displayName)
            source.copyTo(destination, overwrite = false)
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(destination.absolutePath),
                arrayOf("image/png"),
                null,
            )
            return displayName
        }
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Kiyori")
            }
        val uri =
            requireNotNull(
                appContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
            ) { "Unable to create screenshot in MediaStore" }
        appContext.contentResolver.openOutputStream(uri).use { output ->
            requireNotNull(output) { "Unable to open screenshot output" }
            source.inputStream().use { input -> input.copyTo(output) }
        }
        return displayName
    }

    private fun setError(message: String, error: Throwable) {
        AppLogger.e(TAG, message, error)
        PlayerDebugLogBuffer.append(TAG, message)
        _state.value = _state.value.copy(loading = false, buffering = false, error = message)
    }

    private fun handleNaturalEndOfFile() {
        val snapshot = _state.value
        if (!snapshot.hasMedia) return
        if (!isPlayerAtNaturalEnd(snapshot.positionSeconds, snapshot.durationSeconds)) return
        when (settingsStore.current.endBehavior) {
            PlayerEndBehavior.PAUSE ->
                _state.value = snapshot.copy(paused = true, buffering = false)
            PlayerEndBehavior.CLOSE -> close()
            PlayerEndBehavior.LOOP -> Unit
        }
    }

    private inline fun runEngineAction(message: String, action: () -> Unit): Boolean =
        try {
            action()
            true
        } catch (error: Exception) {
            setError("$message：${error.message ?: error.javaClass.simpleName}", error)
            false
        }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "PlayerSession must be called on the main thread"
        }
    }

    companion object {
        private const val TAG = "PlayerSession"
        private const val PROGRESS_INTERVAL_MS = 250L
        private const val SCREENSHOT_WAIT_ATTEMPTS = 20
        private const val SCREENSHOT_WAIT_INTERVAL_MS = 50L

        @Volatile private var instance: PlayerSession? = null

        fun getInstance(context: Context): PlayerSession =
            instance ?: synchronized(this) {
                instance
                    ?: PlayerSession(context.applicationContext).also { session ->
                        instance = session
                    }
            }
    }

    private data class PendingMediaLoad(
        val requestId: String,
        val target: String,
        val headers: Map<String, String>,
    )

    private data class PendingSurfaceLease(
        val role: PlayerSurfaceRole,
        val ownerToken: String,
        val generation: Long,
        val surface: Surface,
        val width: Int,
        val height: Int,
    )

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
                snapshot.surfaceLease.phase == PlayerSurfaceTransferPhase.WAITING_FLOATING_SURFACE ||
                snapshot.surfaceLease.phase == PlayerSurfaceTransferPhase.CLOSING
        if (!expectedTransfer && snapshot.presentation == PlayerPresentation.FULLSCREEN_PLAYER) {
            close()
        }
    }
}
