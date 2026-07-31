package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import java.time.Instant

internal fun buildUserscriptLogReport(
    logs: List<UserscriptLogItem>,
    scripts: List<UserscriptListItem>,
    generatedAt: Long = System.currentTimeMillis(),
): String =
    renderUserscriptLogReport(
        logs = logs,
        scriptNames = scripts.associate { script -> script.id to script.name },
        generatedAt = generatedAt,
    )

internal fun buildUserscriptLogEntryReport(
    log: UserscriptLogItem,
    scriptName: String?,
): String =
    renderUserscriptLogReport(
        logs = listOf(log),
        scriptNames =
            if (log.userscriptId != null && scriptName != null) {
                mapOf(log.userscriptId to scriptName)
            } else {
                emptyMap()
            },
        generatedAt = log.createdAt,
    )

private fun renderUserscriptLogReport(
    logs: List<UserscriptLogItem>,
    scriptNames: Map<Long, String>,
    generatedAt: Long,
): String =
    buildString {
        appendLine("Kiyori Userscript Log Report")
        appendLine("Generated: ${Instant.ofEpochMilli(generatedAt)}")
        appendLine("Entries: ${logs.size}")
        appendLine("Privacy: log messages and page URLs are user-visible script data.")
        appendLine()
        logs
            .asReversed()
            .forEachIndexed { index, log ->
                appendLine("========================================")
                appendLine("Entry: ${index + 1}/${logs.size}  id=${log.id}")
                appendLine("Time: ${Instant.ofEpochMilli(log.createdAt)}")
                appendLine("Level: ${log.level}")
                appendLine(
                    "Script: " +
                        (log.userscriptId?.let(scriptNames::get) ?: "Unknown") +
                        (log.userscriptId?.let { " (id=$it)" } ?: ""),
                )
                appendLine("Page: ${log.pageUrl ?: "-"}")
                appendLine("Message:")
                appendLine(log.message)
                appendLine()
            }
    }.trimEnd()
