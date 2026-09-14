package com.ai.assistance.operit.api.chat.llmprovider

import java.io.BufferedReader
import java.io.IOException

/** SSE 的 data 字段属于一个事件；逐行解析会丢失跨行 JSON 与终态。 */
internal class ServerSentEventReader(private val reader: BufferedReader) {
    private var firstLine = true
    private var skipLf = false
    var pendingReadFailure: IOException? = null
        private set

    fun readData(): String? {
        pendingReadFailure?.let { throw it }
        val data = StringBuilder()
        var hasData = false
        while (true) {
            val raw = try {
                readLinePreservingTail()
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

    private fun readLinePreservingTail(): String? {
        pendingReadFailure?.let { throw it }
        val line = StringBuilder()
        while (true) {
            val next = try {
                reader.read()
            } catch (failure: IOException) {
                if (line.isEmpty()) throw failure
                // BufferedReader.readLine() 会丢掉异常前已消费但没有换行的字符。逐字符消费其
                // 内部缓冲才能保住尾行；这里只交付原文，完整 JSON 与成功终态仍由 Provider 校验。
                pendingReadFailure = failure
                return line.toString()
            }
            if (next < 0) return if (line.isEmpty()) null else line.toString()
            val char = next.toChar()
            if (skipLf) {
                skipLf = false
                if (char == '\n') continue
            }
            when (char) {
                '\r' -> {
                    skipLf = true
                    return line.toString()
                }
                '\n' -> return line.toString()
                else -> line.append(char)
            }
        }
    }
}
