package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.coroutines.launch

class ToolExecutionManagerTest {
    @Test
    fun runtimeChatContextSurvivesDispatcherChangesAndDoesNotLeakBetweenCalls() = kotlinx.coroutines.runBlocking {
        val contexts = (1..12).map { ToolExecutionManager.ToolRuntimeContext(callerChatId = "chat-$it") }
        kotlinx.coroutines.coroutineScope {
            val jobs = contexts.map { context ->
                launch {
                    ToolExecutionManager.withToolRuntimeContext(context) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            kotlinx.coroutines.delay(5)
                            assertEquals(context.callerChatId, ToolExecutionManager.currentToolRuntimeContext()?.callerChatId)
                        }
                    }
                    assertEquals(null, ToolExecutionManager.currentToolRuntimeContext())
                }
            }
            jobs.forEach { it.join() }
        }
    }

    @Test
    fun providerResult_usesOriginalInvocationNameInsteadOfDisplayName() {
        val invocation =
            ToolInvocation(
                tool = AITool(name = "package_proxy"),
                rawText = "",
                responseLocation = IntRange.EMPTY,
                providerCallId = "call-1",
            )
        val displayResult =
            ToolResult(
                toolName = "terminal:run",
                success = true,
                result = StringResultData("ok"),
            )

        val providerResult =
            ToolExecutionManager.providerResultForInvocation(displayResult, invocation)

        assertEquals("package_proxy", providerResult.toolName)
        assertEquals("terminal:run", displayResult.toolName)
        assertEquals(displayResult.result, providerResult.result)
    }

    @Test
    fun providerResults_restoreOriginalOrderAcrossImmediateAndExecutedOutcomes() {
        val first = invocation("first", 1)
        val denied = invocation("denied", 2)
        val third = invocation("third", 3)
        val results =
            ToolExecutionManager.mergeProviderResultsInInvocationOrder(
                invocations = listOf(first, denied, third),
                immediateResults = mapOf(denied to result("denied")),
                executedResults = listOf(result("first"), result("third")),
            )

        assertEquals(listOf("first", "denied", "third"), results.map { it.toolName })
    }

    @Test
    fun providerResultOrdering_rejectsMissingTerminalOutcome() {
        assertThrows(IllegalStateException::class.java) {
            ToolExecutionManager.mergeProviderResultsInInvocationOrder(
                invocations = listOf(invocation("missing", 1)),
                immediateResults = emptyMap(),
                executedResults = emptyList(),
            )
        }
    }

    @Test
    fun durableTerminalResult_matchesAggregatedProviderOutcome() {
        val terminal =
            ToolExecutionManager.aggregateTerminalToolResult(
                displayToolName = "terminal:run",
                collectedResults =
                    listOf(
                        result("terminal:run", "first"),
                        ToolResult(
                            toolName = "terminal:run",
                            success = false,
                            result = StringResultData("ignored"),
                            error = "second failed",
                        ),
                    ),
            )

        assertEquals("terminal:run", terminal.toolName)
        assertFalse(terminal.success)
        assertEquals("first\nStep error: second failed", terminal.result.toString())
        assertEquals("second failed", terminal.error)
    }

    private fun invocation(name: String, index: Int) =
        ToolInvocation(
            tool = AITool(name = name),
            rawText = "",
            responseLocation = index..index,
        )

    private fun result(name: String, payload: String = name) =
        ToolResult(
            toolName = name,
            success = true,
            result = StringResultData(payload),
        )
}
