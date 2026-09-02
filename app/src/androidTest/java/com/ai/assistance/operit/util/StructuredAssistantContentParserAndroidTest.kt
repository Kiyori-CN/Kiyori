package com.ai.assistance.operit.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StructuredAssistantContentParserAndroidTest {

    @Test
    fun toolAttributesWithQuotedAnglesRemainInsideOpeningTag() {
        val content =
            "前缀<tool_result_A1 name=\"terminal\" title=\"a > b < c\"><content>ok</content>" +
                "</tool_result_A1>尾部"

        val blocks = StructuredAssistantContentParser.parse(content)
        val xml = blocks.single { it.kind == StructuredAssistantContentParser.BlockKind.XML }

        assertEquals("tool_result", xml.tagName)
        assertEquals("terminal", xml.attrs["name"])
        assertEquals("a > b < c", xml.attrs["title"])
        assertEquals("<content>ok</content>", xml.content)
        assertTrue(xml.closed)
    }

    @Test
    fun closingTagMatchingIsCaseInsensitive() {
        val blocks = StructuredAssistantContentParser.parse("<STATUS>ready</status>")
        val xml = blocks.single { it.kind == StructuredAssistantContentParser.BlockKind.XML }

        assertEquals("status", xml.tagName)
        assertEquals("ready", xml.content)
        assertTrue(xml.closed)
    }

    @Test
    fun bodyTextEndingWithSlashAngleDoesNotClosePairedTag() {
        val blocks = StructuredAssistantContentParser.parse("<status>command output />")
        val xml = blocks.single { it.kind == StructuredAssistantContentParser.BlockKind.XML }

        assertFalse(xml.closed)
        assertEquals("command output />", xml.content)
    }
}
