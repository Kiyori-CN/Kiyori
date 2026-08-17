package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeMediaInformation
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

internal enum class FFmpegConversionProfile(val wireValue: String) {
    H264_AAC_MP4("h264_aac_mp4");

    companion object {
        val default: FFmpegConversionProfile = H264_AAC_MP4

        fun parse(value: String?): FFmpegConversionProfile {
            if (value == null) {
                return default
            }
            require(value.isNotBlank()) { "FFmpeg conversion profile cannot be blank" }
            return requireNotNull(
                entries.singleOrNull { profile ->
                    profile.wireValue == value.lowercase(Locale.ROOT)
                },
            ) {
                "Unsupported FFmpeg conversion profile: $value"
            }
        }
    }
}

internal data class FFmpegOutputResolution(
    val width: Int,
    val height: Int,
) {
    init {
        require(width in MIN_DIMENSION..MAX_DIMENSION && height in MIN_DIMENSION..MAX_DIMENSION) {
            "FFmpeg output resolution must stay between ${MIN_DIMENSION}x$MIN_DIMENSION and " +
                "${MAX_DIMENSION}x$MAX_DIMENSION"
        }
        require(width % 2 == 0 && height % 2 == 0) {
            "FFmpeg output resolution must use even dimensions"
        }
    }

    override fun toString(): String = "${width}x$height"

    companion object {
        private const val MIN_DIMENSION = 16
        private const val MAX_DIMENSION = 8_192
        private val PATTERN = Regex("([1-9][0-9]{1,4})x([1-9][0-9]{1,4})")

        fun parse(value: String?): FFmpegOutputResolution? {
            if (value == null) {
                return null
            }
            require(value.isNotBlank()) { "FFmpeg output resolution cannot be blank" }
            val match =
                requireNotNull(PATTERN.matchEntire(value)) {
                    "Invalid FFmpeg output resolution: $value"
                }
            return FFmpegOutputResolution(
                width = match.groupValues[1].toInt(),
                height = match.groupValues[2].toInt(),
            )
        }
    }
}

internal class FFmpegVideoBitrate private constructor(
    val ffmpegValue: String,
    val bitsPerSecond: Long,
) {
    companion object {
        private const val MIN_BITS_PER_SECOND = 64_000L
        private const val MAX_BITS_PER_SECOND = 100_000_000L
        private val PATTERN = Regex("([1-9][0-9]{0,7})([kKmM])")

        fun parse(value: String?): FFmpegVideoBitrate? {
            if (value == null) {
                return null
            }
            require(value.isNotBlank()) { "FFmpeg video bitrate cannot be blank" }
            val match =
                requireNotNull(PATTERN.matchEntire(value)) {
                    "Invalid FFmpeg video bitrate: $value"
                }
            val amount = match.groupValues[1].toLong()
            val suffix = match.groupValues[2].lowercase(Locale.ROOT)
            val multiplier = if (suffix == "k") 1_000L else 1_000_000L
            val bitsPerSecond = Math.multiplyExact(amount, multiplier)
            require(bitsPerSecond in MIN_BITS_PER_SECOND..MAX_BITS_PER_SECOND) {
                "FFmpeg video bitrate must stay between 64k and 100M"
            }
            return FFmpegVideoBitrate(
                ffmpegValue = if (suffix == "k") "${amount}k" else "${amount}M",
                bitsPerSecond = bitsPerSecond,
            )
        }
    }
}

