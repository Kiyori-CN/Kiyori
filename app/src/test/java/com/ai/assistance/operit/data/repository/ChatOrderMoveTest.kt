package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.ChatHistory
import org.junit.Assert.*
import org.junit.Test

class ChatOrderMoveTest {
    private fun chat(id: String, order: Long, group: String? = null) =
        ChatHistory(id = id, title = id, messages = emptyList(), displayOrder = order, group = group)
    private val a = chat("a", 0)
    private val hidden = chat("hidden", 1, "collapsed")
    private val b = chat("b", 2)
    private val c = chat("c", 3)
    private val all = listOf(a, hidden, b, c)
    private fun move(source: ChatHistory = c, anchor: ChatHistory? = a, before: Boolean = true) =
        ChatOrderMove(ChatPositionSnapshot.from(source), anchor?.let(ChatPositionSnapshot::from), before,
            source.group, source.characterCardName, source.characterGroupId)

    @Test fun filteredMoveKeepsHiddenRowsAndTheirRelativeOrder() {
        val result = applyChatOrderMove(all, move())
        assertEquals(listOf("c", "a", "hidden", "b"), result.map { it.id })
        assertEquals("collapsed", result.first { it.id == "hidden" }.group)
        assertEquals(listOf(0L, 1L, 2L, 3L), result.map { it.displayOrder })
    }

    @Test fun movingAfterAnchorKeepsUnrelatedRows() {
        assertEquals(listOf("hidden", "b", "a", "c"), applyChatOrderMove(all, move(a, b, false)).map { it.id })
    }

    @Test fun latestTitleUsageAndWorkspaceArePreserved() {
        val latest = c.copy(title = "updated elsewhere", inputTokens = 42, workspace = "/new")
        val result = applyChatOrderMove(all.dropLast(1) + latest, move()).first()
        assertEquals("updated elsewhere", result.title)
        assertEquals(42, result.inputTokens)
        assertEquals("/new", result.workspace)
    }

    @Test fun onlyMovedChatReceivesTargetGroupAndBinding() {
        val request = move().copy(targetGroup = "target", targetCharacterGroupId = "role-group")
        val result = applyChatOrderMove(all, request)
        assertEquals("target", result.first().group)
        assertEquals("role-group", result.first().characterGroupId)
        assertEquals(all.dropLast(1).map { it.group }, result.drop(1).map { it.group })
        assertTrue(result.drop(1).all { it.characterGroupId == null })
    }

    @Test fun groupOnlyMoveWithoutVisibleAnchorPreservesFullOrder() {
        val result = applyChatOrderMove(all, move(anchor = null).copy(targetGroup = "new"))
        assertEquals(all.map { it.id }, result.map { it.id })
        assertEquals("new", result.last().group)
    }

    @Test fun deletedMovedChatCannotBeRecreated() {
        assertThrows(ChatOrderChangedException::class.java) { applyChatOrderMove(all.dropLast(1), move()) }
    }

    @Test fun deletedAnchorRejectsInsteadOfMovingToAnotherRow() {
        assertThrows(ChatOrderChangedException::class.java) { applyChatOrderMove(all.drop(1), move()) }
    }

    @Test fun changedMovedBindingIsNotOverwritten() {
        assertThrows(ChatOrderChangedException::class.java) {
            applyChatOrderMove(all.dropLast(1) + c.copy(characterCardName = "other"), move())
        }
    }

    @Test fun changedAnchorOrderOrGroupRejectsStaleMove() {
        for (changed in listOf(a.copy(displayOrder = 7), a.copy(group = "renamed"))) {
            assertThrows(ChatOrderChangedException::class.java) { applyChatOrderMove(listOf(changed) + all.drop(1), move()) }
        }
    }

    @Test fun pinChangeAndCrossPinAnchorAreRejected() {
        val pinned = a.copy(pinned = true)
        assertThrows(ChatOrderChangedException::class.java) { applyChatOrderMove(listOf(pinned) + all.drop(1), move()) }
        assertThrows(ChatOrderChangedException::class.java) { applyChatOrderMove(listOf(pinned) + all.drop(1), move(anchor = pinned)) }
    }

    @Test fun newlyAddedHiddenChatIsPreserved() {
        val added = chat("added", 4)
        assertEquals(listOf("c", "a", "hidden", "b", "added"), applyChatOrderMove(all + added, move()).map { it.id })
    }

    @Test fun legacyDualBindingIsRetainedWhenOnlyOrdering() {
        val legacy = c.copy(characterCardName = "legacy", characterGroupId = "group")
        val result = applyChatOrderMove(all.dropLast(1) + legacy, move(legacy)).first()
        assertEquals("legacy", result.characterCardName)
        assertEquals("group", result.characterGroupId)
    }
}
