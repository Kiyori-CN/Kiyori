package com.ai.assistance.operit.ui.main.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriShellStateTest {
    @Test
    fun `launch state is software home center with bottom navigation`() {
        val state = KiyoriShellState()

        assertEquals(PrimaryDestination.SOFTWARE_HOME, state.primaryDestination)
        assertEquals(SoftwareHomePage.HOME, state.softwareHomePage)
        assertTrue(state.showsBottomBar)
    }

    @Test
    fun `minus one and AI home hide bottom navigation`() {
        assertFalse(
            KiyoriShellState(softwareHomePage = SoftwareHomePage.MINUS_ONE).showsBottomBar,
        )
        assertFalse(
            KiyoriShellState(softwareHomePage = SoftwareHomePage.AI_HOME).showsBottomBar,
        )
    }

    @Test
    fun `all non software root destinations show bottom navigation`() {
        PrimaryDestination.entries
            .filterNot { destination -> destination == PrimaryDestination.SOFTWARE_HOME }
            .forEach { destination ->
                assertTrue(
                    KiyoriShellState(primaryDestination = destination).showsBottomBar,
                )
            }
    }

    @Test
    fun `child destination hides bottom navigation and Back restores owner`() {
        val state =
            KiyoriShellState(
                softwareHomePage = SoftwareHomePage.AI_HOME,
                child = KiyoriShellChild.AI_CENTER,
            )

        assertFalse(state.showsBottomBar)
        assertEquals(
            KiyoriShellBackTransition(
                state = state.copy(child = null),
                result = KiyoriShellBackResult.CONSUMED,
            ),
            state.handleBack(),
        )
    }

    @Test
    fun `Back from side home page returns to software home center`() {
        listOf(SoftwareHomePage.MINUS_ONE, SoftwareHomePage.AI_HOME).forEach { page ->
            val transition = KiyoriShellState(softwareHomePage = page).handleBack()

            assertEquals(KiyoriShellBackResult.CONSUMED, transition.result)
            assertEquals(SoftwareHomePage.HOME, transition.state.softwareHomePage)
        }
    }

    @Test
    fun `Back from another root returns to software home center`() {
        val transition =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.FILE_MANAGEMENT_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
            ).handleBack()

        assertEquals(KiyoriShellBackResult.CONSUMED, transition.result)
        assertEquals(PrimaryDestination.SOFTWARE_HOME, transition.state.primaryDestination)
        assertEquals(SoftwareHomePage.HOME, transition.state.softwareHomePage)
    }

    @Test
    fun `Back from software home center requests exit`() {
        val state = KiyoriShellState()

        assertEquals(
            KiyoriShellBackTransition(
                state = state,
                result = KiyoriShellBackResult.REQUEST_EXIT,
            ),
            state.handleBack(),
        )
    }

    @Test
    fun `selecting software home always returns its center page`() {
        val state =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.BROWSER_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
            )

        assertEquals(
            KiyoriShellState(),
            state.selectPrimary(PrimaryDestination.SOFTWARE_HOME),
        )
    }

    @Test
    fun `AI host translation follows the pager without transforms`() {
        assertEquals(1080f, calculateKiyoriAiHostTranslation(-1f, 1080f), 0f)
        assertEquals(0f, calculateKiyoriAiHostTranslation(0f, 1080f), 0f)
        assertEquals(-540f, calculateKiyoriAiHostTranslation(0.5f, 1080f), 0f)
    }
}
