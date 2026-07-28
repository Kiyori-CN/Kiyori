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
    private var attachedSurface: Surface? = null

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
                        activeEngine.applyHardwareDecoding(settings.hardwareDecodingPolicy)
                        activeEngine.applyPreciseSeeking(settings.preciseSeeking)
                        activeEngine.applyNetworkCache(settings.networkCachePolicy)
                        activeEngine.applySubtitleScale(settings.subtitleScale)
                        activeEngine.applyEndBehavior(settings.endBehavior)
                        val shaderFiles = shaderManager.resolveShaderFiles(settings.anime4KMode)
                        activeEngine.applyShaders(shaderFiles)
                        _state.value =
                            _state.value.copy(
                                hardwareDecodingPolicy = settings.hardwareDecodingPolicy,
                                anime4KMode = settings.anime4KMode,
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

        _state.value = transition.state
        try {
            val activeEngine = ensureEngine(settings)
            val target = mediaResolver.resolve(request)
            val shaderFiles = shaderManager.resolveShaderFiles(settings.anime4KMode)
            _state.value = _state.value.copy(activeShaderFiles = shaderFiles)
            activeEngine.load(target, request.headers, settings, shaderFiles)
            mainHandler.removeCallbacks(progressPoll)
            mainHandler.post(progressPoll)
        } catch (error: Exception) {
            setError("无法加载视频：${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    fun enterFloating() {
        requireMainThread()
        check(_state.value.hasMedia) { "Cannot float without an active player request" }
        _state.value = _state.value.copy(presentation = PlayerPresentation.FLOATING_PLAYER)
    }

    fun enterFullscreen() {
        requireMainThread()
        check(_state.value.hasMedia) { "Cannot enter fullscreen without an active player request" }
        _state.value = _state.value.copy(presentation = PlayerPresentation.FULLSCREEN_PLAYER)
    }

    fun exitFullscreen() {
        requireMainThread()
        val snapshot = _state.value
        if (shouldReturnFullscreenPlayerToFloating(snapshot, settingsStore.current)) {
            _state.value = snapshot.copy(presentation = PlayerPresentation.FLOATING_PLAYER)
        } else {
            close()
        }
    }

    fun attachSurface(owner: String, surface: Surface, width: Int, height: Int) {
        requireMainThread()
        require(owner.isNotBlank()) { "Player surface owner is blank" }
        val activeEngine = engine ?: return
        if (!_state.value.hasMedia || !surface.isValid) return
        if (_state.value.surfaceOwner == owner && attachedSurface === surface) {
            runEngineAction("无法更新播放画面尺寸") {
                activeEngine.updateSurfaceSize(width, height)
            }
            return
        }
        if (!detachAttachedSurface()) return
        if (!runEngineAction("无法连接播放画面") {
                activeEngine.attachSurface(surface, width, height)
            }) {
            return
        }
        attachedSurface = surface
        _state.value = _state.value.copy(surfaceOwner = owner)
    }

    fun updateSurface(owner: String, width: Int, height: Int) {
        requireMainThread()
        if (_state.value.surfaceOwner == owner) {
            engine?.let { activeEngine ->
                runEngineAction("无法更新播放画面尺寸") {
                    activeEngine.updateSurfaceSize(width, height)
                }
            }
        }
    }

    fun detachSurface(owner: String, surface: Surface?) {
        requireMainThread()
        if (_state.value.surfaceOwner != owner) return
        if (surface != null && attachedSurface !== surface) return
        detachAttachedSurface()
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
        }
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
        val current = settingsStore.current.anime4KMode
        val next = Anime4KMode.entries[(current.ordinal + 1) % Anime4KMode.entries.size]
        settingsStore.setAnime4KMode(next)
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
        detachAttachedSurface()
        runCatching { engine?.destroy() }
            .onFailure { AppLogger.e(TAG, "Failed to destroy MPV core", it) }
        engine = null
        attachedSurface = null
        // Keep content:// descriptors valid until libmpv has stopped reading the active media.
        mediaResolver.close()
        _state.value = PlayerSessionState(loadGeneration = _state.value.loadGeneration)
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
        if (attachedSurface == null) {
            _state.value = _state.value.copy(surfaceOwner = null)
            return true
        }
        val detached = runEngineAction("无法断开播放画面") { engine?.detachSurface() }
        if (!detached) return false
        attachedSurface = null
        _state.value = _state.value.copy(surfaceOwner = null)
        return true
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
}
