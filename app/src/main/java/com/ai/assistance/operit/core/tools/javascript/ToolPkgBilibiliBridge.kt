package com.ai.assistance.operit.core.tools.javascript

import android.content.Context
import com.ai.assistance.operit.data.preferences.ToolPkgHostEnvironmentRepository
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.platform.network.KiyoriNetworkModule
import com.kiyori.platform.network.applyKiyoriNetworkProxy
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import org.json.JSONTokener

internal object BilibiliToolPkgContract {
    const val TOOLPKG_ID = "com.kiyori.bilibili_toolkit"
    const val COOKIE_VARIABLE = "BILIBILI_COOKIE"
    const val API_HOST = "api.bilibili.com"

    private const val MAX_URL_CHARS = 8_192
    private val READ_ONLY_API_PATHS =
        setOf(
            "/x/web-interface/nav",
            "/x/web-interface/view",
            "/x/web-interface/wbi/view/detail",
            "/x/web-interface/wbi/search/type",
            "/x/player/wbi/v2",
            "/x/player/wbi/playurl",
            "/x/v2/reply/wbi/main",
            "/x/v2/reply/reply",
            "/x/space/wbi/acc/info",
            "/x/web-interface/card",
            "/x/relation/stat",
            "/x/web-interface/view/conclusion/get",
            "/x/web-interface/history/cursor",
            "/x/v2/history/toview/web",
            "/x/v3/fav/folder/created/list-all",
            "/x/v3/fav/resource/list",
            "/x/space/like/video",
            "/x/space/coin/video",
            "/x/space/bangumi/follow/list",
            "/x/space/wbi/arc/search",
            "/x/stein/edgeinfo_v2",
            "/pgc/review/user",
            "/pgc/view/web/season",
            "/pgc/player/web/playurl",
            "/pgc/player/web/v2/playurl",
        )

    fun ownsBridge(boundToolPkgContainerName: String?): Boolean =
        boundToolPkgContainerName == TOOLPKG_ID

