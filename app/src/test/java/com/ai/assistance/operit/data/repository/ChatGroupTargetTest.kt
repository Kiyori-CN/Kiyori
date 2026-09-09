package com.ai.assistance.operit.data.repository

import org.junit.Assert.*
import org.junit.Test

class ChatGroupTargetTest {
    @Test fun allScopeStillRequiresTheExactFolderName() {
        val target = ChatGroupTarget("work", ChatGroupScope.All)
        assertTrue(target.matches("work", "card", "group"))
        assertFalse(target.matches("other", "card", "group"))
        assertFalse(target.matches(null, null, null))
    }

    @Test fun unboundScopeNeverIncludesCardsOrGroups() {
        val target = ChatGroupTarget("work", ChatGroupScope.Unbound)
        assertTrue(target.matches("work", null, null))
        assertTrue(target.matches("work", " ", " "))
        assertFalse(target.matches("work", "card", null))
        assertFalse(target.matches("work", null, "group"))
    }

    @Test fun cardScopeDoesNotTouchSameNamedFoldersInOtherBindings() {
        val target = ChatGroupTarget("work", ChatGroupScope.Card("a"))
        assertTrue(target.matches("work", "a", null))
        assertFalse(target.matches("work", "b", null))
        assertFalse(target.matches("work", null, null))
        assertFalse(target.matches("work", "a", "group"))
    }

    @Test fun defaultCardIncludesUnboundButNeverGroupChats() {
        val scope = ChatGroupScope.Card("default", includeUnbound = true)
        assertTrue(scope.matchesBinding("default", null))
        assertTrue(scope.matchesBinding(null, null))
        assertFalse(scope.matchesBinding(null, "group"))
        assertFalse(scope.matchesBinding("default", "group"))
        assertFalse(scope.matchesBinding("other", null))
    }

    @Test fun groupIdentityWinsOverLegacyCardName() {
        val target = ChatGroupTarget("work", ChatGroupScope.Group("g1"))
        assertTrue(target.matches("work", "legacy", "g1"))
        assertTrue(target.matches("work", null, " g1 "))
        assertFalse(target.matches("work", null, "g2"))
        assertFalse(target.matches("work", "g1", null))
    }

    @Test fun emptyIdentityIsRejectedInsteadOfBecomingGlobal() {
        assertThrows(IllegalArgumentException::class.java) { ChatGroupTarget("work", ChatGroupScope.Card(" ")) }
        assertThrows(IllegalArgumentException::class.java) { ChatGroupTarget("work", ChatGroupScope.Group("")) }
    }

    @Test fun snapshotAllowsReorderingWithoutChangingMembers() {
        val original = listOf(ChatGroupMember("a", false), ChatGroupMember("b", true))
        requireUnchangedChatGroup(original, original.reversed())
    }

    @Test fun legacyBlankFolderCanBeRepairedWithoutTargetingUngroupedChats() {
        val target = ChatGroupTarget("", ChatGroupScope.Unbound)
        assertTrue(target.matches("", null, null))
        assertFalse(target.matches(null, null, null))
        assertFalse(target.matches("work", null, null))
    }

    @Test fun newlyAddedMemberRejectsDestructiveSnapshot() {
        val original = listOf(ChatGroupMember("a", false))
        assertThrows(ChatGroupChangedException::class.java) {
            requireUnchangedChatGroup(original, original + ChatGroupMember("b", false))
        }
    }

    @Test fun removedOrReboundMemberRejectsDestructiveSnapshot() {
        val original = listOf(ChatGroupMember("a", false))
        assertThrows(ChatGroupChangedException::class.java) { requireUnchangedChatGroup(original, emptyList()) }
    }

    @Test fun lockChangeRejectsDestructiveSnapshot() {
        assertThrows(ChatGroupChangedException::class.java) {
            requireUnchangedChatGroup(listOf(ChatGroupMember("a", true)), listOf(ChatGroupMember("a", false)))
        }
    }

    @Test fun emptySnapshotIsNotSuccess() {
        assertThrows(ChatGroupChangedException::class.java) { requireUnchangedChatGroup(emptyList(), emptyList()) }
    }
}
