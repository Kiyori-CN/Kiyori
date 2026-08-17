package com.ai.assistance.operit.core.ffmpeg.runtime

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Parses only the bounded FFprobe projection owned by Kiyori.
 *
 * FFmpeg n9 writes structured FFprobe output through AVTextWriter instead of FFmpegKit's log
 * channel. Keeping this parser independent from FFmpegKit prevents a successful native probe from
 * being mistaken for missing metadata when the wrapper cannot reconstruct stdout.
 */
internal object FFmpegRuntimeMediaInformationParser {
    private val json = Json {
        isLenient = false
    }

    fun parse(file: File): FFmpegRuntimeMediaInformation {
        val document = readBoundedUtf8(file)
        val root =
            requireJsonObject(
                json.parseToJsonElement(document),
                "FFprobe document root",
            )
        val format = requireJsonObject(root["format"], "FFprobe format")
        val streams = requireJsonArray(root["streams"], "FFprobe streams")
        require(streams.size <= FFMPEG_RUNTIME_MAX_MEDIA_STREAMS) {
            "FFprobe stream count exceeds $FFMPEG_RUNTIME_MAX_MEDIA_STREAMS"
        }
        return FFmpegRuntimeMediaInformation(
            format = format.optionalString("format_name"),
            duration = format.optionalString("duration"),
            bitrate = format.optionalString("bit_rate"),
            streams =
                streams.mapIndexed { position, element ->
                    parseStream(position, element)
                },
        )
    }

    private fun parseStream(
        position: Int,
        element: JsonElement,
    ): FFmpegRuntimeStreamInformation {
        val stream = requireJsonObject(element, "FFprobe stream[$position]")
        val index =
            requireNotNull(stream.optionalInt("index")) {
                "FFprobe stream[$position].index is missing"
            }
        require(index >= 0) { "FFprobe stream[$position].index must be non-negative" }
        val width = stream.optionalInt("width")
        val height = stream.optionalInt("height")
        val channels = stream.optionalInt("channels")
        require(width == null || width in 0..FFMPEG_RUNTIME_MAX_MEDIA_DIMENSION) {
            "FFprobe stream[$position].width is outside the supported range"
        }
        require(height == null || height in 0..FFMPEG_RUNTIME_MAX_MEDIA_DIMENSION) {
            "FFprobe stream[$position].height is outside the supported range"
        }
        require(channels == null || channels in 0..FFMPEG_RUNTIME_MAX_AUDIO_CHANNELS) {
            "FFprobe stream[$position].channels is outside the supported range"
        }
        return FFmpegRuntimeStreamInformation(
            index = index,
            type = stream.optionalString("codec_type"),
            codec = stream.optionalString("codec_name"),
            profile = stream.optionalStringOrInteger("profile"),
            pixelFormat = stream.optionalString("pix_fmt"),
            width = width,
            height = height,
            realFrameRate = stream.optionalString("r_frame_rate"),
            sampleRate = stream.optionalString("sample_rate"),
            channels = channels,
        )
    }

    private fun readBoundedUtf8(file: File): String {
        require(file.isFile) { "FFprobe output JSON is missing" }
        require(file.length() in 1..FFMPEG_RUNTIME_MAX_MEDIA_INFORMATION_BYTES.toLong()) {
            "FFprobe output JSON is empty or exceeds $FFMPEG_RUNTIME_MAX_MEDIA_INFORMATION_BYTES bytes"
        }
        val output = ByteArrayOutputStream(file.length().toInt())
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                require(output.size() + read <= FFMPEG_RUNTIME_MAX_MEDIA_INFORMATION_BYTES) {
                    "FFprobe output JSON exceeds $FFMPEG_RUNTIME_MAX_MEDIA_INFORMATION_BYTES bytes"
                }
                output.write(buffer, 0, read)
            }
        }
        require(output.size() > 0) { "FFprobe output JSON is empty" }
        return Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output.toByteArray()))
            .toString()
    }

    private fun JsonObject.optionalString(name: String): String? {
        val element = this[name] ?: return null
        if (element is JsonNull) {
            return null
        }
        val primitive =
            element as? JsonPrimitive
                ?: throw IllegalArgumentException("FFprobe field $name must be a string")
        require(primitive.isString) { "FFprobe field $name must be a string" }
        val value = primitive.contentOrNull ?: return null
        require(value.length <= FFMPEG_RUNTIME_MAX_MEDIA_STRING_CHARS) {
            "FFprobe field $name exceeds $FFMPEG_RUNTIME_MAX_MEDIA_STRING_CHARS characters"
        }
        return value
    }

    private fun JsonObject.optionalInt(name: String): Int? {
        val element = this[name] ?: return null
        if (element is JsonNull) {
            return null
        }
        val primitive =
            element as? JsonPrimitive
                ?: throw IllegalArgumentException("FFprobe field $name must be an integer")
        return requireNotNull(primitive.intOrNull) {
            "FFprobe field $name must be an integer"
        }
    }

    private fun JsonObject.optionalStringOrInteger(name: String): String? {
        val element = this[name] ?: return null
        if (element is JsonNull) {
            return null
        }
        val primitive =
            element as? JsonPrimitive
                ?: throw IllegalArgumentException(
                    "FFprobe field $name must be a string or integer",
                )
        if (primitive.isString) {
            return optionalString(name)
        }
        return requireNotNull(primitive.intOrNull) {
            "FFprobe field $name must be a string or integer"
        }.toString()
    }

    private fun requireJsonObject(
        element: JsonElement?,
        label: String,
    ): JsonObject =
        element as? JsonObject
            ?: throw IllegalArgumentException("$label must be a JSON object")

    private fun requireJsonArray(
        element: JsonElement?,
        label: String,
    ): JsonArray =
        element as? JsonArray
            ?: throw IllegalArgumentException("$label must be a JSON array")
}

internal const val FFMPEG_RUNTIME_MAX_MEDIA_INFORMATION_BYTES = 1024 * 1024
internal const val FFMPEG_RUNTIME_MAX_MEDIA_STREAMS = 128
internal const val FFMPEG_RUNTIME_MAX_MEDIA_STRING_CHARS = 4_096
internal const val FFMPEG_RUNTIME_MAX_MEDIA_DIMENSION = 131_072
internal const val FFMPEG_RUNTIME_MAX_AUDIO_CHANNELS = 1_024
internal const val FFMPEG_RUNTIME_PROBE_SHOW_ENTRIES =
    "format=format_name,duration,bit_rate:" +
        "stream=index,codec_type,codec_name,profile,pix_fmt,width,height," +
        "r_frame_rate,sample_rate,channels"
