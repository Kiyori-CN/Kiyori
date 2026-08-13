package com.ai.assistance.operit.api.chat.llmprovider

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAIHostedWebSearchGatewayTest {
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
    fun sendsExactlyOneFixedResponsesPostWithConfiguredAuthentication() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(successResponse().toString()))
        val gateway = gateway()
        val request = OpenAIHostedWebSearchTestFixtures.effectiveRequest()
        val binding =
            relayBinding(
                authHeaderName = "X-Relay-Key",
                authScheme = "",
                extraHeaders = mapOf("X-Tenant" to "tenant"),
            )

        val result = gateway.execute(binding, request)

        assertEquals("Evidence", result.result.answer)
        assertEquals(1, result.diagnostics.totalActionSourceCount)
        assertEquals(1, result.diagnostics.validActionSourceUrlCount)
        assertEquals(0, result.diagnostics.invalidActionSourceCount)
        val recorded = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(recorded)
        requireNotNull(recorded)
        assertEquals("POST", recorded.method)
        assertEquals("/v1/responses", recorded.path)
        assertEquals("test-key", recorded.getHeader("X-Relay-Key"))
        assertEquals("tenant", recorded.getHeader("X-Tenant"))
        assertEquals("application/json", recorded.getHeader("Accept"))
        val payload = JSONObject(recorded.body.readUtf8())
        assertEquals("required", payload.getString("tool_choice"))
        assertFalse(payload.getBoolean("store"))
        assertFalse(payload.getBoolean("stream"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun doesNotFollowRedirectOrIssueSecondRequest() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(307)
                .setHeader("Location", server.url("/redirected"))
        )
        val gateway = gateway()

        val failure =
            try {
                gateway.execute(
                    relayBinding(),
                    OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                )
                throw AssertionError("Expected redirect rejection")
            } catch (error: OpenAIHostedWebSearchException) {
                error
            }

        assertEquals(OpenAIHostedWebSearchErrorCode.OPENAI_HTTP_FAILURE, failure.code)
        assertEquals(307, failure.httpStatus)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun rejectsResponseLargerThanFourMebibytes() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("x".repeat(OpenAIHostedWebSearchContract.MAX_RESPONSE_BYTES.toInt() + 1))
        )
        val gateway = gateway()

        val failure =
            try {
                gateway.execute(
                    relayBinding(),
                    OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                )
                throw AssertionError("Expected response-size rejection")
            } catch (error: OpenAIHostedWebSearchException) {
                error
            }

        assertEquals(OpenAIHostedWebSearchErrorCode.RESPONSE_TOO_LARGE, failure.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun explicitCancelCancelsTheSingleActiveCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val gateway = gateway()
        val request =
            OpenAIHostedWebSearchTestFixtures.effectiveRequest(requestId = "ows_cancel")
        supervisorScope {
            val deferred = async(Dispatchers.IO) {
                gateway.execute(relayBinding(timeoutSeconds = 30), request)
            }
            assertNotNull(
                withContext(Dispatchers.IO) {
                    server.takeRequest(5, TimeUnit.SECONDS)
                }
            )
            assertTrue(gateway.cancel(request.requestId))
            val failure =
                try {
                    deferred.await()
                    throw AssertionError("Expected cancellation")
                } catch (error: OpenAIHostedWebSearchException) {
                    error
                }
            assertEquals(OpenAIHostedWebSearchErrorCode.REQUEST_CANCELLED, failure.code)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun callTimeoutIsReportedAsRequestTimeoutEvenThoughOkHttpCancelsCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val gateway = gateway()
        val request =
            OpenAIHostedWebSearchTestFixtures.effectiveRequest(requestId = "ows_timeout")

        val failure =
            try {
                gateway.execute(
                    relayBinding(timeoutSeconds = 1),
                    request,
                )
                throw AssertionError("Expected call timeout")
            } catch (error: OpenAIHostedWebSearchException) {
                error
            }

        assertEquals(OpenAIHostedWebSearchErrorCode.REQUEST_TIMEOUT, failure.code)
        assertEquals("waiting_response_headers", failure.phase)
        assertEquals("submission_unknown", failure.submissionState)
        assertEquals(1_000L, failure.configuredTimeoutMs)
        assertTrue(requireNotNull(failure.elapsedMs) >= 900L)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun queueTimeoutDoesNotCreateHttpCall() = runBlocking {
        val controller =
            OpenAIHostedWebSearchAdmissionController(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        val first =
            controller.acquire(
                maxConcurrentRequests = 1,
                requestsPerMinute = 0,
                queueTimeoutMs = 10_000,
                lifecycle = OpenAIHostedWebSearchRequestLifecycle("ows_blocker"),
            )
        val gateway = gateway(controller)
        val request =
            OpenAIHostedWebSearchTestFixtures.effectiveRequest(requestId = "ows_queue_timeout")

        val failure =
            try {
                gateway.execute(
                    relayBinding().copy(queueTimeoutSeconds = 1),
                    request,
                )
                throw AssertionError("Expected queue timeout")
            } catch (error: OpenAIHostedWebSearchException) {
                error
            } finally {
                first.release()
            }

        assertEquals(OpenAIHostedWebSearchErrorCode.QUEUE_TIMEOUT, failure.code)
        assertEquals("waiting_concurrency", failure.phase)
        assertEquals("not_sent", failure.submissionState)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun httpStatusUsesSanitizedStructuredProviderDiagnostics() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("X-Request-Id", "request-401")
                .setBody(
                    """
                    {
                      "error": {
                        "type": "authentication_error",
                        "code": "invalid_key",
                        "message": "Authorization: Bearer secret-token"
                      }
                    }
                    """.trimIndent()
                )
        )
        val gateway = gateway()

        val failure =
            try {
                gateway.execute(
                    relayBinding(),
                    OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                )
                throw AssertionError("Expected authentication rejection")
            } catch (error: OpenAIHostedWebSearchException) {
                error
            }

        assertEquals(OpenAIHostedWebSearchErrorCode.AUTH_REJECTED, failure.code)
        assertEquals("authentication_error", failure.providerErrorType)
        assertEquals("invalid_key", failure.providerErrorCode)
        assertEquals("request-401", failure.providerRequestId)
        assertFalse(failure.message.contains("secret-token"))
        assertTrue(failure.message.contains("<redacted>"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun parsesSanitizedRelayStructuredApiFeedFixture() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(structuredApiFeedResponse().toString())
        )
        val result =
            gateway().execute(
                relayBinding(),
                OpenAIHostedWebSearchTestFixtures.effectiveRequest(
                    query = "What is the current UTC date?"
                ),
            )

        assertEquals(
            OpenAIHostedWebSearchEvidenceMode.STRUCTURED_FEEDS,
            result.result.evidenceMode,
        )
        assertEquals("Evidence", result.result.answerWithSourceMarkers)
        assertTrue(result.result.citations.isEmpty())
        assertEquals("api", result.result.allSources.single().type)
        assertEquals("time", result.result.allSources.single().title)
        assertEquals(1, result.diagnostics.structuredFeedSourceCount)
    }

    private fun gateway(
        admissionController: OpenAIHostedWebSearchAdmissionController =
            OpenAIHostedWebSearchAdmissionController.shared,
    ): OpenAIHostedWebSearchGateway =
        OpenAIHostedWebSearchGateway(
            baseHttpClient =
                OkHttpClient.Builder()
                    .retryOnConnectionFailure(false)
                    .build(),
            admissionController = admissionController,
        )

    private fun relayBinding(
        authHeaderName: String = "Authorization",
        authScheme: String = "Bearer",
        extraHeaders: Map<String, String> = emptyMap(),
        timeoutSeconds: Int = 5,
    ): OpenAIHostedWebSearchBinding =
        OpenAIHostedWebSearchTestFixtures.binding(
            providerContract =
                OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
            endpoint = server.url("/v1/responses").toString().replace("http://", "https://"),
            authHeaderName = authHeaderName,
            authScheme = authScheme,
            extraHeaders = extraHeaders,
            timeoutSeconds = timeoutSeconds,
        ).copy(endpoint = server.url("/v1/responses").toString())

    private fun successResponse(): JSONObject =
        JSONObject()
            .put("id", "resp_gateway")
            .put("status", "completed")
            .put(
                "output",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("type", "web_search_call")
                            .put(
                                "action",
                                JSONObject()
                                    .put("type", "search")
                                    .put("query", "fixture")
                                    .put(
                                        "sources",
                                        JSONArray()
                                            .put(
                                                JSONObject()
                                                    .put("type", "url")
                                                    .put("url", "https://example.com/source")
                                                    .put("title", "Example"),
                                            ),
                                    ),
                            ),
                    )
                    .put(
                        JSONObject()
                            .put("type", "message")
                            .put(
                                "content",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("type", "output_text")
                                            .put("text", "Evidence")
                                            .put(
                                                "annotations",
                                                JSONArray()
                                                    .put(
                                                        JSONObject()
                                                            .put("type", "url_citation")
                                                            .put(
                                                                "url",
                                                                "https://example.com/source",
                                                            )
                                                            .put("title", "Example")
                                                            .put("start_index", 0)
                                                            .put("end_index", 8),
                                                    ),
                                            ),
                                    ),
                            ),
                    ),
            )
            .put(
                "usage",
                JSONObject()
                    .put("input_tokens", 10)
                    .put("output_tokens", 4),
            )

    private fun structuredApiFeedResponse(): JSONObject =
        JSONObject()
            .put("id", "resp_relay_api_feed")
            .put("status", "completed")
            .put(
                "output",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("type", "web_search_call")
                            .put(
                                "action",
                                JSONObject()
                                    .put("type", "search")
                                    .put("query", "current UTC date")
                                    .put(
                                        "sources",
                                        JSONArray()
                                            .put(
                                                JSONObject()
                                                    .put("type", "api")
                                                    .put("name", "time")
                                            )
                                    )
                            )
                    )
                    .put(
                        JSONObject()
                            .put("type", "message")
                            .put(
                                "content",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("type", "output_text")
                                            .put("text", "Evidence")
                                            .put("annotations", JSONArray())
                                    )
                            )
                    )
            )
            .put(
                "usage",
                JSONObject()
                    .put("input_tokens", 10)
                    .put("output_tokens", 4)
            )
}
