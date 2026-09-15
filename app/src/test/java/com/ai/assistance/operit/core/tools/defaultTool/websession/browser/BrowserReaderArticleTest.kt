package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserReaderArticleTest {
    private fun wrapped(payload: String): String =
        org.json.JSONObject().put("v", payload).toString().removePrefix("{\"v\":").removeSuffix("}")

    @Test
    fun `script result is unwrapped once before parsing`() {
        val payload = """{"blocks":[{"k":"h2","x":"小节"},{"k":"p","x":"正文"}],"characters":4,"truncated":false,"title":"标题","byline":"作者"}"""
        val article = parseBrowserReaderArticle(wrapped(payload), "回退标题", "example.com")

        assertFalse(article.failed)
        assertEquals("标题", article.title)
        assertEquals("作者", article.byline)
        assertEquals("example.com", article.siteLabel)
        assertEquals(
            listOf(BrowserReaderBlockKind.HEADING_2, BrowserReaderBlockKind.PARAGRAPH),
            article.blocks.map { it.kind },
        )
        assertEquals(listOf("小节", "正文"), article.blocks.map { it.text })
    }

    @Test
    fun `plain object result is also accepted`() {
        val article = parseBrowserReaderArticle(
            """{"blocks":[{"k":"p","x":"正文"}],"characters":2,"truncated":true,"title":"","byline":""}""",
            "回退标题",
            "example.com",
        )

        assertFalse(article.failed)
        assertTrue(article.truncated)
        assertEquals("回退标题", article.title)
    }

    /** 脚本被页面环境拒绝与页面确实没有正文，用户的下一步不同，解析结果必须能分辨。 */
    @Test
    fun `rejected script is reported as failure not as empty article`() {
        for (raw in listOf(null, "", "   ", "null", "not-json")) {
            val article = parseBrowserReaderArticle(raw, "回退标题", "example.com")
            assertTrue(raw.toString(), article.failed)
            assertTrue(raw.toString(), article.isEmpty)
        }

        val empty = parseBrowserReaderArticle(
            """{"blocks":[],"characters":0,"truncated":false,"title":"标题","byline":""}""",
            "回退标题",
            "example.com",
        )
        assertFalse(empty.failed)
        assertTrue(empty.isEmpty)
    }

    @Test
    fun `blank blocks are dropped and unknown kinds fall back to paragraph`() {
        val article = parseBrowserReaderArticle(
            """{"blocks":[{"k":"p","x":"  "},{"k":"unknown","x":" 内容 "}],"characters":2,"truncated":false,"title":"标题","byline":""}""",
            "回退标题",
            "example.com",
        )

        assertEquals(1, article.blocks.size)
        assertEquals(BrowserReaderBlockKind.PARAGRAPH, article.blocks.single().kind)
        assertEquals("内容", article.blocks.single().text)
    }

    @Test
    fun `reading time never reports zero minutes`() {
        val article = parseBrowserReaderArticle(
            """{"blocks":[{"k":"p","x":"短"}],"characters":1,"truncated":false,"title":"标题","byline":""}""",
            "回退标题",
            "example.com",
        )
        assertEquals(1, article.estimatedMinutes)
        assertEquals(3, article.copy(characterCount = 1000).estimatedMinutes)
    }

    @Test
    fun `speech text joins title and blocks without blank lines`() {
        val article = BrowserReaderArticle(
            title = "标题",
            byline = "",
            siteLabel = "example.com",
            blocks = listOf(
                BrowserReaderBlock(BrowserReaderBlockKind.PARAGRAPH, "第一段"),
                BrowserReaderBlock(BrowserReaderBlockKind.PARAGRAPH, "第二段"),
            ),
            characterCount = 6,
            truncated = false,
            failed = false,
        )
        assertEquals("标题\n第一段\n第二段", browserReaderSpeechText(article))
    }

    @Test
    fun `font scale index snaps arbitrary stored values to a usable step`() {
        assertEquals(BROWSER_READER_FONT_SCALES.indexOf(1.0f), browserReaderFontScaleIndex(1.0f))
        assertEquals(0, browserReaderFontScaleIndex(0.1f))
        assertEquals(BROWSER_READER_FONT_SCALES.lastIndex, browserReaderFontScaleIndex(9f))
        assertEquals(BROWSER_READER_FONT_SCALES.indexOf(1.15f), browserReaderFontScaleIndex(1.13f))
    }

    @Test
    fun `extraction script carries the documented character limit`() {
        assertTrue(BROWSER_READER_EXTRACTION_SCRIPT.contains("const LIMIT = $BROWSER_READER_MAX_CHARACTERS;"))
        assertEquals(120_000, BROWSER_READER_MAX_CHARACTERS)
    }
}
