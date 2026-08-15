package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

internal enum class BrowserAdBlockSubscriptionGroup {
    PRO,
    ADBLOCK_PLUS,
    CUSTOM,
}

internal data class BrowserAdBlockBuiltInSubscriptionDefinition(
    val id: String,
    val name: String,
    val url: String,
    val group: BrowserAdBlockSubscriptionGroup,
    val refreshIntervalMillis: Long,
)

internal val BrowserAdBlockBuiltInSubscriptions =
    listOf(
        BrowserAdBlockBuiltInSubscriptionDefinition(
            id = "builtin-pro-my-adfilters",
            name = "My AdFilters",
            url = "https://cdn.jsdelivr.net/gh/xun404/adblock@main/ad.txt",
            group = BrowserAdBlockSubscriptionGroup.PRO,
            refreshIntervalMillis = 12L * 60L * 60L * 1_000L,
        ),
        BrowserAdBlockBuiltInSubscriptionDefinition(
            id = "builtin-pro-adguard-chinese",
            name = "AdGuard 中文过滤器",
            url = "https://filters.adtidy.org/extension/chromium/filters/224.txt",
            group = BrowserAdBlockSubscriptionGroup.PRO,
            refreshIntervalMillis = 24L * 60L * 60L * 1_000L,
        ),
        BrowserAdBlockBuiltInSubscriptionDefinition(
            id = "builtin-pro-adrules-lite",
            name = "AdRules Lite",
            url = "https://adrules.top/adblock_lite.txt",
            group = BrowserAdBlockSubscriptionGroup.PRO,
            refreshIntervalMillis = 24L * 60L * 60L * 1_000L,
        ),
        BrowserAdBlockBuiltInSubscriptionDefinition(
            id = "builtin-abp-easylist-china",
            name = "EasyList China + EasyList",
            url = "https://easylist-downloads.adblockplus.org/easylistchina+easylist.txt",
            group = BrowserAdBlockSubscriptionGroup.ADBLOCK_PLUS,
            refreshIntervalMillis = 24L * 60L * 60L * 1_000L,
        ),
        BrowserAdBlockBuiltInSubscriptionDefinition(
            id = "builtin-abp-acceptable-ads",
            name = "可接受广告例外规则",
            url = "https://easylist-downloads.adblockplus.org/exceptionrules.txt",
            group = BrowserAdBlockSubscriptionGroup.ADBLOCK_PLUS,
            refreshIntervalMillis = 24L * 60L * 60L * 1_000L,
        ),
    )

internal fun browserAdBlockBuiltInSubscriptionDefinition(
    id: String,
): BrowserAdBlockBuiltInSubscriptionDefinition? =
    BrowserAdBlockBuiltInSubscriptions.singleOrNull { definition -> definition.id == id }
