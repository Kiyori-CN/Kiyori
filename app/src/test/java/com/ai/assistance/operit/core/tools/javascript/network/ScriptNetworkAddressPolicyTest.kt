package com.ai.assistance.operit.core.tools.javascript.network

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptNetworkAddressPolicyTest {
    @Test
    fun `loopback is always direct`() {
        assertTrue(shouldUseDirectConnection("localhost", proxyPrivateNetworks = true))
        assertTrue(shouldUseDirectConnection("127.0.0.1", proxyPrivateNetworks = true))
        assertTrue(shouldUseDirectConnection("127.1", proxyPrivateNetworks = true))
        assertTrue(shouldUseDirectConnection("2130706433", proxyPrivateNetworks = true))
        assertTrue(shouldUseDirectConnection("::1", proxyPrivateNetworks = true))
        assertTrue(shouldUseDirectConnection("::ffff:127.0.0.1", proxyPrivateNetworks = true))
    }

    @Test
    fun `private addresses follow the explicit private network switch`() {
        listOf("10.0.0.1", "172.16.3.4", "192.168.1.9", "169.254.3.2", "fd00::1").forEach { host ->
            assertTrue(shouldUseDirectConnection(host, proxyPrivateNetworks = false))
            assertFalse(shouldUseDirectConnection(host, proxyPrivateNetworks = true))
        }
    }

    @Test
    fun `public destinations use the selected proxy`() {
        assertFalse(shouldUseDirectConnection("example.com", proxyPrivateNetworks = false))
        assertFalse(shouldUseDirectConnection("8.8.8.8", proxyPrivateNetworks = false))
        assertFalse(shouldUseDirectConnection("2001:4860:4860::8888", proxyPrivateNetworks = false))
    }

    @Test
    fun `scoped selector returns exactly one deterministic route`() {
        val configuredProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example", 7890))
        val selector = ScopedScriptProxySelector(configuredProxy, proxyPrivateNetworks = false)

        assertEquals(listOf(configuredProxy), selector.select(URI("https://example.com/path")))
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(URI("http://127.0.0.1/local")))
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(URI("http://192.168.1.8/local")))
    }
}
