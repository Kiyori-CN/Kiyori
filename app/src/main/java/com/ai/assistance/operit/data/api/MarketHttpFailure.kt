package com.ai.assistance.operit.data.api

/** 保留收到的 HTTP 状态，不把传输中断或服务器执行错误解释成未提交。 */
internal class MarketHttpFailure(val statusCode: Int, message: String) : IllegalStateException(message)
internal class MarketSubmissionNotStarted(message: String) : IllegalStateException(message)

internal fun canRetryRejectedMarketRegistration(error: Throwable): Boolean =
    error is MarketSubmissionNotStarted ||
        (error is MarketHttpFailure && error.statusCode in setOf(400, 401, 403, 404, 405, 413, 415, 422, 429))

internal fun isAcceptedMarketResponse(statusCode: Int, allowDownloadRedirect: Boolean): Boolean =
    statusCode in 200..299 || (allowDownloadRedirect && statusCode in 300..399)