    fun requireAuthorizedCaller(
        boundToolPkgContainerName: String?,
        callId: String,
        isExecutionCallActive: (String) -> Boolean,
    ): String {
        if (!ownsBridge(boundToolPkgContainerName)) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.CALLER_NOT_AUTHORIZED,
                message = "Bilibili host service is only available to its bound ToolPkg container.",
            )
        }
        val normalizedCallId = callId.trim()
        if (normalizedCallId.isEmpty() || !isExecutionCallActive(normalizedCallId)) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.CALLER_NOT_AUTHORIZED,
                message = "Bilibili host service requires an active bound ToolPkg execution call.",
            )
        }
        return normalizedCallId
    }

    fun parseRequest(requestJson: String): BilibiliToolPkgRequest {
        val parsed =
            runCatching { JSONTokener(requestJson.trim()).nextValue() }.getOrElse { error ->
                throw BilibiliToolPkgException(
                    code = BilibiliToolPkgErrorCode.INVALID_ARGUMENT,
                    message = "Bilibili request must be a JSON object.",
                    cause = error,
                )
            }
        val requestObject =
            parsed as? JSONObject
                ?: throw BilibiliToolPkgException(
                    code = BilibiliToolPkgErrorCode.INVALID_ARGUMENT,
                    message = "Bilibili request must be a JSON object.",
                )
        val unsupported = requestObject.keys().asSequence().toSet() - setOf("mode", "url")
        if (unsupported.isNotEmpty()) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.INVALID_ARGUMENT,
                message =
                    "Unsupported Bilibili request fields: " +
                        unsupported.sorted().joinToString() +
                        ".",
            )
        }
        val modeValue = requestObject.requireString("mode")
        val urlValue = requestObject.requireString("url")
        if (urlValue.length > MAX_URL_CHARS) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.INVALID_ARGUMENT,
                message = "Bilibili request URL exceeds $MAX_URL_CHARS characters.",
            )
        }
        val mode =
            BilibiliToolPkgRequestMode.entries.firstOrNull { it.wireValue == modeValue }
                ?: throw BilibiliToolPkgException(
                    code = BilibiliToolPkgErrorCode.INVALID_ARGUMENT,
                    message =
                        "Bilibili request mode must be api, api_anonymous, public, or resolve.",
                )
        val url =
            urlValue.toHttpUrlOrNull()
                ?: throw BilibiliToolPkgException(
                    code = BilibiliToolPkgErrorCode.INVALID_ARGUMENT,
                    message = "Bilibili request URL is invalid.",
                )
        validateCommonUrl(url)
        when (mode) {
            BilibiliToolPkgRequestMode.API,
            BilibiliToolPkgRequestMode.API_ANONYMOUS -> requireApiUrl(url)
            BilibiliToolPkgRequestMode.PUBLIC -> requirePublicResourceUrl(url)
            BilibiliToolPkgRequestMode.RESOLVE -> requireShortLinkUrl(url)
        }
        return BilibiliToolPkgRequest(mode = mode, url = url)
    }

    fun requireRedirectTarget(mode: BilibiliToolPkgRequestMode, url: HttpUrl): HttpUrl {
        validateCommonUrl(url)
        when (mode) {
            BilibiliToolPkgRequestMode.API,
            BilibiliToolPkgRequestMode.API_ANONYMOUS -> requireApiUrl(url)
            BilibiliToolPkgRequestMode.PUBLIC -> requirePublicResourceUrl(url)
            BilibiliToolPkgRequestMode.RESOLVE -> requireResolveDestination(url)
        }
        return url
    }

    fun shouldAttachCookie(mode: BilibiliToolPkgRequestMode, url: HttpUrl): Boolean =
        mode == BilibiliToolPkgRequestMode.API && url.host == API_HOST

    fun validateCookie(rawCookie: String?): String? {
        val cookie = rawCookie?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (cookie.length > 16_384 || cookie.any { it == '\r' || it == '\n' }) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.INVALID_ARGUMENT,
                message = "Configured Bilibili Cookie is invalid.",
            )
        }
        return cookie
    }

    private fun JSONObject.requireString(name: String): String {
        if (!has(name) || isNull(name)) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.INVALID_ARGUMENT,
                message = "Bilibili request field $name is required.",
            )
        }
        val value = get(name)
        if (value !is String || value.trim().isEmpty()) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.INVALID_ARGUMENT,
                message = "Bilibili request field $name must be a non-empty string.",
            )
        }
        return value.trim()
    }

    private fun validateCommonUrl(url: HttpUrl) {
        if (
            !url.isHttps ||
                url.port != 443 ||
                url.username.isNotEmpty() ||
                url.password.isNotEmpty()
        ) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.INVALID_ARGUMENT,
                message =
                    "Bilibili host service requires HTTPS, the default port, and no URL credentials.",
            )
        }
    }

    private fun requireApiUrl(url: HttpUrl) {
        if (url.host != API_HOST || url.encodedPath !in READ_ONLY_API_PATHS) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.CALLER_NOT_AUTHORIZED,
                message = "Bilibili API host or read-only path is not allowed.",
            )
        }
    }

    private fun requireShortLinkUrl(url: HttpUrl) {
        if (url.host != "b23.tv" || url.encodedPath == "/") {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.INVALID_ARGUMENT,
                message = "Resolve mode accepts only a non-empty b23.tv short link.",
            )
        }
    }

    private fun requireResolveDestination(url: HttpUrl) {
        if (url.host == "b23.tv") {
            requireShortLinkUrl(url)
            return
        }
        if (url.host != "bilibili.com" && !url.host.endsWith(".bilibili.com")) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.CALLER_NOT_AUTHORIZED,
                message = "b23.tv redirected outside the Bilibili site boundary.",
            )
        }
    }

    private fun requirePublicResourceUrl(url: HttpUrl) {
        val danmaku =
            url.host == "comment.bilibili.com" &&
                Regex("^/[0-9]+\\.xml$").matches(url.encodedPath)
        val subtitle =
            (url.host == "hdslb.com" || url.host.endsWith(".hdslb.com")) &&
                url.encodedPath.startsWith("/bfs/")
        if (!danmaku && !subtitle) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.CALLER_NOT_AUTHORIZED,
                message = "Bilibili public resource host or path is not allowed.",
            )
        }
    }
}

