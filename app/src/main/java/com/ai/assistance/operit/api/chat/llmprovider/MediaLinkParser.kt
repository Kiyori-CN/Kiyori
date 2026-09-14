package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.MediaBase64Limiter
import com.ai.assistance.operit.util.MediaPoolManager

data class MediaLink(
    val type: String,
    val id: String,
    val base64Data: String,
    val mimeType: String,
    val fileName: String? = null,
)

data class ImageLink(
    val type: String,
    val id: String,
    val base64Data: String,
    val mimeType: String
)

data class MediaLinkTag(
    val type: String,
    val id: String,
    val fileName: String? = null,
)

object MediaLinkParser {
    /** 工具信封外的媒体使用同一个有序解析器，不能按类型重排或丢失音视频。 */
    fun extractAttachmentTags(message: String): List<MediaLinkTag> = linkPattern.findAll(message)
        .map { match ->
            val type = match.groupValues[1].lowercase()
            MediaLinkTag(type, match.groupValues[2], if (type == "file") extractFileName(match) else null)
        }.filter { it.id != "error" && (it.type != "file" || it.fileName != null) }
        .distinctBy { "${it.type}:${it.id}" }.toList()

    fun removeAttachmentLinks(message: String): String = linkPattern.replace(message, "")

    fun attachmentMarkup(tag: MediaLinkTag): String {
        fun escape(value: String) = value.replace("&", "&amp;").replace("\"", "&quot;")
            .replace("<", "&lt;").replace(">", "&gt;")
        val filename = tag.fileName?.let { " filename=\"${escape(it)}\"" }.orEmpty()
        return "<link type=\"${tag.type}\" id=\"${escape(tag.id)}\"$filename></link>"
    }

    /** 配置关闭、协议不支持和池内容失效都必须在提交请求前可见。 */
    fun requireAvailableInput(message: String, image: Boolean, audio: Boolean, video: Boolean, file: Boolean = false) {
        for (tag in extractAttachmentTags(message)) {
            val supported = when (tag.type) { "image" -> image; "audio" -> audio; "video" -> video; else -> file }
            require(supported) { "${tag.type} input is disabled or unsupported by the selected model/API protocol; change the configuration or explicitly choose another reading method" }
            val available = if (tag.type == "image") ImagePoolManager.getImage(tag.id) != null
                else MediaPoolManager.getMedia(tag.id) != null
            require(available) { "${tag.type} content is no longer available; read the file or attach it again before continuing" }
        }
    }
    // One encounter-ordered matcher prevents type-by-type scans from reordering mixed attachments.
    private val linkPattern = Regex(
        """<link\b(?=[^>]*\btype\s*=\s*\\*["']?(image|audio|video|file)\\*["']?)(?=[^>]*\bid\s*=\s*\\*["']?([^"'\\\s>]+)\\*["']?)[^>]*(?:/>|>.*?</link>)""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    private val fileNameAttributePattern = Regex(
        """filename\s*=\s*\\*["']?([^"'\\>]+)\\*["']?""",
        RegexOption.IGNORE_CASE,
    )

    private fun isMediaType(type: String): Boolean =
        type.equals("audio", ignoreCase = true) ||
            type.equals("video", ignoreCase = true) ||
            type.equals("file", ignoreCase = true)

    private fun matchesForType(message: String, type: String): Sequence<MatchResult> =
        linkPattern.findAll(message).filter {
            it.groupValues[1].equals(type, ignoreCase = true)
        }

    private fun matchesForMediaType(message: String): Sequence<MatchResult> =
        linkPattern.findAll(message).filter { isMediaType(it.groupValues[1]) }

    private fun extractFileName(match: MatchResult): String? =
        fileNameAttributePattern.find(match.value)
            ?.groupValues
            ?.get(1)
            ?.let(::unescapeXml)
            ?.takeIf { it.isNotBlank() }

    fun extractImageLinks(message: String): List<ImageLink> {
        val imageLinks = mutableListOf<ImageLink>()
        val seenIds = mutableSetOf<String>()
        for (match in matchesForType(message, "image")) {
            val id = match.groupValues[2]
            if (id == "error" || !seenIds.add(id)) continue
            val imageData = ImagePoolManager.getImage(id) ?: continue
            imageLinks.add(
                ImageLink(
                    type = "image",
                    id = id,
                    base64Data = imageData.base64,
                    mimeType = imageData.mimeType
                )
            )
        }
        return imageLinks
    }

    fun extractImageLinkIds(message: String): List<String> {
        val ids = mutableListOf<String>()
        val seenIds = mutableSetOf<String>()
        for (match in matchesForType(message, "image")) {
            val id = match.groupValues[2]
            if (id != "error" && seenIds.add(id)) {
                ids.add(id)
            }
        }
        return ids
    }

    fun removeImageLinks(message: String): String =
        linkPattern.replace(message) { match ->
            if (match.groupValues[1].equals("image", ignoreCase = true)) "" else match.value
        }

    fun replaceImageLinks(message: String, replacer: (id: String) -> String): String =
        linkPattern.replace(message) { match ->
            if (!match.groupValues[1].equals("image", ignoreCase = true)) {
                match.value
            } else {
                val id = match.groupValues[2]
                if (id == "error") "" else replacer(id)
            }
        }

    fun hasImageLinks(message: String): Boolean = matchesForType(message, "image").any()

    fun extractMediaLinks(message: String): List<MediaLink> {
        val links = mutableListOf<MediaLink>()
        val seenIds = mutableSetOf<String>()
        for (match in matchesForMediaType(message)) {
            val type = match.groupValues[1].lowercase()
            val id = match.groupValues[2]
            if (id == "error") continue
            val key = "$type:$id"
            if (key in seenIds) continue
            val fileName = if (type == "file") extractFileName(match) else null
            if (type == "file" && fileName == null) continue
            seenIds.add(key)
            val mediaData = MediaPoolManager.getMedia(id) ?: continue
            val limited = requireNotNull(MediaBase64Limiter.limitBase64ForAi(mediaData.base64, mediaData.mimeType)) {
                "Media content cannot be prepared within the input limit; use an explicit smaller clip"
            }
            links.add(
                MediaLink(
                    type = type,
                    id = id,
                    base64Data = limited.base64,
                    mimeType = limited.mimeType,
                    fileName = fileName,
                )
            )
        }
        return links
    }

    fun extractMediaLinkTags(message: String): List<MediaLinkTag> {
        val tags = mutableListOf<MediaLinkTag>()
        val seenIds = mutableSetOf<String>()
        for (match in matchesForMediaType(message)) {
            val type = match.groupValues[1].lowercase()
            val id = match.groupValues[2]
            if (id == "error") continue
            val key = "$type:$id"
            if (key in seenIds) continue
            val fileName = if (type == "file") extractFileName(match) else null
            if (type == "file" && fileName == null) continue
            seenIds.add(key)
            tags.add(MediaLinkTag(type = type, id = id, fileName = fileName))
        }
        return tags
    }

    fun replaceMediaLinks(message: String, replacer: (type: String, id: String) -> String): String =
        linkPattern.replace(message) { match ->
            val type = match.groupValues[1].lowercase()
            val id = match.groupValues[2]
            when {
                !isMediaType(type) -> match.value
                id == "error" -> ""
                else -> replacer(type, id)
            }
        }

    fun removeMediaLinks(message: String): String =
        linkPattern.replace(message) { match ->
            if (isMediaType(match.groupValues[1])) "" else match.value
        }

    fun hasMediaLinks(message: String): Boolean = matchesForMediaType(message).any()

    private fun unescapeXml(value: String): String =
        value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
}
