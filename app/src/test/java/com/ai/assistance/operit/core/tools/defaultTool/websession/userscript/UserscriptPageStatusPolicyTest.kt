package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptPageStatusPolicyTest {
    @Test
    fun `missing active page is not reported as not matched`() {
        val status =
            UserscriptPageStatusPolicy.resolve(
                script = userscript(),
                userScriptsAllowed = true,
                pageUrl = null,
            )

        assertEquals(UserscriptPageRuntimeState.NO_ACTIVE_PAGE, status.state)
    }

    @Test
    fun `matching and excluded pages retain diagnostic detail`() {
        val script =
            userscript().copy(
                matches = listOf("http*://*.example.com/*"),
                excludeMatches = listOf("https://private.example.com/*"),
            )

        val matched =
            UserscriptPageStatusPolicy.resolve(
                script = script,
                userScriptsAllowed = true,
                pageUrl = "https://www.example.com/path",
            )
        val excluded =
            UserscriptPageStatusPolicy.resolve(
                script = script,
                userScriptsAllowed = true,
                pageUrl = "https://private.example.com/path",
            )

        assertEquals(UserscriptPageRuntimeState.MATCHED, matched.state)
        assertTrue(matched.detail.orEmpty().contains("@match"))
        assertEquals(UserscriptPageRuntimeState.NOT_MATCHED, excluded.state)
        assertTrue(excluded.detail.orEmpty().contains("@exclude-match"))
    }

    @Test
    fun `site settings denial is reported separately from global authorization`() {
        val status =
            UserscriptPageStatusPolicy.resolve(
                script = userscript(),
                userScriptsAllowed = true,
                siteScriptsAllowed = false,
                pageUrl = "https://example.com/page",
            )

        assertEquals(UserscriptPageRuntimeState.PERMISSION_REQUIRED, status.state)
        assertEquals("当前网站配置已禁用用户脚本", status.detail)
    }

    private fun userscript(): UserscriptListItem =
        UserscriptListItem(
            id = 1L,
            name = "Status",
            namespace = null,
            version = "1.0.0",
            description = null,
            sourceDisplay = null,
            enabled = true,
            unknownGrants = emptyList(),
            blockedReasons = emptyList(),
            executionWorld = UserscriptExecutionWorld.PAGE,
            unsafeWindowMode = UserscriptUnsafeWindowMode.NONE,
            runAt = UserscriptRunAt.DOCUMENT_END,
            grants = listOf("none"),
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
