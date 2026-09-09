package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatMetadataEditTest {
    private val original = ChatMetadataSnapshot("Original", "Alice", null)

    @Test fun titleEditPreservesConcurrentBindingChange() {
        val current = original.copy(characterCardName = null, characterGroupId = "group-b")
        assertEquals(current.copy(title = "Edited"),
            mergeChatMetadataEdit(current, original, original.copy(title = "Edited")))
    }

    @Test fun bindingEditPreservesConcurrentTitleChange() {
        val current = original.copy(title = "Automatic title")
        val edited = original.copy(characterCardName = null, characterGroupId = "group-b")
        assertEquals(edited.copy(title = current.title), mergeChatMetadataEdit(current, original, edited))
    }

    @Test fun conflictingTitleRejectsWholeEditIncludingBinding() {
        assertThrows(ChatMetadataConflictException::class.java) {
            mergeChatMetadataEdit(original.copy(title = "Other edit"), original,
                ChatMetadataSnapshot("My edit", null, "group-b"))
        }
    }

    @Test fun switchingBindingKindConflictsWithConcurrentCharacterEdit() {
        assertThrows(ChatMetadataConflictException::class.java) {
            mergeChatMetadataEdit(original.copy(characterCardName = "Bob"), original,
                original.copy(characterCardName = null, characterGroupId = "group-b"))
        }
    }

    @Test fun repeatedSaveOfSameValuesIsIdempotent() {
        val edited = ChatMetadataSnapshot("Edited", null, "group-b")
        assertEquals(edited, mergeChatMetadataEdit(edited, original, edited))
    }

    @Test fun unchangedFormDoesNotOverwriteNewValues() {
        val current = ChatMetadataSnapshot("Changed", "Bob", null)
        assertEquals(current, mergeChatMetadataEdit(current, original, original))
    }

    @Test fun blankTitleAndMixedBindingAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            mergeChatMetadataEdit(original, original, original.copy(title = " \n "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            mergeChatMetadataEdit(original, original, original.copy(characterGroupId = "group-b"))
        }
    }
}
