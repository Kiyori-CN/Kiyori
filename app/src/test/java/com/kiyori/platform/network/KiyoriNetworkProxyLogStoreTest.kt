package com.kiyori.platform.network

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KiyoriNetworkProxyLogStoreTest {
    @Before
    fun setUp() {
        KiyoriNetworkProxyLogStore.clear()
    }

    @After
    fun tearDown() {
        KiyoriNetworkProxyLogStore.clear()
    }

    @Test
    fun `redactor removes credentials urls and private paths while keeping failure reason`() {
        val secret = "0123456789abcdef0123456789abcdef0123456789abcdef"
        val raw =
            "failed to load geosite from /data/user/0/com.kiyori/files/private/config.yaml " +
                "url=https://user:pass@example.com/private/subscription?token=$secret " +
                "Bearer $secret password=hunter2 uuid=00000000-0000-4000-8000-000000000000 " +
                "vless://$secret@example.com:443"

        val redacted = KiyoriNetworkProxyLogStore.redact(raw)

        assertTrue(redacted.contains("failed to load geosite"))
        assertTrue(redacted.contains("https://example.com/[redacted]"))
        assertTrue(redacted.contains("[private-path]"))
        assertTrue(redacted.contains("Bearer [redacted]"))
        assertTrue(redacted.contains("password=[redacted]"))
        assertTrue(redacted.contains("uuid=[redacted]"))
        assertTrue(redacted.contains("[proxy-uri-redacted]"))
        assertFalse(redacted.contains(secret))
        assertFalse(redacted.contains("hunter2"))
        assertFalse(redacted.contains("user:pass"))
    }

    @Test
    fun `log history is bounded exportable and clearable`() {
        repeat(340) { index -> KiyoriNetworkProxyLogStore.info("test", "entry-$index") }

        val entries = KiyoriNetworkProxyLogStore.entries.value
        assertEquals(300, entries.size)
        assertEquals("entry-40", entries.first().message)
        assertEquals("entry-339", entries.last().message)
        assertTrue(KiyoriNetworkProxyLogStore.exportText().contains("[INFO] [test] entry-339"))

        KiyoriNetworkProxyLogStore.clear()
        assertTrue(KiyoriNetworkProxyLogStore.entries.value.isEmpty())
    }
}
