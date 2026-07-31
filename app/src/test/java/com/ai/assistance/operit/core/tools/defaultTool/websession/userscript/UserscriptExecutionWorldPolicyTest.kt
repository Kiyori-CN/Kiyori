package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptExecutionWorldPolicyTest {
    private val fullCapabilities =
        UserscriptRuntimeCapabilities(
            pageWorldSupported = true,
            isolatedWorldSupported = true,
        )

    @Test
    fun `grant none uses page world`() {
        val resolution =
            UserscriptExecutionWorldPolicy.resolve(
                metadata = metadata(grants = listOf("none")),
                capabilities = fullCapabilities,
            )

        assertEquals(UserscriptExecutionWorld.PAGE, resolution.world)
        assertEquals(UserscriptUnsafeWindowMode.NONE, resolution.unsafeWindowMode)
        assertTrue(resolution.blockedReasons.isEmpty())
    }

    @Test
    fun `privileged grants use isolated world`() {
        val resolution =
            UserscriptExecutionWorldPolicy.resolve(
                metadata = metadata(grants = listOf("GM.setValue")),
                capabilities = fullCapabilities,
            )

        assertEquals(UserscriptExecutionWorld.ISOLATED, resolution.world)
        assertEquals(UserscriptUnsafeWindowMode.NONE, resolution.unsafeWindowMode)
        assertTrue(resolution.blockedReasons.isEmpty())
    }

    @Test
    fun `content world requires isolated WebView capability`() {
        val resolution =
            UserscriptExecutionWorldPolicy.resolve(
                metadata =
                    metadata(
                        grants = listOf("GM.addStyle"),
                        injectInto = UserscriptInjectInto.CONTENT,
                    ),
                capabilities =
                    UserscriptRuntimeCapabilities(
                        pageWorldSupported = true,
                        isolatedWorldSupported = false,
                    ),
            )

        assertNull(resolution.world)
        assertEquals(
            listOf("Current WebView does not support isolated userscript worlds"),
            resolution.blockedReasons,
        )
    }

    @Test
    fun `page world rejects privileged grants`() {
        val resolution =
            UserscriptExecutionWorldPolicy.resolve(
                metadata =
                    metadata(
                        grants = listOf("GM.xmlHttpRequest"),
                        injectInto = UserscriptInjectInto.PAGE,
                    ),
                capabilities = fullCapabilities,
            )

        assertNull(resolution.world)
        assertEquals(
            listOf("@inject-into page cannot use privileged grants"),
            resolution.blockedReasons,
        )
    }

    @Test
    fun `unsafeWindow without host grants uses page world`() {
        val resolution =
            UserscriptExecutionWorldPolicy.resolve(
                metadata = metadata(grants = listOf("unsafeWindow")),
                capabilities = fullCapabilities,
            )

        assertEquals(UserscriptExecutionWorld.PAGE, resolution.world)
        assertEquals(UserscriptUnsafeWindowMode.DIRECT_PAGE, resolution.unsafeWindowMode)
        assertTrue(resolution.blockedReasons.isEmpty())
    }

    @Test
    fun `unsafeWindow and host grants use isolated page bridge`() {
        val resolution =
            UserscriptExecutionWorldPolicy.resolve(
                metadata = metadata(grants = listOf("unsafeWindow", "GM.setValue")),
                capabilities = fullCapabilities,
            )

        assertEquals(UserscriptExecutionWorld.ISOLATED, resolution.world)
        assertEquals(
            UserscriptUnsafeWindowMode.ISOLATED_PAGE_BRIDGE,
            resolution.unsafeWindowMode,
        )
        assertTrue(resolution.blockedReasons.isEmpty())
    }

    @Test
    fun `content unsafeWindow uses isolated page bridge`() {
        val resolution =
            UserscriptExecutionWorldPolicy.resolve(
                metadata =
                    metadata(
                        grants = listOf("unsafeWindow", "GM.setValue"),
                        injectInto = UserscriptInjectInto.CONTENT,
                    ),
                capabilities = fullCapabilities,
            )

        assertEquals(UserscriptExecutionWorld.ISOLATED, resolution.world)
        assertEquals(
            UserscriptUnsafeWindowMode.ISOLATED_PAGE_BRIDGE,
            resolution.unsafeWindowMode,
        )
        assertTrue(resolution.blockedReasons.isEmpty())
    }

    @Test
    fun `unsafeWindow bridge capability is required for privileged grants`() {
        val resolution =
            UserscriptExecutionWorldPolicy.resolve(
                metadata = metadata(grants = listOf("unsafeWindow", "GM.setValue")),
                capabilities =
                    UserscriptRuntimeCapabilities(
                        pageWorldSupported = true,
                        isolatedWorldSupported = true,
                        unsafeWindowBridgeSupported = false,
                    ),
            )

        assertNull(resolution.world)
        assertEquals(
            listOf(
                "Current WebView cannot bridge unsafeWindow into an isolated userscript world",
            ),
            resolution.blockedReasons,
        )
        assertEquals(UserscriptUnsafeWindowMode.NONE, resolution.unsafeWindowMode)
    }

    @Test
    fun `unsupported inject-into value is blocked`() {
        val resolution =
            UserscriptExecutionWorldPolicy.resolve(
                metadata =
                    metadata(
                        grants = listOf("none"),
                        injectInto = UserscriptInjectInto.UNSUPPORTED,
                    ),
                capabilities = fullCapabilities,
            )

        assertNull(resolution.world)
        assertEquals(
            listOf("Unsupported @inject-into value"),
            resolution.blockedReasons,
        )
    }

    @Test
    fun `unsupported execution metadata is blocked explicitly`() {
        val resolution =
            UserscriptExecutionWorldPolicy.resolve(
                metadata =
                    metadata(grants = listOf("none")).copy(
                        runAt = UserscriptRunAt.UNSUPPORTED,
                        sandbox = "JavaScript",
                        runIn = "normal-tabs",
                        unwrap = true,
                    ),
                capabilities = fullCapabilities,
            )

        assertEquals(UserscriptExecutionWorld.PAGE, resolution.world)
        assertEquals(
            listOf(
                "Unsupported @run-at value",
                "@sandbox is not supported by the current userscript runtime",
                "@run-in is not supported by the current userscript runtime",
                "@unwrap is not supported by the current userscript runtime",
            ),
            resolution.blockedReasons,
        )
    }

    private fun metadata(
        grants: List<String>,
        injectInto: UserscriptInjectInto = UserscriptInjectInto.AUTO,
    ): ParsedUserscriptMetadata =
        ParsedUserscriptMetadata(
            name = "Test",
            grants = grants,
            injectInto = injectInto,
        )
}
