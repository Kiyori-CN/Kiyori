package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderExecutionSequenceTest {
    @Test
    fun expectedNextSequence_usesOneBasedResponsesEventSequence() {
        assertEquals(
            ProviderExecutionEntity.FIRST_EVENT_SEQUENCE,
            ProviderExecutionEntity.expectedNextSequence(
                ProviderExecutionEntity.NO_APPLIED_SEQUENCE
            ),
        )
        assertEquals(2L, ProviderExecutionEntity.expectedNextSequence(1L))
        assertEquals(43L, ProviderExecutionEntity.expectedNextSequence(42L))
    }
}
