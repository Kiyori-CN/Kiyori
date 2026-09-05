package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ToolExecutorTest {
    private val tool = AITool("test", emptyList())

    @Test
    fun `default streaming invocation is cold`() = runBlocking {
        var invocations = 0
        val executor = object : ToolExecutor {
            override fun invoke(tool: AITool): ToolResult {
                invocations += 1
                return ToolResult(tool.name, true, StringResultData("ok"))
            }
        }

        val stream = executor.invokeAndStream(tool)
        assertEquals(0, invocations)
        stream.collect { result -> assertEquals("ok", (result.result as StringResultData).value) }
        assertEquals(1, invocations)
    }

    @Test
    fun `cancelling before collection prevents default invocation`() = runBlocking {
        var invocations = 0
        val executor = object : ToolExecutor {
            override fun invoke(tool: AITool): ToolResult {
                invocations += 1
                return ToolResult(tool.name, true, StringResultData("ok"))
            }
        }

        val stream = executor.invokeAndStream(tool)
        val collection = launch(start = CoroutineStart.LAZY) { stream.collect() }
        collection.cancel()
        collection.join()
        assertEquals(0, invocations)
    }

    @Test
    fun `default streaming propagates invocation failure during collection`() {
        val failure = IllegalStateException("invoke failed")
        val executor = object : ToolExecutor {
            override fun invoke(tool: AITool): ToolResult = throw failure
        }

        assertThrows(IllegalStateException::class.java) {
            runBlocking { executor.invokeAndStream(tool).collect() }
        }
    }
}
