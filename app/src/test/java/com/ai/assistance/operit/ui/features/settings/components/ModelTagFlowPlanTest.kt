package com.ai.assistance.operit.ui.features.settings.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTagFlowPlanTest {
    @Test
    fun `collapsed plan omits expand indicator when every tag fits`() {
        val plan =
            planModelTagFlow(
                modelWidths = listOf(90, 90, 90),
                availableWidth = 290,
                horizontalSpacing = 10,
                maxRows = 3,
                expandIndicatorWidth = 80
            )

        assertEquals(listOf(listOf(0, 1, 2)), plan.rows.map { it.modelIndexes })
        assertFalse(plan.rows.any { it.includesExpandIndicator })
        assertEquals(0, plan.hiddenModelCount)
    }

    @Test
    fun `collapsed plan omits expand indicator when tags exactly fill three rows`() {
        val plan =
            planModelTagFlow(
                modelWidths = listOf(120, 120, 120, 120, 120, 120),
                availableWidth = 250,
                horizontalSpacing = 10,
                maxRows = 3,
                expandIndicatorWidth = 80
            )

        assertEquals(
            listOf(listOf(0, 1), listOf(2, 3), listOf(4, 5)),
            plan.rows.map { it.modelIndexes }
        )
        assertFalse(plan.rows.any { it.includesExpandIndicator })
        assertEquals(0, plan.hiddenModelCount)
    }

    @Test
    fun `collapsed plan reserves a complete indicator in the third row`() {
        val plan =
            planModelTagFlow(
                modelWidths = listOf(120, 120, 120, 120, 120, 120, 120),
                availableWidth = 250,
                horizontalSpacing = 10,
                maxRows = 3,
                expandIndicatorWidth = 80
            )

        assertEquals(
            listOf(listOf(0, 1), listOf(2, 3), listOf(4)),
            plan.rows.map { it.modelIndexes }
        )
        assertFalse(plan.rows.first().includesExpandIndicator)
        assertTrue(plan.rows.last().includesExpandIndicator)
        assertEquals(2, plan.hiddenModelCount)
    }

    @Test
    fun `collapsed plan removes a whole tag instead of clipping it`() {
        val plan =
            planModelTagFlow(
                modelWidths = listOf(250, 250, 120, 120, 120),
                availableWidth = 250,
                horizontalSpacing = 10,
                maxRows = 3,
                expandIndicatorWidth = 80
            )

        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), plan.rows.map { it.modelIndexes })
        assertTrue(plan.rows.last().includesExpandIndicator)
        assertEquals(2, plan.hiddenModelCount)
    }

    @Test
    fun `collapsed plan never skips a hidden tag to show a later narrow tag`() {
        val plan =
            planModelTagFlow(
                modelWidths = listOf(120, 120, 120, 120, 220, 50, 10),
                availableWidth = 250,
                horizontalSpacing = 10,
                maxRows = 3,
                expandIndicatorWidth = 20
            )

        assertEquals(
            listOf(listOf(0, 1), listOf(2, 3), listOf(4)),
            plan.rows.map { it.modelIndexes }
        )
        assertTrue(plan.rows.last().includesExpandIndicator)
        assertEquals(2, plan.hiddenModelCount)
    }

    @Test
    fun `expanded plan keeps every tag and never adds the indicator`() {
        val plan =
            planModelTagFlow(
                modelWidths = listOf(120, 120, 120, 120, 120, 120, 120),
                availableWidth = 250,
                horizontalSpacing = 10,
                maxRows = Int.MAX_VALUE,
                expandIndicatorWidth = null
            )

        assertEquals(
            listOf(listOf(0, 1), listOf(2, 3), listOf(4, 5), listOf(6)),
            plan.rows.map { it.modelIndexes }
        )
        assertFalse(plan.rows.any { it.includesExpandIndicator })
        assertEquals(0, plan.hiddenModelCount)
    }
}
