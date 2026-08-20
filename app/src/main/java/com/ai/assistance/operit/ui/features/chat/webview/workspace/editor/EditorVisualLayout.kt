package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import kotlin.math.max

private const val EDITOR_CELL_CHECKPOINT_INTERVAL = 128

internal data class EditorVisualRow(
    val logicalLine: Int,
    val lineStartOffset: Int,
    val lineEndOffset: Int,
    val startOffset: Int,
    val endOffset: Int,
    val startCell: Int,
    val endCell: Int,
    /**
     * Alternating absolute cell and source-offset pairs. Long horizontal rows use these
     * checkpoints to enter the visible range without rescanning the entire logical line.
     */
    val cellCheckpoints: IntArray = intArrayOf(),
) {
    val isContinuation: Boolean
        get() = startOffset > lineStartOffset

    val cellCount: Int
        get() = endCell - startCell
}

internal data class EditorWrapModeViewportTransition(
    val scrollX: Float,
    val scrollY: Float,
)

/**
 * Both source browsing modes start from the document origin. This keeps a mode switch predictable
 * for minified pages and prevents a stale caret from moving the viewport to a distant line.
 */
internal fun resolveEditorWrapModeViewportTransition(): EditorWrapModeViewportTransition =
    EditorWrapModeViewportTransition(
        scrollX = 0f,
        scrollY = 0f,
    )

/**
 * Maps immutable document offsets to visual rows without inserting line breaks into the document.
 *
 * Page source often contains minified HTML or inline scripts with hundreds of thousands of
 * characters on one logical line. Keeping visual wrapping in a separate layout preserves exact
 * source offsets for editing, undo, search and page application.
 */