internal data class FFmpegConversionRequest(
    val inputFile: File,
    val outputFile: File,
    val profile: FFmpegConversionProfile,
    val resolution: FFmpegOutputResolution?,
    val videoBitrate: FFmpegVideoBitrate?,
) {
    init {
        require(inputFile.isAbsolute && outputFile.isAbsolute) {
            "FFmpeg conversion paths must be absolute"
        }
        require(inputFile.path.length <= MAX_PATH_CHARS && outputFile.path.length <= MAX_PATH_CHARS) {
            "FFmpeg conversion path exceeds $MAX_PATH_CHARS characters"
        }
        require(inputFile.isFile && inputFile.length() > 0L) {
            "Input file does not exist or is empty: ${inputFile.path}"
        }
        require(
            inputFile.canonicalFileForConversion("Input") !=
                outputFile.canonicalFileForConversion("Output"),
        ) {
            "FFmpeg input and output must be different files"
        }
        require(outputFile.extension.equals("mp4", ignoreCase = true)) {
            "Profile ${profile.wireValue} requires an .mp4 output path"
        }
        require(!outputFile.exists()) {
            "Output file already exists; ffmpeg_convert never overwrites files: ${outputFile.path}"
        }
        val outputParent = requireNotNull(outputFile.parentFile) { "Output path has no parent directory" }
        require(outputParent.isDirectory) {
            "Output directory does not exist: ${outputParent.path}"
        }
    }

    fun createTemporaryOutput(): File {
        val parent = requireNotNull(outputFile.parentFile)
        repeat(MAX_TEMPORARY_NAME_ATTEMPTS) {
            val candidate =
                File(
                    parent,
                    ".${outputFile.nameWithoutExtension}.${UUID.randomUUID()}.partial.mp4",
                )
            if (!candidate.exists()) {
                return candidate
            }
        }
        error("Unable to allocate a unique FFmpeg temporary output path")
    }

    fun buildArguments(temporaryOutput: File): List<String> {
        require(
            temporaryOutput.parentFile?.canonicalFileForConversion("Temporary output directory") ==
                outputFile.parentFile?.canonicalFileForConversion("Output directory"),
        ) {
            "FFmpeg temporary output must share the final output directory"
        }
        require(!temporaryOutput.exists()) { "FFmpeg temporary output already exists" }
        return buildList {
            add("-nostdin")
            add("-hide_banner")
            add("-n")
            add("-i")
            add(inputFile.absolutePath)
            add("-map")
            add("0:V:0")
            add("-map")
            add("0:a:0?")
            add("-sn")
            add("-dn")
            resolution?.let { size ->
                add("-vf")
                add("scale=${size.width}:${size.height}:flags=lanczos")
            }
            when (profile) {
                FFmpegConversionProfile.H264_AAC_MP4 -> {
                    add("-c:v")
                    add("libopenh264")
                    add("-profile:v")
                    add("constrained_baseline")
                    add("-pix_fmt")
                    add("yuv420p")
                    videoBitrate?.let { bitrate ->
                        add("-b:v")
                        add(bitrate.ffmpegValue)
                    }
                    add("-c:a")
                    add("aac")
                    add("-movflags")
                    add("+faststart")
                    add("-f")
                    add("mp4")
                }
            }
            add(temporaryOutput.absolutePath)
        }
    }

    fun validateOutput(mediaInformation: FFmpegRuntimeMediaInformation) {
        val durationSeconds = mediaInformation.duration?.toDoubleOrNull()
        require(durationSeconds != null && durationSeconds.isFinite() && durationSeconds > 0.0) {
            "FFprobe did not report a positive conversion output duration"
        }
        require(mediaInformation.format?.split(',')?.any { format -> format == "mp4" } == true) {
            "FFprobe did not identify the conversion output as MP4"
        }
        val videoStreams =
            mediaInformation.streams.filter { stream ->
                stream.type.equals("video", ignoreCase = true)
            }
        require(videoStreams.size == 1) {
            "Conversion output must contain exactly one video stream"
        }
        val video = videoStreams.single()
        require(video.codec.equals("h264", ignoreCase = true)) {
            "Conversion output video codec is ${video.codec ?: "unknown"}, expected h264"
        }
        require(video.profile.isConstrainedBaselineProfile()) {
            "Conversion output video profile is ${video.profile ?: "unknown"}, " +
                "expected constrained baseline"
        }
        require(video.pixelFormat.equals("yuv420p", ignoreCase = true)) {
            "Conversion output pixel format is ${video.pixelFormat ?: "unknown"}, expected yuv420p"
        }
        resolution?.let { expected ->
            require(video.width == expected.width && video.height == expected.height) {
                "Conversion output resolution is ${video.width}x${video.height}, expected $expected"
            }
        }
        val audioStreams =
            mediaInformation.streams.filter { stream ->
                stream.type.equals("audio", ignoreCase = true)
            }
        require(audioStreams.size <= 1) {
            "Conversion output must contain at most one audio stream"
        }
        audioStreams.singleOrNull()?.let { audio ->
            require(audio.codec.equals("aac", ignoreCase = true)) {
                "Conversion output audio codec is ${audio.codec ?: "unknown"}, expected aac"
            }
        }
    }

    companion object {
        private const val MAX_PATH_CHARS = 4_096
        private const val MAX_TEMPORARY_NAME_ATTEMPTS = 16

        fun parse(
            inputPath: String?,
            outputPath: String?,
            profile: String?,
            resolution: String?,
            videoBitrate: String?,
        ): FFmpegConversionRequest {
            require(!inputPath.isNullOrBlank()) { "Must provide input_path parameter" }
            require(!outputPath.isNullOrBlank()) { "Must provide output_path parameter" }
            return FFmpegConversionRequest(
                inputFile = File(inputPath),
                outputFile = File(outputPath),
                profile = FFmpegConversionProfile.parse(profile),
                resolution = FFmpegOutputResolution.parse(resolution),
                videoBitrate = FFmpegVideoBitrate.parse(videoBitrate),
            )
        }
    }
}

private fun File.canonicalFileForConversion(label: String): File =
    try {
        canonicalFile
    } catch (error: IOException) {
        throw IllegalArgumentException("$label path cannot be resolved: $path", error)
    } catch (error: SecurityException) {
        throw IllegalArgumentException("$label path cannot be accessed: $path", error)
    }

private fun String?.isConstrainedBaselineProfile(): Boolean {
    val token = normalizedFfmpegToken()
    // FFmpeg represents AV_PROFILE_H264_CONSTRAINED_BASELINE as 66 | 512 = 578.
    // The numeric value is semantically the same profile as the human-readable
    // "Constrained Baseline" emitted by other FFprobe builds.
    return token == "constrainedbaseline" || token == "578"
}

private fun String?.normalizedFfmpegToken(): String =
    this
        ?.lowercase(Locale.ROOT)
        ?.filter(Char::isLetterOrDigit)
        .orEmpty()

internal fun formatFfmpegArgumentsForDisplay(arguments: List<String>): String =
    arguments.joinToString(" ") { argument ->
        if (argument.any(Char::isWhitespace) || '"' in argument) {
            "\"${argument.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        } else {
            argument
        }
    }
