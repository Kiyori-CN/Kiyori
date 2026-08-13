package com.ai.assistance.operit.api.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSubtreeTraceTest {
    @Test
    fun `child tool batch increments depth and owns a distinct invocation id`() {
        val parent =
            ToolSubtreeTrace.create(
                round = 0,
                parent = null,
                invocationId = "root-invocation",
            )
        val child =
            ToolSubtreeTrace.create(
                round = 1,
                parent = parent,
                invocationId = "child-invocation",
            )

        assertEquals(1, parent.depth)
        assertEquals(2, child.depth)
        assertEquals(1, child.round)
        assertEquals("child-invocation", child.invocationId)
    }

    @Test
    fun `completion details expose the exact subtree correlation contract`() {
        val trace =
            ToolSubtreeTrace(
                round = 3,
                depth = 2,
                invocationId = "invocation-42",
            )

        val details = trace.completionDetails(resultCount = 4)

        assertTrue(details.contains("round=3"))
        assertTrue(details.contains("depth=2"))
        assertTrue(details.contains("invocationId=invocation-42"))
        assertTrue(details.contains("resultCount=4"))
    }
}
