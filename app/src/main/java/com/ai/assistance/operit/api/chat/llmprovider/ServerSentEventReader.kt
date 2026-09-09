package com.ai.assistance.operit.api.chat.llmprovider

import java.io.BufferedReader

/** SSE 的 data 字段属于一个事件；逐行解析会丢失跨行 JSON 与终态。 */
internal class ServerSentEventReader(private val reader: BufferedReader) {
    private var firstLine = true

    fun readData(): String? {
        val data = StringBuilder()
        var hasData = false
        while (true) {
            val raw = reader.readLine() ?: return if (hasData) data.toString() else null
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
