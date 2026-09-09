package com.ai.assistance.operit.ui.features.chat.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingMessageQueueStoreTest {
    @Test fun lateRestoreDoesNotRecreateDeletedChat() {
        val store = PendingMessageQueueStore()
        store.enqueue("chat", "pending", true)
        val item = store.states.value.getValue("chat").messages.single()
        store.remove("chat", item.id)
        store.removeChat("chat")
        store.restore("chat", item)
        assertFalse("chat" in store.states.value)
    }

    @Test fun outOfOrderCancellationRestoresOriginalQueueOrderWithoutDuplicates() {
        val store = PendingMessageQueueStore()
        listOf("first", "second", "third").forEach { store.enqueue("chat", it, true) }
        val items = store.states.value.getValue("chat").messages
        store.remove("chat", items[0].id)
        store.remove("chat", items[1].id)
        store.restore("chat", items[0])
        store.restore("chat", items[1])
        store.restore("chat", items[0])
        assertEquals(items, store.states.value.getValue("chat").messages)
    }

    @Test
    fun queueRemainsAvailableWhenAnotherChatIsVisited() {
        val store = PendingMessageQueueStore()

        store.enqueue(chatId = "chat-a", text = "send after reply", isQueueBlocked = true)

        assertFalse(store.consumeAutoDequeueSignal(chatId = "chat-b", isQueueBlocked = false))
        assertEquals(
            listOf("send after reply"),
            store.states.value.getValue("chat-a").messages.map { it.text }
        )
        assertTrue(store.consumeAutoDequeueSignal(chatId = "chat-a", isQueueBlocked = false))
    }

    @Test
    fun cancellingCurrentTurnConsumesOnlyTheNextAutoDequeue() {
        val store = PendingMessageQueueStore()

        store.enqueue(chatId = "chat-a", text = "first", isQueueBlocked = true)
        store.suppressNextAutoDequeue("chat-a")

        assertFalse(store.consumeAutoDequeueSignal(chatId = "chat-a", isQueueBlocked = false))
        assertFalse(store.consumeAutoDequeueSignal(chatId = "chat-a", isQueueBlocked = false))
    }
}
