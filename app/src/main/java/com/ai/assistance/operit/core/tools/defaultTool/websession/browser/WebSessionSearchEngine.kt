package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.net.Uri

internal enum class WebSessionSearchEngine(
    val id: String,
    val displayName: String,
    private val searchUrlPrefix: String,
) {
    BAIDU("baidu", "百度", "https://www.baidu.com/s?wd="),
    SOGOU("sogou", "搜狗", "https://www.sogou.com/web?query="),
    BING("bing", "必应", "https://www.bing.com/search?q="),
    SO360("so360", "360搜索", "https://www.so.com/s?q="),
    QUARK("quark", "夸克", "https://quark.sm.cn/s?q="),
    GOOGLE("google", "谷歌", "https://www.google.com/search?q="),
    BRAVE("brave", "Brave", "https://search.brave.com/search?q="),
    YANDEX("yandex", "Yandex", "https://yandex.com/search/?text="),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
    ;

    fun buildSearchUrl(query: String): String = searchUrlPrefix + Uri.encode(query)

    companion object {
        val DEFAULT: WebSessionSearchEngine = BAIDU

        fun fromId(id: String?): WebSessionSearchEngine =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

