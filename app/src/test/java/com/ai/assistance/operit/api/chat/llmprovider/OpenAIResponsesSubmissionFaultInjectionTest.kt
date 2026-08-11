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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class OpenAIResponsesSubmissionFaultInjectionTest {
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
                    assertTrue(failure.cause?.message?.contains("502") == true)
                    assertEquals(listOf("POST /v1/responses"), server.requests.toList())

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

    private fun createContext(): Context {
        val context = mock<Context>()
        whenever(context.getString(any())).thenReturn("request cancelled")
        whenever(context.getString(any(), any(), any())).thenAnswer { invocation ->
            "API request failed, status=${invocation.arguments[1]}, body=${invocation.arguments[2]}"
        }
        return context
    }

    private class FaultInjectionResponsesProvider(
        endpoint: String,
        private val persistence: OpenAIResponsesExecutionPersistence,
    ) : OpenAIProvider(
        apiEndpoint = endpoint,
        apiKeyProvider =
            object : ApiKeyProvider {
                override suspend fun getApiKey(): String = "local-test-key"

                override suspend fun getCandidateKeyCount(): Int = 1
            },
        modelName = "gpt-5.6-sol",
        client =
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build(),
        providerType = ApiProviderType.OPENAI_RESPONSES,
    ) {
        override val useResponsesApi: Boolean = true
        override val supportsResponsesStreamResumption: Boolean = true

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
            """{"model":"gpt-5.6-sol","stream":true}"""
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
