package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIResponsesReasoningProjectionTest {
    @Test
    fun deltasAreProjectedInOrderAndCompletedEventsDoNotRepeatThem() {
        val projection = OpenAIResponsesReasoningProjection()

        assertEquals(
            "First ",
            projection.acceptDelta(
                partKey = "summary:0:0",
                outputIndex = 0,
                delta = "First ",
            ),
        )
        assertEquals(
            "step",
            projection.acceptDelta(
                partKey = "summary:0:0",
                outputIndex = 0,
                delta = "step",
            ),
        )
        assertEquals(
            "",
            projection.acceptCompletedPart(
                partKey = "summary:0:0",
                outputIndex = 0,
                completedText = "First step",
            ),
        )
        assertEquals(
            "",
            projection.acceptCompletedOutputItem(
                outputIndex = 0,
                completedText = "First step",
            ),
        )
        assertEquals("", projection.acceptTerminalSnapshot("First step"))
        assertTrue(projection.hasEmittedText)
    }

    @Test
    fun completedEventsOnlyAppendTheMissingTail() {
        val projection = OpenAIResponsesReasoningProjection()

        assertEquals(
            "First",
            projection.acceptDelta(
                partKey = "summary:0:0",
                outputIndex = 0,
                delta = "First",
            ),
        )
        assertEquals(
            " step",
            projection.acceptCompletedPart(
                partKey = "summary:0:0",
                outputIndex = 0,
                completedText = "First step",
            ),
        )
        assertEquals(
            " complete",
            projection.acceptCompletedOutputItem(
                outputIndex = 0,
                completedText = "First step complete",
            ),
        )
        assertEquals(
            ".",
            projection.acceptTerminalSnapshot("First step complete."),
        )
    }

    @Test
    fun multipleSummaryPartsUseOneStableSeparator() {
        val projection = OpenAIResponsesReasoningProjection()

        assertEquals(
            "Plan",
            projection.acceptCompletedPart(
                partKey = "summary:0:0",
                outputIndex = 0,
                completedText = "Plan",
            ),
        )
        assertEquals(
            "\n\nCheck",
            projection.acceptCompletedPart(
                partKey = "summary:0:1",
                outputIndex = 0,
                completedText = "Check",
            ),
        )
        assertEquals(
            "",
            projection.acceptCompletedOutputItem(
                outputIndex = 0,
                completedText = "Plan\n\nCheck",
            ),
        )
        assertEquals("", projection.acceptTerminalSnapshot("Plan\n\nCheck"))
    }

    @Test
    fun multipleReasoningOutputItemsKeepItemAndGlobalSeparatorsIndependent() {
        val projection = OpenAIResponsesReasoningProjection()

        assertEquals(
            "Plan",
            projection.acceptCompletedPart(
                partKey = "summary:0:0",
                outputIndex = 0,
                completedText = "Plan",
            ),
        )
        assertEquals(
            "\n\nVerify",
            projection.acceptCompletedPart(
                partKey = "summary:1:0",
                outputIndex = 1,
                completedText = "Verify",
            ),
        )
        assertEquals(
            "",
            projection.acceptCompletedOutputItem(
                outputIndex = 1,
                completedText = "Verify",
            ),
        )
        assertEquals("", projection.acceptTerminalSnapshot("Plan\n\nVerify"))
    }

    @Test
    fun terminalSnapshotRejectsDivergentText() {
        val projection = OpenAIResponsesReasoningProjection()
        projection.acceptDelta(
            partKey = "summary:0:0",
            outputIndex = 0,
            delta = "Plan A",
        )

        assertThrows(OpenAIResponsesProtocolException::class.java) {
            projection.acceptTerminalSnapshot("Plan B")
        }
    }

    @Test
    fun aReasoningLifecycleEventIsRecognizedBeforeTextExists() {
        assertTrue(
            OpenAIResponsesReasoningEventPolicy.isReasoningLifecycleEvent(
                eventType = "response.reasoning_summary_part.added",
                payload =
                    JSONObject()
                        .put("output_index", 0)
                        .put("summary_index", 0),
            )
        )
        assertTrue(
            OpenAIResponsesReasoningEventPolicy.isReasoningLifecycleEvent(
                eventType = "response.output_item.added",
                payload = JSONObject().put("item", JSONObject().put("type", "reasoning")),
            )
        )
        assertFalse(
            OpenAIResponsesReasoningEventPolicy.isReasoningLifecycleEvent(
                eventType = "response.output_text.delta",
                payload = JSONObject(),
            )
        )
    }

    @Test
    fun aReasoningPartCannotChangeItsOutputIdentity() {
        val projection = OpenAIResponsesReasoningProjection()
        projection.acceptDelta(
            partKey = "summary:0:0",
            outputIndex = 0,
            delta = "Plan",
        )

        assertThrows(OpenAIResponsesProtocolException::class.java) {
            projection.acceptCompletedPart(
                partKey = "summary:0:0",
                outputIndex = 1,
                completedText = "Plan",
            )
        }
    }
}
