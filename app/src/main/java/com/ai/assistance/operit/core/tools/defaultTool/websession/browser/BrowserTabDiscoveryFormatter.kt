package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

internal data class BrowserTabDiscoveryEntry(
    val index: Int,
    val sessionId: String,
    val title: String,
    val url: String,
    val isActive: Boolean,
    val profile: WebSessionProfile,
)

internal object BrowserTabDiscoveryFormatter {
    fun render(entries: List<BrowserTabDiscoveryEntry>): String {
        if (entries.isEmpty()) {
            return "No open tabs."
        }

        return buildString {
            appendLine(
                "Shared Browser Runtime: these are the same tabs used by Kiyori UI and AI browser tools."
            )
            entries.forEachIndexed { entryIndex, entry ->
                val active = if (entry.isActive) " [active]" else ""
                appendLine("- [${entry.index}] ${entry.title.singleLine()}$active")
                appendLine("  session_id: ${entry.sessionId}")
                appendLine("  profile: ${entry.profile.wireName}")
                append("  url: ${entry.url.singleLine()}")
                if (entryIndex != entries.lastIndex) {
                    appendLine()
                }
            }
        }
    }

    private fun String.singleLine(): String =
        replace('\r', ' ').replace('\n', ' ').trim()
}
