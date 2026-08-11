package com.ai.assistance.operit.api.chat.llmprovider

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.model.ApiProviderType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenAIProviderToolCallIdentityAndroidTest {
    private val provider =
        OpenAIProvider(
            apiEndpoint = "http://127.0.0.1/v1/responses#",
            apiKeyProvider =
                object : ApiKeyProvider {
                    override suspend fun getApiKey(): String = "local-test-key"

                    override suspend fun getCandidateKeyCount(): Int = 1
                },
            modelName = "gpt-5.6-sol",
            client = OkHttpClient(),
            providerType = ApiProviderType.OPENAI_RESPONSES,
            enableToolCall = true,
        )

    @Test
    fun duplicateProviderCallIdProducesOneHistoryFunctionCall() = runBlocking {
        val content =
            """
            <tool_A1 name="use_package" provider_name="OPENAI_RESPONSES" provider_call_id="call-1">
              <param name="package_name">browser</param>
            </tool_A1>
            <tool_B2 name="use_package" provider_name="OPENAI_RESPONSES" provider_call_id="call-1">
              <param name="package_name">browser</param>
            </tool_B2>
            """.trimIndent()

        val (text, toolCalls) = provider.parseXmlToolCalls(content)

        assertEquals("", text)
        assertEquals(1, toolCalls?.length())
        assertEquals("call-1", toolCalls?.getJSONObject(0)?.getString("id"))
    }

    @Test
    fun conflictingProviderCallIdFailsHistoryCompilation() {
        val failure =
            runCatching {
                provider.parseXmlToolCalls(
                    """
                    <tool_A1 name="use_package" provider_name="OPENAI_RESPONSES" provider_call_id="call-1">
                      <param name="package_name">browser</param>
                    </tool_A1>
                    <tool_B2 name="use_package" provider_name="OPENAI_RESPONSES" provider_call_id="call-1">
                      <param name="package_name">daily_life</param>
                    </tool_B2>
                    """.trimIndent()
                )
            }.exceptionOrNull()

        assertTrue(failure is ProviderToolCallIdentityConflictException)
    }
}
