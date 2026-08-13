package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenAIHostedWebSearchAnswerNormalizerTest {
    @Test
    fun removesEmptyMarkdownOutsideCodeAndRemapsCitationOffsets() {
        val answer = "Before []() Evidence"
        val citationStart = answer.indexOf("Evidence")
        val result =
            OpenAIHostedWebSearchAnswerNormalizer.normalize(
                answer = answer,
                citations =
                    listOf(
                        citation(
                            startIndex = citationStart,
                            endIndex = citationStart + "Evidence".length,
                        )
                    ),
            )

        assertEquals("Before  Evidence", result.answer)
        assertEquals(8, result.citations.single().startIndex)
        assertEquals(16, result.citations.single().endIndex)
    }

    @Test
    fun preservesEmptyMarkdownInsideInlineAndFencedCode() {
        val answer = "Inline `[]()`\n```\n![]()\n```\nOutside ![]()"

        val result =
            OpenAIHostedWebSearchAnswerNormalizer.normalize(
                answer = answer,
                citations = emptyList(),
            )

        assertEquals("Inline `[]()`\n```\n![]()\n```\nOutside ", result.answer)
    }

    @Test
    fun rejectsCitationThatOverlapsRemovedMarkdown() {
        val answer = "Before []() After"
        val startIndex = answer.indexOf("[]()")

        val error =
            assertThrows(OpenAIHostedWebSearchException::class.java) {
                OpenAIHostedWebSearchAnswerNormalizer.normalize(
                    answer = answer,
                    citations =
                        listOf(
                            citation(
                                startIndex = startIndex,
                                endIndex = startIndex + 4,
                            )
                        ),
                )
            }

        assertEquals(OpenAIHostedWebSearchErrorCode.CITATION_INVALID, error.code)
    }

    private fun citation(
        startIndex: Int,
        endIndex: Int,
    ): OpenAIHostedWebSearchCitation =
        OpenAIHostedWebSearchCitation(
            sourceId = "S1",
            title = "Example",
            url = "https://example.com/",
            startIndex = startIndex,
            endIndex = endIndex,
        )
}
