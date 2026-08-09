package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelNameListTest {
    @Test
    fun `manual input accepts commas full width commas and new lines`() {
        assertEquals(
            listOf("gpt-5", "claude-sonnet", "gemini-pro"),
            parseModelNameInput(" gpt-5, claude-sonnet，\n gemini-pro\r\n gpt-5 ")
        )
    }

    @Test
    fun `serialization removes blanks and exact duplicates while preserving order`() {
        assertEquals(
            "model-b,model-a",
            serializeModelNames(listOf(" model-b ", "", "model-a", "model-b"))
        )
    }

    @Test
    fun `merge appends only new models`() {
        assertEquals(
            listOf("model-a", "model-b", "model-c"),
            mergeModelNames(
                currentModels = listOf("model-a", "model-b"),
                addedModels = listOf("model-b", "model-c")
            )
        )
    }

    @Test
    fun `upstream selection removes only deselected upstream models`() {
        assertEquals(
            UpstreamModelSelectionChange(
                nextModels = listOf("manual-model", "model-b", "model-c"),
                addedModels = listOf("model-c"),
                removedModels = listOf("model-a")
            ),
            reconcileUpstreamModelSelection(
                currentModels = listOf("manual-model", "model-a", "model-b"),
                upstreamModels = listOf("model-a", "model-b", "model-c"),
                selectedUpstreamModels = listOf("model-b", "model-c")
            )
        )
    }

    @Test
    fun `upstream selection preserves current order and ignores unknown selections`() {
        assertEquals(
            UpstreamModelSelectionChange(
                nextModels = listOf("model-b", "manual-model", "model-a"),
                addedModels = emptyList(),
                removedModels = emptyList()
            ),
            reconcileUpstreamModelSelection(
                currentModels = listOf("model-b", "manual-model", "model-a"),
                upstreamModels = listOf("model-a", "model-b"),
                selectedUpstreamModels = listOf("unknown", "model-a", "model-b")
            )
        )
    }

    @Test
    fun `move preserves every model and changes only order`() {
        assertEquals(
            listOf("model-c", "model-a", "model-b"),
            moveModelName(listOf("model-a", "model-b", "model-c"), 2, 0)
        )
    }

    @Test
    fun `index remap follows the same model after reorder`() {
        assertEquals(
            2,
            remapModelIndex(
                oldModels = listOf("model-a", "model-b", "model-c"),
                newModels = listOf("model-c", "model-a", "model-b"),
                requestedIndex = 1
            )
        )
    }

    @Test
    fun `removed binding uses only the explicit replacement`() {
        assertEquals(
            0,
            remapModelIndex(
                oldModels = listOf("model-a", "model-b"),
                newModels = listOf("model-b"),
                requestedIndex = 0,
                replacementModelName = "model-b"
            )
        )
    }

    @Test
    fun `removed binding without replacement is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            remapModelIndex(
                oldModels = listOf("model-a", "model-b"),
                newModels = listOf("model-b"),
                requestedIndex = 0
            )
        }
    }
}