internal enum class BilibiliToolPkgRequestMode(val wireValue: String) {
    API("api"),
    API_ANONYMOUS("api_anonymous"),
    PUBLIC("public"),
    RESOLVE("resolve"),
}

internal data class BilibiliToolPkgRequest(
    val mode: BilibiliToolPkgRequestMode,
    val url: HttpUrl,
)

internal enum class BilibiliToolPkgErrorCode {
    INVALID_ARGUMENT,
    CALLER_NOT_AUTHORIZED,
    RISK_CONTROL,
    HTTP_ERROR,
    API_ERROR,
    NOT_LOGGED_IN,
    NOT_FOUND,
    ACCESS_RESTRICTED,
    RESPONSE_TOO_LARGE,
    CANCELLED,
    INTERNAL_ERROR,
}

internal class BilibiliToolPkgException(
    val code: BilibiliToolPkgErrorCode,
    override val message: String,
    val httpStatus: Int? = null,
    val apiCode: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    fun toJson(requestId: String?): JSONObject =
        JSONObject()
            .put("success", false)
            .put("request_id", requestId ?: JSONObject.NULL)
            .put(
                "error",
                JSONObject()
                    .put("code", code.name)
                    .put("message", message)
                    .put("http_status", httpStatus ?: JSONObject.NULL)
                    .put("api_code", apiCode ?: JSONObject.NULL),
            )
}