internal class EditorVisualLayout private constructor(
    val rows: List<EditorVisualRow>,
    val logicalLineCount: Int,
) {
    val rowCount: Int
        get() = rows.size

    fun row(index: Int): EditorVisualRow = rows[index.coerceIn(0, rows.lastIndex)]

    fun rowIndexForOffset(offset: Int): Int {
        if (rows.size <= 1) {
            return 0
        }

        val safeOffset = offset.coerceAtLeast(0)
        var low = 0
        var high = rows.lastIndex
        var candidate = 0
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (rows[middle].startOffset <= safeOffset) {
                candidate = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }

        return candidate
    }

    fun offsetForCell(
        text: CharSequence,
        row: EditorVisualRow,
        targetCell: Int,
    ): Int {
        val safeTargetCell = targetCell.coerceIn(row.startCell, row.endCell)
        val checkpointIndex = checkpointIndexForCell(row, safeTargetCell)
        var cell =
            if (checkpointIndex >= 0) {
                row.cellCheckpoints[checkpointIndex * 2]
            } else {
                row.startCell
            }
        var offset =
            if (checkpointIndex >= 0) {
                row.cellCheckpoints[checkpointIndex * 2 + 1]
            } else {
                row.startOffset
            }

        while (offset < row.endOffset && cell < safeTargetCell) {
            val nextOffset = editorNextSymbolOffset(text, offset, row.endOffset)
            val nextCell = cell + editorCellWidth(text, offset, row.endOffset)
            if (nextCell > safeTargetCell) {
                break
            }
            cell = nextCell
            offset = nextOffset
        }
        return offset
    }

    fun cellForOffset(
        text: CharSequence,
        row: EditorVisualRow,
        targetOffset: Int,
    ): Int {
        val safeTargetOffset = targetOffset.coerceIn(row.startOffset, row.endOffset)
        val checkpointIndex = checkpointIndexForOffset(row, safeTargetOffset)
        var cell =
            if (checkpointIndex >= 0) {
                row.cellCheckpoints[checkpointIndex * 2]
            } else {
                row.startCell
            }
        var offset =
            if (checkpointIndex >= 0) {
                row.cellCheckpoints[checkpointIndex * 2 + 1]
            } else {
                row.startOffset
            }

        while (offset < row.endOffset && offset < safeTargetOffset) {
            val nextOffset = editorNextSymbolOffset(text, offset, row.endOffset)
            if (nextOffset > safeTargetOffset) {
                break
            }
            cell += editorCellWidth(text, offset, row.endOffset)
            offset = nextOffset
        }
        return cell
    }

    companion object {
        fun build(
            text: CharSequence,
            softWrap: Boolean,
            maxCellsPerRow: Int,
        ): EditorVisualLayout {
            val rows = ArrayList<EditorVisualRow>()
            val resolvedMaxCells =
                if (softWrap) {
                    max(1, maxCellsPerRow)
                } else {
                    Int.MAX_VALUE
                }

            var logicalLine = 0
            var lineStart = 0
            var index = 0
            while (index <= text.length) {
                if (index == text.length || text[index] == '\n') {
                    appendLineRows(
                        rows = rows,
                        text = text,
                        logicalLine = logicalLine,
                        lineStart = lineStart,
                        lineEnd = index,
                        maxCellsPerRow = resolvedMaxCells,
                    )
                    logicalLine++
                    if (index == text.length) {
                        break
                    }
                    lineStart = index + 1
                }
                index++
            }

            if (rows.isEmpty()) {
                rows +=
                    EditorVisualRow(
                        logicalLine = 0,
                        lineStartOffset = 0,
                        lineEndOffset = 0,
                        startOffset = 0,
                        endOffset = 0,
                        startCell = 0,
                        endCell = 0,
                    )
                logicalLine = 1
            }

            return EditorVisualLayout(
                rows = rows,
                logicalLineCount = logicalLine,
            )
        }

        private fun appendLineRows(
            rows: MutableList<EditorVisualRow>,
            text: CharSequence,
            logicalLine: Int,
            lineStart: Int,
            lineEnd: Int,
            maxCellsPerRow: Int,
        ) {
            if (lineStart == lineEnd) {
                rows +=
                    EditorVisualRow(
                        logicalLine = logicalLine,
                        lineStartOffset = lineStart,
                        lineEndOffset = lineEnd,
                        startOffset = lineStart,
                        endOffset = lineEnd,
                        startCell = 0,
                        endCell = 0,
                    )
                return
            }

            var rowStart = lineStart
            var rowStartCell = 0
            var offset = lineStart
            var lineCell = 0
            var preferredBreakOffset = -1
            var preferredBreakCell = -1
            val checkpointValues =
                if (maxCellsPerRow == Int.MAX_VALUE) {
                    ArrayList<Int>().apply {
                        add(0)
                        add(lineStart)
                    }
                } else {
                    null
                }
            var nextCheckpointCell = EDITOR_CELL_CHECKPOINT_INTERVAL

            while (offset < lineEnd) {
                if (
                    checkpointValues != null &&
                        lineCell - rowStartCell >= nextCheckpointCell
                ) {
                    checkpointValues.add(lineCell)
                    checkpointValues.add(offset)
                    nextCheckpointCell += EDITOR_CELL_CHECKPOINT_INTERVAL
                }
                val nextOffset = editorNextSymbolOffset(text, offset, lineEnd)
                val symbolCells = editorCellWidth(text, offset, lineEnd)
                val currentRowCells = lineCell - rowStartCell

                if (
                    maxCellsPerRow != Int.MAX_VALUE &&
                        currentRowCells > 0 &&
                        currentRowCells + symbolCells > maxCellsPerRow
                ) {
                    val minimumUsefulBreakCells = max(1, maxCellsPerRow / 2)
                    val usePreferredBreak =
                        preferredBreakOffset > rowStart &&
                            preferredBreakCell - rowStartCell >= minimumUsefulBreakCells
                    val wrapOffset = if (usePreferredBreak) preferredBreakOffset else offset
                    val wrapCell = if (usePreferredBreak) preferredBreakCell else lineCell

                    rows +=
                        EditorVisualRow(
                            logicalLine = logicalLine,
                            lineStartOffset = lineStart,
                            lineEndOffset = lineEnd,
                            startOffset = rowStart,
                            endOffset = wrapOffset,
                            startCell = rowStartCell,
                            endCell = wrapCell,
                        )

                    rowStart = wrapOffset
                    rowStartCell = wrapCell
                    offset = wrapOffset
                    lineCell = wrapCell
                    preferredBreakOffset = -1
                    preferredBreakCell = -1
                    continue
                }

                if (
                    text[offset] == '<' &&
                        offset > rowStart &&
                        currentRowCells >= max(1, maxCellsPerRow / 2)
                ) {
                    preferredBreakOffset = offset
                    preferredBreakCell = lineCell
                }

                lineCell += symbolCells
                if (isEditorWrapOpportunity(text, offset, nextOffset)) {
                    preferredBreakOffset = nextOffset
                    preferredBreakCell = lineCell
                }
                offset = nextOffset
            }

            rows +=
                EditorVisualRow(
                    logicalLine = logicalLine,
                    lineStartOffset = lineStart,
                    lineEndOffset = lineEnd,
                    startOffset = rowStart,
                    endOffset = lineEnd,
                    startCell = rowStartCell,
                    endCell = lineCell,
                    cellCheckpoints = checkpointValues?.toIntArray() ?: intArrayOf(),
                )
        }

        private fun checkpointIndexForCell(
            row: EditorVisualRow,
            targetCell: Int,
        ): Int {
            val checkpointCount = row.cellCheckpoints.size / 2
            var low = 0
            var high = checkpointCount - 1
            var candidate = -1
            while (low <= high) {
                val middle = (low + high) ushr 1
                val checkpointCell = row.cellCheckpoints[middle * 2]
                if (checkpointCell <= targetCell) {
                    candidate = middle
                    low = middle + 1
                } else {
                    high = middle - 1
                }
            }
            return candidate
        }

        private fun checkpointIndexForOffset(
            row: EditorVisualRow,
            targetOffset: Int,
        ): Int {
            val checkpointCount = row.cellCheckpoints.size / 2
            var low = 0
            var high = checkpointCount - 1
            var candidate = -1
            while (low <= high) {
                val middle = (low + high) ushr 1
                val checkpointOffset = row.cellCheckpoints[middle * 2 + 1]
                if (checkpointOffset <= targetOffset) {
                    candidate = middle
                    low = middle + 1
                } else {
                    high = middle - 1
                }
            }
            return candidate
        }

        private fun isEditorWrapOpportunity(
            text: CharSequence,
            start: Int,
            end: Int,
        ): Boolean {
            if (end != start + 1) {
                return false
            }
            val character = text[start]
            return character.isWhitespace() ||
                character == '>' ||
                character == ';' ||
                character == ',' ||
                character == '}' ||
                character == ')' ||
                character == ']' ||
                character == '/' ||
                character == '&' ||
                character == '?'
        }
    }
}
