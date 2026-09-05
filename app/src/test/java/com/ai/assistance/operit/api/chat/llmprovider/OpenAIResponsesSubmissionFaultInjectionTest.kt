package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.MessageProviderStateEntity
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ProviderExecutionEntity
import com.ai.assistance.operit.data.model.ProviderExecutionStatus
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.repository.ProviderExecutionRepository
import com.ai.assistance.operit.util.AppLogger
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class OpenAIResponsesSubmissionFaultInjectionTest {
    @Test
    fun httpClientTransportPolicy_keepsDefaultPoolingAndIsolatesResponsesConnections() {
        val defaultPolicy =
            LlmHttpClientProtocolPolicy.policyFor(
                ProtocolServiceKind.OPENAI_CHAT_GENERIC
            )
        val responsesPolicy =
            LlmHttpClientProtocolPolicy.policyFor(
                ProtocolServiceKind.OPENAI_RESPONSES
            )

        assertEquals(
            listOf(Protocol.HTTP_2, Protocol.HTTP_1_1),
            defaultPolicy.protocols,
        )
        assertEquals(10, defaultPolicy.maxIdleConnections)
        assertEquals(false, defaultPolicy.retryOnConnectionFailure)
        assertEquals(
            listOf(Protocol.HTTP_1_1),
            responsesPolicy.protocols,
        )
        assertEquals(0, responsesPolicy.maxIdleConnections)
        assertEquals(false, responsesPolicy.retryOnConnectionFailure)

        val deepSeekChatPolicy =
            LlmHttpClientProtocolPolicy.policyFor(ProtocolServiceKind.DEEPSEEK_CHAT)
        assertEquals(
            listOf(Protocol.HTTP_1_1),
            deepSeekChatPolicy.protocols,
        )
        assertEquals(0, deepSeekChatPolicy.maxIdleConnections)
        assertEquals(false, deepSeekChatPolicy.retryOnConnectionFailure)
    }

    @Test
    fun responsesTransportPolicy_opensANewConnectionForEachSequentialHop() {
        MockWebServer().use { server ->
            repeat(2) {
                server.enqueue(
                    MockResponse()
                        .setHeader("Connection", "keep-alive")
                        .setBody("ok")
                )
            }
            server.start()
            val client =
                LlmHttpClientProtocolPolicy.responsesPolicy
                    .applyTo(
                        OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .writeTimeout(5, TimeUnit.SECONDS)
                    )
                    .build()

            repeat(2) {
                val request =
                    Request.Builder()
                        .url(server.url("/v1/responses"))
                        .post("{}".toRequestBody("application/json".toMediaType()))
                        .build()
                client.newCall(request).execute().use { response ->
                    assertEquals(200, response.code)
                    assertEquals("ok", response.body?.string())
                }
            }

            val recordedRequests =
                List(2) {
                    requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                }
            assertEquals(
                listOf(
                    "POST /v1/responses HTTP/1.1",
                    "POST /v1/responses HTTP/1.1",
                ),
                recordedRequests.map { it.requestLine },
            )
            assertEquals(
                "A sequence number of zero proves each hop used a newly accepted connection",
                listOf(0, 0),
                recordedRequests.map { it.sequenceNumber },
            )
        }
    }

    @Test
    fun submissionUnknownMessage_includesHttpDetailWithoutExecutionContext() {
        val failure =
            OpenAIResponsesSubmissionUnknownException(
                localExecutionId = null,
                cause = IOException("API request failed, status=502, body=openai_error"),
            )

        assertEquals(null, failure.localExecutionId)
        assertTrue(failure.message?.contains("502") == true)
        assertTrue(failure.message?.contains("openai_error") == true)
    }

    @Test
    fun deepSeekResponsesStream_sendsSseAcceptAndCompletesOnSemanticTerminal() = runTest {
        Mockito.mockStatic(AppLogger::class.java).use {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "text/event-stream")
                        .setBody(completedResponsesSse("semantic terminal"))
                )
                val repository = mock<ProviderExecutionRepository>()
                val provider = createDeepSeekResponsesProvider(server, repository)
                val received = StringBuilder()

                provider.sendMessage(
                    context = createContext(),
                    chatHistory = testHistory("complete with response.completed"),
                    modelParameters = emptyList(),
                    enableThinking = true,
                    stream = true,
                    availableTools = null,
                    preserveThinkInHistory = false,
                    providerRequestContext = requestContext("local-deepseek-completed"),
                    onTokensUpdated = { _, _, _ -> },
                    onNonFatalError = {},
                    enableRetry = true,
                ).collect(received::append)

                val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                assertEquals("text/event-stream", request.getHeader("Accept"))
                assertEquals(1, server.requestCount)
                assertEquals("semantic terminal", received.toString())
                verifyNoInteractions(repository)
            }
        }
    }

    @Test
    fun deepSeekResponsesNonStreaming_sendsJsonAccept() = runTest {
        Mockito.mockStatic(AppLogger::class.java).use {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(completedResponsesJson("non-stream response"))
                )
                val repository = mock<ProviderExecutionRepository>()
                val provider = createDeepSeekResponsesProvider(server, repository)
                val received = StringBuilder()

                provider.sendMessage(
                    context = createContext(),
                    chatHistory = testHistory("complete without streaming"),
                    modelParameters = emptyList(),
                    enableThinking = true,
                    stream = false,
                    availableTools = null,
                    preserveThinkInHistory = false,
                    providerRequestContext = requestContext("local-deepseek-json"),
                    onTokensUpdated = { _, _, _ -> },
                    onNonFatalError = {},
                    enableRetry = true,
                ).collect(received::append)

                val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                assertEquals("application/json", request.getHeader("Accept"))
                assertEquals(1, server.requestCount)
                assertEquals("non-stream response", received.toString())
                verifyNoInteractions(repository)
            }
        }
    }

    @Test
    fun deepSeekResponsesCleanEofWithoutTerminal_postsOnceAndFailsUnknown() = runTest {
        Mockito.mockStatic(AppLogger::class.java).use {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "text/event-stream")
                        .setBody(responsesTextDeltaSse("partial before clean eof"))
                )
                val repository = mock<ProviderExecutionRepository>()
                val provider = createDeepSeekResponsesProvider(server, repository)
                val received = StringBuilder()

                val failure =
                    runCatching {
                        provider.sendMessage(
                            context = createContext(),
                            chatHistory = testHistory("end without terminal"),
                            modelParameters = emptyList(),
                            enableThinking = true,
                            stream = true,
                            availableTools = null,
                            preserveThinkInHistory = false,
                            providerRequestContext = requestContext("local-deepseek-clean-eof"),
                            onTokensUpdated = { _, _, _ -> },
                            onNonFatalError = {},
                            enableRetry = true,
                        ).collect(received::append)
                    }.exceptionOrNull()

                assertTrue("failure=$failure cause=${failure?.cause}", failure is OpenAIResponsesSubmissionUnknownException)
                val unknown = failure as OpenAIResponsesSubmissionUnknownException
                assertEquals("local-deepseek-clean-eof", unknown.localExecutionId)
                assertTrue(unknown.cause?.message?.contains("response.completed") == true)
                assertEquals(null, unknown.transportDiagnostics)
                assertEquals("partial before clean eof", received.toString())
                val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                assertEquals("text/event-stream", request.getHeader("Accept"))
                assertEquals(1, server.requestCount)
                verifyNoInteractions(repository)
            }
        }
    }

    @Test
    fun deepSeekResponsesBodyDisconnect_preservesPartialAndPostsOnce() = runTest {
        Mockito.mockStatic(AppLogger::class.java).use {
            MockWebServer().use { server ->
                server.start()
                val firstEvent = responsesTextDeltaSse("partial before body disconnect")
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "text/event-stream")
                        .setBody(firstEvent + "data: " + "x".repeat(64 * 1024))
                        .throttleBody(1024, 1, TimeUnit.MILLISECONDS)
                        .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                )
                val repository = mock<ProviderExecutionRepository>()
                val provider = createDeepSeekResponsesProvider(server, repository)
                val received = StringBuilder()

                val failure =
                    runCatching {
                        provider.sendMessage(
                            context = createContext(),
                            chatHistory = testHistory("disconnect response body"),
                            modelParameters = emptyList(),
                            enableThinking = true,
                            stream = true,
                            availableTools = null,
                            preserveThinkInHistory = false,
                            providerRequestContext = requestContext("local-deepseek-body-eof"),
                            onTokensUpdated = { _, _, _ -> },
                            onNonFatalError = {},
                            enableRetry = true,
                        ).collect(received::append)
                    }.exceptionOrNull()

                assertTrue("failure=$failure cause=${failure?.cause}", failure is OpenAIResponsesSubmissionUnknownException)
                val unknown = failure as OpenAIResponsesSubmissionUnknownException
                assertEquals("local-deepseek-body-eof", unknown.localExecutionId)
                assertEquals(
                    "LLM_TRANSPORT_RESPONSE_BODY_INTERRUPTED",
                    unknown.transportDiagnostics?.diagnosticCode,
                )
                assertEquals("partial before body disconnect", received.toString())
                assertEquals(1, server.requestCount)
                verifyNoInteractions(repository)
            }
        }
    }

    @Test
    fun deepSeekResponsesHeaderDisconnect_postsOnceWithActualExecutionId() = runTest {
        Mockito.mockStatic(AppLogger::class.java).use {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                )
                val repository = mock<ProviderExecutionRepository>()
                val provider = createDeepSeekResponsesProvider(server, repository)

                val failure =
                    runCatching {
                        provider.sendMessage(
                            context = createContext(),
                            chatHistory = testHistory("disconnect before response headers"),
                            modelParameters = emptyList(),
                            enableThinking = true,
                            stream = true,
                            availableTools = null,
                            preserveThinkInHistory = false,
                            providerRequestContext = requestContext("local-deepseek-last-hop"),
                            onTokensUpdated = { _, _, _ -> },
                            onNonFatalError = {},
                            enableRetry = true,
                        ).collect { error("header disconnect must not emit content") }
                    }.exceptionOrNull()

                assertTrue("failure=$failure cause=${failure?.cause}", failure is OpenAIResponsesSubmissionUnknownException)
                val unknown = failure as OpenAIResponsesSubmissionUnknownException
                assertEquals("local-deepseek-last-hop", unknown.localExecutionId)
                assertEquals(
                    "LLM_TRANSPORT_RESPONSE_HEADERS_NOT_RECEIVED",
                    unknown.transportDiagnostics?.diagnosticCode,
                )
                assertEquals(1, server.requestCount)
                verifyNoInteractions(repository)
            }
        }
    }

    @Test
    fun http502BeforeResponseCreated_postsOnceAndPersistsSubmissionUnknown() = runTest {
        Mockito.mockStatic(AppLogger::class.java).use {
            Mockito.mockConstruction(JSONArray::class.java) { jsonArray, _ ->
                whenever(jsonArray.toString()).thenReturn("[]")
            }.use {
                LocalHttpResponseServer(
                    statusCode = 502,
                    responseBody = """{"error":{"message":"Upstream request failed","type":"upstream_error"}}""",
                ).use { server ->
                    val repository = mock<ProviderExecutionRepository>()
                    val persistence =
                        RepositoryOpenAIResponsesExecutionPersistence(repository)
                    val context = createContext()
                    val requestContext =
                        ProviderRequestContext(
                            localExecutionId = "local-502",
                            chatId = "chat-502",
                            messageTimestamp = 1L,
                            variantIndex = 0,
                            hopOrdinal = 0,
                        )
                    val provider =
                        FaultInjectionResponsesProvider(
                            endpoint = server.responsesEndpoint,
                            persistence = persistence,
                        )

                    val failure =
                        runCatching {
                            provider.sendMessage(
                                context = context,
                                chatHistory =
                                    listOf(
                                        PromptTurn(
                                            kind = PromptTurnKind.USER,
                                            content = "trigger local 502",
                                        )
                                    ),
                                modelParameters = emptyList(),
                                enableThinking = true,
                                stream = true,
                                availableTools = null,
                                preserveThinkInHistory = false,
                                providerRequestContext = requestContext,
                                onTokensUpdated = { _, _, _ -> },
                                onNonFatalError = {},
                                enableRetry = true,
                            ).collect { error("HTTP 502 must not emit a visible chunk") }
                        }.exceptionOrNull()

                    server.assertHealthy()
                    assertTrue(
                        "Unexpected failure=${failure?.javaClass?.name}: ${failure?.message}; " +
                            "cause=${failure?.cause?.javaClass?.name}: ${failure?.cause?.message}; " +
                            "requests=${server.requests}",
                        failure is OpenAIResponsesSubmissionUnknownException,
                    )
                    assertEquals(
                        "local-502",
                        (failure as OpenAIResponsesSubmissionUnknownException).localExecutionId,
                    )
                    assertTrue(failure.message?.contains("502") == true)
                    assertTrue(
                        "Expected HTTP status in cause=${failure.cause?.message}",
                        failure.cause?.message?.contains("502") == true,
                    )
                    assertEquals(
                        "LLM_TRANSPORT_HTTP_STATUS_502",
                        failure.transportDiagnostics
                            ?.diagnosticCode,
                    )
                    assertEquals(listOf("POST /v1/responses"), server.requests.toList())
                    assertEquals(
                        listOf("POST /v1/responses HTTP/1.1"),
                        server.requestLines.toList(),
                    )

                    val executionCaptor = argumentCaptor<ProviderExecutionEntity>()
                    val messageStateCaptor = argumentCaptor<MessageProviderStateEntity>()
                    verify(repository).createExecution(
                        executionCaptor.capture(),
                        messageStateCaptor.capture(),
                    )
                    val createdExecution = executionCaptor.firstValue
                    assertEquals(ProviderExecutionStatus.SUBMITTING.name, createdExecution.status)
                    assertEquals("local-502", createdExecution.localExecutionId)
                    val createdMessageState = messageStateCaptor.firstValue
                    assertEquals(
                        ProviderExecutionStatus.SUBMITTING.name,
                        createdMessageState.status,
                    )
                    assertEquals(null, createdMessageState.remoteResponseId)
                    assertEquals(
                        ProviderExecutionEntity.NO_APPLIED_SEQUENCE,
                        createdMessageState.lastAppliedSequence,
                    )

                    val errorCodeCaptor = argumentCaptor<String>()
                    val errorMessageCaptor = argumentCaptor<String>()
                    verify(repository).updateStatus(
                        localExecutionId = eq("local-502"),
                        status = eq(ProviderExecutionStatus.SUBMISSION_UNKNOWN),
                        lastErrorCode = errorCodeCaptor.capture(),
                        lastErrorMessage = errorMessageCaptor.capture(),
                        completedAt = isNull(),
                        updatedAt = any(),
                    )
                    assertEquals("HTTP_502_SUBMISSION_UNKNOWN", errorCodeCaptor.firstValue)
                    assertTrue(errorMessageCaptor.firstValue.contains("502"))
                    verifyNoMoreInteractions(repository)
                }
            }
        }
    }

    @Test
    fun deepSeekResponses404_isAConfigurationFailureAndPostsOnce() = runTest {
        Mockito.mockStatic(AppLogger::class.java).use {
            LocalHttpResponseServer(
                statusCode = 404,
                responseBody = "{\"error\":{\"message\":\"Not Found\",\"type\":\"not_found\"}}",
            ).use { server ->
                val repository = mock<ProviderExecutionRepository>()
                val provider =
                    FaultInjectionResponsesProvider(
                        endpoint = server.responsesEndpoint,
                        persistence = RepositoryOpenAIResponsesExecutionPersistence(repository),
                        providerType = ApiProviderType.DEEPSEEK,
                        capabilityProviderType = ApiProviderType.OPENAI_RESPONSES_GENERIC,
                    )
                val requestContext =
                    ProviderRequestContext(
                        localExecutionId = "local-deepseek-404",
                        chatId = "chat-deepseek-404",
                        messageTimestamp = 3L,
                        variantIndex = 0,
                        hopOrdinal = 0,
                    )

                val failure =
                    runCatching {
                        provider.sendMessage(
                            context = createContext(),
                            chatHistory =
                                listOf(
                                    PromptTurn(
                                        kind = PromptTurnKind.USER,
                                        content = "deepseek siliconflow misconfigured",
                                    )
                                ),
                            modelParameters = emptyList(),
                            enableThinking = true,
                            stream = true,
                            availableTools = null,
                            preserveThinkInHistory = false,
                            providerRequestContext = requestContext,
                            onTokensUpdated = { _, _, _ -> },
                            onNonFatalError = {},
                            enableRetry = true,
                        ).collect { error("HTTP 404 must not emit a visible chunk") }
                    }.exceptionOrNull()

                assertTrue(failure?.message?.contains("404") == true)
                assertTrue(failure !is OpenAIResponsesSubmissionUnknownException)
                assertEquals(listOf("POST /v1/responses"), server.requests.toList())
                verify(repository).createExecution(any(), any())
                verify(repository).updateStatus(
                    localExecutionId = eq("local-deepseek-404"),
                    status = eq(ProviderExecutionStatus.FAILED),
                    lastErrorCode = eq("HTTP_404"),
                    lastErrorMessage = any(),
                    completedAt = any(),
                    updatedAt = any(),
                )
                verifyNoMoreInteractions(repository)
            }
        }
    }

    @Test
    fun responseHeaderAbort_postsOnceAndPersistsTransportDiagnostic() = runTest {
        Mockito.mockStatic(AppLogger::class.java).use {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse()
                        .setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                )
                val repository = mock<ProviderExecutionRepository>()
                val persistence =
                    RepositoryOpenAIResponsesExecutionPersistence(repository)
                val provider =
                    FaultInjectionResponsesProvider(
                        endpoint = server.url("/v1/responses").toString(),
                        persistence = persistence,
                    )
                val requestContext =
                    ProviderRequestContext(
                        localExecutionId = "local-abort",
                        chatId = "chat-abort",
                        messageTimestamp = 2L,
                        variantIndex = 0,
                        hopOrdinal = 0,
                    )

                val failure =
                    runCatching {
                        provider.sendMessage(
                            context = createContext(),
                            chatHistory =
                                listOf(
                                    PromptTurn(
                                        kind = PromptTurnKind.USER,
                                        content = "trigger response header abort",
                                    )
                                ),
                            modelParameters = emptyList(),
                            enableThinking = true,
                            stream = true,
                            availableTools = null,
                            preserveThinkInHistory = false,
                            providerRequestContext = requestContext,
                            onTokensUpdated = { _, _, _ -> },
                            onNonFatalError = {},
                            enableRetry = true,
                        ).collect { error("response-header abort must not emit a visible chunk") }
                    }.exceptionOrNull()

                assertTrue(failure is OpenAIResponsesSubmissionUnknownException)
                val unknown = failure as OpenAIResponsesSubmissionUnknownException
                assertEquals(
                    "Unexpected transport snapshot=${unknown.transportDiagnostics}",
                    "LLM_TRANSPORT_RESPONSE_HEADERS_NOT_RECEIVED",
                    unknown.transportDiagnostics?.diagnosticCode,
                )
                assertEquals(1, server.requestCount)

                val executionCaptor = argumentCaptor<ProviderExecutionEntity>()
                val messageStateCaptor = argumentCaptor<MessageProviderStateEntity>()
                verify(repository).createExecution(
                    executionCaptor.capture(),
                    messageStateCaptor.capture(),
                )
                assertEquals(
                    ProviderExecutionStatus.SUBMITTING.name,
                    executionCaptor.firstValue.status,
                )
                assertEquals(
                    "local-abort",
                    executionCaptor.firstValue.localExecutionId,
                )
                assertEquals(
                    ProviderExecutionStatus.SUBMITTING.name,
                    messageStateCaptor.firstValue.status,
                )

                val errorCodeCaptor = argumentCaptor<String>()
                val errorMessageCaptor = argumentCaptor<String>()
                verify(repository).updateStatus(
                    localExecutionId = eq("local-abort"),
                    status = eq(ProviderExecutionStatus.SUBMISSION_UNKNOWN),
                    lastErrorCode = errorCodeCaptor.capture(),
                    lastErrorMessage = errorMessageCaptor.capture(),
                    completedAt = isNull(),
                    updatedAt = any(),
                )
                assertEquals("SUBMISSION_UNKNOWN", errorCodeCaptor.firstValue)
                assertTrue(
                    errorMessageCaptor.firstValue.contains(
                        "LLM_TRANSPORT_RESPONSE_HEADERS_NOT_RECEIVED"
                    )
                )
                verifyNoMoreInteractions(repository)
            }
        }
    }

    private fun createContext(): Context {
        val context = mock<Context>()
        whenever(context.getString(any())).thenReturn("request cancelled")
        whenever(context.getString(any(), any(), any())).thenAnswer { invocation ->
            "API request failed, status=${invocation.arguments[1]}, body=${invocation.arguments[2]}"
        }
        return context
    }

    private fun createDeepSeekResponsesProvider(
        server: MockWebServer,
        repository: ProviderExecutionRepository,
    ): FaultInjectionResponsesProvider =
        FaultInjectionResponsesProvider(
            endpoint = server.url("/v1/responses").toString(),
            persistence = RepositoryOpenAIResponsesExecutionPersistence(repository),
            providerType = ApiProviderType.DEEPSEEK,
            capabilityProviderType = ApiProviderType.OPENAI_RESPONSES_GENERIC,
            supportsStreamResumption = false,
        )

    private fun requestContext(localExecutionId: String): ProviderRequestContext =
        ProviderRequestContext(
            localExecutionId = localExecutionId,
            chatId = "chat-deepseek",
            messageTimestamp = 4L,
            variantIndex = 0,
            hopOrdinal = 6,
        )

    private fun testHistory(content: String): List<PromptTurn> =
        listOf(PromptTurn(kind = PromptTurnKind.USER, content = content))

    private fun responsesTextDeltaSse(text: String): String =
        "data: " +
            org.json.JSONObject()
                .put("type", "response.output_text.delta")
                .put("delta", text)
                .toString() +
            "\n\n"

    private fun completedResponsesSse(text: String): String =
        "data: " + completedResponsesEventJson(text) + "\n\n"

    private fun completedResponsesJson(text: String): String =
        org.json.JSONObject()
            .put("id", "resp-deepseek-test")
            .put("status", "completed")
            .put("output", responseOutput(text))
            .toString()

    private fun completedResponsesEventJson(text: String): String =
        org.json.JSONObject()
            .put("type", "response.completed")
            .put("response", org.json.JSONObject(completedResponsesJson(text)))
            .toString()

    private fun responseOutput(text: String): org.json.JSONArray =
        org.json.JSONArray().put(
            org.json.JSONObject()
                .put("type", "message")
                .put(
                    "content",
                    org.json.JSONArray().put(
                        org.json.JSONObject()
                            .put("type", "output_text")
                            .put("text", text)
                    )
                )
        )

    private class FaultInjectionResponsesProvider(
        endpoint: String,
        private val persistence: OpenAIResponsesExecutionPersistence,
        providerType: ApiProviderType = ApiProviderType.OPENAI_RESPONSES,
        capabilityProviderType: ApiProviderType = providerType,
        private val supportsStreamResumption: Boolean = true,
    ) : OpenAIProvider(
        apiEndpoint = endpoint,
        apiKeyProvider =
            object : ApiKeyProvider {
                override suspend fun getApiKey(): String = "local-test-key"

                override suspend fun getCandidateKeyCount(): Int = 1
            },
        modelName = "gpt-5.6-sol",
        client =
            LlmHttpClientProtocolPolicy.responsesPolicy
                .applyTo(
                    OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .writeTimeout(5, TimeUnit.SECONDS)
                        .eventListenerFactory(LlmNetworkEventListenerFactory.silent())
                )
                .build(),
        providerType = providerType,
        capabilityProviderType = capabilityProviderType,
        endpointProtocol = com.ai.assistance.operit.data.model.ApiProtocol.OPENAI_RESPONSES,
    ) {
        override val useResponsesApi: Boolean = true
        override val supportsResponsesStreamResumption: Boolean = supportsStreamResumption
        override val responseExecutionDispatcher = Dispatchers.Unconfined

        internal override fun createResponsesExecutionPersistence(
            context: Context,
        ): OpenAIResponsesExecutionPersistence = persistence

        override fun createRequestBody(
            context: Context,
            chatHistory: List<PromptTurn>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            stream: Boolean,
            availableTools: List<ToolPrompt>?,
            preserveThinkInHistory: Boolean,
        ): RequestBody =
            """{"model":"gpt-5.6-sol","stream":$stream}"""
                .toRequestBody("application/json".toMediaType())
    }

    private class LocalHttpResponseServer(
        private val statusCode: Int,
        private val responseBody: String,
    ) : AutoCloseable {
        private val serverSocket =
            ServerSocket(
                0,
                16,
                InetAddress.getByName("127.0.0.1"),
            )
        private val failure = AtomicReference<Throwable?>()
        private val serverThread =
            thread(
                name = "openai-responses-fault-server",
                isDaemon = true,
                start = true,
            ) {
                while (!serverSocket.isClosed) {
                    try {
                        handle(serverSocket.accept())
                    } catch (error: SocketException) {
                        if (!serverSocket.isClosed) {
                            failure.compareAndSet(null, error)
                        }
                    } catch (error: Throwable) {
                        failure.compareAndSet(null, error)
                    }
                }
            }

        val requests: MutableList<String> =
            Collections.synchronizedList(mutableListOf())
        val requestLines: MutableList<String> =
            Collections.synchronizedList(mutableListOf())
        val responsesEndpoint: String =
            "http://127.0.0.1:${serverSocket.localPort}/v1/responses#"

        private fun handle(socket: Socket) {
            socket.use { accepted ->
                accepted.soTimeout = 5_000
                val reader =
                    BufferedReader(
                        InputStreamReader(
                            accepted.getInputStream(),
                            StandardCharsets.US_ASCII,
                        )
                    )
                val requestLine = requireNotNull(reader.readLine())
                val requestParts = requestLine.split(' ')
                require(requestParts.size >= 2) { "Malformed request line: $requestLine" }
                requestLines += requestLine
                requests += "${requestParts[0]} ${requestParts[1]}"

                var contentLength = 0
                while (true) {
                    val header = reader.readLine() ?: break
                    if (header.isEmpty()) break
                    if (header.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = header.substringAfter(':').trim().toInt()
                    }
                }
                if (contentLength > 0) {
                    val body = CharArray(contentLength)
                    var offset = 0
                    while (offset < body.size) {
                        val read = reader.read(body, offset, body.size - offset)
                        if (read < 0) break
                        offset += read
                    }
                }

                val responseBytes = responseBody.toByteArray(StandardCharsets.UTF_8)
                val reason = if (statusCode == 502) "Bad Gateway" else "Test Failure"
                val headers =
                    buildString {
                        append("HTTP/1.1 $statusCode $reason\r\n")
                        append("Content-Type: application/json\r\n")
                        append("Content-Length: ${responseBytes.size}\r\n")
                        append("Connection: close\r\n")
                        append("\r\n")
                    }.toByteArray(StandardCharsets.US_ASCII)
                accepted.getOutputStream().use { output ->
                    output.write(headers)
                    output.write(responseBytes)
                    output.flush()
                }
            }
        }

        fun assertHealthy() {
            failure.get()?.let { throw AssertionError("Local HTTP server failed", it) }
        }

        override fun close() {
            serverSocket.close()
            serverThread.join(5_000)
            assertHealthy()
        }
    }
}
