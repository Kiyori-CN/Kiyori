package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.data.model.AITool
import java.util.Locale

/** 只解释显式媒体读取意图，不读取全局聊天配置或把文件路径当成附件。 */
internal object FileMediaReadPolicy {
    const val MAX_TRANSFER_BYTES = 20 * 1024 * 1024

    data class Format(val kind: String, val mimeType: String)

    private val formats = mapOf(
        "jpg" to Format("image", "image/jpeg"), "jpeg" to Format("image", "image/jpeg"),
        "png" to Format("image", "image/png"), "gif" to Format("image", "image/gif"),
        "bmp" to Format("image", "image/bmp"), "webp" to Format("image", "image/webp"),
        "heic" to Format("image", "image/heic"), "heif" to Format("image", "image/heif"),
        "avif" to Format("image", "image/avif"),
        "mp3" to Format("audio", "audio/mpeg"), "wav" to Format("audio", "audio/wav"),
        "m4a" to Format("audio", "audio/mp4"), "aac" to Format("audio", "audio/aac"),
        "flac" to Format("audio", "audio/flac"), "ogg" to Format("audio", "audio/ogg"),
        "opus" to Format("audio", "audio/opus"),
        "mp4" to Format("video", "video/mp4"), "mkv" to Format("video", "video/x-matroska"),
        "mov" to Format("video", "video/quicktime"), "webm" to Format("video", "video/webm"),
        "avi" to Format("video", "video/x-msvideo"), "m4v" to Format("video", "video/mp4"),
        "3gp" to Format("video", "video/3gpp"), "ogv" to Format("video", "video/ogg"),
    )

    fun format(path: String): Format? = formats[path.substringAfterLast('.', "").lowercase(Locale.ROOT)]

    fun requested(tool: AITool): Boolean = tool.parameters.any {
        it.name in setOf("direct_image", "direct_audio", "direct_video") && it.value.equals("true", true)
    }

    fun resolve(tool: AITool, path: String, mimeType: String? = null): Format? {
        if (!requested(tool)) return null
        require(tool.parameters.none { it.name == "text_only" && it.value.toBoolean() || it.name == "read_mode" }) {
            "Direct media input cannot be combined with text_only or read_mode"
        }
        val explicitKinds = tool.parameters.filter {
            it.name in setOf("direct_image", "direct_audio", "direct_video") && it.value.toBoolean()
        }.map { it.name.removePrefix("direct_") }.distinct()
        require(explicitKinds.size == 1) { "Specify exactly one matching direct_image, direct_audio or direct_video" }
        val suppliedMime = mimeType?.substringBefore(';')?.lowercase(Locale.ROOT)
        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        // WebM/MP4/Ogg 容器可以只有音轨；有来源 MIME 时优先用它，否则用显式参数消除容器歧义。
        val containerFormat = if (extension in setOf("webm", "mp4", "ogg") && explicitKinds.single() in setOf("audio", "video")) {
            Format(explicitKinds.single(), "${explicitKinds.single()}/$extension")
        } else null
        val detected = suppliedMime?.takeIf { it.substringBefore('/') in setOf("image", "audio", "video") && !it.endsWith("/*") }
            ?.let { Format(it.substringBefore('/'), it) } ?: format(path)
                ?.let { containerFormat ?: it }
        requireNotNull(detected) { "Unsupported media format; render document pages to images or convert the media first" }
        require(detected.kind == explicitKinds.single()) { "Requested ${explicitKinds.single()} input does not match ${detected.kind} file" }
        return detected
    }
}
