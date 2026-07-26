package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrowserDownloadM3u8RuntimeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `automatic merge follows highest bandwidth and downloads local package`() = runBlocking {
        LoopbackServer().use { server ->
            server.text("/master.m3u8", """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=100
                low/index.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=900
                high/index.m3u8
            """.trimIndent())
            server.text("/high/index.m3u8", """
                #EXTM3U
                #EXT-X-KEY:METHOD=AES-128,URI="keys/key.bin"
                #EXT-X-MAP:URI="init.mp4"
                #EXTINF:4,
                segments/one.ts
            """.trimIndent())
            server.bytes("/high/index.m3u8/keys/key.bin", byteArrayOf(1, 2))
            server.bytes("/high/keys/key.bin", byteArrayOf(3, 4, 5))
            server.bytes("/high/index.mp4", byteArrayOf(6))
            server.bytes("/high/init.mp4", byteArrayOf(7, 8))
            server.bytes("/high/segments/one.ts", byteArrayOf(9, 10, 11, 12))
            server.start()

            val output = temporaryFolder.newFile("video.m3u8.part").also { it.delete() }
            val packageDirectory = temporaryFolder.root.resolve("video.m3u8.files")
            val reported = AtomicInteger()
            newTransport().use { transport ->
                val result =
                    downloadBrowserM3u8(
                        playlistUrl = server.url("/master.m3u8"),
                        headers = emptyMap(),
                        transport = transport,
                        autoMerge = true,
                        m3u8ThreadCount = 3,
                        outputPlaylistFile = output,
                        packageDirectory = packageDirectory,
                        onChunk = { bytes -> reported.addAndGet(bytes) },
                    )

                assertTrue(result.isPackage)
                assertTrue(output.exists())
                assertTrue(packageDirectory.resolve("segment_0.ts").exists())
                assertTrue(packageDirectory.resolve("resource_0.bin").exists())
                assertTrue(packageDirectory.resolve("resource_1.mp4").exists())
                assertTrue(output.readText().contains(packageDirectoryUri(packageDirectory)))
                assertEquals(result.storedBytes, output.length() + browserM3u8DirectorySizeBytes(packageDirectory))
                assertEquals(result.storedBytes.toInt(), reported.get())
                assertFalse(output.readText().contains("low/index.m3u8"))
            }
        }
    }

    @Test
    fun `automatic merge disabled stores the server playlist without a companion directory`() = runBlocking {
        LoopbackServer().use { server ->
            val playlist = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nvariant.m3u8"
            server.text("/master.m3u8", playlist)
            server.start()
            val output = temporaryFolder.newFile("master.m3u8.part").also { it.delete() }
            val packageDirectory = temporaryFolder.root.resolve("master.m3u8.files")

            newTransport().use { transport ->
                val result =
                    downloadBrowserM3u8(
                        playlistUrl = server.url("/master.m3u8"),
                        headers = emptyMap(),
                        transport = transport,
                        autoMerge = false,
                        m3u8ThreadCount = 3,
                        outputPlaylistFile = output,
                        packageDirectory = packageDirectory,
                    )

                assertFalse(result.isPackage)
                assertEquals(playlist, output.readText())
                assertFalse(packageDirectory.exists())
            }
        }
    }

    @Test
    fun `resource failure removes the incomplete package and rolls progress back`() = runBlocking {
        LoopbackServer().use { server ->
            server.text("/playlist.m3u8", "#EXTM3U\n#EXTINF:4,\nsegment.ts")
            server.status("/segment.ts", 503)
            server.start()
            val output = temporaryFolder.newFile("failed.m3u8.part").also { it.delete() }
            val packageDirectory = temporaryFolder.root.resolve("failed.m3u8.files")
            var reported = 0L

            newTransport(retryDelay = {}).use { transport ->
                assertThrows(Exception::class.java) {
                    runBlocking {
                        downloadBrowserM3u8(
                            playlistUrl = server.url("/playlist.m3u8"),
                            headers = emptyMap(),
                            transport = transport,
                            autoMerge = true,
                            m3u8ThreadCount = 3,
                            outputPlaylistFile = output,
                            packageDirectory = packageDirectory,
                            onChunk = { bytes -> reported += bytes.toLong() },
                        )
                    }
                }
            }

            assertFalse(output.exists())
            assertFalse(packageDirectory.exists())
            assertEquals(0L, reported)
        }
    }

    @Test
    fun `automatic merge rejects a fifth master level`() {
        LoopbackServer().use { server ->
            repeat(MAX_BROWSER_M3U8_VARIANT_DEPTH + 1) { level ->
                server.text(
                    "/level$level.m3u8",
                    "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=$level\nlevel${level + 1}.m3u8",
                )
            }
            server.text("/level5.m3u8", "#EXTM3U\n#EXTINF:4,\nsegment.ts")
            server.start()
            val output = temporaryFolder.newFile("deep.m3u8.part").also { it.delete() }
            val packageDirectory = temporaryFolder.root.resolve("deep.m3u8.files")

            newTransport().use { transport ->
                assertThrows(IOException::class.java) {
                    runBlocking {
                        downloadBrowserM3u8(
                            playlistUrl = server.url("/level0.m3u8"),
                            headers = emptyMap(),
                            transport = transport,
                            autoMerge = true,
                            m3u8ThreadCount = 3,
                            outputPlaylistFile = output,
                            packageDirectory = packageDirectory,
                        )
                    }
                }
            }

            assertFalse(output.exists())
            assertFalse(packageDirectory.exists())
        }
    }

    @Test
    fun `renaming a package moves the companion directory and local playlist URIs`() {
        val current = temporaryFolder.newFile("video.m3u8")
        val currentDirectory = browserM3u8PackageDirectoryFor(current)
        assertTrue(currentDirectory.mkdirs())
        currentDirectory.resolve("segment_0.ts").writeText("segment")
        current.writeText("${packageDirectoryUri(currentDirectory)}segment_0.ts")
        val renamed = temporaryFolder.root.resolve("renamed.m3u8")

        renameBrowserM3u8Package(current, renamed)

        assertFalse(current.exists())
        assertTrue(renamed.exists())
        assertFalse(currentDirectory.exists())
        assertTrue(browserM3u8PackageDirectoryFor(renamed).exists())
        assertTrue(renamed.readText().contains(packageDirectoryUri(browserM3u8PackageDirectoryFor(renamed))))
    }

    private fun newTransport(
        retryDelay: (Long) -> Unit = {},
    ): BrowserDownloadTransport =
        BrowserDownloadTransport(
            BrowserDownloadTransportConfig(
                maxConcurrentTasks = 1,
                normalThreadCount = 1,
                m3u8ThreadCount = 3,
                chunkSizeKb = DEFAULT_BROWSER_DOWNLOAD_CHUNK_SIZE_KB,
                enableHttp2 = true,
            ),
            retryDelay = retryDelay,
        )

    private class LoopbackServer : Closeable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        fun text(path: String, content: String) {
            bytes(path, content.toByteArray(StandardCharsets.UTF_8), "application/vnd.apple.mpegurl")
        }

        fun bytes(path: String, content: ByteArray, contentType: String = "application/octet-stream") {
            server.createContext(path) { exchange -> exchange.respond(200, content, contentType) }
        }

        fun status(path: String, status: Int) {
            server.createContext(path) { exchange -> exchange.respond(status, ByteArray(0), "text/plain") }
        }

        fun start() = server.start()

        fun url(path: String): String = "http://127.0.0.1:${server.address.port}$path"

        override fun close() = server.stop(0)
    }
}

private fun HttpExchange.respond(status: Int, body: ByteArray, contentType: String) {
    responseHeaders.set("Content-Type", contentType)
    sendResponseHeaders(status, body.size.toLong())
    responseBody.use { output -> output.write(body) }
}
