package com.ai.assistance.operit.util.streamnative

import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

private fun Int.toMarkdownTypeOrNull(): MarkdownProcessorType? =
    MarkdownProcessorType.entries.getOrNull(this)

private fun IntArray.toInlineStableNodes(content: String): List<MarkdownNodeStable> {
    val nodes = ArrayList<MarkdownNodeStable>(size / 3)
    var index = 0

    while (index + 2 < size) {
        val typeOrdinal = this[index]
        val start = this[index + 1]
        val end = this[index + 2]
        index += 3

        if (typeOrdinal < 0 || start < 0 || end < start || end > content.length) {
            continue
        }

        val type = typeOrdinal.toMarkdownTypeOrNull() ?: MarkdownProcessorType.PLAIN_TEXT
        val nodeContent =
            when (type) {
                MarkdownProcessorType.HTML_BREAK -> "\n"
                MarkdownProcessorType.INLINE_CODE ->
                    stripMarkdownInlineCodeDelimiters(content.substring(start, end))
                else -> content.substring(start, end)
            }
        nodes +=
            MarkdownNodeStable(
                type = type,
                content = nodeContent,
                children = emptyList()
            )
    }

    return nodes
}

/**
 * Native inline-code groups retain their outer backtick run so unmatched shorter runs inside a
 * multi-backtick code span are not lost. AST consumers remove only one matching outer run.
 */
internal fun stripMarkdownInlineCodeDelimiters(content: String): String {
    val openingLength = content.takeWhile { it == '`' }.length
    if (openingLength == 0 || content.length < openingLength * 2) {
        return content
    }

    val closingStart = content.length - openingLength
    if ((closingStart until content.length).any { content[it] != '`' }) {
        return content
    }
    return content.substring(openingLength, closingStart)
}

object NativeMarkdownSplitter {

    init {
        System.loadLibrary("streamnative")
    }

    private external fun nativeCreateBlockSession(): Long
    private external fun nativeCreateInlineSession(): Long
    private external fun nativeDestroySession(handle: Long)
    private external fun nativePush(handle: Long, chunk: String): IntArray

    class Session internal constructor(
        private val handle: Long,
    ) {
        fun push(chunk: String): IntArray = nativePush(handle, chunk)
        fun destroy() = nativeDestroySession(handle)
    }

    fun createBlockSession(): Session = Session(nativeCreateBlockSession())
    fun createInlineSession(): Session = Session(nativeCreateInlineSession())

    fun parseInlineToStableNodes(content: String): List<MarkdownNodeStable> {
        if (content.isEmpty()) return emptyList()

        val session = createInlineSession()
        return try {
            session.push(content).toInlineStableNodes(content)
        } finally {
            session.destroy()
        }
    }
}
