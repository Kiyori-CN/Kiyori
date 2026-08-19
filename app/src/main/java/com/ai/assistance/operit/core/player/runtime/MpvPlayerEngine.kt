package com.ai.assistance.operit.core.player.runtime

import android.content.Context
import android.graphics.Bitmap
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.ai.assistance.operit.core.player.PlayerDebugLogLevel
import com.ai.assistance.operit.core.player.PlayerDecoderBackend
import com.ai.assistance.operit.core.player.PlayerChapter
import com.ai.assistance.operit.core.player.PlayerNetworkCachePolicy
import com.ai.assistance.operit.core.player.PlayerRenderingProfile
import com.ai.assistance.operit.core.player.PlayerSettings
import com.ai.assistance.operit.core.player.PlayerTrack
import com.ai.assistance.operit.core.player.PlayerVideoFitMode
import com.ai.assistance.operit.core.player.isPlayerNetworkMediaUri
import com.ai.assistance.operit.core.player.sanitizePlayerDiagnosticMessage
import com.ai.assistance.operit.core.player.shortPlayerDiagnosticId
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import java.io.File
import java.nio.file.Files

private const val PLAYER_RUNTIME_METADATA_VALUE_MAX_LENGTH = 64

internal data class MpvPlayerProgress(
    val positionSeconds: Double?,
    val durationSeconds: Double?,
    val paused: Boolean?,
    val buffering: Boolean?,
    val speed: Double?,
    val networkSpeedBytesPerSecond: Long,
    val fullVideoCacheActive: Boolean,
    val fullVideoCacheComplete: Boolean,
    val fullVideoCacheStartSeconds: Double?,
    val fullVideoCacheEndSeconds: Double?,
    val fullVideoCachePhase: String,
    val fullVideoCacheReason: String?,
    val fullVideoCacheStateEvidence: String,
    val fullVideoCacheFileBytes: Long,
    val fullVideoCacheExpectedBytes: Long?,
)

internal fun parsePlayerRuntimeHardwareDecoderMetadata(
    optionInfo: MPVNode?,
): PlayerRuntimeHardwareDecoderMetadata {
    if (optionInfo == null) {
        return PlayerRuntimeHardwareDecoderMetadata(
            evidence = PlayerRuntimeHardwareDecoderEvidence.OPTION_INFO_UNAVAILABLE,
            optionType = null,
            availableHardwareDecoders = emptySet(),
        )
    }
    val optionMap =
        optionInfo.asMap()
            ?: return PlayerRuntimeHardwareDecoderMetadata(
                evidence = PlayerRuntimeHardwareDecoderEvidence.OPTION_INFO_NOT_MAP,
                optionType = null,
                availableHardwareDecoders = emptySet(),
            )
    val optionType = normalizePlayerRuntimeMetadataValue(optionMap["type"]?.asString())
    val choicesNode =
        optionMap["choices"]
            ?: return PlayerRuntimeHardwareDecoderMetadata(
                evidence = PlayerRuntimeHardwareDecoderEvidence.CHOICES_ABSENT,
                optionType = optionType,
                availableHardwareDecoders = emptySet(),
            )
    val choices =
        choicesNode.asArray()
            ?: return PlayerRuntimeHardwareDecoderMetadata(
                evidence = PlayerRuntimeHardwareDecoderEvidence.CHOICES_NOT_ARRAY,
                optionType = optionType,
                availableHardwareDecoders = emptySet(),
            )
    val values = linkedSetOf<String>()
    choices.forEach { choice ->
        val value =
            choice.asString()
                ?: return PlayerRuntimeHardwareDecoderMetadata(
                    evidence = PlayerRuntimeHardwareDecoderEvidence.CHOICE_ENTRY_NOT_STRING,
                    optionType = optionType,
                    availableHardwareDecoders = emptySet(),
                )
        values += value
    }
    return PlayerRuntimeHardwareDecoderMetadata(
        evidence = PlayerRuntimeHardwareDecoderEvidence.CHOICES_AVAILABLE,
        optionType = optionType,
        availableHardwareDecoders = values,
    )
}

private fun normalizePlayerRuntimeMetadataValue(value: String?): String? {
    val normalized = value?.trim() ?: return null
    return normalized
        .takeIf { candidate ->
            candidate.isNotEmpty() &&
                candidate.length <= PLAYER_RUNTIME_METADATA_VALUE_MAX_LENGTH &&
                !candidate.contains('\r') &&
                !candidate.contains('\n')
        }?.replace(' ', '-')
}

private data class MpvFullVideoCacheState(
    val phase: PlayerFullVideoCachePhase,
    val reason: PlayerFullVideoCacheReason,
    val complete: Boolean,
    val startSeconds: Double?,
    val endSeconds: Double?,
    val fileCacheBytes: Long,
    val expectedFileBytes: Long?,
    val stateEvidence: PlayerFullVideoCacheStateEvidence,
) {
    val active: Boolean
        get() =
            phase == PlayerFullVideoCachePhase.ACTIVE ||
                phase == PlayerFullVideoCachePhase.COMPLETE
}

internal data class MpvPlayerTrackSnapshot(
    val audioTracks: List<PlayerTrack>,
    val subtitleTracks: List<PlayerTrack>,
    val chapters: List<PlayerChapter>,
    val fileFormat: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val videoTrackCount: Int,
    val activeHardwareDecoder: String?,
    val videoPixelFormat: String?,
    val videoCodecProfile: String?,
)

internal data class MpvPlayerMediaIdentitySnapshot(
    val fileFormat: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val videoTrackCount: Int,
    val activeHardwareDecoder: String?,
    val videoPixelFormat: String?,
    val videoCodecProfile: String?,
)

internal data class MpvThumbnailSource(
    val source: String,
    val positionSeconds: Double,
    val temporaryFile: File? = null,
)

internal interface MpvPlayerEngineListener {
    fun onBooleanProperty(name: String, value: Boolean)

    fun onDoubleProperty(name: String, value: Double)

    fun onFileLoaded()

    fun onVideoReconfigured()

    fun onSeek()

    fun onPlaybackRestart()

    fun onRuntimeError(message: String)

    fun onDiagnosticLog(
        level: PlayerDebugLogLevel,
        tag: String,
        message: String,
    )
}

internal class MpvRuntimeException(
    operation: String,
    cause: LinkageError,
) : IllegalStateException(
        "mpv JNI $operation failed: ${cause.message ?: cause.javaClass.simpleName}",
        cause,
    )

