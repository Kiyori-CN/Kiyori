package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import java.time.Instant

class NetworkDirectoryClientTest {
    @Test fun `network root cannot be escaped by dot segments or commands`() {
        assertEquals("/data/目录", networkRemotePath("/data", "/目录"))
        listOf("/../etc", "/a/../../x", "/name\r\nDELE x", "/a\\b").forEach {
            assertThrows(IllegalArgumentException::class.java) { networkRemotePath("/data", it) }
        }
    }
    @Test fun `unimplemented remote writes fail before touching local paths`() = runBlocking {
        val tool = AITool("delete_file", listOf(ToolParameter("environment", "network:missing"), ToolParameter("path", "/storage/emulated/0/keep")))
        val result = NetworkFileSystemTools.executeIfNetwork(mock(), tool)
        assertNotNull(result); assertFalse(result!!.success)
        assertTrue(result.error.orEmpty().contains("不会修改"))
    }
    @Test fun `remote copy parameters are recognized in either direction`() {
        listOf("source_environment", "dest_environment").forEach { name ->
            assertTrue(NetworkFileSystemTools.hasNetworkEnvironment(AITool("copy_file", listOf(ToolParameter(name, "network:test")))))
        }
    }
    @Test fun `WebDAV decodes unicode children and omits requested collection`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(207).setBody("""<d:multistatus xmlns:d="DAV:">
                <d:response><d:href>/dav/</d:href><d:propstat><d:status>HTTP/1.1 200 OK</d:status><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
                <d:response><d:href>/dav/%E6%96%87.txt</d:href><d:propstat><d:status>HTTP/1.1 200 OK</d:status><d:prop><d:getcontentlength>42</d:getcontentlength><d:getlastmodified>Thu, 10 Sep 2026 00:00:00 GMT</d:getlastmodified></d:prop></d:propstat></d:response>
                </d:multistatus>"""))
            val profile = NetworkStorageProfile("id", "dav", NetworkStorageProtocol.WEBDAV, server.url("/dav/").toString())
            val entry = NetworkDirectoryClient.list(profile, "/", "").single()
            assertEquals("文.txt", entry.name); assertEquals(42L, entry.size)
            val request = server.takeRequest()
            assertEquals("PROPFIND", request.method); assertEquals("1", request.getHeader("Depth"))
        }
    }
    @Test fun `WebDAV never follows authentication redirects`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://other.invalid/"))
            val profile = NetworkStorageProfile("id", "dav", NetworkStorageProtocol.WEBDAV, server.url("/").toString(), username = "user")
            assertThrows(IllegalStateException::class.java) { NetworkDirectoryClient.list(profile, "/", "test") }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun `WebDAV rejects external entities`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(207).setBody("""<!DOCTYPE a [<!ENTITY x SYSTEM "file:///private">]><a>&x;</a>"""))
            val profile = NetworkStorageProfile("id", "dav", NetworkStorageProtocol.WEBDAV, server.url("/").toString())
            assertThrows(Exception::class.java) { NetworkDirectoryClient.list(profile, "/", "") }
        }
    }
    @Test fun `S3 signs canonical query order and host port without exposing secret`() {
        val instant = Instant.parse("2026-09-10T00:00:00Z")
        val a = NetworkDirectoryClient.signedS3Request("https://s3.example:9443/bucket?prefix=a%20b&list-type=2".toHttpUrl(), "ACCESS", "test-secret", "us-east-1", instant)
        val b = NetworkDirectoryClient.signedS3Request("https://s3.example:9443/bucket?list-type=2&prefix=a%20b".toHttpUrl(), "ACCESS", "test-secret", "us-east-1", instant)
        assertEquals(a.header("Authorization"), b.header("Authorization"))
        assertFalse(a.header("Authorization")!!.contains("test-secret"))
        assertEquals("20260910T000000Z", a.header("x-amz-date"))
    }
}
