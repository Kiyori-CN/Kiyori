package com.ai.assistance.operit.ui.features.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSourceEditorSearchTest {
    private val source = "one two one two one"

    @Test
    fun `next search starts after the full previous match and wraps`() {
        val first = findNextPageSourceMatch(source, "one", previousEnd = -1)
        val second = findNextPageSourceMatch(source, "one", previousEnd = first!!.end)
        val wrapped = findNextPageSourceMatch(source, "one", previousEnd = second!!.end)

        assertEquals(PageSourceMatch(0, 3), first)
        assertEquals(PageSourceMatch(8, 11), second)
        assertEquals(PageSourceMatch(16, 19), wrapped)
    }

    @Test
    fun `previous search starts before the current match and wraps`() {
        val previousFromThird =
            findPreviousPageSourceMatch(
                source = source,
                query = "one",
                currentStart = 16,
            )
        val previousFromFirst =
            findPreviousPageSourceMatch(
                source = source,
                query = "one",
                currentStart = 0,
            )
        val previousWithoutCurrent =
            findPreviousPageSourceMatch(
                source = source,
                query = "one",
                currentStart = -1,
            )

        assertEquals(PageSourceMatch(8, 11), previousFromThird)
        assertEquals(PageSourceMatch(16, 19), previousFromFirst)
        assertEquals(PageSourceMatch(16, 19), previousWithoutCurrent)
    }

    @Test
    fun `empty search has no match`() {
        assertNull(findNextPageSourceMatch(source, "", previousEnd = -1))
        assertNull(findPreviousPageSourceMatch(source, "", currentStart = -1))
    }

    @Test
    fun `replacement length becomes the next search cursor`() {
        val first = findNextPageSourceMatch(source, "one", previousEnd = -1)!!
        val replacedSource = source.replaceRange(first.start, first.end, "replacement")
        val replacementEnd = first.start + "replacement".length
        val next =
            findNextPageSourceMatch(
                replacedSource,
                "one",
                previousEnd = replacementEnd,
            )

        assertTrue(next != null)
        assertEquals(PageSourceMatch(16, 19), next)
    }
}
