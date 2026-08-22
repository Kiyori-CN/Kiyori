package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenAIProviderLocalFailureRetryTest {
    @Test
    fun toolHistoryProtocolFailureBeforeSubmissionIsNotRetriedOrWrapped() = runTest {
        Mockito.mockStatic(Log::class.java).use {
            val provider = ProtocolFailureProvider()
            val context = mock<Context>()
            whenever(context.getString(any())).thenReturn("request cancelled")

            val failure =
                runCatching {
                    provider.sendMessage(
                        context = context,
                        chatHistory =
                            listOf(
                                PromptTurn(
                                    kind = PromptTurnKind.USER,
                                    content = "trigger local history failure",
                                )
                            ),
                        modelParameters = emptyList(),
                        enableThinking = false,
                        stream = true,
                        availableTools = null,
                        preserveThinkInHistory = false,
                        providerRequestContext = null,
                        onTokensUpdated = { _, _, _ -> },
                        onNonFatalError = { error("Local protocol failure must not emit retry UI") },
                        enableRetry = true,
                    ).collect { error("Local protocol failure must not emit content") }
                }.exceptionOrNull()

            assertTrue(failure is ProviderToolHistoryProtocolException)
            assertEquals(ProviderToolHistoryViolation.MISSING_TOOL_RESULT, (failure as ProviderToolHistoryProtocolException).violation)
            assertEquals(1, provider.requestBodyBuildCount.get())
        }
    }

    private class ProtocolFailureProvider : OpenAIProvider(
        apiEndpoint = "http://127.0.0.1:1/v1/chat/completions",
        apiKeyProvider =
            object : ApiKeyProvider {
                override suspend fun getApiKey(): String = "local-test-key"

                override suspend fun getCandidateKeyCount(): Int = 1
            },
        modelName = "local-test-model",
        client = OkHttpClient(),
        providerType = ApiProviderType.OPENAI,
    ) {
        val requestBodyBuildCount = AtomicInteger()

        override fun createRequestBody(
            context: Context,
            chatHistory: List<PromptTurn>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            stream: Boolean,
            availableTools: List<ToolPrompt>?,
            preserveThinkInHistory: Boolean,
        ): RequestBody {
            requestBodyBuildCount.incrementAndGet()
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.MISSING_TOOL_RESULT,
                detail = "Missing one tool result before local request submission",
            )
        }
    }
}
