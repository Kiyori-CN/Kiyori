package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NativeProviderAttemptBoundaryTest {
    @Test
    fun claudeDisconnectAfterRequestDoesNotCreateSecondPost() = runTest {
        assertSinglePostAfterSubmissionFailure { endpoint, client ->
            ClaudeProvider(
                apiEndpoint = endpoint,
                apiKeyProvider = SingleApiKeyProvider("local-test-key"),
                modelName = "local-test-model",
                client = client,
                providerType = ApiProviderType.ANTHROPIC,
            )
        }
    }

    @Test
    fun geminiDisconnectAfterRequestDoesNotCreateSecondPost() = runTest {
        assertSinglePostAfterSubmissionFailure { endpoint, client ->
            GeminiProvider(
                apiEndpoint = endpoint,
                apiKeyProvider = SingleApiKeyProvider("local-test-key"),
                modelName = "local-test-model",
                client = client,
                providerType = ApiProviderType.GOOGLE,
            )
        }
    }

    private suspend fun assertSinglePostAfterSubmissionFailure(
        createProvider: (String, OkHttpClient) -> AIService,
    ) {
        Mockito.mockStatic(Log::class.java).use {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                )
                server.start()
                val context = mock<Context>()
                whenever(context.getString(any())).thenReturn("request failed")
                whenever(context.getString(any(), any())).thenReturn("request failed")
                whenever(context.getString(any(), any(), any())).thenReturn("request failed")
                val retryNotifications = AtomicInteger(0)
                val provider =
                    createProvider(
                        server.url("/").toString(),
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
                                    listOf(PromptTurn(PromptTurnKind.USER, "submission boundary")),
                                modelParameters = emptyList<ModelParameter<*>>(),
                                enableThinking = false,
                                stream = true,
                                availableTools = null,
                                preserveThinkInHistory = false,
                                providerRequestContext = null,
                                onTokensUpdated = { _, _, _ -> },
                                onNonFatalError = { retryNotifications.incrementAndGet() },
                                enableRetry = true,
                            )
                            .collect { }
                    }.exceptionOrNull()

                assertNotNull(failure)
                assertEquals(0, retryNotifications.get())
                assertEquals(
                    "failure=${failure?.javaClass?.name}: ${failure?.message}",
                    1,
                    server.requestCount,
                )
                assertEquals("POST", server.takeRequest(5, TimeUnit.SECONDS)?.method)
            }
        }
    }
}