internal class BilibiliToolPkgGateway(
    private val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .applyKiyoriNetworkProxy(KiyoriNetworkModule.SCRIPTS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .build(),
) {
    fun execute(
        request: BilibiliToolPkgRequest,
        cookie: String?,
        requestId: String,
        onCallCreated: (Call) -> Unit,
    ): JSONObject =
        if (request.mode == BilibiliToolPkgRequestMode.RESOLVE) {
            resolveShortLink(request, requestId, onCallCreated)
        } else {
            fetch(request, cookie, requestId, onCallCreated)
        }

    private fun resolveShortLink(
        request: BilibiliToolPkgRequest,
        requestId: String,
        onCallCreated: (Call) -> Unit,
    ): JSONObject {
        var currentUrl = request.url
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val response = executeCall(currentUrl, null, onCallCreated)
            response.use {
                if (it.isRedirectResponse()) {
                    if (redirectCount >= MAX_REDIRECTS) {
                        throw BilibiliToolPkgException(
                            code = BilibiliToolPkgErrorCode.HTTP_ERROR,
                            message = "b23.tv redirect limit exceeded.",
                            httpStatus = it.code,
                        )
                    }
                    val next = resolveLocation(currentUrl, it)
                    BilibiliToolPkgContract.requireRedirectTarget(request.mode, next)
                    if (next.host != "b23.tv") {
                        return successEnvelope(
                            requestId = requestId,
                            status = it.code,
                            finalUrl = next,
                            contentType = null,
                            body = "",
                            cookieConfigured = false,
                        )
                    }
                    currentUrl = next
                    return@repeat
                }
                throw httpFailure(it, "b23.tv did not return a usable Bilibili redirect.")
            }
        }
        throw BilibiliToolPkgException(
            code = BilibiliToolPkgErrorCode.HTTP_ERROR,
            message = "b23.tv redirect limit exceeded.",
        )
    }

    private fun fetch(
        request: BilibiliToolPkgRequest,
        cookie: String?,
        requestId: String,
        onCallCreated: (Call) -> Unit,
    ): JSONObject {
        var currentUrl = request.url
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val requestCookie =
                cookie?.takeIf {
                    BilibiliToolPkgContract.shouldAttachCookie(request.mode, currentUrl)
                }
            val response = executeCall(currentUrl, requestCookie, onCallCreated)
            response.use {
                if (it.isRedirectResponse()) {
                    if (redirectCount >= MAX_REDIRECTS) {
                        throw BilibiliToolPkgException(
                            code = BilibiliToolPkgErrorCode.HTTP_ERROR,
                            message = "Bilibili redirect limit exceeded.",
                            httpStatus = it.code,
                        )
                    }
                    currentUrl =
                        BilibiliToolPkgContract.requireRedirectTarget(
                            request.mode,
                            resolveLocation(currentUrl, it),
                        )
                    return@repeat
                }
                if (!it.isSuccessful) {
                    throwHttpFailure(it)
                }
                val responseBody = readBoundedBody(it)
                if (
                    request.mode == BilibiliToolPkgRequestMode.API ||
                        request.mode == BilibiliToolPkgRequestMode.API_ANONYMOUS
                ) {
                    validateApiResponse(responseBody, it.code)
                }
                return successEnvelope(
                    requestId = requestId,
                    status = it.code,
                    finalUrl = currentUrl,
                    contentType = it.body?.contentType()?.toString(),
                    body = responseBody,
                    cookieConfigured = cookie != null,
                )
            }
        }
        throw BilibiliToolPkgException(
            code = BilibiliToolPkgErrorCode.HTTP_ERROR,
            message = "Bilibili redirect limit exceeded.",
        )
    }

    private fun executeCall(
        url: HttpUrl,
        cookie: String?,
        onCallCreated: (Call) -> Unit,
    ): Response {
        val requestBuilder =
            Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json,text/plain,application/xml,text/xml,*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
                .header("Referer", BILIBILI_REFERER)
                .header("User-Agent", BILIBILI_USER_AGENT)
        if (cookie != null) {
            requestBuilder.header("Cookie", cookie)
        }
        val call = httpClient.newCall(requestBuilder.build())
        onCallCreated(call)
        return call.execute()
    }

    private fun resolveLocation(currentUrl: HttpUrl, response: Response): HttpUrl {
        val rawLocation =
            response.header("Location")?.trim()?.takeIf(String::isNotEmpty)
                ?: throw BilibiliToolPkgException(
                    code = BilibiliToolPkgErrorCode.HTTP_ERROR,
                    message = "Bilibili redirect response did not include Location.",
                    httpStatus = response.code,
                )
        return currentUrl.resolve(rawLocation)
            ?: throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.HTTP_ERROR,
                message = "Bilibili redirect Location is invalid.",
                httpStatus = response.code,
            )
    }

    private fun Response.isRedirectResponse(): Boolean = code in 300..399

    private fun throwIfRiskControl(response: Response) {
        if (response.code == 412) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.RISK_CONTROL,
                message = "Bilibili risk control rejected the request. Stop and try again later.",
                httpStatus = response.code,
            )
        }
    }

    private fun throwHttpFailure(response: Response): Nothing {
        throwIfRiskControl(response)
        throw httpFailure(response, "Bilibili HTTP request failed.")
    }

    private fun httpFailure(response: Response, message: String): BilibiliToolPkgException {
        throwIfRiskControl(response)
        val code =
            when (response.code) {
                401, 403 -> BilibiliToolPkgErrorCode.ACCESS_RESTRICTED
                404 -> BilibiliToolPkgErrorCode.NOT_FOUND
                else -> BilibiliToolPkgErrorCode.HTTP_ERROR
            }
        return BilibiliToolPkgException(
            code = code,
            message = message + " HTTP " + response.code + ".",
            httpStatus = response.code,
        )
    }

    private fun readBoundedBody(response: Response): String {
        val body =
            response.body
                ?: throw BilibiliToolPkgException(
                    code = BilibiliToolPkgErrorCode.HTTP_ERROR,
                    message = "Bilibili response body is missing.",
                    httpStatus = response.code,
                )
        val declaredLength = body.contentLength()
        if (declaredLength > MAX_RESPONSE_BYTES) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.RESPONSE_TOO_LARGE,
                message = "Bilibili response exceeds the 16 MiB host limit.",
                httpStatus = response.code,
            )
        }
        val source = body.source()
        val buffer = Buffer()
        while (buffer.size <= MAX_RESPONSE_BYTES) {
            val remaining = MAX_RESPONSE_BYTES + 1L - buffer.size
            val read = source.read(buffer, minOf(8_192L, remaining))
            if (read == -1L) {
                break
            }
        }
        if (buffer.size > MAX_RESPONSE_BYTES) {
            throw BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.RESPONSE_TOO_LARGE,
                message = "Bilibili response exceeds the 16 MiB host limit.",
                httpStatus = response.code,
            )
        }
        return buffer.readByteArray().toString(Charsets.UTF_8)
    }

    private fun validateApiResponse(body: String, httpStatus: Int) {
        val payload =
            runCatching { JSONTokener(body).nextValue() as? JSONObject }.getOrNull()
                ?: throw BilibiliToolPkgException(
                    code = BilibiliToolPkgErrorCode.API_ERROR,
                    message = "Bilibili API returned invalid JSON.",
                    httpStatus = httpStatus,
                )
        val apiCode =
            (payload.opt("code") as? Number)?.toInt()
                ?: throw BilibiliToolPkgException(
                    code = BilibiliToolPkgErrorCode.API_ERROR,
                    message = "Bilibili API response did not contain a numeric code.",
                    httpStatus = httpStatus,
                )
        if (apiCode == 0) {
            return
        }
        val apiMessage =
            listOf(payload.optString("message"), payload.optString("msg"))
                .firstOrNull(String::isNotBlank)
                ?.take(240)
                ?: "Bilibili API request failed."
        val riskMessage = apiMessage.lowercase()
        val errorCode =
            when {
                apiCode == -352 ||
                    apiCode == -412 ||
                    "captcha" in riskMessage ||
                    "风控" in apiMessage -> BilibiliToolPkgErrorCode.RISK_CONTROL
                apiCode == -101 -> BilibiliToolPkgErrorCode.NOT_LOGGED_IN
                apiCode == -404 -> BilibiliToolPkgErrorCode.NOT_FOUND
                apiCode == -403 || apiCode == -10403 ->
                    BilibiliToolPkgErrorCode.ACCESS_RESTRICTED
                else -> BilibiliToolPkgErrorCode.API_ERROR
            }
        throw BilibiliToolPkgException(
            code = errorCode,
            message =
                if (errorCode == BilibiliToolPkgErrorCode.RISK_CONTROL) {
                    "Bilibili risk control rejected the request. Stop and try again later."
                } else {
                    "Bilibili API error $apiCode: $apiMessage"
                },
            httpStatus = httpStatus,
            apiCode = apiCode,
        )
    }

    private fun successEnvelope(
        requestId: String,
        status: Int,
        finalUrl: HttpUrl,
        contentType: String?,
        body: String,
        cookieConfigured: Boolean,
    ): JSONObject =
        JSONObject()
            .put("success", true)
            .put("request_id", requestId)
            .put("http_status", status)
            .put("final_url", finalUrl.toString())
            .put("content_type", contentType ?: JSONObject.NULL)
            .put("body", body)
            .put("cookie_configured", cookieConfigured)

    private companion object {
        private const val MAX_REDIRECTS = 5
        private const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
        private const val BILIBILI_REFERER = "https://www.bilibili.com/"
        private const val BILIBILI_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Kiyori) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"
    }
}

