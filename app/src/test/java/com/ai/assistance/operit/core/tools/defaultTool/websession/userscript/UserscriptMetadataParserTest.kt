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

    @Test
    fun `document body run-at is parsed as a supported timing`() {
        val metadata =
            UserscriptMetadataParser.parse(
                """
                // ==UserScript==
                // @name Body timing
                // @run-at document-body
                // ==/UserScript==
                """.trimIndent(),
            )

        assertEquals(UserscriptRunAt.DOCUMENT_BODY, metadata.runAt)
    }

    @Test
    fun `light novel library real metadata keeps matches dependencies resources and permissions`() {
        val metadata =
            UserscriptMetadataParser.parse(
                """
                // ==UserScript==
                // @name               轻小说文库+
                // @namespace          https://greasyfork.org/users/667968-pyudng
                // @version            2.31.2
                // @description        轻小说文库全方位体验改善
                // @author             PY-DNG
                // @homepageURL        https://greasyfork.org/scripts/539514
                // @supportURL         https://greasyfork.org/scripts/539514/feedback
                // @match              http*://*.wenku8.com/*
                // @match              http*://*.wenku8.net/*
                // @match              http*://*.wenku8.cc/*
                // @require            data:application/javascript,window.setImmediate%20%3D%20window.setImmediate
                // @require            https://update.greasyfork.org/scripts/456034/basic.js
                // @require            https://update.greasyfork.org/scripts/471280/url.js
                // @require            https://update.greasyfork.org/scripts/549682/bbcode.js
                // @require            https://fastly.jsdelivr.net/npm/sortablejs@1.15.6/Sortable.min.js
                // @require            https://cdnjs.cloudflare.com/ajax/libs/jszip/3.10.1/jszip.min.js
                // @require            https://fastly.jsdelivr.net/npm/ejs@3.1.9/ejs.min.js
                // @require            https://fastly.jsdelivr.net/npm/jepub@2.1.4/dist/jepub.min.js
                // @require            https://fastly.jsdelivr.net/npm/canvas-confetti@1.9.3/dist/confetti.browser.min.js
                // @require            https://cdnjs.cloudflare.com/ajax/libs/jspdf/3.0.3/jspdf.umd.min.js
                // @require            https://unpkg.com/@popperjs/core@2
                // @require            https://unpkg.com/tippy.js@6
                // @require            https://update.greasyfork.org/scripts/557804/css.js
                // @resource           vue-js https://unpkg.com/vue@3.5.13/dist/vue.global.prod.js
                // @resource           quasar-js https://fastly.jsdelivr.net/npm/quasar@2.15.1/dist/quasar.umd.prod.js
                // @resource           vue-js-bak https://fastly.jsdelivr.net/npm/vue@3.5.13/dist/vue.global.min.js
                // @resource           quasar-js-bak https://unpkg.com/quasar@2.15.1/dist/quasar.umd.prod.js
                // @connect            wenku8.com
                // @connect            wenku8.net
                // @connect            wenku8.cc
                // @connect            777743.xyz
                // @connect            wenku8-relay.mewx.org
                // @grant              GM_getResourceText
                // @grant              GM_registerMenuCommand
                // @grant              GM_setValue
                // @grant              GM_getValue
                // @grant              GM_listValues
                // @grant              GM_deleteValue
                // @grant              GM_addValueChangeListener
                // @grant              GM_removeValueChangeListener
                // @grant              GM_log
                // @grant              GM_addElement
                // @grant              GM_xmlhttpRequest
                // @grant              GM_setClipboard
                // ==/UserScript==
                """.trimIndent(),
            )

        assertEquals("轻小说文库+", metadata.name)
        assertEquals("2.31.2", metadata.version)
        assertEquals("PY-DNG", metadata.author)
        assertEquals("https://greasyfork.org/scripts/539514", metadata.homepage)
        assertEquals(3, metadata.matches.size)
        assertEquals(13, metadata.requires.size)
        assertEquals(4, metadata.resources.size)
        assertEquals(5, metadata.connects.size)
        assertEquals(12, metadata.grants.size)
    }
}
