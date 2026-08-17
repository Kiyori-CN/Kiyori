package com.ai.assistance.operit.core.ffmpeg.runtime

import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FFmpegRuntimeMediaInformationParserTest {
    @Test
    fun parsesOnlyTheQualifiedMediaProjection() {
        val root = Files.createTempDirectory("ffprobe-json-parser").toFile()
        try {
            val file =
                root.resolve("probe.json").apply {
                    writeText(
                        """
                        {
                          "format": {
                            "format_name": "mov,mp4,m4a,3gp,3g2,mj2",
                            "duration": "19.320000",
                            "bit_rate": "10460000",
                            "tags": {"title": "ignored"}
                          },
                          "streams": [
                            {
                              "index": 0,
                              "codec_type": "video",
                              "codec_name": "h264",
                              "profile": "Constrained Baseline",
                              "pix_fmt": "yuv420p",
                              "width": 2560,
                              "height": 1440,
                              "r_frame_rate": "30/1",
                              "side_data_list": [{"ignored": true}]
                            },
                            {
                              "index": 1,
                              "codec_type": "audio",
                              "codec_name": "aac",
                              "sample_rate": "44100",
                              "channels": 2
                            }
                          ],
                          "chapters": [{"ignored": true}]
                        }
                        """.trimIndent(),
                        Charsets.UTF_8,
                    )
                }

            val information = FFmpegRuntimeMediaInformationParser.parse(file)

            assertEquals("mov,mp4,m4a,3gp,3g2,mj2", information.format)
            assertEquals("19.320000", information.duration)
            assertEquals("10460000", information.bitrate)
            assertEquals(2, information.streams.size)
            assertEquals("Constrained Baseline", information.streams[0].profile)
            assertEquals("yuv420p", information.streams[0].pixelFormat)
            assertEquals(2560, information.streams[0].width)
            assertEquals("44100", information.streams[1].sampleRate)
            assertEquals(2, information.streams[1].channels)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun preservesNumericProfileValuesUsedBySomeFfprobeBuilds() {
        val root = Files.createTempDirectory("ffprobe-numeric-profile").toFile()
        try {
            val file =
                root.resolve("probe.json").apply {
                    writeText(
                        """
                        {
                          "format": {},
                          "streams": [
                            {
                              "index": 0,
                              "codec_type": "video",
                              "codec_name": "h264",
                              "profile": 578
                            }
                          ]
                        }
                        """.trimIndent(),
                        Charsets.UTF_8,
                    )
                }

            val information = FFmpegRuntimeMediaInformationParser.parse(file)

            assertEquals("578", information.streams.single().profile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMissingMalformedAndOversizedProbeDocuments() {
        val root = Files.createTempDirectory("ffprobe-json-rejection").toFile()
        try {
            val missing = root.resolve("missing.json")
            assertThrows(IllegalArgumentException::class.java) {
                FFmpegRuntimeMediaInformationParser.parse(missing)
            }

            val malformed =
                root.resolve("malformed.json").apply {
                    writeText("""{"format": [], "streams": {}}""", Charsets.UTF_8)
                }
            assertThrows(IllegalArgumentException::class.java) {
                FFmpegRuntimeMediaInformationParser.parse(malformed)
            }

            val oversized =
                root.resolve("oversized.json").apply {
                    writeBytes(ByteArray(FFMPEG_RUNTIME_MAX_MEDIA_INFORMATION_BYTES + 1) { 'x'.code.toByte() })
                }
            val oversizedError =
                assertThrows(IllegalArgumentException::class.java) {
                    FFmpegRuntimeMediaInformationParser.parse(oversized)
                }
            assertTrue(oversizedError.message.orEmpty().contains("exceeds"))

            val malformedUtf8 =
                root.resolve("malformed-utf8.json").apply {
                    writeBytes(
                        byteArrayOf(
                            '{'.code.toByte(),
                            '"'.code.toByte(),
                            0xC3.toByte(),
                            0x28,
                            '"'.code.toByte(),
                            '}'.code.toByte(),
                        ),
                    )
                }
            assertThrows(CharacterCodingException::class.java) {
                FFmpegRuntimeMediaInformationParser.parse(malformedUtf8)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnboundedStreamCountsAndFields() {
        val root = Files.createTempDirectory("ffprobe-json-bounds").toFile()
        try {
            val streams =
                (0..FFMPEG_RUNTIME_MAX_MEDIA_STREAMS).joinToString(",") { index ->
                    """{"index":$index,"codec_type":"video","codec_name":"h264"}"""
                }
            val tooManyStreams =
                root.resolve("streams.json").apply {
                    writeText(
                        """{"format":{},"streams":[$streams]}""",
                        Charsets.UTF_8,
                    )
                }
            assertThrows(IllegalArgumentException::class.java) {
                FFmpegRuntimeMediaInformationParser.parse(tooManyStreams)
            }

            val tooLongCodec =
                root.resolve("field.json").apply {
                    writeText(
                        """{"format":{},"streams":[{"index":0,"codec_name":"${"x".repeat(FFMPEG_RUNTIME_MAX_MEDIA_STRING_CHARS + 1)}"}]}""",
                        Charsets.UTF_8,
                    )
                }
            assertThrows(IllegalArgumentException::class.java) {
                FFmpegRuntimeMediaInformationParser.parse(tooLongCodec)
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
