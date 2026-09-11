package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageVariantEntity
import org.junit.Assert.*
import org.junit.Test

class ToolPkgVariantSnapshotTest {
    @Test fun regeneratedEventUsesPersistedIdentityAndPreservesFavoriteAndExactBody() {
        val base = ChatMessage(sender = "ai", content = "original", timestamp = 100L, roleName = "role", isFavorite = true)
        val regenerated = ChatMessage(sender = "ai", content = "new".repeat(70_000), timestamp = 999L, modelName = "model", outputTokens = 123, completedAt = 456L)
        val variant = MessageVariantEntity.fromChatMessage("chat", 100L, 2, regenerated)
        val snapshot = variant.applyTo(base, 2)
        val payload = ToolPkgChatMessageHookBridge.buildChatMessageEventPayload("chat", snapshot)
        assertEquals(100L, payload["timestamp"])
        assertEquals("chat", payload["chatId"])
        assertEquals(true, payload["isFavorite"])
        assertEquals("role", payload["roleName"])
        assertEquals(2, payload["selectedVariantIndex"])
        assertEquals(regenerated.content, payload["content"])
        assertEquals(123, payload["outputTokens"])
        assertEquals(456L, payload["completedAt"])
        assertEquals("model", payload["modelName"])
        assertEquals(2, snapshot.variantCount)
        assertEquals("original", base.content)
    }
}