internal class MpvPlayerEngine(
    context: Context,
    private val listener: MpvPlayerEngineListener,
) : MPVLib.EventObserver, MPVLib.LogObserver {
    private val appContext = context.applicationContext
    private var initialized = false
    private var attachedSurface: Surface? = null
    private var videoOutput = VIDEO_OUTPUT_GPU
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var videoFitMode = PlayerVideoFitMode.FIT
    private var sessionCacheDirectory: File? = null
    private var fullVideoCacheRequested = false
    private var fullVideoCachePhase = PlayerFullVideoCachePhase.DISABLED
    private var fullVideoCacheReason = PlayerFullVideoCacheReason.NONE
    private var fullVideoCacheExpectedBytes: Long? = null
    private var fullVideoCachePolicy = PlayerNetworkCachePolicy.BALANCED
    private var lastFullCacheStorageCheckElapsedMillis = 0L
    private var lastFullCacheStateReadElapsedMillis = 0L
    private var cachedFullVideoCacheState =
        MpvFullVideoCacheState(
            phase = PlayerFullVideoCachePhase.DISABLED,
            reason = PlayerFullVideoCacheReason.NONE,
            complete = false,
            startSeconds = null,
            endSeconds = null,
            fileCacheBytes = 0L,
            expectedFileBytes = null,
            stateEvidence = PlayerFullVideoCacheStateEvidence.NOT_APPLICABLE,
        )
    private var lastFullVideoCacheStateEvidence =
        PlayerFullVideoCacheStateEvidence.NOT_APPLICABLE
    private var firstPlaybackFailure: PlayerPlaybackFailureEvidence? = null
    private var fullCacheCompletionLogged = false
    private var appliedDecoderBackend: PlayerDecoderBackend? = null
    private var appliedRenderingProfile: PlayerRenderingProfile? = null
    private var appliedPreciseSeeking: Boolean? = null
    private var appliedSubtitleScale: Double? = null
    private var appliedVolumeBoost: Boolean? = null
    private var appliedShaderFiles: List<String>? = null

    fun initialize(settings: PlayerSettings): Unit = callMpv("初始化") {
        if (initialized) return
        diagnostic(
            PlayerDebugLogLevel.INFO,
            TAG,
            "初始化 mpv 内核 decoderBackend=${settings.decoderBackend.persistedId} " +
                "renderingProfile=${settings.renderingProfile.persistedId} " +
                "gpuNext=${settings.gpuNextEnabled} vulkan=${settings.vulkanEnabled} " +
                "cache=${settings.networkCachePolicy.persistedId}",
        )
        // mbedTLS does not use Android's platform trust store directly. The fixed mpv
        // input ships the CA bundle it was designed to use, so make that exact asset
        // available before enabling certificate verification.
        Utils.copyAssets(appContext)
        val tlsCaFile = appContext.filesDir.resolve("cacert.pem")
        check(tlsCaFile.isFile && tlsCaFile.length() > 0L) {
            "mpv TLS CA bundle is missing"
        }
        clearStaleFullVideoCacheDirectories()
        clearSeekPreviewExcerptDirectory()
        MPVLib.create(appContext)
        setRequiredOption("config", "no")
        setRequiredOption("msg-level", "all=warn,ffmpeg=info,demux=info")
        setRequiredOption("profile", settings.renderingProfile.persistedId)
        videoOutput =
            if (settings.gpuNextEnabled) VIDEO_OUTPUT_GPU_NEXT else VIDEO_OUTPUT_GPU
        setRequiredOption("vo", videoOutput)
        setRequiredOption(
            "gpu-context",
            if (settings.vulkanEnabled) GPU_CONTEXT_VULKAN else GPU_CONTEXT_OPENGL,
        )
        setDecoderBackendOptions(settings.decoderBackend)
        setRequiredOption("ao", "audiotrack")
        setRequiredOption("keep-open", "yes")
        setRequiredOption("idle", "yes")
        setRequiredOption("force-window", "no")
        setRequiredOption("cache", "yes")
        setRequiredOption("cache-pause-initial", "no")
        setRequiredOption("cache-pause", "yes")
        setRequiredOption("cache-pause-wait", "1.0")
        setRequiredOption("sub-auto", "fuzzy")
        setRequiredOption("sub-codepage", "auto")
        setRequiredOption("sub-font-provider", "auto")
        setRequiredOption("sub-fonts-dir", "/system/fonts")
        setRequiredOption("sub-font", "Noto Sans CJK SC")
        setRequiredOption("embeddedfonts", "yes")
        setRequiredOption("sub-use-margins", "yes")
        setRequiredOption("sub-ass-force-margins", "yes")
        setRequiredOption("blend-subtitles", "video")
        setRequiredOption("slang", "zh,chi,zho,chs,cht,zh-CN,zh-TW,en,eng")
        setRequiredOption("tls-ca-file", tlsCaFile.absolutePath)
        setRequiredOption("tls-verify", "yes")
        // Browser media candidates are already direct executable media requests.
        // No yt-dlp binary is distributed, so the ytdl hook must not turn a native
        // HTTP error into unrelated subprocess lookup failures.
        setRequiredOption("ytdl", "no")
        diagnostic(
            PlayerDebugLogLevel.INFO,
            TAG,
            "网络媒体协议配置 tlsBackend=mbedTLS tlsVerify=true caBundle=packaged ytdl=false",
        )
        setRequiredOption("gpu-shader-cache-dir", appContext.cacheDir.absolutePath)
        setRequiredOption("icc-cache-dir", appContext.cacheDir.absolutePath)
        setNetworkCacheOptions(settings.networkCachePolicy)
        setPreciseSeekingOptions(settings.preciseSeeking)
        setRequiredOption("sub-scale", settings.subtitleScale.toString())
        // 自然 EOF 必须交给唯一 PlayerSession 处理，MPV 自循环会吞掉“下一集/最后一集”语义。
        setRequiredOption("loop-file", "no")
        setVolumeBoostOptions(settings.volumeBoostEnabled)
        // The packaged mpvlibAndroid binding initializes the core before Surface callbacks.
        // Attaching a native window here would let VO startup race Android's first Surface frame.
        MPVLib.addObserver(this)
        MPVLib.addLogObserver(this)
        MPVLib.init()
        // From this point the native core exists and destroy() must remain able to release it
        // even when a required capability property or observer contract fails validation.
        initialized = true
        MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("speed", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        val capabilitySnapshot = readRuntimeCapabilitySnapshot()
        appliedRenderingProfile = settings.renderingProfile
        appliedDecoderBackend = settings.decoderBackend
        appliedPreciseSeeking = settings.preciseSeeking
        appliedSubtitleScale = settings.subtitleScale
        appliedVolumeBoost = settings.volumeBoostEnabled
        diagnostic(
            PlayerDebugLogLevel.INFO,
            TAG,
            "播放器运行时能力 ${capabilitySnapshot.diagnosticSummary()}",
        )
        diagnostic(PlayerDebugLogLevel.INFO, TAG, "mpv 内核初始化完成")
    }

    fun load(
        requestId: String,
        target: String,
        headers: Map<String, String>,
        settings: PlayerSettings,
        shaderFiles: List<String>,
        initialSpeed: Double,
    ) = callMpv("加载媒体") {
        check(initialized) { "mpv engine is not initialized" }
        firstPlaybackFailure = null
        diagnostic(
            PlayerDebugLogLevel.INFO,
            TAG,
            "播放器网络快照 ${capturePlayerNetworkSnapshot(appContext).diagnosticSummary()}",
        )
        val headerPlan = buildPlayerMpvHttpHeaderPlan(headers)
        val requestFields =
            headerPlan.forwardedHeaders.keys.joinToString("|").ifEmpty { "none" }
        diagnostic(
            PlayerDebugLogLevel.INFO,
            TAG,
            "加载媒体 inputHeaderCount=${headers.size} " +
                "forwardedHeaderCount=${headerPlan.forwardedHeaders.size} " +
                "requestFields=$requestFields rangeInput=${headerPlan.rangeHeaderObserved} " +
                "rangeOwner=mpv decoderBackend=${settings.decoderBackend.persistedId} " +
                "renderingProfile=${settings.renderingProfile.persistedId} " +
                "shaderCount=${shaderFiles.size} initialSpeed=$initialSpeed",
        )
        applyRequestHeaders(headerPlan.forwardedHeaders)
        // Built-in profiles can own more than rendering knobs. Apply the broad profile first,
        // then let each explicit Kiyori setting remain the final owner of its mpv properties.
        applyRenderingProfile(settings.renderingProfile)
        applyDecoderBackend(settings.decoderBackend)
        applyPreciseSeeking(settings.preciseSeeking)
        applyNetworkCacheForRequest(
            requestId = requestId,
            target = target,
            policy = settings.networkCachePolicy,
        )
        applySubtitleScale(settings.subtitleScale)
        applyVolumeBoost(settings.volumeBoostEnabled)
        applyShaders(shaderFiles)
        MPVLib.setPropertyDouble("speed", initialSpeed)
        MPVLib.command("loadfile", target, "replace")
        MPVLib.setPropertyBoolean("pause", false)
    }

    fun attachSurface(surface: Surface, width: Int, height: Int) = callMpv("连接播放画面") {
        check(initialized) { "mpv engine is not initialized" }
        check(attachedSurface == null) {
            "PlayerSession must detach the active Surface before attaching another one"
        }
        check(surface.isValid) { "player surface is invalid" }
        MPVLib.attachSurface(surface)
        MPVLib.setPropertyString("vo", videoOutput)
        MPVLib.setPropertyString("force-window", "yes")
        attachedSurface = surface
        updateSurfaceSize(width, height)
        diagnostic(PlayerDebugLogLevel.INFO, TAG, "连接播放画面 ${width}x$height")
    }

    fun updateSurfaceSize(width: Int, height: Int) = callMpv("更新播放画面尺寸") {
        if (initialized && width > 0 && height > 0) {
            surfaceWidth = width
            surfaceHeight = height
            MPVLib.setPropertyString("android-surface-size", "${width}x$height")
            if (videoFitMode == PlayerVideoFitMode.STRETCH) {
                applyVideoFitModeProperties(videoFitMode)
            }
        }
    }

    fun detachSurface(): Unit = callMpv("断开播放画面") {
        if (!initialized || attachedSurface == null) return
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setPropertyString("force-window", "no")
        MPVLib.detachSurface()
        attachedSurface = null
        surfaceWidth = 0
        surfaceHeight = 0
        diagnostic(PlayerDebugLogLevel.INFO, TAG, "断开播放画面")
    }

    fun setPaused(paused: Boolean) = callMpv("设置暂停状态") {
        MPVLib.setPropertyBoolean("pause", paused)
    }

    fun seekTo(positionSeconds: Double, precise: Boolean) = callMpv("跳转播放位置") {
        MPVLib.command(
            "seek",
            positionSeconds.toString(),
            if (precise) "absolute+exact" else "absolute+keyframes",
        )
    }

    fun setSpeed(speed: Double) = callMpv("设置播放速度") {
        MPVLib.setPropertyDouble("speed", speed)
    }

    fun setAudioTrack(id: Int) = callMpv("切换音轨") {
        MPVLib.setPropertyInt("aid", id)
    }

    fun setSubtitleTrack(id: Int?) = callMpv("切换字幕") {
        if (id == null) {
            MPVLib.setPropertyString("sid", "no")
        } else {
            MPVLib.setPropertyInt("sid", id)
        }
    }

    fun applyDecoderBackend(backend: PlayerDecoderBackend) = callMpv("应用解码方式") {
        if (appliedDecoderBackend == backend) return@callMpv
        MPVLib.setPropertyString("hwdec", backend.mpvValue)
        if (backend.hardwareAccelerated) {
            MPVLib.setPropertyString("hwdec-codecs", "all")
        }
        appliedDecoderBackend = backend
    }

    fun applyRenderingProfile(profile: PlayerRenderingProfile) = callMpv("应用渲染预设") {
        if (appliedRenderingProfile == profile) return@callMpv
        MPVLib.command("apply-profile", profile.persistedId)
        appliedRenderingProfile = profile
        // mpv profile 可以同时重写多个属性；后续显式设置必须重新成为最终 owner。
        appliedDecoderBackend = null
        appliedPreciseSeeking = null
        appliedSubtitleScale = null
        appliedVolumeBoost = null
        appliedShaderFiles = null
    }

    fun applyPreciseSeeking(enabled: Boolean) = callMpv("应用精确跳转设置") {
        if (appliedPreciseSeeking == enabled) return@callMpv
        MPVLib.setPropertyString("hr-seek", if (enabled) "yes" else "no")
        MPVLib.setPropertyString("hr-seek-framedrop", "yes")
        appliedPreciseSeeking = enabled
    }

    private fun applyNetworkCache(policy: PlayerNetworkCachePolicy) = callMpv("应用网络缓存设置") {
        MPVLib.setPropertyString("demuxer-max-bytes", policy.forwardBytes.toString())
        MPVLib.setPropertyString("demuxer-max-back-bytes", policy.backwardBytes.toString())
        MPVLib.setPropertyString("cache-secs", policy.cacheSeconds.toString())
    }

    fun applySubtitleScale(scale: Double) = callMpv("应用字幕缩放") {
        if (appliedSubtitleScale == scale) return@callMpv
        MPVLib.setPropertyDouble("sub-scale", scale)
        appliedSubtitleScale = scale
    }

    fun applyVolumeBoost(enabled: Boolean) = callMpv("应用音量增强设置") {
        if (appliedVolumeBoost == enabled) return@callMpv
        val targetVolume = if (enabled) BOOSTED_VOLUME_PERCENT else NORMAL_VOLUME_PERCENT
        MPVLib.setPropertyDouble("volume-max", if (enabled) MAX_BOOSTED_VOLUME_PERCENT else NORMAL_VOLUME_PERCENT)
        MPVLib.setPropertyDouble("volume", targetVolume)
        appliedVolumeBoost = enabled
    }

    fun applyShaders(shaderFiles: List<String>) = callMpv("应用 Anime4K 着色器") {
        if (appliedShaderFiles == shaderFiles) return@callMpv
        val shaderPayloads =
            shaderFiles.map { path ->
                File(path).also { file ->
                    check(file.isAbsolute && file.isFile && file.length() > 0L) {
                        "Anime4K shader is unavailable in player runtime: $path"
                    }
                }
            }
        val serialized = shaderPayloads.joinToString(":") { file -> file.absolutePath }
        MPVLib.setPropertyString("glsl-shaders", serialized)
        val applied = MPVLib.getPropertyString("glsl-shaders")
        if (serialized.isEmpty()) {
            check(applied.isNullOrEmpty()) {
                "mpv did not clear glsl-shaders: $applied"
            }
        } else {
            check(applied == serialized) {
                "mpv glsl-shaders verification failed: expected=$serialized actual=$applied"
            }
        }
        diagnostic(
            PlayerDebugLogLevel.INFO,
            TAG,
            "Anime4K 属性已核验 shaderCount=${shaderPayloads.size} " +
                "files=${shaderPayloads.joinToString("|") { file -> file.name }.ifEmpty { "none" }}",
        )
        appliedShaderFiles = shaderFiles.toList()
    }

    fun applyVideoFitMode(mode: PlayerVideoFitMode) = callMpv("切换画面比例") {
        videoFitMode = mode
        applyVideoFitModeProperties(mode)
    }

    private fun applyVideoFitModeProperties(mode: PlayerVideoFitMode) {
        when (mode) {
            PlayerVideoFitMode.FIT -> {
                MPVLib.setPropertyDouble("panscan", 0.0)
                MPVLib.setPropertyDouble("video-aspect-override", -1.0)
            }
            PlayerVideoFitMode.STRETCH -> {
                val ratio = resolvePlayerSurfaceAspectRatio(surfaceWidth, surfaceHeight)
                if (ratio == null) {
                    diagnostic(
                        PlayerDebugLogLevel.DEBUG,
                        TAG,
                        "等待有效 Surface 几何后应用拉伸画面",
                    )
                    return
                }
                MPVLib.setPropertyDouble("panscan", 0.0)
                MPVLib.setPropertyDouble("video-aspect-override", ratio)
            }
            PlayerVideoFitMode.CROP -> {
                MPVLib.setPropertyDouble("video-aspect-override", -1.0)
                MPVLib.setPropertyDouble("panscan", 1.0)
            }
        }
    }

    fun captureScreenshot(path: String) = callMpv("截取视频画面") {
        MPVLib.command("screenshot-to-file", path, "video")
    }

    fun grabThumbnail(
        source: String,
        positionSeconds: Double,
        maxSize: Int,
    ): Bitmap? = callMpv("提取进度缩略图") {
        check(source.isNotBlank()) { "Player thumbnail source is blank" }
        require(positionSeconds.isFinite() && positionSeconds >= 0.0) {
            "Player thumbnail position is invalid"
        }
        require(maxSize in 64..512) { "Player thumbnail size is invalid" }
        MPVLib.grabThumbnailFast(source, positionSeconds, maxSize)
    }

    fun prepareThumbnailSource(
        source: String,
        positionSeconds: Double,
    ): MpvThumbnailSource = callMpv("准备进度缩略图来源") {
        check(source.isNotBlank()) { "Player thumbnail source is blank" }
        require(positionSeconds.isFinite() && positionSeconds >= 0.0) {
            "Player thumbnail position is invalid"
        }
        if (!isPlayerNetworkMediaUri(source)) {
            return@callMpv MpvThumbnailSource(source, positionSeconds)
        }
        check(fullVideoCachePhase == PlayerFullVideoCachePhase.COMPLETE) {
            "在线媒体尚未完成完整缓存"
        }
        val duration = MPVLib.getPropertyDouble("duration")
            ?.takeIf { value -> value.isFinite() && value > 0.0 }
            ?: error("完整缓存媒体缺少有效时长")
        val excerptStart =
            (positionSeconds - THUMBNAIL_CACHE_EXCERPT_LEAD_SECONDS).coerceAtLeast(0.0)
        val excerptEnd =
            (positionSeconds + THUMBNAIL_CACHE_EXCERPT_TAIL_SECONDS)
                .coerceAtMost(duration)
                .coerceAtLeast(excerptStart + 0.25)
        val excerptDirectory =
            appContext.cacheDir.resolve(THUMBNAIL_CACHE_EXCERPT_DIRECTORY).also { directory ->
                check(directory.exists() || directory.mkdirs()) {
                    "无法创建完整缓存缩略图目录"
                }
            }
        val excerptFile =
            File.createTempFile(
                "seek-preview-",
                ".mkv",
                excerptDirectory,
            )
        try {
            // 只从唯一 mpv demuxer 的已完成缓存导出目标附近的小片段。直接把远程 URL 交给
            // grabThumbnailFast 会建立缺少浏览器请求头的第二连接，并且无法利用当前完整缓存。
            MPVLib.command(
                "dump-cache",
                excerptStart.toString(),
                excerptEnd.toString(),
                excerptFile.absolutePath,
            )
            check(excerptFile.isFile && excerptFile.length() > 0L) {
                "完整缓存没有导出可用的缩略图片段"
            }
            MpvThumbnailSource(
                source = excerptFile.absolutePath,
                positionSeconds = positionSeconds - excerptStart,
                temporaryFile = excerptFile,
            )
        } catch (error: Exception) {
            excerptFile.delete()
            throw error
        }
    }

    fun readProgress(): MpvPlayerProgress = callMpv("读取播放状态") {
        val fullCacheState = readFullVideoCacheState()
        if (fullCacheState.complete && !fullCacheCompletionLogged) {
            fullCacheCompletionLogged = true
            diagnostic(
                PlayerDebugLogLevel.INFO,
                TAG,
                "完整缓存已确认 bofCached=true eofCached=true ranges=1",
            )
        }
        MpvPlayerProgress(
            positionSeconds = MPVLib.getPropertyDouble("time-pos"),
            durationSeconds = MPVLib.getPropertyDouble("duration"),
            paused = MPVLib.getPropertyBoolean("pause"),
            buffering = MPVLib.getPropertyBoolean("paused-for-cache"),
            speed = MPVLib.getPropertyDouble("speed"),
            networkSpeedBytesPerSecond =
                MPVLib.getPropertyInt("cache-speed")?.toLong()?.coerceAtLeast(0L) ?: 0L,
            fullVideoCacheActive = fullCacheState.active,
            fullVideoCacheComplete = fullCacheState.complete,
            fullVideoCacheStartSeconds = fullCacheState.startSeconds,
            fullVideoCacheEndSeconds = fullCacheState.endSeconds,
            fullVideoCachePhase = fullCacheState.phase.name,
            fullVideoCacheReason =
                fullCacheState.reason.takeUnless { it == PlayerFullVideoCacheReason.NONE }?.name,
            fullVideoCacheStateEvidence = fullCacheState.stateEvidence.name,
            fullVideoCacheFileBytes = fullCacheState.fileCacheBytes,
            fullVideoCacheExpectedBytes = fullCacheState.expectedFileBytes,
        )
    }

    fun readTracks(): MpvPlayerTrackSnapshot = callMpv("读取媒体轨道") {
        val audioTracks = mutableListOf<PlayerTrack>()
        val subtitleTracks = mutableListOf<PlayerTrack>()
        var videoTrackCount = 0
        var selectedVideoTrackIndex: Int? = null
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        repeat(count) { index ->
            val type = MPVLib.getPropertyString("track-list/$index/type") ?: return@repeat
            val id = MPVLib.getPropertyInt("track-list/$index/id") ?: return@repeat
            val language = MPVLib.getPropertyString("track-list/$index/lang")
            val title =
                MPVLib.getPropertyString("track-list/$index/title")
                    ?.takeIf(String::isNotBlank)
                    ?: language?.takeIf(String::isNotBlank)
                    ?: "轨道 $id"
            val selected = MPVLib.getPropertyBoolean("track-list/$index/selected") == true
            val track = PlayerTrack(id, title, language, selected)
            when (type) {
                "video" -> {
                    videoTrackCount += 1
                    if (selected) {
                        selectedVideoTrackIndex = index
                    }
                }
                "audio" -> audioTracks += track
                "sub" -> subtitleTracks += track
            }
        }
        val chapters = mutableListOf<PlayerChapter>()
        val chapterCount = MPVLib.getPropertyInt("chapter-list/count") ?: 0
        repeat(chapterCount) { index ->
            val startSeconds =
                MPVLib.getPropertyDouble("chapter-list/$index/time")
                    ?.takeIf { value -> value.isFinite() && value >= 0.0 }
                    ?: return@repeat
            val title =
                MPVLib.getPropertyString("chapter-list/$index/title")
                    ?.takeIf(String::isNotBlank)
                    ?: "章节 ${index + 1}"
            chapters += PlayerChapter(title = title, startSeconds = startSeconds)
        }
        configureFullVideoCacheAfterLoad(videoTrackCount)
        return MpvPlayerTrackSnapshot(
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            chapters = chapters.sortedBy(PlayerChapter::startSeconds),
            fileFormat = MPVLib.getPropertyString("file-format"),
            videoCodec = MPVLib.getPropertyString("video-codec"),
            audioCodec = MPVLib.getPropertyString("audio-codec-name"),
            videoTrackCount = videoTrackCount,
            activeHardwareDecoder =
                normalizePlayerActiveHardwareDecoder(
                    MPVLib.getPropertyString("hwdec-current"),
                ),
            videoPixelFormat =
                MPVLib.getPropertyString("video-params/pixelformat")
                    ?.takeIf(String::isNotBlank),
            videoCodecProfile =
                selectedVideoTrackIndex
                    ?.let { index -> MPVLib.getPropertyString("track-list/$index/codec-profile") }
                    ?.takeIf(String::isNotBlank),
        )
    }

    fun readMediaIdentity(): MpvPlayerMediaIdentitySnapshot = callMpv("读取媒体身份") {
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        var videoTrackCount = 0
        var selectedVideoTrackIndex: Int? = null
        repeat(count) { index ->
            if (MPVLib.getPropertyString("track-list/$index/type") == "video") {
                videoTrackCount += 1
                if (MPVLib.getPropertyBoolean("track-list/$index/selected") == true) {
                    selectedVideoTrackIndex = index
                }
            }
        }
        MpvPlayerMediaIdentitySnapshot(
            fileFormat = MPVLib.getPropertyString("file-format"),
            videoCodec = MPVLib.getPropertyString("video-codec"),
            audioCodec = MPVLib.getPropertyString("audio-codec-name"),
            videoTrackCount = videoTrackCount,
            activeHardwareDecoder =
                normalizePlayerActiveHardwareDecoder(
                    MPVLib.getPropertyString("hwdec-current"),
                ),
            videoPixelFormat =
                MPVLib.getPropertyString("video-params/pixelformat")
                    ?.takeIf(String::isNotBlank),
            videoCodecProfile =
                selectedVideoTrackIndex
                    ?.let { index -> MPVLib.getPropertyString("track-list/$index/codec-profile") }
                    ?.takeIf(String::isNotBlank),
        )
    }

    private fun readRuntimeCapabilitySnapshot(): PlayerRuntimeCapabilitySnapshot {
        val hardwareDecoderMetadata =
            parsePlayerRuntimeHardwareDecoderMetadata(
                MPVLib.getPropertyNode("option-info/hwdec"),
            )
        if (hardwareDecoderMetadata.evidence.malformed) {
            diagnostic(
                PlayerDebugLogLevel.WARN,
                TAG,
                "mpv hwdec 能力元数据结构异常 property=option-info/hwdec " +
                    "evidence=${hardwareDecoderMetadata.evidence.diagnosticValue}",
            )
        }
        return buildPlayerRuntimeCapabilitySnapshot(
            mpvVersion = requireRuntimeStringProperty("mpv-version"),
            ffmpegVersion = requireRuntimeStringProperty("ffmpeg-version"),
            availableProtocols = requireRuntimeStringArrayProperty("protocol-list"),
            availableDemuxers = requireRuntimeStringArrayProperty("demuxer-lavf-list"),
            availableDecoderCodecs = requireRuntimeDecoderCodecs(),
            hardwareDecoderMetadata = hardwareDecoderMetadata,
        )
    }

    private fun requireRuntimeStringProperty(name: String): String =
        requireNotNull(MPVLib.getPropertyString(name)) {
            "mpv runtime property is unavailable: $name"
        }

    private fun requireRuntimeStringArrayProperty(name: String): Set<String> {
        val values =
            requireNotNull(MPVLib.getPropertyNode(name)?.asArray()) {
                "mpv runtime array property is unavailable: $name"
            }
        return values
            .mapIndexed { index, node ->
                requireNotNull(node.asString()) {
                    "mpv runtime array property contains a non-string value: $name[$index]"
                }
            }.toSet()
    }

    private fun requireRuntimeDecoderCodecs(): Set<String> {
        val decoders =
            requireNotNull(MPVLib.getPropertyNode("decoder-list")?.asArray()) {
                "mpv runtime decoder-list is unavailable"
            }
        return decoders
            .mapIndexed { index, decoder ->
                requireNotNull(decoder["codec"]?.asString()) {
                    "mpv runtime decoder-list entry is missing codec: index=$index"
                }
            }.toSet()
    }

    fun destroy(): Unit = callMpv("销毁内核") {
        if (!initialized) return
        check(attachedSurface == null) {
            "PlayerSession must detach the active Surface before destroying mpv"
        }
        MPVLib.command("stop")
        MPVLib.removeObserver(this)
        MPVLib.removeLogObserver(this)
        MPVLib.destroy()
        initialized = false
        clearSessionCacheDirectory()
        diagnostic(PlayerDebugLogLevel.INFO, TAG, "销毁 mpv 内核")
    }

    private fun setRequiredOption(name: String, value: String) {
        val result = MPVLib.setOptionString(name, value)
        check(result >= 0) { "mpv rejected option $name" }
    }

    private fun setNetworkCacheOptions(policy: PlayerNetworkCachePolicy) {
        setRequiredOption("demuxer-max-bytes", policy.forwardBytes.toString())
        setRequiredOption("demuxer-max-back-bytes", policy.backwardBytes.toString())
        setRequiredOption("cache-secs", policy.cacheSeconds.toString())
    }

    private fun setPreciseSeekingOptions(enabled: Boolean) {
        setRequiredOption("hr-seek", if (enabled) "yes" else "no")
        setRequiredOption("hr-seek-framedrop", "yes")
    }

    private fun setDecoderBackendOptions(backend: PlayerDecoderBackend) {
        setRequiredOption("hwdec", backend.mpvValue)
        if (backend.hardwareAccelerated) {
            setRequiredOption("hwdec-codecs", "all")
        }
    }

    private fun applyNetworkCacheForRequest(
        requestId: String,
        target: String,
        policy: PlayerNetworkCachePolicy,
    ) {
        clearSessionCacheDirectory()
        fullVideoCacheRequested = policy.usesSessionDiskCache
        fullVideoCachePolicy = policy
        fullVideoCacheExpectedBytes = null
        fullVideoCachePhase = PlayerFullVideoCachePhase.DISABLED
        fullVideoCacheReason = PlayerFullVideoCacheReason.NONE
        fullCacheCompletionLogged = false
        lastFullCacheStorageCheckElapsedMillis = 0L
        lastFullCacheStateReadElapsedMillis = 0L
        lastFullVideoCacheStateEvidence = PlayerFullVideoCacheStateEvidence.NOT_APPLICABLE
        cachedFullVideoCacheState = currentFullVideoCacheState()
        applyNetworkCache(policy)
        val isNetworkRequest =
            target.startsWith("https://", ignoreCase = true) ||
                target.startsWith("http://", ignoreCase = true)
        if (!fullVideoCacheRequested) {
            MPVLib.setPropertyString("cache-on-disk", "no")
            return
        }
        if (!isNetworkRequest) {
            MPVLib.setPropertyString("cache-on-disk", "no")
            fullVideoCachePhase = PlayerFullVideoCachePhase.INELIGIBLE
            fullVideoCacheReason = PlayerFullVideoCacheReason.NOT_NETWORK
            return
        }
        val cacheDirectory =
            runCatching {
                val canonicalRoot = requireFullVideoCacheRoot(create = true)
                val resolved =
                    canonicalRoot.resolve(shortPlayerDiagnosticId(requestId)).canonicalFile
                check(resolved.toPath().startsWith(canonicalRoot.toPath())) {
                    "播放器完整缓存目录越界"
                }
                check(resolved.mkdirs() || resolved.isDirectory) {
                    "无法创建播放器完整缓存目录"
                }
                check(!Files.isSymbolicLink(resolved.toPath()) && resolved.canWrite()) {
                    "播放器完整缓存目录不安全或不可写"
                }
                resolved
            }.getOrElse { error ->
                MPVLib.setPropertyString("cache-on-disk", "no")
                fullVideoCachePhase = PlayerFullVideoCachePhase.INELIGIBLE
                fullVideoCacheReason = PlayerFullVideoCacheReason.PREPARATION_ERROR
                diagnostic(
                    PlayerDebugLogLevel.WARN,
                    TAG,
                    "完整缓存准备失败：${error.message ?: error.javaClass.simpleName}",
                )
                return
            }
        val availableBytes = StatFs(cacheDirectory.absolutePath).availableBytes
        if (availableBytes < PLAYER_FULL_CACHE_MINIMUM_FREE_BYTES) {
            cacheDirectory.delete()
            MPVLib.setPropertyString("cache-on-disk", "no")
            fullVideoCachePhase = PlayerFullVideoCachePhase.INELIGIBLE
            fullVideoCacheReason = PlayerFullVideoCacheReason.SPACE_INSUFFICIENT
            diagnostic(
                PlayerDebugLogLevel.WARN,
                TAG,
                "完整缓存未启动：可用空间低于 1 GiB",
            )
            return
        }
        sessionCacheDirectory = cacheDirectory
        fullVideoCachePhase = PlayerFullVideoCachePhase.PREPARING
        MPVLib.setPropertyString("cache-on-disk", "yes")
        MPVLib.setPropertyString("demuxer-cache-dir", cacheDirectory.absolutePath)
        MPVLib.setPropertyString("demuxer-cache-unlink-files", "immediate")
        diagnostic(
            PlayerDebugLogLevel.INFO,
            TAG,
            "完整缓存准备中 request=${shortPlayerDiagnosticId(requestId)} " +
                "metadataForward=${policy.forwardBytes} metadataBack=${policy.backwardBytes} " +
                "cacheSeconds=${policy.cacheSeconds}",
        )
    }

    private fun configureFullVideoCacheAfterLoad(videoTrackCount: Int) {
        val cacheDirectory = sessionCacheDirectory ?: return
        if (!fullVideoCacheRequested || fullVideoCachePhase != PlayerFullVideoCachePhase.PREPARING) {
            return
        }
        val plan =
            resolvePlayerFullVideoCachePlan(
                PlayerFullVideoCacheQualification(
                    viaNetwork = MPVLib.getPropertyBoolean("demuxer-via-network"),
                    videoTrackCount = videoTrackCount,
                    fileFormat = MPVLib.getPropertyString("file-format"),
                    seekable = MPVLib.getPropertyBoolean("seekable"),
                    partiallySeekable = MPVLib.getPropertyBoolean("partially-seekable"),
                    durationSeconds = MPVLib.getPropertyDouble("duration"),
                    fileSizeBytes = MPVLib.getPropertyLong("file-size"),
                    availableBytes = StatFs(cacheDirectory.absolutePath).availableBytes,
                    selectedForwardBytes = fullVideoCachePolicy.forwardBytes,
                    selectedBackwardBytes = fullVideoCachePolicy.backwardBytes,
                    selectedCacheSeconds = fullVideoCachePolicy.cacheSeconds,
                ),
            )
        fullVideoCachePhase = plan.phase
        fullVideoCacheReason = plan.reason
        fullVideoCacheExpectedBytes = plan.expectedFileBytes
        lastFullCacheStateReadElapsedMillis = 0L
        lastFullVideoCacheStateEvidence =
            if (plan.active) {
                PlayerFullVideoCacheStateEvidence.UNAVAILABLE
            } else {
                PlayerFullVideoCacheStateEvidence.NOT_APPLICABLE
            }
        cachedFullVideoCacheState = currentFullVideoCacheState()
        MPVLib.setPropertyString("demuxer-max-bytes", plan.metadataForwardBytes.toString())
        MPVLib.setPropertyString("demuxer-max-back-bytes", plan.metadataBackwardBytes.toString())
        MPVLib.setPropertyString("cache-secs", plan.cacheSeconds.toString())
        if (plan.active) {
            diagnostic(
                PlayerDebugLogLevel.INFO,
                TAG,
                "完整缓存已激活 expectedBytes=${plan.expectedFileBytes} " +
                    "requiredFreeBytes=${plan.requiredFreeBytes} " +
                    "metadataForward=${plan.metadataForwardBytes} " +
                    "metadataBack=${plan.metadataBackwardBytes} " +
                    "cacheSeconds=${plan.cacheSeconds}",
            )
        } else {
            MPVLib.setPropertyString("cache-on-disk", "no")
            clearSessionCacheDirectory(resetState = false)
            diagnostic(
                PlayerDebugLogLevel.INFO,
                TAG,
                "完整缓存资格未满足 reason=${plan.reason.name}；保持 FULL_VIDEO 基础播放缓存 " +
                    "metadataForward=${plan.metadataForwardBytes} " +
                    "metadataBack=${plan.metadataBackwardBytes} " +
                    "cacheSeconds=${plan.cacheSeconds}",
            )
        }
    }

    private fun clearStaleFullVideoCacheDirectories() {
        val cacheRoot =
            appContext.noBackupFilesDir
                .resolve("player")
                .resolve("mpv-session-cache")
        if (!cacheRoot.exists()) return
        try {
            val canonicalRoot = requireFullVideoCacheRoot(create = false)
            val entries =
                requireNotNull(canonicalRoot.listFiles()) {
                    "无法列出播放器完整缓存根目录"
                }
            entries.forEach { entry ->
                check(!Files.isSymbolicLink(entry.toPath())) {
                    "播放器完整缓存残留不能是符号链接"
                }
                val canonicalEntry = entry.canonicalFile
                check(canonicalEntry.parentFile == canonicalRoot) {
                    "播放器完整缓存残留目录越界"
                }
                check(canonicalEntry.deleteRecursively()) {
                    "播放器完整缓存残留清理失败"
                }
            }
            if (entries.isNotEmpty()) {
                diagnostic(
                    PlayerDebugLogLevel.INFO,
                    TAG,
                    "已清理播放器完整缓存残留 count=${entries.size}",
                )
            }
        } catch (error: Exception) {
            // 残留清理失败不能冒充成功；保留明确诊断，后续新 request 仍校验自己的目录。
            diagnostic(
                PlayerDebugLogLevel.WARN,
                TAG,
                "播放器完整缓存残留清理失败：${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    private fun clearSeekPreviewExcerptDirectory() {
        val directory = appContext.cacheDir.resolve(THUMBNAIL_CACHE_EXCERPT_DIRECTORY)
        if (!directory.exists()) return
        try {
            check(!Files.isSymbolicLink(directory.toPath())) {
                "播放器缩略图片段目录不能是符号链接"
            }
            val canonicalRoot = appContext.cacheDir.canonicalFile
            val canonicalDirectory = directory.canonicalFile
            check(canonicalDirectory.parentFile == canonicalRoot) {
                "播放器缩略图片段目录越界"
            }
            check(canonicalDirectory.deleteRecursively()) {
                "播放器缩略图片段残留清理失败"
            }
            diagnostic(
                PlayerDebugLogLevel.INFO,
                TAG,
                "已清理播放器缩略图片段残留",
            )
        } catch (error: Exception) {
            diagnostic(
                PlayerDebugLogLevel.WARN,
                TAG,
                "播放器缩略图片段残留清理失败：" +
                    (error.message ?: error.javaClass.simpleName),
            )
        }
    }

    private fun requireFullVideoCacheRoot(create: Boolean): File {
        val cacheRoot =
            appContext.noBackupFilesDir
                .resolve("player")
                .resolve("mpv-session-cache")
        if (create) {
            check(cacheRoot.mkdirs() || cacheRoot.isDirectory) {
                "无法创建播放器完整缓存根目录"
            }
        } else {
            check(cacheRoot.isDirectory) {
                "播放器完整缓存根目录不存在或不是目录"
            }
        }
        check(!Files.isSymbolicLink(cacheRoot.toPath())) {
            "播放器完整缓存根目录不能是符号链接"
        }
        val canonicalNoBackupRoot = appContext.noBackupFilesDir.canonicalFile
        val canonicalRoot = cacheRoot.canonicalFile
        check(canonicalRoot.toPath().startsWith(canonicalNoBackupRoot.toPath())) {
            "播放器完整缓存根目录越界"
        }
        check(canonicalRoot.canWrite()) {
            "播放器完整缓存根目录不可写"
        }
        return canonicalRoot
    }

    private fun clearSessionCacheDirectory(resetState: Boolean = true) {
        val directory = sessionCacheDirectory
        sessionCacheDirectory = null
        if (directory != null) {
            try {
                val root = requireFullVideoCacheRoot(create = false)
                val canonicalDirectory = directory.canonicalFile
                check(canonicalDirectory.parentFile == root) {
                    "播放器完整缓存清理目录越界"
                }
                check(!Files.isSymbolicLink(canonicalDirectory.toPath())) {
                    "播放器完整缓存清理目录不能是符号链接"
                }
                check(canonicalDirectory.deleteRecursively()) {
                    "播放器完整缓存目录清理失败"
                }
            } catch (error: Exception) {
                diagnostic(
                    PlayerDebugLogLevel.WARN,
                    TAG,
                    "完整缓存目录清理失败：${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
        if (resetState) {
            fullVideoCachePhase = PlayerFullVideoCachePhase.DISABLED
            fullVideoCacheReason = PlayerFullVideoCacheReason.NONE
            fullVideoCacheExpectedBytes = null
        }
        lastFullVideoCacheStateEvidence = PlayerFullVideoCacheStateEvidence.NOT_APPLICABLE
        fullCacheCompletionLogged = false
        lastFullCacheStorageCheckElapsedMillis = 0L
        lastFullCacheStateReadElapsedMillis = 0L
        cachedFullVideoCacheState = currentFullVideoCacheState()
    }

    private fun readFullVideoCacheState(): MpvFullVideoCacheState {
        val stateReadAt = SystemClock.elapsedRealtime()
        if (
            lastFullCacheStateReadElapsedMillis != 0L &&
                stateReadAt - lastFullCacheStateReadElapsedMillis <
                FULL_CACHE_STATE_READ_INTERVAL_MS
        ) {
            return cachedFullVideoCacheState
        }
        lastFullCacheStateReadElapsedMillis = stateReadAt
        val cacheDirectory = sessionCacheDirectory
        if (cacheDirectory == null) {
            return currentFullVideoCacheState().also {
                cachedFullVideoCacheState = it
            }
        }
        val state = MPVLib.getPropertyNode("demuxer-cache-state")
        val stateMap = state?.asMap()
        val rangeArray = stateMap?.get("seekable-ranges")?.asArray()
        val observation =
            resolvePlayerFullVideoCacheStateObservation(
                when {
                    state == null -> null
                    stateMap == null ->
                        PlayerFullVideoCacheStateInput(
                            structurallyValid = false,
                            fileCacheBytes = null,
                            bofCached = null,
                            eofCached = null,
                            seekableRanges = null,
                        )
                    else ->
                        PlayerFullVideoCacheStateInput(
                            structurallyValid = true,
                            fileCacheBytes = stateMap["file-cache-bytes"]?.asInt(),
                            bofCached = stateMap["bof-cached"]?.asBoolean(),
                            eofCached = stateMap["eof-cached"]?.asBoolean(),
                            seekableRanges =
                                rangeArray?.map { range ->
                                    PlayerFullVideoCacheRangeInput(
                                        startSeconds = range["start"]?.asDouble(),
                                        endSeconds = range["end"]?.asDouble(),
                                    )
                                },
                        )
                },
            )
        updateFullVideoCacheStateEvidence(observation.evidence)
        if (observation.evidence != PlayerFullVideoCacheStateEvidence.AVAILABLE) {
            val unavailableState =
                if (fullVideoCachePhase == PlayerFullVideoCachePhase.COMPLETE) {
                    cachedFullVideoCacheState.copy(stateEvidence = observation.evidence)
                } else {
                    currentFullVideoCacheState()
                }
            cachedFullVideoCacheState = unavailableState
            return unavailableState
        }
        val fileCacheBytes = observation.fileCacheBytes
        if (playerFullVideoCacheFileLimitExceeded(fileCacheBytes)) {
            terminateFullVideoCache(
                PlayerFullVideoCacheReason.FILE_LIMIT_REACHED,
                "完整缓存已停止：磁盘缓存超过 20 GiB 上限",
            )
        }
        val now = SystemClock.elapsedRealtime()
        if (
            lastFullCacheStorageCheckElapsedMillis == 0L ||
                now - lastFullCacheStorageCheckElapsedMillis >= FULL_CACHE_STORAGE_CHECK_INTERVAL_MS
        ) {
            lastFullCacheStorageCheckElapsedMillis = now
            if (
                playerFullVideoCacheStorageFloorReached(
                    StatFs(cacheDirectory.absolutePath).availableBytes,
                )
            ) {
                terminateFullVideoCache(
                    PlayerFullVideoCacheReason.STORAGE_FLOOR_REACHED,
                    "完整缓存已停止：可用空间低于 512 MiB 安全线",
                )
            }
        }
        val ranges = observation.seekableRanges
        val bofCached = observation.bofCached
        val eofCached = observation.eofCached
        val currentCompletionProof = bofCached && eofCached && ranges.size == 1
        if (
            fullVideoCachePhase == PlayerFullVideoCachePhase.COMPLETE &&
                !currentCompletionProof
        ) {
            // COMPLETE is entered only after one authoritative bof/eof/single-range proof.
            // A transiently incomplete cache-state node must not revoke that session fact.
            return cachedFullVideoCacheState
                .copy(stateEvidence = PlayerFullVideoCacheStateEvidence.AVAILABLE)
                .also { state ->
                    cachedFullVideoCacheState = state
                }
        }
        val complete =
            isPlayerFullVideoCacheComplete(
                phase = fullVideoCachePhase,
                bofCached = bofCached,
                eofCached = eofCached,
                seekableRanges = ranges,
            )
        if (complete) {
            fullVideoCachePhase = PlayerFullVideoCachePhase.COMPLETE
        }
        return MpvFullVideoCacheState(
            phase = fullVideoCachePhase,
            reason = fullVideoCacheReason,
            complete = complete,
            startSeconds = ranges.singleOrNull()?.first,
            endSeconds = ranges.singleOrNull()?.second,
            fileCacheBytes = fileCacheBytes,
            expectedFileBytes = fullVideoCacheExpectedBytes,
            stateEvidence = PlayerFullVideoCacheStateEvidence.AVAILABLE,
        ).also {
            cachedFullVideoCacheState = it
        }
    }

    private fun currentFullVideoCacheState(): MpvFullVideoCacheState =
        MpvFullVideoCacheState(
            phase = fullVideoCachePhase,
            reason = fullVideoCacheReason,
            complete = fullVideoCachePhase == PlayerFullVideoCachePhase.COMPLETE,
            startSeconds = null,
            endSeconds = null,
            fileCacheBytes = 0L,
            expectedFileBytes = fullVideoCacheExpectedBytes,
            stateEvidence = lastFullVideoCacheStateEvidence,
        )

    private fun updateFullVideoCacheStateEvidence(
        evidence: PlayerFullVideoCacheStateEvidence,
    ) {
        if (lastFullVideoCacheStateEvidence == evidence) return
        lastFullVideoCacheStateEvidence = evidence
        diagnostic(
            if (
                evidence == PlayerFullVideoCacheStateEvidence.UNAVAILABLE ||
                    evidence == PlayerFullVideoCacheStateEvidence.MALFORMED
            ) {
                PlayerDebugLogLevel.WARN
            } else {
                PlayerDebugLogLevel.DEBUG
            },
            TAG,
            "完整缓存状态证据 evidence=${evidence.name}",
        )
    }

    private fun terminateFullVideoCache(
        reason: PlayerFullVideoCacheReason,
        message: String,
    ): Nothing {
        fullVideoCachePhase = PlayerFullVideoCachePhase.TERMINATED
        fullVideoCacheReason = reason
        MPVLib.command("stop")
        clearSessionCacheDirectory(resetState = false)
        throw IllegalStateException(message)
    }

    private fun setVolumeBoostOptions(enabled: Boolean) {
        setRequiredOption(
            "volume-max",
            if (enabled) MAX_BOOSTED_VOLUME_PERCENT.toInt().toString()
            else NORMAL_VOLUME_PERCENT.toInt().toString(),
        )
        setRequiredOption(
            "volume",
            if (enabled) BOOSTED_VOLUME_PERCENT.toInt().toString()
            else NORMAL_VOLUME_PERCENT.toInt().toString(),
        )
    }

    private fun applyRequestHeaders(headers: Map<String, String>) {
        val serialized =
            headers.entries.joinToString(",") { (name, value) ->
                "$name: ${escapeMpvListValue(value)}"
            }
        MPVLib.setPropertyString("http-header-fields", serialized)
    }

    private fun escapeMpvListValue(value: String): String =
        value.replace("\\", "\\\\").replace(",", "\\,")

    override fun eventProperty(property: String) = Unit

    override fun eventProperty(property: String, value: Long) = Unit

    override fun eventProperty(property: String, value: Boolean) {
        listener.onBooleanProperty(property, value)
    }

    override fun eventProperty(property: String, value: String) = Unit

    override fun eventProperty(property: String, value: Double) {
        listener.onDoubleProperty(property, value)
    }

    override fun eventProperty(property: String, value: MPVNode) = Unit

    override fun event(eventId: Int, data: MPVNode) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_START_FILE,
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED,
            MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG,
            MPVLib.MpvEvent.MPV_EVENT_AUDIO_RECONFIG,
            MPVLib.MpvEvent.MPV_EVENT_SEEK,
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART,
            MPVLib.MpvEvent.MPV_EVENT_END_FILE,
            MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN,
            ->
                diagnostic(
                    PlayerDebugLogLevel.DEBUG,
                    TAG,
                    "mpv event=${mpvEventName(eventId)} id=$eventId",
                )
        }
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                diagnostic(PlayerDebugLogLevel.INFO, TAG, "媒体文件已加载")
                listener.onFileLoaded()
            }
            MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG -> {
                listener.onVideoReconfigured()
            }
            MPVLib.MpvEvent.MPV_EVENT_SEEK -> {
                diagnostic(PlayerDebugLogLevel.INFO, TAG, "mpv 发生 seek event")
                listener.onSeek()
            }
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                diagnostic(PlayerDebugLogLevel.INFO, TAG, "媒体已恢复输出")
                listener.onPlaybackRestart()
            }
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                val endFile =
                    resolvePlayerMpvEndFileState(
                        reason = data["reason"]?.asString(),
                        fileError = data["file_error"]?.asString(),
                    )
                diagnostic(
                    if (endFile.failed) {
                        PlayerDebugLogLevel.ERROR
                    } else {
                        PlayerDebugLogLevel.INFO
                    },
                    TAG,
                    "媒体结束 reason=${endFile.reason ?: "unknown"} " +
                        "fileError=${endFile.fileError ?: "none"}",
                )
                if (endFile.failed) {
                    listener.onRuntimeError(
                        firstPlaybackFailure?.userMessage
                            ?: "mpv 无法继续播放：${endFile.fileError ?: "unknown"}",
                    )
                }
            }
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        val message = text.trim().takeIf(String::isNotEmpty) ?: return
        if (firstPlaybackFailure == null) {
            classifyPlayerPlaybackFailureLog(message)?.let { evidence ->
                firstPlaybackFailure = evidence
                diagnostic(
                    PlayerDebugLogLevel.WARN,
                    TAG,
                    "播放失败证据 kind=${evidence.kind.name}",
                )
            }
        }
        diagnostic(
            level = mpvLogLevel(level),
            tag = "mpv.${prefix.ifBlank { "core" }}",
            message = "[${mpvLogLevelName(level)}] $message",
        )
    }

    private inline fun <T> callMpv(operation: String, block: () -> T): T =
        try {
            block()
        } catch (error: LinkageError) {
            diagnostic(
                PlayerDebugLogLevel.ERROR,
                TAG,
                "$operation 失败：${error.javaClass.simpleName}: ${error.message.orEmpty()}",
            )
            Log.e(TAG, "$operation 失败", error)
            throw MpvRuntimeException(operation, error)
        }

    private fun diagnostic(
        level: PlayerDebugLogLevel,
        tag: String,
        message: String,
    ) {
        val sanitized = sanitizePlayerDiagnosticMessage(message)
        if (sanitized.isBlank()) return
        when (level) {
            PlayerDebugLogLevel.DEBUG -> Log.d(tag, sanitized)
            PlayerDebugLogLevel.INFO -> Log.i(tag, sanitized)
            PlayerDebugLogLevel.WARN -> Log.w(tag, sanitized)
            PlayerDebugLogLevel.ERROR -> Log.e(tag, sanitized)
        }
        listener.onDiagnosticLog(level, tag, sanitized)
    }

    private fun mpvLogLevel(level: Int): PlayerDebugLogLevel =
        when {
            level <= MPV_LOG_LEVEL_ERROR -> PlayerDebugLogLevel.ERROR
            level <= MPV_LOG_LEVEL_WARN -> PlayerDebugLogLevel.WARN
            level <= MPV_LOG_LEVEL_INFO -> PlayerDebugLogLevel.INFO
            else -> PlayerDebugLogLevel.DEBUG
        }

    private fun mpvLogLevelName(level: Int): String =
        when (level) {
            0 -> "none"
            10 -> "fatal"
            20 -> "error"
            30 -> "warn"
            40 -> "info"
            50 -> "verbose"
            60 -> "debug"
            70 -> "trace"
            else -> level.toString()
        }

    private fun mpvEventName(eventId: Int): String =
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN -> "SHUTDOWN"
            MPVLib.MpvEvent.MPV_EVENT_START_FILE -> "START_FILE"
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> "END_FILE"
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> "FILE_LOADED"
            MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG -> "VIDEO_RECONFIG"
            MPVLib.MpvEvent.MPV_EVENT_AUDIO_RECONFIG -> "AUDIO_RECONFIG"
            MPVLib.MpvEvent.MPV_EVENT_SEEK -> "SEEK"
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> "PLAYBACK_RESTART"
            else -> "UNKNOWN"
        }

    private companion object {
        const val TAG = "MpvPlayerEngine"
        const val THUMBNAIL_CACHE_EXCERPT_DIRECTORY = "player-seek-preview"
        const val THUMBNAIL_CACHE_EXCERPT_LEAD_SECONDS = 4.0
        const val THUMBNAIL_CACHE_EXCERPT_TAIL_SECONDS = 1.5
        const val VIDEO_OUTPUT_GPU = "gpu"
        const val VIDEO_OUTPUT_GPU_NEXT = "gpu-next"
        const val GPU_CONTEXT_OPENGL = "android"
        const val GPU_CONTEXT_VULKAN = "androidvk"
        const val NORMAL_VOLUME_PERCENT = 100.0
        const val BOOSTED_VOLUME_PERCENT = 150.0
        const val MAX_BOOSTED_VOLUME_PERCENT = 300.0
        const val MPV_LOG_LEVEL_ERROR = 20
        const val MPV_LOG_LEVEL_WARN = 30
        const val MPV_LOG_LEVEL_INFO = 40
        const val FULL_CACHE_STATE_READ_INTERVAL_MS = 1_000L
        const val FULL_CACHE_STORAGE_CHECK_INTERVAL_MS = 5_000L
    }
}
