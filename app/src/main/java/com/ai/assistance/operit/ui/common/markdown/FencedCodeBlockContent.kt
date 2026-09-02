package com.ai.assistance.operit.ui.common.markdown

/** The only representation consumed by code, Mermaid and HTML previews. */
internal data class FencedCodeBlockPayload(
    val language: String,
    val code: String,
)

/**
 * Removes exactly one confirmed opening/closing fence from a CODE_BLOCK node.
 *
 * The stream splitters have already classified the node, so this function does
 * not try to repair malformed Markdown.  It only recognizes the same fence
 * contract as the splitters and otherwise keeps the raw payload observable.
 */
internal fun extractFencedCodeBlockPayload(raw: String): FencedCodeBlockPayload {
    val openingLineEnd = raw.indexOf('\n')
    val openingLine =
        if (openingLineEnd >= 0) raw.substring(0, openingLineEnd).removeSuffix("\r")
        else raw
    var openingIndex = 0
    while (openingIndex < openingLine.length && openingIndex < 3 && openingLine[openingIndex] == ' ') {
        openingIndex++
    }

    var openingFenceLength = 0
    while (
        openingIndex + openingFenceLength < openingLine.length &&
            openingLine[openingIndex + openingFenceLength] == '`'
    ) {
        openingFenceLength++
    }
    if (openingFenceLength < 3) {
        return FencedCodeBlockPayload(language = "", code = raw)
    }

    val infoString = openingLine.substring(openingIndex + openingFenceLength)
    val language = infoString.trim().takeWhile { !it.isWhitespace() }
    val codeStart = if (openingLineEnd >= 0) openingLineEnd + 1 else raw.length
    var lineStart = codeStart

    while (lineStart <= raw.length) {
        val lineEnd = raw.indexOf('\n', lineStart).let { if (it >= 0) it else raw.length }
        val line = raw.substring(lineStart, lineEnd).let {
            if (lineEnd < raw.length) it.removeSuffix("\r") else it
        }
        var indent = 0
        while (indent < line.length && indent < 3 && line[indent] == ' ') {
            indent++
        }
        var runLength = 0
        while (indent + runLength < line.length && line[indent + runLength] == '`') {
            runLength++
        }
        val trailing = line.substring(indent + runLength)
        val isClosing =
            runLength >= openingFenceLength &&
                runLength >= 3 &&
                trailing.all { it == ' ' || it == '\t' }
        if (isClosing) {
            var bodyEnd = lineStart
            if (bodyEnd > codeStart && raw[bodyEnd - 1] == '\n') {
                bodyEnd--
                if (bodyEnd > codeStart && raw[bodyEnd - 1] == '\r') {
                    bodyEnd--
                }
            }
            return FencedCodeBlockPayload(
                language = language,
                code = raw.substring(codeStart, bodyEnd),
            )
        }

        if (lineEnd == raw.length) {
            break
        }
        lineStart = lineEnd + 1
    }

    // An unfinished stream still belongs to one CODE_BLOCK.  Only the opening
    // line is structural; every subsequent byte remains code content.
    return FencedCodeBlockPayload(language = language, code = raw.substring(codeStart))
}
