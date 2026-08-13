package com.ai.assistance.operit.ui.features.chat.components.part

import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchContract
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.ui.common.markdown.MarkdownGroupedItem
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkToolsXmlNodeGrouperTest {
    @Test
    fun `single OpenAI Web Search result becomes a level zero group in every mode`() {
        ToolCollapseMode.entries.forEach { mode ->
            val grouped =
                grouper(mode).group(
                    nodes =
                        listOf(
                            xmlNode(
                                """<tool_result name="${OpenAIHostedWebSearchContract.TOOL_NAME}" status="success"><content>{}</content></tool_result>"""
                            )
                        ),
                    rendererId = "renderer",
                )

            assertEquals(
                "$mode should group one rich Web Search result",
                listOf(
                    MarkdownGroupedItem.Group(
                        startIndex = 0,
                        endIndexInclusive = 0,
                        stableKey = "tools-only-0",
                    )
                ),
                grouped,
            )
        }
    }

    @Test
    fun `single OpenAI Web Search request and request result pair both group`() {
        val request =
            xmlNode(
                """<tool name="${OpenAIHostedWebSearchContract.TOOL_NAME}"><param name="query">test</param></tool>"""
            )
        val result =
            xmlNode(
                """<tool_result name="${OpenAIHostedWebSearchContract.TOOL_NAME}" status="success"><content>{}</content></tool_result>"""
            )

        assertEquals(
            MarkdownGroupedItem.Group(0, 0, "tools-only-0"),
            grouper(ToolCollapseMode.ALL).group(listOf(request), "renderer").single(),
        )
        assertEquals(
            MarkdownGroupedItem.Group(0, 1, "tools-only-0"),
            grouper(ToolCollapseMode.READ_ONLY)
                .group(listOf(request, result), "renderer")
                .single(),
        )
    }

    @Test
    fun `ordinary single tools retain existing read only and all behavior`() {
        val ordinarySearch =
            xmlNode(
                """<tool_result name="visit_web" status="success"><content>done</content></tool_result>"""
            )
        val ordinaryWrite =
            xmlNode(
                """<tool_result name="write_file" status="success"><content>done</content></tool_result>"""
            )

        assertEquals(
            MarkdownGroupedItem.Single(0),
            grouper(ToolCollapseMode.READ_ONLY)
                .group(listOf(ordinarySearch), "renderer")
                .single(),
        )
        assertEquals(
            MarkdownGroupedItem.Single(0),
            grouper(ToolCollapseMode.ALL)
                .group(listOf(ordinaryWrite), "renderer")
                .single(),
        )
        assertEquals(
            MarkdownGroupedItem.Group(0, 0, "tools-only-0"),
            grouper(ToolCollapseMode.FULL)
                .group(listOf(ordinaryWrite), "renderer")
                .single(),
        )
    }

    @Test
    fun `OpenAI Web Search tool name matching is exact`() {
        assertTrue(
            isOpenAIWebSearchToolName(OpenAIHostedWebSearchContract.TOOL_NAME)
        )
        assertTrue(
            isOpenAIWebSearchToolName(
                " ${OpenAIHostedWebSearchContract.TOOL_NAME} "
            )
        )
        assertFalse(isOpenAIWebSearchToolName("openai_web_search:search_more"))
        assertFalse(isOpenAIWebSearchToolName("other:openai_web_search:search"))
        assertFalse(isOpenAIWebSearchToolName(null))
    }

    private fun grouper(mode: ToolCollapseMode): ThinkToolsXmlNodeGrouper =
        ThinkToolsXmlNodeGrouper(
            showThinkingProcess = true,
            toolCollapseMode = mode,
        )

    private fun xmlNode(content: String): MarkdownNodeStable =
        MarkdownNodeStable(
            type = MarkdownProcessorType.XML_BLOCK,
            content = content,
            children = emptyList(),
        )
}
