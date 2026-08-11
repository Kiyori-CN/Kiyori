package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import java.io.BufferedReader
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
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenAIResponsesToolProjectionAndroidTest {
    @Test
    fun streamedFunctionCallAndCompletedSnapshot_projectOneXmlInvocation() = runBlocking {
        LocalSseResponseServer(completedToolCallSse()).use { server ->
            val provider = ProjectionResponsesProvider(server.responsesEndpoint)
            val output = StringBuilder()

            provider.sendMessage(
                context = ApplicationProvider.getApplicationContext(),
                chatHistory =
                    listOf(
                        PromptTurn(
                            kind = PromptTurnKind.USER,
                            content = "read notes",
                        )
                    ),
                modelParameters = emptyList(),
                enableThinking = false,
                stream = true,
                availableTools = null,
                preserveThinkInHistory = false,
                providerRequestContext =
                    ProviderRequestContext(
                        localExecutionId = "local-tool-projection",
                        chatId = "chat-tool-projection",
                        messageTimestamp = 2L,
                        variantIndex = 0,
                        hopOrdinal = 0,
                    ),
                onTokensUpdated = { _, _, _ -> },
                onNonFatalError = {},
                enableRetry = true,
            ).collect { chunk ->
                output.append(chunk)
            }

            server.assertHealthy()
            assertEquals(listOf("POST /v1/responses"), server.requests.toList())
            assertEquals(
                1,
                Regex("""provider_call_id="call-1"""")
                    .findAll(output)
                    .count(),
            )
            assertTrue(output.contains("""<param name="path">notes.txt</param>"""))
        }
    }

    @Test
    fun completedArgumentsWithoutDelta_projectOneXmlInvocation() = runBlocking {
        LocalSseResponseServer(completedToolCallWithoutArgumentDeltaSse()).use { server ->
            val provider = ProjectionResponsesProvider(server.responsesEndpoint)
            val output = StringBuilder()

            provider.sendMessage(
                context = ApplicationProvider.getApplicationContext(),
                chatHistory =
                    listOf(
                        PromptTurn(
                            kind = PromptTurnKind.USER,
                            content = "read notes",
                        )
                    ),
                modelParameters = emptyList(),
                enableThinking = false,
                stream = true,
                availableTools = null,
                preserveThinkInHistory = false,
                providerRequestContext =
                    ProviderRequestContext(
                        localExecutionId = "local-tool-done-snapshot",
                        chatId = "chat-tool-done-snapshot",
                        messageTimestamp = 3L,
                        variantIndex = 0,
                        hopOrdinal = 0,
                    ),
                onTokensUpdated = { _, _, _ -> },
                onNonFatalError = {},
                enableRetry = true,
            ).collect { chunk ->
                output.append(chunk)
            }

            server.assertHealthy()
            assertEquals(listOf("POST /v1/responses"), server.requests.toList())
            assertEquals(
                1,
                Regex("""provider_call_id="call-1"""")
                    .findAll(output)
                    .count(),
            )
            assertTrue(output.contains("""<param name="path">notes.txt</param>"""))
        }
    }

    private class ProjectionResponsesProvider(
        endpoint: String,
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
        enableToolCall = true,
    ) {
        override val useResponsesApi: Boolean = true
        override val supportsResponsesStreamResumption: Boolean = false

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

    private class LocalSseResponseServer(
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
                name = "openai-responses-tool-projection-server",
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
                val headers =
                    buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/event-stream\r\n")
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
            failure.get()?.let { throw AssertionError("Local SSE server failed", it) }
        }

        override fun close() {
            serverSocket.close()
            serverThread.join(5_000)
            assertHealthy()
        }
    }

    private fun completedToolCallSse(): String {
        val events =
            listOf(
                """{"type":"response.created","response":{"id":"resp-1","status":"in_progress"}}""",
                """{"type":"response.output_text.delta","delta":"I will read."}""",
                """{"type":"response.output_item.added","output_index":1,"item":{"type":"function_call","id":"fc-1","call_id":"call-1","name":"read_file"}}""",
                """{"type":"response.function_call_arguments.delta","output_index":1,"delta":"{\"path\":\"notes.txt\"}"}""",
                """{"type":"response.function_call_arguments.done","output_index":1,"arguments":"{\"path\":\"notes.txt\"}"}""",
                """{"type":"response.completed","response":{"id":"resp-1","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"I will read."}]},{"type":"function_call","id":"fc-1","call_id":"call-1","name":"read_file","arguments":"{\"path\":\"notes.txt\"}"}],"usage":{"input_tokens":5,"output_tokens":3}}}""",
            )
        return events.joinToString(separator = "\n\n", postfix = "\n\n") { event ->
            "data: $event"
        }
    }

    private fun completedToolCallWithoutArgumentDeltaSse(): String {
        val events =
            listOf(
                """{"type":"response.created","response":{"id":"resp-1","status":"in_progress"}}""",
                """{"type":"response.output_item.added","output_index":1,"item":{"type":"function_call","id":"fc-1","call_id":"call-1","name":"read_file"}}""",
                """{"type":"response.function_call_arguments.done","output_index":1,"arguments":"{\"path\":\"notes.txt\"}"}""",
                """{"type":"response.completed","response":{"id":"resp-1","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"I will read."}]},{"type":"function_call","id":"fc-1","call_id":"call-1","name":"read_file","arguments":"{\"path\":\"notes.txt\"}"}],"usage":{"input_tokens":5,"output_tokens":3}}}""",
            )
        return events.joinToString(separator = "\n\n", postfix = "\n\n") { event ->
            "data: $event"
        }
    }
}
