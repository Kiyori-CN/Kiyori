package com.ai.assistance.operit.core.tools.javascript

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ToolPkgBilibiliBridgeTest {
    @Test
    fun onlyBilibiliToolPkgContainerOwnsBridge() {
        assertTrue(BilibiliToolPkgContract.ownsBridge(BilibiliToolPkgContract.TOOLPKG_ID))
        assertFalse(BilibiliToolPkgContract.ownsBridge("com.example.other"))
        assertFalse(BilibiliToolPkgContract.ownsBridge(null))
    }

    @Test
    fun authorizedCallerRequiresBoundContainerAndActiveExecution() {
        assertEquals(
            "call-1",
            BilibiliToolPkgContract.requireAuthorizedCaller(
                boundToolPkgContainerName = BilibiliToolPkgContract.TOOLPKG_ID,
                callId = " call-1 ",
                isExecutionCallActive = { candidate -> candidate == "call-1" },
            ),
        )
        assertBilibiliFailure(BilibiliToolPkgErrorCode.CALLER_NOT_AUTHORIZED) {
            BilibiliToolPkgContract.requireAuthorizedCaller(
                boundToolPkgContainerName = "com.example.other",
                callId = "call-1",
                isExecutionCallActive = { true },
            )
        }
        assertBilibiliFailure(BilibiliToolPkgErrorCode.CALLER_NOT_AUTHORIZED) {
            BilibiliToolPkgContract.requireAuthorizedCaller(
                boundToolPkgContainerName = BilibiliToolPkgContract.TOOLPKG_ID,
                callId = "call-ended",
                isExecutionCallActive = { false },
            )
        }
    }

    @Test
    fun apiParserPermitsOnlyExplicitReadOnlyEndpoints() {
        val request =
            BilibiliToolPkgContract.parseRequest(
                """{"mode":"api","url":"https://api.bilibili.com/x/web-interface/view?bvid=BV1xx411c7mD"}"""
            )
        assertEquals(BilibiliToolPkgRequestMode.API, request.mode)
        assertEquals("/x/web-interface/view", request.url.encodedPath)
        assertTrue(BilibiliToolPkgContract.shouldAttachCookie(request.mode, request.url))

        listOf(
            """{"mode":"api","url":"http://api.bilibili.com/x/web-interface/view"}""",
            """{"mode":"api","url":"https://api.bilibili.com:444/x/web-interface/view"}""",
            """{"mode":"api","url":"https://user:pass@api.bilibili.com/x/web-interface/view"}""",
            """{"mode":"api","url":"https://example.com/x/web-interface/view"}""",
            """{"mode":"api","url":"https://api.bilibili.com/x/v2/reply/add"}""",
            """{"mode":"api","url":"https://api.bilibili.com/x/web-interface/view","headers":{}}""",
            """{"mode":"post","url":"https://api.bilibili.com/x/web-interface/view"}""",
        ).forEach { json ->
            assertBilibiliFailure {
                BilibiliToolPkgContract.parseRequest(json)
            }
        }
    }

    @Test
    fun publicResourcesNeverReceiveConfiguredCookie() {
        val danmaku =
            BilibiliToolPkgContract.parseRequest(
                """{"mode":"public","url":"https://comment.bilibili.com/12345.xml"}"""
            )
        val subtitle =
            BilibiliToolPkgContract.parseRequest(
                """{"mode":"public","url":"https://aisubtitle.hdslb.com/bfs/ai_subtitle/file.json"}"""
            )
        assertFalse(BilibiliToolPkgContract.shouldAttachCookie(danmaku.mode, danmaku.url))
        assertFalse(BilibiliToolPkgContract.shouldAttachCookie(subtitle.mode, subtitle.url))
        assertBilibiliFailure(BilibiliToolPkgErrorCode.CALLER_NOT_AUTHORIZED) {
            BilibiliToolPkgContract.parseRequest(
                """{"mode":"public","url":"https://evil.example/bfs/ai_subtitle/file.json"}"""
            )
        }
    }

    @Test
    fun shortLinkModeCannotCrossBilibiliSiteBoundary() {
        val request =
            BilibiliToolPkgContract.parseRequest(
                """{"mode":"resolve","url":"https://b23.tv/abc123"}"""
            )
        assertEquals(BilibiliToolPkgRequestMode.RESOLVE, request.mode)
        BilibiliToolPkgContract.requireRedirectTarget(
            request.mode,
            requireNotNull("https://www.bilibili.com/video/BV1xx411c7mD".toHttpUrlOrNull()),
        )
        assertBilibiliFailure(BilibiliToolPkgErrorCode.CALLER_NOT_AUTHORIZED) {
            BilibiliToolPkgContract.requireRedirectTarget(
                request.mode,
                requireNotNull("https://evil.example/video/BV1xx411c7mD".toHttpUrlOrNull()),
            )
        }
    }

    @Test
    fun cookieValidationRejectsHeaderInjection() {
        assertEquals(
            "SESSDATA=value; bili_jct=token",
            BilibiliToolPkgContract.validateCookie(" SESSDATA=value; bili_jct=token "),
        )
        assertEquals(null, BilibiliToolPkgContract.validateCookie(" "))
        assertBilibiliFailure(BilibiliToolPkgErrorCode.INVALID_ARGUMENT) {
            BilibiliToolPkgContract.validateCookie("SESSDATA=value\r\nX-Injected: yes")
        }
    }

    @Test
    fun gatewayMapsRiskControlsWithoutRetry() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"code":0,"data":{"ok":true}}""")
            )
            server.enqueue(MockResponse().setResponseCode(412).setBody("blocked"))
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"code":-352,"message":"风控校验失败"}""")
            )
            val gateway = BilibiliToolPkgGateway(OkHttpClient.Builder().build())

            val success =
                gateway.execute(
                    request =
                        BilibiliToolPkgRequest(
                            mode = BilibiliToolPkgRequestMode.API,
                            url = server.url("/x/web-interface/nav"),
                        ),
                    cookie = null,
                    requestId = "request-success",
                    onCallCreated = {},
                )
            assertTrue(success.getBoolean("success"))
            assertEquals("""{"code":0,"data":{"ok":true}}""", success.getString("body"))

            assertBilibiliFailure(BilibiliToolPkgErrorCode.RISK_CONTROL) {
                gateway.execute(
                    request =
                        BilibiliToolPkgRequest(
                            mode = BilibiliToolPkgRequestMode.API,
                            url = server.url("/x/web-interface/nav"),
                        ),
                    cookie = null,
                    requestId = "request-http-412",
                    onCallCreated = {},
                )
            }
            assertBilibiliFailure(BilibiliToolPkgErrorCode.RISK_CONTROL) {
                gateway.execute(
                    request =
                        BilibiliToolPkgRequest(
                            mode = BilibiliToolPkgRequestMode.API,
                            url = server.url("/x/web-interface/nav"),
                        ),
                    cookie = null,
                    requestId = "request-api-352",
                    onCallCreated = {},
                )
            }
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun anonymousNavKeysAreDataButOtherLoginErrorsRemainFailures() {
        MockWebServer().use { server ->
            val keys = """{"code":-101,"message":"账号未登录","data":{"isLogin":false,"wbi_img":{"img_url":"https://i0.hdslb.com/bfs/wbi/0123456789abcdef0123456789abcdef.png","sub_url":"https://i0.hdslb.com/bfs/wbi/abcdef0123456789abcdef0123456789.png"}}}"""
            server.enqueue(MockResponse().setBody(keys))
            server.enqueue(MockResponse().setBody(keys))
            server.enqueue(MockResponse().setBody("""{"code":-101,"message":"账号未登录","data":{"isLogin":false}}"""))
            val gateway = BilibiliToolPkgGateway(OkHttpClient(), retryPause = {})
            val success = gateway.execute(
                BilibiliToolPkgRequest(BilibiliToolPkgRequestMode.API_ANONYMOUS, server.url("/x/web-interface/nav")),
                "private-cookie", "nav", {},
            )
            assertTrue(success.getBoolean("success"))
            assertEquals(keys, success.getString("body"))
            assertNull(server.takeRequest().getHeader("Cookie"))
            val failure = assertBilibiliFailure(BilibiliToolPkgErrorCode.NOT_LOGGED_IN) {
                gateway.execute(BilibiliToolPkgRequest(BilibiliToolPkgRequestMode.API, server.url("/x/player/wbi/v2?secret=hidden")), null, "player", {})
            }
            assertEquals(-101, failure.apiCode)
            assertEquals("账号未登录", failure.apiMessage)
            assertFalse(failure.toJson("player").toString().contains("hidden"))
            assertEquals(server.url("/x/player/wbi/v2").toString(), failure.endpoint)
            assertBilibiliFailure(BilibiliToolPkgErrorCode.NOT_LOGGED_IN) {
                gateway.execute(BilibiliToolPkgRequest(BilibiliToolPkgRequestMode.API_ANONYMOUS, server.url("/x/web-interface/nav")), null, "missing-keys", {})
            }
        }
    }

    @Test
    fun transientRetriesAreBoundedAndDoNotRetryAccessFailures() {
        MockWebServer().use { server ->
            val gateway = BilibiliToolPkgGateway(OkHttpClient(), retryPause = {})
            val request = BilibiliToolPkgRequest(BilibiliToolPkgRequestMode.API, server.url("/x/web-interface/view"))
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{}}"""))
            assertTrue(gateway.execute(request, null, "retry", {}).getBoolean("success"))
            repeat(3) { server.enqueue(MockResponse().setResponseCode(502)) }
            val failure = assertBilibiliFailure(BilibiliToolPkgErrorCode.HTTP_ERROR) {
                gateway.execute(request, null, "bounded", {})
            }
            assertEquals(3, failure.attempts)
            assertEquals(502, failure.httpStatus)
            server.enqueue(MockResponse().setResponseCode(403))
            assertBilibiliFailure(BilibiliToolPkgErrorCode.ACCESS_RESTRICTED) {
                gateway.execute(request, null, "access", {})
            }
            assertEquals(6, server.requestCount)
        }
    }

    @Test
    fun socketTimeoutHasSafeCauseAndEndpointAfterThreeAttempts() {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor {
            calls += 1
            throw java.net.SocketTimeoutException("must-not-expose-cookie-or-signature")
        }.build()
        val failure = assertBilibiliFailure(BilibiliToolPkgErrorCode.HTTP_ERROR) {
            BilibiliToolPkgGateway(client, retryPause = {}).execute(
                BilibiliToolPkgRequest(BilibiliToolPkgRequestMode.API, "https://api.bilibili.com/x/web-interface/view?w_rid=secret".toHttpUrl()),
                "private", "timeout", {},
            )
        }
        assertEquals(3, calls)
        assertEquals("SocketTimeoutException", failure.causeType)
        assertFalse(failure.toJson("timeout").toString().contains("secret"))
        assertFalse(failure.toJson("timeout").toString().contains("must-not-expose"))
    }

    @Test
    fun segmentEndpointIsReadOnlyAndNeverReceivesCookie() {
        val request = BilibiliToolPkgContract.parseRequest(
            """{"mode":"public","url":"https://api.bilibili.com/x/v2/dm/web/seg.so?type=1&oid=123&segment_index=1"}"""
        )
        assertFalse(BilibiliToolPkgContract.shouldAttachCookie(request.mode, request.url))
        assertBilibiliFailure(BilibiliToolPkgErrorCode.CALLER_NOT_AUTHORIZED) {
            BilibiliToolPkgContract.parseRequest("""{"mode":"public","url":"https://api.bilibili.com/x/v2/dm/post"}""")
        }
    }

    @Test
    fun binarySegmentsAreBase64AndJsonRiskErrorsRemainStructured() {
        var binary = true
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertNull(chain.request().header("Cookie"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(if (binary) byteArrayOf(10, 0).toResponseBody() else
                    """{"code":-352,"message":"风控"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val request = BilibiliToolPkgContract.parseRequest(
            """{"mode":"public","url":"https://api.bilibili.com/x/v2/dm/web/seg.so?type=1&oid=123&segment_index=1"}"""
        )
        val gateway = BilibiliToolPkgGateway(client, retryPause = {})
        val result = gateway.execute(request, "private", "binary", {})
        assertEquals("base64", result.getString("body_encoding"))
        assertEquals("CgA=", result.getString("body"))
        binary = false
        assertBilibiliFailure(BilibiliToolPkgErrorCode.RISK_CONTROL) {
            gateway.execute(request, "private", "json-risk", {})
        }
    }

    @Test
    fun cancellationDuringBackoffDoesNotSendAnotherRequest() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            var current: okhttp3.Call? = null
            val gateway = BilibiliToolPkgGateway(OkHttpClient(), retryPause = { current?.cancel() })
            var cancelled = false
            try {
                gateway.execute(
                    BilibiliToolPkgRequest(BilibiliToolPkgRequestMode.API, server.url("/x/web-interface/view")),
                    null, "cancel", { current = it },
                )
            } catch (error: java.util.concurrent.CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
            assertEquals(1, server.requestCount)
        }
    }

    private fun assertBilibiliFailure(
        expected: BilibiliToolPkgErrorCode? = null,
        block: () -> Unit,
    ): BilibiliToolPkgException {
        val error =
            try {
                block()
                throw AssertionError("Expected BilibiliToolPkgException")
            } catch (error: BilibiliToolPkgException) {
                error
            }
        if (expected != null) {
            assertEquals(expected, error.code)
        }
        return error
    }
}
