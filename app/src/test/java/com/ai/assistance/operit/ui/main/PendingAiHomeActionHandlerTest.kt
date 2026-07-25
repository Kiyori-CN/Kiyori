package com.ai.assistance.operit.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingAiHomeActionHandlerTest {
    @Test
    fun actionBecomesConsumableOnlyAfterAiHomeIsReady() {
        val store = PendingAiHomeActionStore()

        val requestId = store.request(AiHomeQuickAction.FOCUS_INPUT)

        assertEquals(requestId, store.pendingAction.value?.requestId)
        assertFalse(store.pendingAction.value?.isAiHomeReady == true)

        store.markAiHomeReady()

        assertTrue(store.pendingAction.value?.isAiHomeReady == true)
        store.consume(requestId)
        assertNull(store.pendingAction.value)
    }

    @Test
    fun staleConsumerCannotClearNewerAction() {
        val store = PendingAiHomeActionStore()
        val firstRequestId = store.request(AiHomeQuickAction.OPEN_ATTACHMENTS)
        val secondRequestId = store.request(AiHomeQuickAction.CAPTURE_PHOTO)

        store.consume(firstRequestId)

        assertEquals(secondRequestId, store.pendingAction.value?.requestId)
        assertEquals(AiHomeQuickAction.CAPTURE_PHOTO, store.pendingAction.value?.action)
    }
}
