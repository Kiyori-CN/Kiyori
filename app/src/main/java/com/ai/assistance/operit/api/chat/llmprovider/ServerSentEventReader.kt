package com.ai.assistance.operit.api.chat.llmprovider

import java.io.BufferedReader
import java.io.IOException

/** SSE 的 data 字段属于一个事件；逐行解析会丢失跨行 JSON 与终态。 */
internal class ServerSentEventReader(private val reader: BufferedReader) {
    private var firstLine = true
    var pendingReadFailure: IOException? = null
        private set

    fun readData(): String? {
        pendingReadFailure?.let { throw it }
        val data = StringBuilder()
        var hasData = false
        while (true) {
            val raw = try {
                reader.readLine()
            } catch (failure: IOException) {
                if (!hasData) throw failure
                // 与干净 EOF 一样先交付已经读完的 data 行，不能因下一次分块读取失败而丢掉
                // 最后一段正文或真实终态。下次读取仍抛原异常；没有终态绝不据此伪造完成。
                pendingReadFailure = failure
                return data.toString()
            } ?: return if (hasData) data.toString() else null
            val line = if (firstLine) raw.removePrefix("\uFEFF") else raw
            firstLine = false
            if (line.isEmpty()) {
                if (hasData) return data.toString()
                continue
            }
            if (line.startsWith(':')) continue
            val separator = line.indexOf(':')
            val field = if (separator < 0) line else line.substring(0, separator)
            if (field != "data") continue
            val value = if (separator < 0) "" else line.substring(separator + 1).removePrefix(" ")
            if (hasData) data.append('\n')
            data.append(value)
            hasData = true
        }
    }
}
