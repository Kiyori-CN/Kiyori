package com.ai.assistance.operit.core.player

import android.content.Context
import android.view.Surface
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode

internal data class MpvPlayerProgress(
    val positionSeconds: Double?,
    val durationSeconds: Double?,
    val paused: Boolean?,
    val speed: Double?,
    val networkSpeedBytesPerSecond: Long,
)

internal data class MpvPlayerTrackSnapshot(
    val audioTracks: List<PlayerTrack>,
    val subtitleTracks: List<PlayerTrack>,
)

internal interface MpvPlayerEngineListener {
    fun onBooleanProperty(name: String, value: Boolean)

    fun onDoubleProperty(name: String, value: Double)

    fun onFileLoaded()

    fun onRuntimeError(message: String)
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
) : MPVLib.EventObserver {
    private val appContext = context.applicationContext
    private var initialized = false
    private var attachedSurface: Surface? = null
    private var videoOutput = VIDEO_OUTPUT_GPU

    fun initialize(settings: PlayerSettings): Unit = callMpv("初始化") {
        if (initialized) return
        PlayerDebugLogBuffer.append("MpvPlayerEngine", "初始化 mpv 内核")
        MPVLib.create(appContext)
        setRequiredOption("config", "no")
        setRequiredOption("profile", settings.decoderPreset.persistedId)
        videoOutput =
            if (settings.gpuNextEnabled) VIDEO_OUTPUT_GPU_NEXT else VIDEO_OUTPUT_GPU
        setRequiredOption("vo", videoOutput)
        setRequiredOption(
            "gpu-context",
            if (settings.vulkanEnabled) GPU_CONTEXT_VULKAN else GPU_CONTEXT_OPENGL,
        )
        setRequiredOption("hwdec-codecs", "all")
        setRequiredOption("ao", "audiotrack")
        setRequiredOption("keep-open", "yes")
        setRequiredOption("idle", "yes")
        setRequiredOption("force-window", "no")
        setRequiredOption("cache", "yes")
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
        setRequiredOption("tls-verify", "yes")
        setRequiredOption("gpu-shader-cache-dir", appContext.cacheDir.absolutePath)
        setRequiredOption("icc-cache-dir", appContext.cacheDir.absolutePath)
        setNetworkCacheOptions(settings.networkCachePolicy)
        setPreciseSeekingOptions(settings.preciseSeeking)
        setRequiredOption("sub-scale", settings.subtitleScale.toString())
        setRequiredOption("loop-file", loopFileValue(settings.endBehavior))
        setVolumeBoostOptions(settings.volumeBoostEnabled)
        // The packaged mpvlibAndroid binding initializes the core before Surface callbacks.
        // Attaching a native window here would let VO startup race Android's first Surface frame.
        MPVLib.addObserver(this)
        MPVLib.init()
        MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("speed", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        initialized = true
        PlayerDebugLogBuffer.append("MpvPlayerEngine", "mpv 内核初始化完成")
    }

    fun load(
        target: String,
        headers: Map<String, String>,
        settings: PlayerSettings,
        shaderFiles: List<String>,
        initialSpeed: Double,
    ) = callMpv("加载媒体") {
        check(initialized) { "mpv engine is not initialized" }
        PlayerDebugLogBuffer.append(
            "MpvPlayerEngine",
            "加载媒体 uri=${target.substringBefore('?')}",
        )
        applyRequestHeaders(headers)
        applyDecoderPreset(settings.decoderPreset)
        applyPreciseSeeking(settings.preciseSeeking)
        applyNetworkCache(settings.networkCachePolicy)
        applySubtitleScale(settings.subtitleScale)
        applyEndBehavior(settings.endBehavior)
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
        PlayerDebugLogBuffer.append("MpvPlayerEngine", "连接播放画面 ${width}x$height")
    }

    fun updateSurfaceSize(width: Int, height: Int) = callMpv("更新播放画面尺寸") {
        if (initialized && width > 0 && height > 0) {
            MPVLib.setPropertyString("android-surface-size", "${width}x$height")
        }
    }

    fun detachSurface(): Unit = callMpv("断开播放画面") {
        if (!initialized || attachedSurface == null) return
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setPropertyString("force-window", "no")
        MPVLib.detachSurface()
        attachedSurface = null
        PlayerDebugLogBuffer.append("MpvPlayerEngine", "断开播放画面")
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

    fun applyDecoderPreset(preset: PlayerDecoderPreset) = callMpv("应用解码器预设") {
        MPVLib.command("apply-profile", preset.persistedId)
    }

    fun applyPreciseSeeking(enabled: Boolean) = callMpv("应用精确跳转设置") {
        MPVLib.setPropertyString("hr-seek", if (enabled) "yes" else "no")
        MPVLib.setPropertyString("hr-seek-framedrop", if (enabled) "no" else "yes")
    }

    fun applyNetworkCache(policy: PlayerNetworkCachePolicy) = callMpv("应用网络缓存设置") {
        MPVLib.setPropertyString("demuxer-max-bytes", policy.forwardBytes.toString())
        MPVLib.setPropertyString("demuxer-max-back-bytes", policy.backwardBytes.toString())
        MPVLib.setPropertyString("cache-secs", policy.cacheSeconds.toString())
    }

    fun applySubtitleScale(scale: Double) = callMpv("应用字幕缩放") {
        MPVLib.setPropertyDouble("sub-scale", scale)
    }

    fun applyEndBehavior(behavior: PlayerEndBehavior) = callMpv("应用播放结束行为") {
        MPVLib.setPropertyString("loop-file", loopFileValue(behavior))
    }

    fun applyVolumeBoost(enabled: Boolean) = callMpv("应用音量增强设置") {
        val targetVolume = if (enabled) BOOSTED_VOLUME_PERCENT else NORMAL_VOLUME_PERCENT
        MPVLib.setPropertyDouble("volume-max", if (enabled) MAX_BOOSTED_VOLUME_PERCENT else NORMAL_VOLUME_PERCENT)
        MPVLib.setPropertyDouble("volume", targetVolume)
    }

    fun applyShaders(shaderFiles: List<String>) = callMpv("应用 Anime4K 着色器") {
        MPVLib.setPropertyString("glsl-shaders", shaderFiles.joinToString(":"))
    }

    fun applyVideoFitMode(mode: PlayerVideoFitMode) = callMpv("切换画面比例") {
        when (mode) {
            PlayerVideoFitMode.FIT -> {
                MPVLib.setPropertyDouble("panscan", 0.0)
                MPVLib.setPropertyDouble("video-aspect-override", -1.0)
            }
            PlayerVideoFitMode.STRETCH -> {
                val ratio = appContext.resources.displayMetrics.widthPixels /
                    appContext.resources.displayMetrics.heightPixels.toDouble()
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

    fun readProgress(): MpvPlayerProgress = callMpv("读取播放状态") {
        MpvPlayerProgress(
            positionSeconds = MPVLib.getPropertyDouble("time-pos"),
            durationSeconds = MPVLib.getPropertyDouble("duration"),
            paused = MPVLib.getPropertyBoolean("pause"),
            speed = MPVLib.getPropertyDouble("speed"),
            networkSpeedBytesPerSecond =
                MPVLib.getPropertyInt("cache-speed")?.toLong()?.coerceAtLeast(0L) ?: 0L,
        )
    }

    fun readTracks(): MpvPlayerTrackSnapshot = callMpv("读取媒体轨道") {
        val audioTracks = mutableListOf<PlayerTrack>()
        val subtitleTracks = mutableListOf<PlayerTrack>()
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
                "audio" -> audioTracks += track
                "sub" -> subtitleTracks += track
            }
        }
        return MpvPlayerTrackSnapshot(audioTracks, subtitleTracks)
    }

    fun destroy(): Unit = callMpv("销毁内核") {
        if (!initialized) return
        check(attachedSurface == null) {
            "PlayerSession must detach the active Surface before destroying mpv"
        }
        MPVLib.command("stop")
        MPVLib.removeObserver(this)
        MPVLib.destroy()
        initialized = false
        PlayerDebugLogBuffer.append("MpvPlayerEngine", "销毁 mpv 内核")
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
        setRequiredOption("hr-seek-framedrop", if (enabled) "no" else "yes")
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
                require(name.isNotBlank() && !name.contains(':') && !name.contains('\n') && !name.contains('\r')) {
                    "Invalid player request header name"
                }
                require(!value.contains('\n') && !value.contains('\r')) {
                    "Invalid player request header value"
                }
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
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                PlayerDebugLogBuffer.append("MpvPlayerEngine", "媒体文件已加载")
                listener.onFileLoaded()
            }
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                val reason = data["reason"]?.asInt()
                PlayerDebugLogBuffer.append("MpvPlayerEngine", "媒体结束 reason=${reason ?: "unknown"}")
                if (reason == MPV_END_FILE_REASON_ERROR) {
                    val errorCode = data["error"]?.asInt()
                    listener.onRuntimeError(
                        "mpv 无法继续播放，错误码 ${errorCode ?: "unknown"}",
                    )
                }
            }
        }
    }

    private inline fun <T> callMpv(operation: String, block: () -> T): T =
        try {
            block()
        } catch (error: LinkageError) {
            PlayerDebugLogBuffer.append(
                "MpvPlayerEngine",
                "$operation 失败：${error.message ?: error.javaClass.simpleName}",
            )
            throw MpvRuntimeException(operation, error)
        }

    private fun loopFileValue(behavior: PlayerEndBehavior): String =
        if (behavior == PlayerEndBehavior.LOOP) "inf" else "no"

    private companion object {
        const val VIDEO_OUTPUT_GPU = "gpu"
        const val VIDEO_OUTPUT_GPU_NEXT = "gpu-next"
        const val GPU_CONTEXT_OPENGL = "android"
        const val GPU_CONTEXT_VULKAN = "androidvk"
        const val NORMAL_VOLUME_PERCENT = 100.0
        const val BOOSTED_VOLUME_PERCENT = 150.0
        const val MAX_BOOSTED_VOLUME_PERCENT = 300.0
        const val MPV_END_FILE_REASON_ERROR = 4L
    }
}
