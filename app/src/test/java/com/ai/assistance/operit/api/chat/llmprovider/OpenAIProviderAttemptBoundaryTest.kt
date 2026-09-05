package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ApiProtocol
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.exceptions.UserCancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenAIProviderAttemptBoundaryTest {
    @Test
    fun responseBodyInterruptionDoesNotCreateSecondChatCompletionPost() = runTest {
        Mockito.mockStatic(Log::class.java).use {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setBody(
                            ("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n")
                                .repeat(512)
                        )
                        .throttleBody(64, 1, TimeUnit.MILLISECONDS)
                        .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                )
                server.start()

                val retryNotifications = AtomicInteger(0)
                val context = mock<Context>()
                whenever(context.getString(any())).thenReturn("request failed")
                val provider =
                    FaultInjectionProvider(
                        apiEndpoint = server.url("/v1/chat/completions").toString(),
                        client =
                            OkHttpClient.Builder()
                                .retryOnConnectionFailure(false)
                                .readTimeout(10, TimeUnit.SECONDS)
                                .writeTimeout(10, TimeUnit.SECONDS)
                                .build(),
                    )

                val failure =
                    runCatching {
                        provider
                            .sendMessage(
                                context = context,
                                chatHistory =
                                    listOf(
                                        PromptTurn(
                                            kind = PromptTurnKind.USER,
                                            content = "trigger response interruption",
                                        )
                                    ),
                                modelParameters = emptyList<ModelParameter<*>>(),
                                enableThinking = false,
                                stream = true,
                                availableTools = null,
                                providerRequestContext = ProviderRequestContext(
                                    localExecutionId = "chat-failure-hop",
                                    chatId = "chat-1",
                                    messageTimestamp = 1L,
                                    variantIndex = 0,
                                    hopOrdinal = 3,
                                ),
                                onNonFatalError = { retryNotifications.incrementAndGet() },
                            )
                            .collect { }
                    }.exceptionOrNull()

                assertNotNull(failure)
                assertTrue(failure !is UserCancellationException)
                assertTrue(failure is OpenAIChatTransportFailure)
                assertEquals(
                    "chat-failure-hop",
                    (failure as OpenAIChatTransportFailure).localExecutionId,
                )
                assertNotNull(failure.cause)
                assertFalse(failure.message.orEmpty().contains("chat-failure-hop"))
                assertFalse(failure.message.orEmpty().contains("LLM_TRANSPORT"))
                assertEquals(0, retryNotifications.get())
                assertEquals(
                    "failure=${failure.javaClass.name}: ${failure.message}",
                    1,
                    server.requestCount,
                )
                assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun explicitServerFailureDoesNotCreateSecondChatCompletionPost() = runTest {
        Mockito.mockStatic(Log::class.java).use {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(503)
                        .setBody("{\"error\":{\"message\":\"temporary outage\"}}")
                )
                server.start()

                val retryNotifications = AtomicInteger(0)
                val context = mock<Context>()
                whenever(context.getString(any())).thenReturn("request failed")
                whenever(context.getString(any(), any(), any())).thenReturn("request failed")
                val provider =
                    FaultInjectionProvider(
                        apiEndpoint = server.url("/v1/chat/completions").toString(),
                        client =
                            OkHttpClient.Builder()
                                .retryOnConnectionFailure(false)
                                .readTimeout(10, TimeUnit.SECONDS)
                                .writeTimeout(10, TimeUnit.SECONDS)
                                .build(),
                    )

                val failure =
                    runCatching {
                        provider
                            .sendMessage(
                                context = context,
                                chatHistory =
                                    listOf(
                                        PromptTurn(
                                            kind = PromptTurnKind.USER,
                                            content = "do not retry explicit server failure",
                                        )
                                    ),
                                modelParameters = emptyList<ModelParameter<*>>(),
                                enableThinking = false,
                                stream = true,
                                availableTools = null,
                                providerRequestContext = null,
                                onNonFatalError = { retryNotifications.incrementAndGet() },
                            )
                            .collect { }
                    }.exceptionOrNull()

                assertNotNull(failure)
                assertEquals(0, retryNotifications.get())
                assertEquals(1, server.requestCount)
                assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            }
        }
    }

    private class FaultInjectionProvider(
        apiEndpoint: String,
        client: OkHttpClient,
    ) : OpenAIProvider(
        apiEndpoint = apiEndpoint,
        apiKeyProvider =
            object : ApiKeyProvider {
                override suspend fun getApiKey(): String = "local-test-key"

                override suspend fun getCandidateKeyCount(): Int = 1
            },
        modelName = "local-test-model",
        client = client,
        providerType = ApiProviderType.OPENAI,
        endpointProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
    ) {
        override val responseExecutionDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

        override fun createRequestBody(
            context: Context,
            chatHistory: List<PromptTurn>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            stream: Boolean,
            availableTools: List<ToolPrompt>?,
            preserveThinkInHistory: Boolean,
        ): RequestBody =
            "{\"model\":\"local-test-model\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"trigger response interruption\"}]}"
                .toRequestBody("application/json".toMediaType())
    }
}
