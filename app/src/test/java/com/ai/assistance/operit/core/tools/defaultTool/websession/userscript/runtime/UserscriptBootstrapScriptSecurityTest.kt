package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptBootstrapScriptSecurityTest {
    @Test
    fun `isolated runtime uses one bridge for the WebView lifetime`() {
        assertTrue(UserscriptBootstrapScript.ISOLATED_BRIDGE_NAME.endsWith("_isolated"))
    }

    @Test
    fun `bootstrap captures bridge and keeps authorization map private`() {
        val source =
            UserscriptBootstrapScript.documentStartScript(
                UserscriptBootstrapScript.ISOLATED_BRIDGE_NAME,
            )

        assertTrue(source.contains("const bridgePostMessage = bridge.postMessage.bind(bridge);"))
        assertTrue(source.contains("const scriptAuthorizationTokens = new Map();"))
        assertTrue(source.contains("normalized.authorizationToken ="))
        assertFalse(source.contains("window.__operitUserscriptRuntime"))
    }

    @Test
    fun `bootstrap does not let script messages own navigation or menu reset`() {
        val source = UserscriptBootstrapScript.documentStartScript()

        assertFalse(source.contains("runtime.post(\"url_change\""))
        assertFalse(source.contains("runtime.post(\"menu_reset\""))
    }

    @Test
    fun `page relay never contains native authorization material`() {
        val source = UserscriptUnsafeWindowBridgeScript.runtimeSource(pageWorldRuntime = true)

        assertTrue(source.contains("__kiyori_userscript_page_request_v1"))
        assertTrue(source.contains("unsafeWindowPageOperation"))
        assertFalse(source.contains("authorizationToken"))
        assertFalse(source.contains(UserscriptBootstrapScript.BRIDGE_NAME))
    }

    @Test
    fun `isolated bootstrap exposes unsafeWindow through page object proxy`() {
        val source =
            UserscriptBootstrapScript.documentStartScript(
                UserscriptBootstrapScript.ISOLATED_BRIDGE_NAME,
            )

        assertTrue(source.contains("createUnsafeWindowProxy()"))
        assertTrue(
            source.contains(
                "hasGrant(\"unsafeWindow\")\n" +
                    "                                ? createUnsafeWindowProxy()",
            ),
        )
        assertTrue(source.contains("__kiyori_userscript_callback_v1"))
        assertTrue(source.contains("unsafeWindowCallbacks"))
    }

    @Test
    fun `page bootstrap passes the original window as unsafeWindow`() {
        val source = UserscriptBootstrapScript.documentStartScript()

        assertTrue(
            source.contains(
                "hasGrant(\"unsafeWindow\")\n" +
                    "                                ? window",
            ),
        )
    }

    @Test
    fun `every script receives local GM info and document body has its own scheduler`() {
        val source = UserscriptBootstrapScript.documentStartScript()

        assertTrue(source.contains("\"document-body\": []"))
        assertTrue(source.contains("installAll(grouped[\"document-body\"])"))
        assertTrue(source.contains("const scriptInfo = makeInfo();"))
        assertTrue(source.contains("gmObject.info = scriptInfo;"))
        assertTrue(source.contains("legacy.GM_info = scriptInfo;"))
        assertFalse(source.contains("if (hasGrant(\"GM.info\") || grantNone)"))
        assertTrue(source.contains("author: metadata.author || null"))
        assertTrue(source.contains("homepageURL: homepageValue"))
        assertTrue(source.contains("\"run-at\": runAtValue"))
        assertTrue(source.contains("grant: grantList"))
    }
}
