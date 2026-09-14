package com.kiyori.platform.network

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class NetworkResponseFailureObserverTest {
    @Test
    fun `body observation stays lazy preserves partial bytes and original failure exactly once`() {
        val reads = AtomicInteger()
        val reported = mutableListOf<IOException>()
        val failure = IOException("fixture stream truncated")
        val response = response(object : Source {
            override fun timeout() = Timeout.NONE
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (reads.getAndIncrement() == 0) {
                    sink.writeUtf8("partial")
                    return 7
                }
                throw failure
            }
            override fun close() { throw failure }
        }).observeBodyFailure(reported::add)
        assertEquals(0, reads.get())
        assertEquals("text/event-stream".toMediaType(), response.body!!.contentType())
        val source = response.body!!.source()
        assertEquals("partial", source.readUtf8(7))
        assertTrue(reported.isEmpty())
        assertSame(failure, assertThrows(IOException::class.java) { source.readByte() })
        assertSame(failure, assertThrows(IOException::class.java) { source.close() })
        assertEquals(listOf(failure), reported)
    }

    @Test
    fun `normal EOF and explicit close do not manufacture failures`() {
        val reported = mutableListOf<IOException>()
        val closed = AtomicInteger()
        val content = Buffer().writeUtf8("data: completed\n\n")
        response(object : Source {
            override fun timeout() = Timeout.NONE
            override fun read(sink: Buffer, byteCount: Long) = content.read(sink, byteCount)
            override fun close() { closed.incrementAndGet() }
        }).observeBodyFailure(reported::add).use {
            assertEquals("data: completed\n\n", it.body!!.string())
        }
        assertTrue(reported.isEmpty())
        assertEquals(1, closed.get())
    }

    private fun response(source: Source): Response {
        val buffered = source.buffer()
        return Response.Builder().request(Request.Builder().url("https://example.com/").build())
            .protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(object : ResponseBody() {
                override fun contentType() = "text/event-stream".toMediaType()
                override fun contentLength() = -1L
                override fun source() = buffered
            }).build()
    }
}
