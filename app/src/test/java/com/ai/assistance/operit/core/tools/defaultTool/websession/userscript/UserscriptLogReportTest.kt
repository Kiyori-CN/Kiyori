package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptLogReportTest {
    @Test
    fun `full report is chronological and includes script identity and page`() {
        val older =
            UserscriptLogItem(
                id = 1L,
                userscriptId = 7L,
                level = "info",
                message = "old message",
                pageUrl = "https://example.com/old",
                createdAt = 1_000L,
            )
        val newer =
            older.copy(
                id = 2L,
                message = "new message",
                pageUrl = "https://example.com/new",
                createdAt = 2_000L,
            )

        val report =
            buildUserscriptLogReport(
                logs = listOf(newer, older),
                scripts =
                    listOf(
                        testScript(7L, "Example userscript"),
                    ),
                generatedAt = 3_000L,
            )

        assertTrue(report.indexOf("old message") < report.indexOf("new message"))
        assertTrue(report.contains("Script: Example userscript (id=7)"))
        assertTrue(report.contains("Page: https://example.com/new"))
        assertTrue(report.contains("Entries: 2"))
    }

    @Test
    fun `single entry report preserves copied script name`() {
        val report =
            buildUserscriptLogEntryReport(
                log =
                    UserscriptLogItem(
                        id = 9L,
                        userscriptId = 11L,
                        level = "error",
                        message = "failed",
                        pageUrl = null,
                        createdAt = 4_000L,
                    ),
                scriptName = "Broken script",
            )

        assertTrue(report.contains("Script: Broken script (id=11)"))
        assertTrue(report.contains("Message:\nfailed"))
    }

    private fun testScript(
        id: Long,
        name: String,
    ): UserscriptListItem =
        UserscriptListItem(
            id = id,
            name = name,
            namespace = null,
            version = "1.0.0",
            description = null,
            sourceDisplay = null,
            enabled = true,
            unknownGrants = emptyList(),
            blockedReasons = emptyList(),
            executionWorld = null,
            unsafeWindowMode = UserscriptUnsafeWindowMode.NONE,
            runAt = UserscriptRunAt.DOCUMENT_END,
            grants = emptyList(),
            matches = emptyList(),
            includes = emptyList(),
            excludes = emptyList(),
            excludeMatches = emptyList(),
            connects = emptyList(),
            requires = emptyList(),
            resources = emptyList(),
            homepage = null,
            website = null,
            supportUrl = null,
            icons = UserscriptIconSet(),
            tags = emptyList(),
            injectInto = UserscriptInjectInto.AUTO,
            sandbox = null,
            runIn = null,
            noFrames = false,
            unwrap = false,
            webRequestRules = emptyList(),
            sourceUrl = null,
            updateUrl = null,
            downloadUrl = null,
            installedAt = 1L,
            updatedAt = 1L,
        )
}
