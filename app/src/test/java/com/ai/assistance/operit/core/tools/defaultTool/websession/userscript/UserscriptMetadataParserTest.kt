package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import org.junit.Assert.assertEquals
import org.junit.Test

class UserscriptMetadataParserTest {
    @Test
    fun `inject-into content is parsed explicitly`() {
        val metadata =
            UserscriptMetadataParser.parse(
                """
                // ==UserScript==
                // @name        Content world script
                // @inject-into content
                // ==/UserScript==
                """.trimIndent(),
            )

        assertEquals(UserscriptInjectInto.CONTENT, metadata.injectInto)
    }

    @Test
    fun `missing inject-into uses auto policy`() {
        val metadata =
            UserscriptMetadataParser.parse(
                """
                // ==UserScript==
                // @name Missing inject policy
                // ==/UserScript==
                """.trimIndent(),
            )

        assertEquals(UserscriptInjectInto.AUTO, metadata.injectInto)
    }

    @Test
    fun `unknown inject-into is preserved as unsupported`() {
        val metadata =
            UserscriptMetadataParser.parse(
                """
                // ==UserScript==
                // @name Unknown inject policy
                // @inject-into future-world
                // ==/UserScript==
                """.trimIndent(),
            )

        assertEquals(UserscriptInjectInto.UNSUPPORTED, metadata.injectInto)
    }

    @Test
    fun `unknown run-at is preserved as unsupported`() {
        val metadata =
            UserscriptMetadataParser.parse(
                """
                // ==UserScript==
                // @name Unknown run timing
                // @run-at context-menu
                // ==/UserScript==
                """.trimIndent(),
            )

        assertEquals(UserscriptRunAt.UNSUPPORTED, metadata.runAt)
    }
}