internal class ToolPkgBilibiliBridge(
    context: Context,
    private val boundToolPkgContainerName: String?,
    private val isExecutionCallActive: (String) -> Boolean,
    private val environmentRepository: ToolPkgHostEnvironmentRepository =
        ToolPkgHostEnvironmentRepository.getInstance(context.applicationContext),
    private val gateway: BilibiliToolPkgGateway = BilibiliToolPkgGateway(),
    private val bridgeScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private data class ActiveRequest(
        val callId: String,
        val job: Job,
        val currentCall: AtomicReference<Call?>,
    )

    private val requests = ConcurrentHashMap<String, ActiveRequest>()
    private val requestMutex = Mutex()
    private var lastRequestStartedAtNanos = 0L

    fun request(
        callId: String,
        requestJson: String,
        deliverResult: (String) -> Unit,
    ) {
        val normalizedCallId =
            runCatching {
                BilibiliToolPkgContract.requireAuthorizedCaller(
                    boundToolPkgContainerName = boundToolPkgContainerName,
                    callId = callId,
                    isExecutionCallActive = isExecutionCallActive,
                )
            }.getOrElse { error ->
                deliverResult(error.toBilibiliEnvelope(requestId = null).toString())
                return
            }
        val requestId = "bili_" + UUID.randomUUID().toString().replace("-", "")
        val parsedRequest =
            runCatching { BilibiliToolPkgContract.parseRequest(requestJson) }.getOrElse { error ->
                deliverResult(error.toBilibiliEnvelope(requestId).toString())
                return
            }
        val currentCall = AtomicReference<Call?>(null)
        lateinit var job: Job
        job =
            bridgeScope.launch(start = CoroutineStart.LAZY) {
                val result =
                    try {
                        requestMutex.withLock {
                            awaitRequestSpacing()
                            lastRequestStartedAtNanos = System.nanoTime()
                            val cookie =
                                BilibiliToolPkgContract.validateCookie(
                                    environmentRepository.getValue(
                                        BilibiliToolPkgContract.TOOLPKG_ID,
                                        BilibiliToolPkgContract.COOKIE_VARIABLE,
                                    )
                                )
                            gateway.execute(
                                request = parsedRequest,
                                cookie = cookie,
                                requestId = requestId,
                                onCallCreated = currentCall::set,
                            )
                        }
                    } catch (error: Throwable) {
                        if (error !is BilibiliToolPkgException && error !is CancellationException) {
                            AppLogger.e(
                                TAG,
                                "Bilibili host request failed: " +
                                    (error.message ?: error::class.java.name),
                                error,
                            )
                        }
                        error.toBilibiliEnvelope(requestId)
                    } finally {
                        requests.remove(requestId)
                    }
                if (isExecutionCallActive(normalizedCallId)) {
                    deliverResult(result.toString())
                }
            }
        requests[requestId] =
            ActiveRequest(
                callId = normalizedCallId,
                job = job,
                currentCall = currentCall,
            )
        if (!isExecutionCallActive(normalizedCallId)) {
            cancelForCall(
                callId = normalizedCallId,
                reason = "Bilibili execution call ended before request dispatch.",
            )
            return
        }
        job.start()
    }

    fun cancelForCall(callId: String, reason: String): Int {
        val normalizedCallId = callId.trim()
        val owned = requests.values.filter { it.callId == normalizedCallId }
        owned.forEach { active ->
            active.currentCall.get()?.cancel()
            active.job.cancel(CancellationException(reason))
        }
        return owned.size
    }

    fun close(reason: String) {
        requests.values.forEach { active ->
            active.currentCall.get()?.cancel()
            active.job.cancel(CancellationException(reason))
        }
        requests.clear()
        bridgeScope.cancel(reason)
    }

    private suspend fun awaitRequestSpacing() {
        val previous = lastRequestStartedAtNanos
        if (previous == 0L) {
            return
        }
        val elapsed = System.nanoTime() - previous
        val remaining = MIN_REQUEST_INTERVAL_NANOS - elapsed
        if (remaining > 0L) {
            delay(TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1L))
        }
    }

    private companion object {
        private const val TAG = "ToolPkgBilibiliBridge"
        private val MIN_REQUEST_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(350)
    }
}

private fun Throwable.toBilibiliEnvelope(requestId: String?): JSONObject =
    when (this) {
        is BilibiliToolPkgException -> toJson(requestId)
        is CancellationException ->
            BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.CANCELLED,
                message = "Bilibili request was cancelled.",
                cause = this,
            ).toJson(requestId)
        is SocketTimeoutException ->
            BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.HTTP_ERROR,
                message = "Bilibili request timed out.",
                cause = this,
            ).toJson(requestId)
        is IOException ->
            BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.HTTP_ERROR,
                message = "Bilibili network request failed.",
                cause = this,
            ).toJson(requestId)
        else ->
            BilibiliToolPkgException(
                code = BilibiliToolPkgErrorCode.INTERNAL_ERROR,
                message = "Bilibili host service failed.",
                cause = this,
            ).toJson(requestId)
    }
