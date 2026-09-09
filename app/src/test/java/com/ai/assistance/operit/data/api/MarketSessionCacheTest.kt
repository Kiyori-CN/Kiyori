package com.ai.assistance.operit.data.api

import org.junit.Assert.*
import org.junit.Test

class MarketSessionCacheTest {
    @Test fun `same credentials share one authentication and account change does not reuse it`() {
        val cache = MarketSessionCache()
        var credential = "account-a"
        var count = 0
        val authenticate: (String) -> String = { count++; "session-$it" }
        assertEquals("session-account-a", cache.get({ credential }, authenticate))
        assertEquals("session-account-a", cache.get({ credential }, authenticate))
        credential = "account-b"
        assertEquals("session-account-b", cache.get({ credential }, authenticate))
        assertEquals(2, count)
    }

    @Test fun `logout rejects cached session and requires fresh authentication after login`() {
        val cache = MarketSessionCache()
        var credential: String? = "account-a"
        var count = 0
        val authenticate: (String) -> String = { count++; "session" }
        cache.get({ credential }, authenticate)
        credential = null
        try { cache.get({ credential }, authenticate); fail("Cached login survived logout") }
        catch (_: IllegalStateException) { }
        assertEquals(1, count)
        credential = "account-a"
        cache.get({ credential }, authenticate)
        assertEquals(2, count)
    }

    @Test fun `account switch during authentication never publishes old session`() {
        val cache = MarketSessionCache()
        var credential = "account-a"
        try {
            cache.get({ credential }) { credential = "account-b"; "old-session" }
            fail("Old account session was returned")
        } catch (_: IllegalStateException) { }
        assertEquals("new-session", cache.get({ credential }) { assertEquals("account-b", it); "new-session" })
    }

    @Test fun `failed authentication never returns previously cached account`() {
        val cache = MarketSessionCache()
        cache.get({ "account-a" }) { "old-session" }
        try { cache.get({ "account-b" }) { error("offline") }; fail("Failure was swallowed") }
        catch (_: IllegalStateException) { }
        var count = 0
        assertEquals("new-session", cache.get({ "account-a" }) { count++; "new-session" })
        assertEquals(1, count)
    }
}
