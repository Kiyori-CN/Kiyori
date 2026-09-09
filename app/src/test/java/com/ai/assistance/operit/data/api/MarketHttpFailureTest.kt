package com.ai.assistance.operit.data.api

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class MarketHttpFailureTest {
    @Test fun `only known rejection permits a registration retry`() {
        assertTrue(canRetryRejectedMarketRegistration(MarketHttpFailure(429, "rate limited")))
        assertTrue(canRetryRejectedMarketRegistration(MarketSubmissionNotStarted("account changed")))
        for (code in listOf(302, 408, 409, 500, 502, 503, 504)) {
            assertFalse("HTTP $code", canRetryRejectedMarketRegistration(MarketHttpFailure(code, "unconfirmed")))
        }
        assertFalse(canRetryRejectedMarketRegistration(IOException("connection interrupted")))
    }

    @Test fun `redirect is not successful mutation but explicit download tracking can accept it`() {
        for (code in listOf(301, 302, 303, 307, 308)) {
            assertFalse(isAcceptedMarketResponse(code, allowDownloadRedirect = false))
            assertTrue(isAcceptedMarketResponse(code, allowDownloadRedirect = true))
        }
        assertTrue(isAcceptedMarketResponse(204, allowDownloadRedirect = false))
        assertFalse(isAcceptedMarketResponse(500, allowDownloadRedirect = true))
    }
}
