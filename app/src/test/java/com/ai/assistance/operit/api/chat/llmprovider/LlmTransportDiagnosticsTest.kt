package com.ai.assistance.operit.api.chat.llmprovider

import java.io.IOException
import java.util.concurrent.TimeUnit
import com.ai.assistance.operit.util.AppLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.Mockito
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LlmTransportDiagnosticsTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun requestBodySentThenConnectionClosesBeforeResponseHeaders() {
        withLoggerMock {
            server.enqueue(
                MockResponse()
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            )
            val state = LlmRequestTraceState()
            val client = clientFor(state)
            val request =
                Request.Builder()
                    .url(server.url("/v1/responses"))
                    .tag(
                        LlmRequestTraceContext::class.java,
                        traceContext(state),
                    )
                    .post("{}".toRequestBody())
                    .build()

            assertThrowsIOException { client.newCall(request).execute() }

            val diagnostics = state.snapshot(IOException("connection abort"))
            assertEquals(
                "LLM_TRANSPORT_RESPONSE_HEADERS_NOT_RECEIVED",
                diagnostics.diagnosticCode,
            )
            assertEquals(LlmTransportStage.WAITING_FOR_RESPONSE_HEADERS, diagnostics.stage)
            assertTrue(diagnostics.requestBodyBytes >= 0L)
            assertTrue(diagnostics.requestBodyStarted)
            assertFalse(diagnostics.responseHeadersReceived)
            assertFalse(diagnostics.responseBodyStarted)
            assertNull(diagnostics.responseStatusCode)
        }
    }

    @Test
    fun responseHeadersReceivedThenResponseBodyCloses() {
        withLoggerMock {
            server.enqueue(
                MockResponse()
                    .setBody("x".repeat(16 * 1024))
                    .throttleBody(1024, 1, TimeUnit.MILLISECONDS)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
            )
            val state = LlmRequestTraceState()
            val client = clientFor(state)
            val request =
                Request.Builder()
                    .url(server.url("/v1/responses"))
                    .tag(
                        LlmRequestTraceContext::class.java,
                        traceContext(state),
                    )
                    .post("{}".toRequestBody())
                    .build()

            val response = client.newCall(request).execute()
            assertNotNull(response.body)
            assertThrowsIOException { response.body!!.bytes() }
            response.close()

            val diagnostics = state.snapshot(IOException("response body interrupted"))
            assertEquals(
                "LLM_TRANSPORT_RESPONSE_BODY_INTERRUPTED",
                diagnostics.diagnosticCode,
            )
            assertTrue(diagnostics.responseHeadersReceived)
            assertTrue(diagnostics.responseBodyStarted)
            assertEquals(200, diagnostics.responseStatusCode)
        }
    }

    @Test
    fun httpStatusIsRecordedOnlyAfterResponseHeadersArrive() {
        withLoggerMock {
            server.enqueue(
                MockResponse()
                    .setResponseCode(502)
                    .setHeader("X-Request-Id", "request-id-for-test")
                    .setBody("{}")
            )
            val state = LlmRequestTraceState()
            val client = clientFor(state)
            val request =
                Request.Builder()
                    .url(server.url("/v1/responses"))
                    .tag(
                        LlmRequestTraceContext::class.java,
                        traceContext(state),
                    )
                    .post("{}".toRequestBody())
                    .build()

            client.newCall(request).execute().use { response ->
                assertEquals(502, response.code)
            }

            val diagnostics = state.snapshotForHttpStatus(502)
            assertEquals("LLM_TRANSPORT_HTTP_STATUS_502", diagnostics.diagnosticCode)
            assertTrue(diagnostics.responseHeadersReceived)
            assertEquals(502, diagnostics.responseStatusCode)
            assertNotNull(diagnostics.responseCorrelationId)
            assertFalse(
                diagnostics.summary().contains("request-id-for-test"),
            )
        }
    }

    @Test
    fun correlationIdIsHashedAndBounded() {
        val first = LlmTransportDiagnostics.redactCorrelationId("response-id")
        val second = LlmTransportDiagnostics.redactCorrelationId("response-id")

        assertEquals(first, second)
        assertEquals(16, first?.length)
        assertFalse(first.orEmpty().contains("response-id"))
        assertNull(LlmTransportDiagnostics.redactCorrelationId(" "))
    }

    private fun clientFor(state: LlmRequestTraceState): OkHttpClient =
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .eventListenerFactory(LlmNetworkEventListenerFactory.silent())
            .build()

    private fun traceContext(state: LlmRequestTraceState): LlmRequestTraceContext =
        LlmRequestTraceContext(
            requestId = "test-request",
            provider = "OPENAI_RESPONSES",
            model = "test-model",
            stream = true,
            attempt = 1,
            endpointLabel = "http://localhost/v1/responses",
            localExecutionId = "local-test",
            state = state,
        )

    private fun assertThrowsIOException(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IOException")
        } catch (_: IOException) {
            // Expected transport fault from the local fixture.
        }
    }

    private fun <T> withLoggerMock(block: () -> T): T =
        Mockito.mockStatic(AppLogger::class.java).use { block() }
}
