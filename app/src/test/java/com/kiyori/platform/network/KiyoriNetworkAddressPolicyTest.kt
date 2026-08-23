package com.kiyori.platform.network

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriNetworkAddressPolicyTest {
    @Test
    fun `loopback is always direct`() {
        assertTrue(shouldBypassKiyoriProxy("localhost", proxyPrivateNetworks = true))
        assertTrue(shouldBypassKiyoriProxy("127.0.0.1", proxyPrivateNetworks = true))
        assertTrue(shouldBypassKiyoriProxy("::1", proxyPrivateNetworks = true))
    }

    @Test
    fun `private addresses follow explicit switch`() {
        assertTrue(shouldBypassKiyoriProxy("192.168.1.9", proxyPrivateNetworks = false))
        assertFalse(shouldBypassKiyoriProxy("192.168.1.9", proxyPrivateNetworks = true))
        assertTrue(shouldBypassKiyoriProxy("fd00::1", proxyPrivateNetworks = false))
        assertFalse(shouldBypassKiyoriProxy("fd00::1", proxyPrivateNetworks = true))
    }

    @Test
    fun `scoped selector returns one deterministic route`() {
        val configuredProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example", 7890))
        val selector = ScopedKiyoriProxySelector(configuredProxy, proxyPrivateNetworks = false)
        assertEquals(listOf(configuredProxy), selector.select(URI("https://example.com/path")))
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(URI("http://127.0.0.1/local")))
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(URI("http://192.168.1.8/local")))
    }

    @Test
    fun `explicit URL connections use the same private-network bypass contract`() {
        assertTrue(shouldBypassKiyoriProxy("127.0.0.1", proxyPrivateNetworks = true))
        assertTrue(shouldBypassKiyoriProxy("192.168.1.8", proxyPrivateNetworks = false))
        assertFalse(shouldBypassKiyoriProxy("192.168.1.8", proxyPrivateNetworks = true))
    }
}
