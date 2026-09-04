package com.ai.assistance.operit.core.tools.javascript

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
