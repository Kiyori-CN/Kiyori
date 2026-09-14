package com.kiyori.platform.network

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

/** 响应头返回后流仍可能断开；按需观察原 body，不能预读、重试或把 EOF 异常转换为成功。 */
internal fun Response.observeBodyFailure(onFailure: (IOException) -> Unit): Response {
    val original = body ?: return this
    val reported = AtomicBoolean(false)
    fun report(error: IOException) {
        if (reported.compareAndSet(false, true)) onFailure(error)
    }
    val observedSource = object : ForwardingSource(original.source()) {
        override fun read(sink: Buffer, byteCount: Long): Long = try {
            super.read(sink, byteCount)
        } catch (error: IOException) {
            report(error)
            throw error
        }

        override fun close() {
            try {
                super.close()
            } catch (error: IOException) {
                report(error)
                throw error
            }
        }
    }.buffer()
    return newBuilder().body(object : ResponseBody() {
        override fun contentType() = original.contentType()
        override fun contentLength() = original.contentLength()
        override fun source() = observedSource
    }).build()
}
