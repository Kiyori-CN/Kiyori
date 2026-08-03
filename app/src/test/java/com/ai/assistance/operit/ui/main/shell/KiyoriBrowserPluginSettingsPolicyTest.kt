package com.ai.assistance.operit.ui.main.shell

import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptExecutionWorld
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptIconSet
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptInjectInto
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeStatus
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptRunAt
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptSupportState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptUnsafeWindowMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriBrowserPluginSettingsPolicyTest {
    @Test
    fun `plugin settings summarize the real userscript owner`() {
        val script =
            userscript(
                grants = listOf("GM_getValue", "GM_setValue", "GM_xmlhttpRequest"),
                connects = listOf("wenku8.com", "wenku8-relay.mewx.org"),
                matches = listOf("http*://*.wenku8.com/*"),
            )
        val state =
            WebSessionUserscriptUiState(
                supportState = UserscriptSupportState(isSupported = true),
                userScriptsAllowed = true,
                installedScripts = listOf(script),
                currentPageUrl = "https://www.wenku8.com/novel/3/3197/158328.htm",
                currentPageStatuses =
                    mapOf(
                        script.id to
                            UserscriptPageRuntimeStatus(
                                state = UserscriptPageRuntimeState.ERROR,
                                detail = "GM_info.script",
                            ),
                    ),
            )

        assertEquals("1 个插件 · 1 个脚本", browserPluginCenterSummary(state))
        assertEquals("1 个脚本 · 3 项权限 · 3 个范围", browserPluginPermissionSummary(state))
        assertEquals("1 个异常", browserPluginCurrentPageSummary(state))
        assertEquals("1 个异常 · 0 条保留日志", browserPluginDiagnosticsSummary(state))
        assertEquals(
            "隔离世界 · 3 项权限 · 页面：http*://*.wenku8.com/* · " +
                "联网：wenku8.com、wenku8-relay.mewx.org",
            browserPluginScriptPermissionDescription(script),
        )
    }

    @Test
    fun `userscript master switch blocks unsupported enable but still allows revoke`() {
        val entry =
            kiyoriBrowserSettingsGroups
                .flatMap(KiyoriBrowserSettingsGroupSpec::entries)
                .single { item -> item.action == KiyoriBrowserSettingsAction.TOGGLE_USER_SCRIPTS_ALLOWED }

        assertTrue(
            isBrowserSettingRuntimeEnabled(
                entry = entry,
                userscriptState =
                    WebSessionUserscriptUiState(
                        supportState = UserscriptSupportState(isSupported = true),
                    ),
            ),
        )
        assertFalse(
            isBrowserSettingRuntimeEnabled(
                entry = entry,
                userscriptState =
                    WebSessionUserscriptUiState(
                        supportState = UserscriptSupportState(isSupported = false),
                    ),
            ),
        )
        assertEquals(
            "运行环境不可用",
            browserPluginCurrentPageSummary(
                WebSessionUserscriptUiState(
                    supportState = UserscriptSupportState(isSupported = false),
                ),
            ),
        )
        assertTrue(
            isBrowserSettingRuntimeEnabled(
                entry = entry,
                userscriptState =
                    WebSessionUserscriptUiState(
                        supportState = UserscriptSupportState(isSupported = false),
                        userScriptsAllowed = true,
                    ),
            ),
        )
        assertEquals(
            "用户脚本未授权",
            browserPluginCurrentPageSummary(
                WebSessionUserscriptUiState(
                    supportState = UserscriptSupportState(isSupported = true),
                    userScriptsAllowed = false,
                ),
            ),
        )
    }

    private fun userscript(
        grants: List<String>,
        connects: List<String>,
        matches: List<String>,
    ): UserscriptListItem =
        UserscriptListItem(
            id = 539514L,
            name = "轻小说文库+",
            namespace = "https://greasyfork.org/users/667968-pyudng",
            version = "2.31.2",
            description = "轻小说文库体验改善",
            sourceDisplay = "Greasy Fork",
            enabled = true,
            unknownGrants = emptyList(),
            blockedReasons = emptyList(),
            executionWorld = UserscriptExecutionWorld.ISOLATED,
            unsafeWindowMode = UserscriptUnsafeWindowMode.NONE,
            runAt = UserscriptRunAt.DOCUMENT_END,
            grants = grants,
            matches = matches,
            includes = emptyList(),
            excludes = emptyList(),
            excludeMatches = emptyList(),
            connects = connects,
            requires = emptyList(),
            resources = emptyList(),
            homepage = "https://greasyfork.org/scripts/539514",
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
            sourceUrl = "https://update.greasyfork.org/scripts/539514/script.user.js",
            updateUrl = "https://update.greasyfork.org/scripts/539514/script.meta.js",
            downloadUrl = "https://update.greasyfork.org/scripts/539514/script.user.js",
            installedAt = 1L,
            updatedAt = 1L,
        )
}
