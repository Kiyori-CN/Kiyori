package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.ffmpeg.runtime.FFMPEG_RUNTIME_PROBE_SHOW_ENTRIES
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeMediaInformation
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeProcessDiedException
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeStreamInformation
import com.ai.assistance.operit.core.tools.FFmpegResultData
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FFmpegToolResultContractTest {
    @Test
    fun probeInputRequiresAnAbsoluteExistingNonEmptyFile() {
        val root = Files.createTempDirectory("ffmpeg-probe-tool-contract").toFile()
        try {
            val input = root.resolve("input.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            assertEquals(input.absolutePath, parseFfmpegProbeInput(input.absolutePath).absolutePath)
            val displayCommand = formatFfprobeCommandForDisplay(input.absolutePath)
            assertTrue(displayCommand.contains("-show_entries"))
            assertTrue(displayCommand.contains(FFMPEG_RUNTIME_PROBE_SHOW_ENTRIES))

            assertThrows(IllegalArgumentException::class.java) {
                parseFfmpegProbeInput("relative.mp4")
            }
            assertThrows(IllegalArgumentException::class.java) {
                parseFfmpegProbeInput(root.resolve("missing.mp4").absolutePath)
            }
            val empty = root.resolve("empty.mp4").apply { createNewFile() }
            assertThrows(IllegalArgumentException::class.java) {
                parseFfmpegProbeInput(empty.absolutePath)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rawExecuteRejectsShellSyntaxWithoutRewritingFfmpegArguments() {
        val raw =
            "-i input.mp4 -vf \"drawtext=fontfile=/system/fonts/Roboto-Regular.ttf:" +
                "text='a|b'\" output.mp4"
        val complexFilter =
            "-filter_complex \"[0:v]hflip[first];[first]vflip[out]\" -map \"[out]\" output.mp4"
        val escapedLiteral = "-metadata comment=a\\|b output.mp4"

        assertEquals(raw, validateRawFfmpegCommand(raw))
        assertEquals(complexFilter, validateRawFfmpegCommand(complexFilter))
        assertEquals(escapedLiteral, validateRawFfmpegCommand(escapedLiteral))
        val pipeError =
            assertThrows(IllegalArgumentException::class.java) {
                validateRawFfmpegCommand("-encoders | grep -iE mediacodec")
            }
        assertTrue(pipeError.message.orEmpty().contains("not a shell"))
        assertTrue(pipeError.message.orEmpty().contains("ffmpeg_info(section)"))
        listOf(
            "-encoders|grep -iE mediacodec",
            "-version>/tmp/ffmpeg-version.txt",
            "-version&&grep n9",
            "-version;grep n9",
        ).forEach { command ->
            assertThrows(IllegalArgumentException::class.java) {
                validateRawFfmpegCommand(command)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateRawFfmpegCommand("-version 2>/dev/null")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateRawFfmpegCommand("ffmpeg -version")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateRawFfmpegCommand("/data/local/tmp/ffmpeg -version")
        }
    }

    @Test
    fun mediaProjectionPreservesProfileAndPixelFormat() {
        val media =
            FFmpegRuntimeMediaInformation(
                format = "mp4",
                duration = "1.0",
                bitrate = "1000000",
                streams =
                    listOf(
                        FFmpegRuntimeStreamInformation(
                            index = 0,
                            type = "video",
                            codec = "h264",
                            profile = "Constrained Baseline",
                            pixelFormat = "yuv420p",
                            width = 1280,
                            height = 720,
                            realFrameRate = "30/1",
                            sampleRate = null,
                            channels = null,
                        ),
                    ),
            )

        val result = media.toToolMediaInfo()

        assertEquals("Constrained Baseline", result.videoStreams.single().profile)
        assertEquals("yuv420p", result.videoStreams.single().pixelFormat)
        assertEquals("1280x720", result.videoStreams.single().resolution)
    }

    @Test
    fun conversionContractAcceptsNumericConstrainedBaselineProfile() {
        val root = Files.createTempDirectory("ffmpeg-conversion-profile-contract").toFile()
        try {
            val input = root.resolve("input.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val output = root.resolve("output.mp4")
            val request =
                FFmpegConversionRequest.parse(
                    inputPath = input.absolutePath,
                    outputPath = output.absolutePath,
                    profile = "h264_aac_mp4",
                    resolution = null,
                    videoBitrate = null,
                )

            request.validateOutput(
                FFmpegRuntimeMediaInformation(
                    format = "mov,mp4",
                    duration = "1.0",
                    bitrate = "1000000",
                    streams =
                        listOf(
                            FFmpegRuntimeStreamInformation(
                                index = 0,
                                type = "video",
                                codec = "h264",
                                profile = "578",
                                pixelFormat = "yuv420p",
                                width = 1280,
                                height = 720,
                                realFrameRate = "30/1",
                                sampleRate = null,
                                channels = null,
                            ),
                            FFmpegRuntimeStreamInformation(
                                index = 1,
                                type = "audio",
                                codec = "aac",
                                profile = null,
                                pixelFormat = null,
                                width = null,
                                height = null,
                                realFrameRate = null,
                                sampleRate = "44100",
                                channels = 2,
                            ),
                        ),
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun conversionContractRejectsUnconstrainedBaselineProfile() {
        val root = Files.createTempDirectory("ffmpeg-conversion-profile-rejection").toFile()
        try {
            val input = root.resolve("input.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val request =
                FFmpegConversionRequest.parse(
                    inputPath = input.absolutePath,
                    outputPath = root.resolve("output.mp4").absolutePath,
                    profile = "h264_aac_mp4",
                    resolution = null,
                    videoBitrate = null,
                )

            val error =
                org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                    request.validateOutput(
                        FFmpegRuntimeMediaInformation(
                            format = "mp4",
                            duration = "1.0",
                            bitrate = "1000000",
                            streams =
                                listOf(
                                    FFmpegRuntimeStreamInformation(
                                        index = 0,
                                        type = "video",
                                        codec = "h264",
                                        profile = "66",
                                        pixelFormat = "yuv420p",
                                        width = 1280,
                                        height = 720,
                                        realFrameRate = "30/1",
                                        sampleRate = null,
                                        channels = null,
                                    ),
                                ),
                        ),
                    )
                }
            assertTrue(error.message.orEmpty().contains("expected constrained baseline"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resultTextExplainsSignedReturnCodeAndPipelineStage() {
        val result =
            FFmpegResultData(
                command = "-version",
                returnCode = -2,
                output = "",
                duration = 1L,
                terminalState = "failed",
                processId = 100,
                sessionId = 200L,
                pipelineStage = "execution",
            )

        val text = result.toString()

        assertTrue(text.contains("signed FFmpeg AVERROR value"))
        assertTrue(text.contains("not a shell exit status"))
        assertTrue(text.contains("Pipeline Stage: execution"))
        assertTrue(
            describeFfmpegConversionFailure(
                FFmpegConversionPipelineStage.PROBE,
                "invalid JSON",
            ).contains("transcode succeeded"),
        )
    }

    @Test
    fun processDeathPreservesStartedSessionAndRetainedDiagnosticLog() {
        val diagnosticLog =
            "/data/user/0/com.kiyori/cache/ffmpeg-runtime/${"a".repeat(32)}.log"
        val result =
            FFmpegRuntimeProcessDiedException(
                requestId = "a".repeat(32),
                partialOutput = "native output",
                diagnosticLogPath = diagnosticLog,
                processId = 4_321,
                sessionId = 99L,
            ).toFFmpegResultData(
                command = "-version",
                pipelineStage = "execution",
            )

        assertNull(result.returnCode)
        assertEquals("process_died", result.terminalState)
        assertEquals(4_321, result.processId)
        assertEquals(99L, result.sessionId)
        assertEquals("runtime_process_died", result.failureCode)
        assertEquals(diagnosticLog, result.diagnosticLogPath)
    }
}
