package com.ai.assistance.operit.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantTurnCancellationTest {
    @Test
    fun explicitUserStop_isSilentIdle() {
        val decision =
            AssistantTurnCancellationPolicy.resolve(
                AssistantTurnCancellationSource.USER_STOP
            )

        assertEquals(AssistantTurnCancellationTerminal.IDLE, decision.terminal)
    }

    @Test
    fun destructiveHistoryMutation_isSilentIdle() {
        val decision =
            AssistantTurnCancellationPolicy.resolve(
                AssistantTurnCancellationSource.DESTRUCTIVE_HISTORY_MUTATION
            )

        assertEquals(AssistantTurnCancellationTerminal.IDLE, decision.terminal)
    }

    @Test
    fun configurationRefresh_isVisibleError() {
        val decision =
            AssistantTurnCancellationPolicy.resolve(
                AssistantTurnCancellationSource.CONFIGURATION_REFRESH
            )

        assertEquals(AssistantTurnCancellationTerminal.ERROR, decision.terminal)
    }

    @Test
    fun lifecycleInvalidation_isVisibleError() {
        val decision =
            AssistantTurnCancellationPolicy.resolve(
                AssistantTurnCancellationSource.LIFECYCLE_INVALIDATION
            )

        assertEquals(AssistantTurnCancellationTerminal.ERROR, decision.terminal)
    }

    @Test
    fun applicationExit_isNotMisclassifiedAsUserStop() {
        val decision =
            AssistantTurnCancellationPolicy.resolve(
                AssistantTurnCancellationSource.APPLICATION_EXIT
            )

        assertEquals(AssistantTurnCancellationTerminal.ERROR, decision.terminal)
    }

    @Test
    fun missingCancellationSource_isVisibleUnexpectedError() {
        val decision = AssistantTurnCancellationPolicy.resolve(null)

        assertEquals(AssistantTurnCancellationSource.UNEXPECTED, decision.source)
        assertEquals(AssistantTurnCancellationTerminal.ERROR, decision.terminal)
    }

    @Test
    fun cancellationRequest_isConsumedOnlyByMatchingTurn() {
        val registry = AssistantTurnCancellationRegistry()
        registry.startOperation("chat", 10L)
        registry.request(
            chatKey = "chat",
            operationId = 10L,
            source = AssistantTurnCancellationSource.USER_STOP,
        )

        assertNull(registry.consume("chat", 9L))
        assertEquals(
            AssistantTurnCancellationSource.USER_STOP,
            registry.consume("chat", 10L),
        )
        assertNull(registry.consume("chat", 10L))
    }

    @Test
    fun newTurn_doesNotInheritPreviousCancellationSource() {
        val registry = AssistantTurnCancellationRegistry()
        registry.request(
            chatKey = "chat",
            operationId = 10L,
            source = AssistantTurnCancellationSource.USER_STOP,
        )

        registry.startOperation("chat", 11L)

        assertNull(registry.consume("chat", 11L))
        assertNull(registry.consume("chat", 10L))
    }
}
