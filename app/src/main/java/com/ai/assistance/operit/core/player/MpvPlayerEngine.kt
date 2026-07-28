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

    fun initialize(settings: PlayerSettings): Unit = callMpv("初始化") {
        if (initialized) return
        MPVLib.create(appContext)
        setRequiredOption("config", "no")
        setRequiredOption("vo", VIDEO_OUTPUT)
        setRequiredOption("gpu-context", "android")
        setRequiredOption("hwdec", settings.hardwareDecodingPolicy.mpvValue)
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
        MPVLib.addObserver(this)
        MPVLib.init()
        MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("speed", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        initialized = true
    }

    fun load(
        target: String,
        headers: Map<String, String>,
        settings: PlayerSettings,
        shaderFiles: List<String>,
    ) = callMpv("加载媒体") {
        check(initialized) { "mpv engine is not initialized" }
        applyRequestHeaders(headers)
        applyHardwareDecoding(settings.hardwareDecodingPolicy)
        applyPreciseSeeking(settings.preciseSeeking)
        applyNetworkCache(settings.networkCachePolicy)
        applySubtitleScale(settings.subtitleScale)
        applyEndBehavior(settings.endBehavior)
        applyShaders(shaderFiles)
        MPVLib.setPropertyDouble("speed", settings.defaultSpeed)
        MPVLib.command("loadfile", target, "replace")
        MPVLib.setPropertyBoolean("pause", false)
    }

    fun attachSurface(surface: Surface, width: Int, height: Int) = callMpv("连接播放画面") {
        check(initialized) { "mpv engine is not initialized" }
        detachSurface()
        MPVLib.attachSurface(surface)
        MPVLib.setPropertyString("vo", VIDEO_OUTPUT)
        MPVLib.setPropertyString("force-window", "yes")
        attachedSurface = surface
        updateSurfaceSize(width, height)
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

    fun applyHardwareDecoding(policy: PlayerHardwareDecodingPolicy) = callMpv("应用硬件解码策略") {
        MPVLib.setPropertyString("hwdec", policy.mpvValue)
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

    fun applyShaders(shaderFiles: List<String>) = callMpv("应用 Anime4K 着色器") {
        MPVLib.setPropertyString("glsl-shaders", shaderFiles.joinToString(":"))
    }

    fun applyVideoFitMode(mode: PlayerVideoFitMode) = callMpv("切换画面比例") {
        when (mode) {
            PlayerVideoFitMode.FIT -> {
                MPVLib.setPropertyDouble("panscan", 0.0)
                MPVLib.setPropertyDouble("video-aspect-override", -1.0)
            }
            PlayerVideoFitMode.CROP -> {
                MPVLib.setPropertyDouble("video-aspect-override", -1.0)
                MPVLib.setPropertyDouble("panscan", 1.0)
            }
            PlayerVideoFitMode.RATIO_16_9 -> {
                MPVLib.setPropertyDouble("panscan", 0.0)
                MPVLib.setPropertyDouble("video-aspect-override", 16.0 / 9.0)
            }
            PlayerVideoFitMode.RATIO_4_3 -> {
                MPVLib.setPropertyDouble("panscan", 0.0)
                MPVLib.setPropertyDouble("video-aspect-override", 4.0 / 3.0)
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
        detachSurface()
        MPVLib.command("stop")
        MPVLib.removeObserver(this)
        MPVLib.destroy()
        initialized = false
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
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> listener.onFileLoaded()
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                val reason = data["reason"]?.asInt()
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
            throw MpvRuntimeException(operation, error)
        }

    private fun loopFileValue(behavior: PlayerEndBehavior): String =
        if (behavior == PlayerEndBehavior.LOOP) "inf" else "no"

    private companion object {
        const val VIDEO_OUTPUT = "gpu"
        const val MPV_END_FILE_REASON_ERROR = 4L
    }
}
