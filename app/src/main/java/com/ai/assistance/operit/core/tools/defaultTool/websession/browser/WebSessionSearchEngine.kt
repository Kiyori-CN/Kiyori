package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.net.Uri
import androidx.annotation.DrawableRes
import com.ai.assistance.operit.R

internal enum class WebSessionSearchEngine(
    val id: String,
    val displayName: String,
    private val searchUrlPrefix: String,
    @DrawableRes val iconResId: Int,
) {
    BAIDU(
        "baidu",
        "百度",
        "https://www.baidu.com/s?wd=",
        R.drawable.ic_kiyori_search_engine_baidu,
    ),
    SOGOU(
        "sogou",
        "搜狗",
        "https://www.sogou.com/web?query=",
        R.drawable.ic_kiyori_search_engine_sogou,
    ),
    BING(
        "bing",
        "必应",
        "https://www.bing.com/search?q=",
        R.drawable.ic_kiyori_search_engine_bing,
    ),
    SO360(
        "so360",
        "360搜索",
        "https://www.so.com/s?q=",
        R.drawable.ic_kiyori_search_engine_so360,
    ),
    QUARK(
        "quark",
        "夸克",
        "https://quark.sm.cn/s?q=",
        R.drawable.ic_kiyori_search_engine_quark,
    ),
    GOOGLE(
        "google",
        "谷歌",
        "https://www.google.com/search?q=",
        R.drawable.ic_kiyori_search_engine_google,
    ),
    BRAVE(
        "brave",
        "Brave",
        "https://search.brave.com/search?q=",
        R.drawable.ic_kiyori_search_engine_brave,
    ),
    YANDEX(
        "yandex",
        "Yandex",
        "https://yandex.com/search/?text=",
        R.drawable.ic_kiyori_search_engine_yandex,
    ),
    DUCKDUCKGO(
        "duckduckgo",
        "DuckDuckGo",
        "https://duckduckgo.com/?q=",
        R.drawable.ic_kiyori_search_engine_duckduckgo,
    ),
    ;

    fun buildSearchUrl(query: String): String = searchUrlPrefix + Uri.encode(query)

    companion object {
        val DEFAULT: WebSessionSearchEngine = BAIDU

        fun fromId(id: String?): WebSessionSearchEngine =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
