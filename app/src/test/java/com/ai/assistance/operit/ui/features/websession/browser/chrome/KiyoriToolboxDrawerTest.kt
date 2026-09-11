package com.ai.assistance.operit.ui.features.websession.browser.chrome

import org.junit.Assert.*
import org.junit.Test

class KiyoriToolboxDrawerTest {
    @Test fun `both toolboxes retain three full rows with inert empty slots`() {
        listOf(0, 2, 9, 15).forEach { count ->
            val rows = toolboxSlotRows(count)
            assertEquals(3, rows.size)
            assertTrue(rows.all { it.size == 5 })
            assertEquals((0 until count).toList(), rows.flatten().filterNotNull())
            assertEquals(15 - count, rows.flatten().count { it == null })
        }
    }
    @Test(expected = IllegalArgumentException::class)
    fun `extra actions cannot silently overflow fixed toolbox`() { toolboxSlotRows(16) }
}
