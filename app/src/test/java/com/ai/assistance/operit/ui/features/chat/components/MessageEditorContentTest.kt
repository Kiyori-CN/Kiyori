package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageEditorContentTest {
    @Test fun openingStructuredContentPreservesWhitespaceExactly() {
        val source = " \n<think a=\"x\">思考</think>\n\n<tool>result</tool>\t \n"
        assertEquals(source, recomposeMessageFromParts(parseMessageContentForEditor(source)))
    }

    @Test fun editingOneTagPreservesAdjacentLayout() {
        val source = "<think>old</think>\n\n<tool>unchanged</tool>\n"
        val parts = parseMessageContentForEditor(source).map {
            if (it.tag == "think") it.copy(content = "new") else it
        }
        assertEquals("<think>new</think>\n\n<tool>unchanged</tool>\n", recomposeMessageFromParts(parts))
    }

    @Test fun whitespaceOnlyAndCodeRemainUnchanged() {
        listOf("", " \t\r\n", "```kotlin\nval x = 2 < 3\n```\n", "unclosed <tool> value").forEach {
            assertEquals(it, recomposeMessageFromParts(parseMessageContentForEditor(it)))
        }
    }

    @Test fun nestedAndAttributedTagsRoundTripWithoutNormalization() {
        val source = "<details open=\"true\"><think>内容</think>\n</details> \nend"
        assertEquals(source, recomposeMessageFromParts(parseMessageContentForEditor(source)))
    }
}
