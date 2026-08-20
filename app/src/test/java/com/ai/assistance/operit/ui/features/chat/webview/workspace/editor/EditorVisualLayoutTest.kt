package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorVisualLayoutTest {
    @Test
    fun `all wrap mode transitions reset to document origin`() {
        val transition = resolveEditorWrapModeViewportTransition()

        assertEquals(0f, transition.scrollX)
        assertEquals(0f, transition.scrollY)
    }

    @Test
    fun `soft wrap preserves document offsets without adding characters`() {
        val source = "<html><body><div class=\"very-long-name\">content</div></body></html>"

        val layout =
            EditorVisualLayout.build(
                text = source,
                softWrap = true,
                maxCellsPerRow = 16,
            )

        assertTrue(layout.rowCount > 1)
        assertEquals(0, layout.rows.first().startOffset)
        assertEquals(source.length, layout.rows.last().endOffset)
        assertEquals(
            source,
            layout.rows.joinToString(separator = "") { row ->
                source.substring(row.startOffset, row.endOffset)
            },
        )
    }

    @Test
    fun `unwrapped layout keeps one visual row per logical line`() {
        val source = "first very long line\nsecond\n"

        val layout =
            EditorVisualLayout.build(
                text = source,
                softWrap = false,
                maxCellsPerRow = 4,
            )

        assertEquals(3, layout.logicalLineCount)
        assertEquals(3, layout.rowCount)
        assertFalse(layout.rows[0].isContinuation)
        assertFalse(layout.rows[1].isContinuation)
        assertFalse(layout.rows[2].isContinuation)
    }

    @Test
    fun `wrap respects wide symbols and never splits a surrogate pair`() {
        val source = "ab中😀cd"

        val layout =
            EditorVisualLayout.build(
                text = source,
                softWrap = true,
                maxCellsPerRow = 4,
            )

        assertEquals(listOf("ab中", "😀cd"), layout.rows.map { row ->
            source.substring(row.startOffset, row.endOffset)
        })
        assertEquals(4, layout.rows[0].cellCount)
        assertEquals(4, layout.rows[1].cellCount)
    }

    @Test
    fun `offset at a wrap boundary maps to the following visual row`() {
        val source = "abcdefgh"
        val layout =
            EditorVisualLayout.build(
                text = source,
                softWrap = true,
                maxCellsPerRow = 4,
            )

        assertEquals(0, layout.rowIndexForOffset(3))
        assertEquals(1, layout.rowIndexForOffset(4))
        assertEquals(1, layout.rowIndexForOffset(source.length))
    }

    @Test
    fun `preferred html boundary is used when it avoids a very short row`() {
        val source = "<section><article>content</article></section>"
        val layout =
            EditorVisualLayout.build(
                text = source,
                softWrap = true,
                maxCellsPerRow = 18,
            )

        assertEquals("<section><article>", source.substring(0, layout.rows.first().endOffset))
    }

    @Test
    fun `newline offset remains on its logical line while next line starts separately`() {
        val source = "abcdefgh\nnext"
        val layout =
            EditorVisualLayout.build(
                text = source,
                softWrap = true,
                maxCellsPerRow = 4,
            )

        assertEquals(1, layout.rowIndexForOffset(source.indexOf('\n')))
        assertEquals(2, layout.rowIndexForOffset(source.indexOf('\n') + 1))
        assertEquals(0, layout.rows[1].logicalLine)
        assertEquals(1, layout.rows[2].logicalLine)
    }

    @Test
    fun `large minified line produces bounded continuous visual rows`() {
        val source = "x".repeat(100_000)
        val layout =
            EditorVisualLayout.build(
                text = source,
                softWrap = true,
                maxCellsPerRow = 40,
            )

        assertEquals(2_500, layout.rowCount)
        assertTrue(layout.rows.all { row -> row.cellCount in 1..40 })
        layout.rows.zipWithNext().forEach { (current, next) ->
            assertEquals(current.endOffset, next.startOffset)
            assertEquals(current.endCell, next.startCell)
        }
        assertEquals(source.length, layout.rows.last().endOffset)
    }

    @Test
    fun `horizontal rows keep checkpoints for long-line viewport mapping`() {
        val source = "x".repeat(512) + "\t界z"
        val layout =
            EditorVisualLayout.build(
                text = source,
                softWrap = false,
                maxCellsPerRow = 40,
            )
        val row = layout.rows.single()

        assertTrue(row.cellCheckpoints.size >= 4)
        assertEquals(512, layout.offsetForCell(source, row, 512))
        assertEquals(512, layout.cellForOffset(source, row, 512))
        assertEquals(512, layout.offsetForCell(source, row, 513))
        assertEquals(513, layout.offsetForCell(source, row, 516))
        assertEquals(513, layout.offsetForCell(source, row, 517))
        assertEquals(514, layout.offsetForCell(source, row, 518))
        assertEquals(516, layout.cellForOffset(source, row, 513))
        assertEquals(518, layout.cellForOffset(source, row, 514))
    }

    @Test
    fun `checkpoint mapping preserves emoji cluster boundaries`() {
        val familyEmoji = "👨‍👩‍👧‍👦"
        val emojiOffset = 512
        val emojiEnd = emojiOffset + familyEmoji.length
        val source = "x".repeat(emojiOffset) + familyEmoji + "z"
        val layout =
            EditorVisualLayout.build(
                text = source,
                softWrap = false,
                maxCellsPerRow = 40,
            )
        val row = layout.rows.single()

        assertEquals(emojiOffset, layout.offsetForCell(source, row, 513))
        assertEquals(emojiEnd, layout.offsetForCell(source, row, 514))
        assertEquals(512, layout.cellForOffset(source, row, emojiOffset + 1))
        assertEquals(514, layout.cellForOffset(source, row, emojiEnd))
    }
}
