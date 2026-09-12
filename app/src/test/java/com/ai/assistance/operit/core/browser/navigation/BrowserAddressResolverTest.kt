package com.ai.assistance.operit.core.browser.navigation

import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAddressResolverTest {
    @Test
    fun `local path encoding preserves literal percent unicode and URI delimiters`() {
        val path = "/storage/emulated/0/下载/100%23 [报告] #?.html"
        val resolved = BrowserAddressResolver.resolve(path, WebSessionSearchEngine.GOOGLE)
        assertEquals(path, java.net.URI(resolved).path)
        assertEquals(null, java.net.URI(resolved).fragment)
        assertEquals(null, java.net.URI(resolved).query)
        assertTrue(resolved.contains("100%2523"))
        assertFalse(BrowserAddressResolver.isSearchQuery(path))
        assertEquals(resolved, BrowserAddressResolver.resolve(resolved, WebSessionSearchEngine.GOOGLE))
    }
    @Test
    fun `selected search engine identity is stable`() {
        assertEquals(
            "bing",
            WebSessionSearchEngine.BING.id,
        )
    }

    @Test
    fun `explicit address bypasses search engine`() {
        assertEquals(
            "https://example.com/path",
            BrowserAddressResolver.resolve("https://example.com/path", WebSessionSearchEngine.GOOGLE),
        )
    }

    @Test
    fun `quick switch bar eligibility only accepts text searches`() {
        assertTrue(BrowserAddressResolver.isSearchQuery("我的 世界"))
        assertTrue(BrowserAddressResolver.isSearchQuery("我的"))
        assertFalse(BrowserAddressResolver.isSearchQuery(""))
        assertFalse(BrowserAddressResolver.isSearchQuery("https://example.com/path"))
        assertFalse(BrowserAddressResolver.isSearchQuery("example.com/path"))
        assertFalse(BrowserAddressResolver.isSearchQuery("localhost:8080"))
    }

    // D-local-file: file:// 与不带 scheme 的绝对路径必须直接当作本地文件地址，不能被误判为
    // 搜索词交给搜索引擎——这曾是"浏览器无法打开本地 HTML 文件"的根因之一。
    @Test
    fun `explicit file url bypasses search engine`() {
        assertEquals(
            "file:///storage/emulated/0/Download/test.html",
            BrowserAddressResolver.resolve(
                "file:///storage/emulated/0/Download/test.html",
                WebSessionSearchEngine.GOOGLE,
            ),
        )
        assertFalse(
            BrowserAddressResolver.isSearchQuery("file:///storage/emulated/0/Download/test.html"),
        )
    }

    @Test
    fun `explicit content url bypasses search engine`() {
        assertEquals(
            "content://com.android.providers.downloads.documents/document/1",
            BrowserAddressResolver.resolve(
                "content://com.android.providers.downloads.documents/document/1",
                WebSessionSearchEngine.GOOGLE,
            ),
        )
    }

    @Test
    fun `bare absolute path is resolved as a local file url`() {
        assertEquals(
            "file:///storage/emulated/0/Download/test.html",
            BrowserAddressResolver.resolve("/storage/emulated/0/Download/test.html", WebSessionSearchEngine.GOOGLE),
        )
        assertFalse(BrowserAddressResolver.isSearchQuery("/storage/emulated/0/Download/test.html"))
    }

    @Test
    fun `bare absolute path with spaces is percent encoded`() {
        assertEquals(
            "file:///storage/emulated/0/Download/my%20notes.html",
            BrowserAddressResolver.resolve("/storage/emulated/0/Download/my notes.html", WebSessionSearchEngine.GOOGLE),
        )
    }

    @Test
    fun `relative looking text is still treated as a search query`() {
        // 以 "/" 开头才判定为本地路径；不以 "/" 开头、也不像域名的普通文本仍应走搜索引擎。
        assertTrue(BrowserAddressResolver.isSearchQuery("拆分/合并 pdf 教程"))
    }
}
