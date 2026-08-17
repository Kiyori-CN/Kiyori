package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeMediaInformation
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeStreamInformation
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FFmpegConversionContractTest {
    @Test
    fun qualifiedProfileBuildsOneStructuredNonOverwritingCommand() {
        val root = Files.createTempDirectory("ffmpeg-conversion-contract").toFile()
        try {
            val input = root.resolve("input with spaces.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val output = root.resolve("output.mp4")
            val request =
                FFmpegConversionRequest.parse(
                    inputPath = input.absolutePath,
                    outputPath = output.absolutePath,
                    profile = "h264_aac_mp4",
                    resolution = "1280x720",
                    videoBitrate = "4m",
                )
            val temporary = request.createTemporaryOutput()
            val arguments = request.buildArguments(temporary)

            assertEquals(FFmpegConversionProfile.H264_AAC_MP4, request.profile)
            assertTrue(arguments.containsAll(listOf("-n", "libopenh264", "constrained_baseline")))
            assertTrue(arguments.containsAll(listOf("-c:a", "aac", "-f", "mp4")))
            assertTrue(arguments.containsAll(listOf("-vf", "scale=1280:720:flags=lanczos")))
            assertTrue(arguments.containsAll(listOf("-b:v", "4M")))
            assertEquals(input.absolutePath, arguments[arguments.indexOf("-i") + 1])
            assertEquals(temporary.absolutePath, arguments.last())
            assertFalse(temporary.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun invalidOrAmbiguousConversionParametersAreRejectedBeforeExecution() {
        val root = Files.createTempDirectory("ffmpeg-conversion-rejection").toFile()
        try {
            val input = root.resolve("input.mp4").apply { writeBytes(byteArrayOf(1)) }
            val output = root.resolve("output.mp4")

            assertThrows(IllegalArgumentException::class.java) {
                FFmpegConversionRequest.parse(
                    input.absolutePath,
                    output.absolutePath,
                    "hevc_aac_mp4",
                    null,
                    null,
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                FFmpegConversionRequest.parse(
                    input.absolutePath,
                    output.absolutePath,
                    null,
                    "1279x720",
                    null,
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                FFmpegConversionRequest.parse(
                    input.absolutePath,
                    output.absolutePath,
                    null,
                    null,
                    "63k",
                )
            }
            output.writeBytes(byteArrayOf(9))
            assertThrows(IllegalArgumentException::class.java) {
                FFmpegConversionRequest.parse(
                    input.absolutePath,
                    output.absolutePath,
                    null,
                    null,
                    null,
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun probeValidationRequiresTheQualifiedContainerAndCodecs() {
        val root = Files.createTempDirectory("ffmpeg-conversion-probe").toFile()
        try {
            val input = root.resolve("input.mp4").apply { writeBytes(byteArrayOf(1)) }
            val request =
                FFmpegConversionRequest.parse(
                    input.absolutePath,
                    root.resolve("output.mp4").absolutePath,
                    null,
                    "1920x1080",
                    null,
                )
            request.validateOutput(
                mediaInformation(
                    videoCodec = "h264",
                    audioCodec = "aac",
                    width = 1920,
                    height = 1080,
                ),
            )

            assertThrows(IllegalArgumentException::class.java) {
                request.validateOutput(
                    mediaInformation(
                        videoCodec = "hevc",
                        audioCodec = "aac",
                        width = 1920,
                        height = 1080,
                    ),
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                request.validateOutput(
                    mediaInformation(
                        videoCodec = "h264",
                        audioCodec = "mp3",
                        width = 1920,
                        height = 1080,
                    ),
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                request.validateOutput(
                    mediaInformation(
                        videoCodec = "h264",
                        audioCodec = "aac",
                        width = 1920,
                        height = 1080,
                        videoProfile = "Main",
                    ),
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                request.validateOutput(
                    mediaInformation(
                        videoCodec = "h264",
                        audioCodec = "aac",
                        width = 1920,
                        height = 1080,
                        pixelFormat = "yuv444p",
                    ),
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                request.validateOutput(
                    mediaInformation(
                        videoCodec = "h264",
                        audioCodec = "aac",
                        width = 1920,
                        height = 1080,
                        duration = "0",
                    ),
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun mediaInformation(
        videoCodec: String,
        audioCodec: String,
        width: Int,
        height: Int,
        videoProfile: String = "Constrained Baseline",
        pixelFormat: String = "yuv420p",
        duration: String = "1.0",
    ): FFmpegRuntimeMediaInformation =
        FFmpegRuntimeMediaInformation(
            format = "mov,mp4,m4a,3gp,3g2,mj2",
            duration = duration,
            bitrate = "1000000",
            streams =
                listOf(
                    FFmpegRuntimeStreamInformation(
                        index = 0,
                        type = "video",
                        codec = videoCodec,
                        profile = videoProfile,
                        pixelFormat = pixelFormat,
                        width = width,
                        height = height,
                        realFrameRate = "30/1",
                        sampleRate = null,
                        channels = null,
                    ),
                    FFmpegRuntimeStreamInformation(
                        index = 1,
                        type = "audio",
                        codec = audioCodec,
                        profile = null,
                        pixelFormat = null,
                        width = null,
                        height = null,
                        realFrameRate = null,
                        sampleRate = "44100",
                        channels = 2,
                    ),
                ),
        )
}
