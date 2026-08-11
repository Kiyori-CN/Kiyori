package com.ai.assistance.operit.api.chat.enhance

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.api.chat.llmprovider.ProviderToolCallIdentityConflictException
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolParameter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolExecutionManagerTest {

    @Test
    fun extractToolInvocations_shouldKeepAllToolBlocksInSameChunk() = runBlocking {
        val response = """
            <tool_A1 name="visit_web"><param name="url">https://www.baidu.com</param></tool_A1>
            <tool_B2 name="visit_web"><param name="url">https://www.bing.com</param></tool_B2>
            <tool_C3 name="visit_web"><param name="url">https://www.github.com</param></tool_C3>
        """.trimIndent()

        val invocations = ToolExecutionManager.extractToolInvocations(response)

        assertEquals(3, invocations.size)
        assertEquals(
            listOf(
                "https://www.baidu.com",
                "https://www.bing.com",
                "https://www.github.com"
            ),
            invocations.map { invocation ->
                invocation.tool.parameters.first { it.name == "url" }.value
            }
        )
    }

    @Test
    fun extractToolInvocations_preservesProviderNativeIdentity() = runBlocking {
        val response =
            """
            <tool_A1 name="read_file" provider_name="OPENAI_RESPONSES" provider_call_id="call_123" provider_response_id="resp_456">
            <param name="path">notes.txt</param>
            </tool_A1>
            """.trimIndent()

        val invocation = ToolExecutionManager.extractToolInvocations(response).single()

        assertEquals("read_file", invocation.tool.name)
        assertEquals("OPENAI_RESPONSES", invocation.providerName)
        assertEquals("call_123", invocation.providerCallId)
        assertEquals("resp_456", invocation.providerResponseId)
    }

    @Test
    fun normalizeProviderInvocations_deduplicatesCallIdWithoutResponseId() {
        val first =
            providerInvocation(
                toolName = "use_package",
                callId = "call-1",
                packageName = "browser",
            )
        val duplicate =
            providerInvocation(
                toolName = "use_package",
                callId = "call-1",
                packageName = "browser",
            )

        val normalized =
            ToolExecutionManager.normalizeProviderInvocations(listOf(first, duplicate))

        assertEquals(1, normalized.size)
        assertEquals("call-1", normalized.single().providerCallId)
    }

    @Test
    fun normalizeProviderInvocations_rejectsConflictingCallIdentity() {
        val failure =
            runCatching {
                ToolExecutionManager.normalizeProviderInvocations(
                    listOf(
                        providerInvocation(
                            toolName = "use_package",
                            callId = "call-1",
                            packageName = "browser",
                        ),
                        providerInvocation(
                            toolName = "use_package",
                            callId = "call-1",
                            packageName = "daily_life",
                        ),
                    )
                )
            }.exceptionOrNull()

        assertTrue(failure is ProviderToolCallIdentityConflictException)
    }

    private fun providerInvocation(
        toolName: String,
        callId: String,
        packageName: String,
    ): ToolInvocation =
        ToolInvocation(
            tool =
                AITool(
                    name = toolName,
                    parameters = listOf(ToolParameter("package_name", packageName)),
                ),
            rawText = "",
            responseLocation = IntRange.EMPTY,
            providerName = "OPENAI_RESPONSES",
            providerCallId = callId,
            providerResponseId = null,
        )
}
